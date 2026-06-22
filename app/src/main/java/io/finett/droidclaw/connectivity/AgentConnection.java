package io.finett.droidclaw.connectivity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an established connection to a remote agent with full protocol
 * support including handshake, keep-alive pings, and framed message exchange.
 * <p>
 * Each connection spawns a reader thread and a writer thread for bidirectional
 * communication. The handshake (HELLO / HELLO_ACK) is initiated automatically
 * when {@link #start()} is called.
 * </p>
 *
 * <h3>Thread safety</h3>
 * All internal state is managed with atomic fields and synchronized blocks.
 * Message sending is thread-safe via the outgoing {@link BlockingQueue}.
 * Callbacks are invoked on the reader/writer daemon threads.
 */
public class AgentConnection {

    /**
     * Connection state enum tracking the lifecycle of a peer connection.
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        HANDSHAKE,
        ACTIVE,
        ERROR
    }

    /**
     * Listener interface for receiving messages and state changes from an AgentConnection.
     */
    public interface MessageListener {
        /**
         * Called when a complete protocol message is received from the peer.
         */
        void onMessageReceived(AgentConnection connection, AgentMessage message);

        /**
         * Called when the connection state changes.
         */
        void onConnectionStateChanged(AgentConnection connection,
                                      ConnectionState newState, ConnectionState oldState);

        /**
         * Called when an error occurs on the connection.
         */
        void onError(AgentConnection connection, String error);
    }

    // --- Constants ---
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static final long PING_INTERVAL_MS = 30_000;
    private static final long PONG_TIMEOUT_MS = 10_000;
    private static final int MAX_PING_RETRIES = 3;

    // --- Identity ---
    private final String connectionId;
    private final AtomicReference<PeerInfo> peerInfo;
    private final Transport transport;
    private final String peerAddress;

    // --- Streams ---
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;

    // --- Threading ---
    private final BlockingQueue<AgentMessage> outgoingQueue;
    private final AtomicReference<Thread> readerThread;
    private final AtomicReference<Thread> writerThread;
    private final AtomicBoolean running;

    // --- State ---
    private final AtomicReference<ConnectionState> state;
    private final AtomicLong lastActivityTimestamp;
    private final Object stateLock;

    // --- Listener ---
    private final AtomicReference<MessageListener> messageListener;

    // --- Ping tracking ---
    private final AtomicLong lastPingSent;
    private final AtomicInteger pingRetries;
    private final Object pingLock;

    /**
     * Constructs a new AgentConnection.
     *
     * @param in            the input stream from the remote peer
     * @param out           the output stream to the remote peer
     * @param transport     the Transport instance that established this connection
     * @param peerAddress   the address of the remote peer (e.g., MAC or IP)
     */
    public AgentConnection(InputStream in, OutputStream out,
                           Transport transport, String peerAddress) {
        this.connectionId = UUID.randomUUID().toString();
        this.peerInfo = new AtomicReference<>(null);
        this.transport = transport;
        this.peerAddress = peerAddress;
        this.inputStream = new DataInputStream(in);
        this.outputStream = new DataOutputStream(out);
        this.outgoingQueue = new LinkedBlockingQueue<>();
        this.readerThread = new AtomicReference<>(null);
        this.writerThread = new AtomicReference<>(null);
        this.running = new AtomicBoolean(false);
        this.state = new AtomicReference<>(ConnectionState.DISCONNECTED);
        this.lastActivityTimestamp = new AtomicLong(System.currentTimeMillis());
        this.stateLock = new Object();
        this.messageListener = new AtomicReference<>(null);
        this.lastPingSent = new AtomicLong(0);
        this.pingRetries = new AtomicInteger(0);
        this.pingLock = new Object();
    }

    // --- Public API ---

    /**
     * Returns the unique identifier for this connection instance.
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Returns the peer info received during the handshake, or null if
     * the handshake has not completed.
     */
    public PeerInfo getPeerInfo() {
        return peerInfo.get();
    }

    /**
     * Returns the address of the remote peer.
     */
    public String getPeerAddress() {
        return peerAddress;
    }

    /**
     * Returns the Transport instance that established this connection.
     */
    public Transport getTransport() {
        return transport;
    }

    /**
     * Returns the current connection state.
     */
    public ConnectionState getState() {
        return state.get();
    }

    /**
     * Returns the timestamp of the last I/O activity on this connection.
     */
    public long getLastActivityTimestamp() {
        return lastActivityTimestamp.get();
    }

    /**
     * Registers a listener for messages and state changes on this connection.
     *
     * @param listener the listener to register, or null to clear
     */
    public void setMessageListener(MessageListener listener) {
        this.messageListener.set(listener);
    }

    /**
     * Start the connection: spawns reader and writer daemon threads and
     * initiates the HELLO handshake.
     * <p>
     * Returns immediately; handshake completion or failure is reported
     * via the registered {@link MessageListener}.
     * </p>
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        setState(ConnectionState.CONNECTING);

        Thread reader = new Thread(this::readerLoop,
                "conn-reader-" + connectionId.substring(0, 8));
        reader.setDaemon(true);
        readerThread.set(reader);

        Thread writer = new Thread(this::writerLoop,
                "conn-writer-" + connectionId.substring(0, 8));
        writer.setDaemon(true);
        writerThread.set(writer);

        reader.start();
        writer.start();

        // Initiate handshake on the current thread
        performHandshake();
    }

    /**
     * Disconnect cleanly by sending a GOODBYE message and releasing all resources.
     * Idempotent -- safe to call multiple times.
     */
    public void disconnect() {
        ConnectionState prevState = state.getAndSet(ConnectionState.DISCONNECTED);
        if (prevState == ConnectionState.DISCONNECTED) {
            return;
        }

        running.set(false);

        // Send GOODBYE if we were past handshake
        if (prevState == ConnectionState.ACTIVE || prevState == ConnectionState.HANDSHAKE) {
            try {
                AgentMessage goodbye = AgentMessage.createGoodbye(
                        peerInfo.get() != null ? peerInfo.get().getAgentId() : "unknown");
                outgoingQueue.offer(goodbye);
            } catch (Exception ignored) {
            }
        }

        // Interrupt threads
        Thread rt = readerThread.getAndSet(null);
        if (rt != null) {
            rt.interrupt();
        }
        Thread wt = writerThread.getAndSet(null);
        if (wt != null) {
            wt.interrupt();
        }

        // Close streams
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
        try {
            outputStream.close();
        } catch (IOException ignored) {
        }

        outgoingQueue.clear();

        notifyStateChange(prevState, ConnectionState.DISCONNECTED);
    }

    /**
     * Enqueue a message to be sent to the peer.
     *
     * @param message the message to send
     */
    public void sendMessage(AgentMessage message) {
        if (!running.get()) {
            notifyError("Connection is not running");
            return;
        }
        outgoingQueue.offer(message);
    }

    /**
     * Check if the connection is alive based on current state.
     *
     * @return true if the connection is in a non-terminal state
     */
    public boolean isAlive() {
        ConnectionState currentState = state.get();
        return currentState == ConnectionState.ACTIVE
                || currentState == ConnectionState.HANDSHAKE
                || currentState == ConnectionState.CONNECTING;
    }

    /**
     * Send a PING to check peer liveness.
     */
    public void sendPing() {
        AgentMessage ping = AgentMessage.createPing();
        outgoingQueue.offer(ping);
    }

    // --- Internal: Handshake ---

    private void performHandshake() {
        setState(ConnectionState.HANDSHAKE);

        // Build local PeerInfo from transport
        String agentId = peerInfo.get() != null
                ? peerInfo.get().getAgentId()
                : UUID.randomUUID().toString();

        PeerInfo localInfo = new PeerInfo(
                agentId,
                transport.getDisplayName(),
                transport.getLocalAddress(),
                transport.getType(),
                "1.0",
                null,
                null
        );

        AgentMessage hello = AgentMessage.createHandshake(localInfo, transport.getType());
        outgoingQueue.offer(hello);

        // Wait for HELLO_ACK with timeout
        long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        synchronized (stateLock) {
            while (running.get() && state.get() == ConnectionState.HANDSHAKE) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    notifyError("Handshake timeout: no HELLO_ACK received within "
                            + HANDSHAKE_TIMEOUT_MS + "ms");
                    disconnect();
                    return;
                }
                try {
                    stateLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    disconnect();
                    return;
                }
            }
        }
    }

    private void handleHandshakeMessage(AgentMessage msg) {
        if (AgentMessage.Type.HELLO.name().equals(msg.getType())) {
            // Received HELLO from peer -- send HELLO_ACK
            PeerInfo remotePeer = extractPeerInfo(msg);
            if (remotePeer != null) {
                peerInfo.set(remotePeer);
            }

            String agentId = peerInfo.get() != null
                    ? peerInfo.get().getAgentId()
                    : "agent";

            PeerInfo localInfo = new PeerInfo(
                    agentId,
                    transport.getDisplayName(),
                    transport.getLocalAddress(),
                    transport.getType(),
                    "1.0",
                    null,
                    null
            );
            AgentMessage ack = AgentMessage.createHandshakeAck(localInfo, transport.getType());
            outgoingQueue.offer(ack);

            setState(ConnectionState.ACTIVE);
            // Notify performHandshake() on this connection that state changed
            synchronized (stateLock) {
                stateLock.notifyAll();
            }
            notifyStateChange(ConnectionState.HANDSHAKE, ConnectionState.ACTIVE);

        } else if (AgentMessage.Type.HELLO_ACK.name().equals(msg.getType())) {
            // Received HELLO_ACK -- handshake complete
            PeerInfo remotePeer = extractPeerInfo(msg);
            if (remotePeer != null) {
                peerInfo.set(remotePeer);
            }

            setState(ConnectionState.ACTIVE);
            synchronized (stateLock) {
                stateLock.notifyAll();
            }
            notifyStateChange(ConnectionState.HANDSHAKE, ConnectionState.ACTIVE);
        }
    }

    private PeerInfo extractPeerInfo(AgentMessage msg) {
        try {
            if (msg.getPayload() != null && msg.getPayload().has("peer_info")) {
                return new Gson().fromJson(msg.getPayload().get("peer_info"), PeerInfo.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // --- Internal: Reader Loop ---

    private void readerLoop() {
        try {
            while (running.get()) {
                // Read 4-byte length prefix (big-endian)
                int length;
                try {
                    length = inputStream.readInt();
                } catch (EOFException e) {
                    // Peer closed connection cleanly
                    break;
                }

                if (length <= 0 || length > 256 * 1024) {
                    notifyError("Invalid message length: " + length);
                    disconnect();
                    return;
                }

                // Read the JSON payload
                byte[] jsonBytes = new byte[length];
                int totalRead = 0;
                while (totalRead < length) {
                    int read = inputStream.read(jsonBytes, totalRead, length - totalRead);
                    if (read == -1) {
                        throw new EOFException("Stream closed during message read");
                    }
                    totalRead += read;
                }

                String json = new String(jsonBytes, StandardCharsets.UTF_8);
                lastActivityTimestamp.set(System.currentTimeMillis());

                // Parse message
                AgentMessage message;
                try {
                    message = AgentMessage.fromJson(json);
                } catch (Exception e) {
                    notifyError("Failed to parse message: " + e.getMessage());
                    continue;
                }

                if (message == null) {
                    continue;
                }

                // Handle protocol-level messages
                String type = message.getType();
                if (AgentMessage.Type.PING.name().equals(type)) {
                    AgentMessage pong = AgentMessage.createPong();
                    outgoingQueue.offer(pong);
                    continue;
                } else if (AgentMessage.Type.PONG.name().equals(type)) {
                    synchronized (pingLock) {
                        pingRetries.set(0);
                        pingLock.notifyAll();
                    }
                    continue;
                } else if (AgentMessage.Type.GOODBYE.name().equals(type)) {
                    disconnect();
                    return;
                } else if (AgentMessage.Type.HELLO.name().equals(type)
                        || AgentMessage.Type.HELLO_ACK.name().equals(type)) {
                    handleHandshakeMessage(message);
                    continue;
                }

                // Dispatch to listener
                MessageListener listener = messageListener.get();
                if (listener != null) {
                    listener.onMessageReceived(this, message);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                notifyError("Reader error: " + e.getMessage());
            }
        } finally {
            if (running.get()) {
                disconnect();
            }
        }
    }

    // --- Internal: Writer Loop ---

    private void writerLoop() {
        long lastPingCheck = System.currentTimeMillis();

        try {
            while (running.get()) {
                // Check if we need to send a ping
                long now = System.currentTimeMillis();
                if (state.get() == ConnectionState.ACTIVE
                        && (now - lastPingCheck) >= PING_INTERVAL_MS) {
                    lastPingCheck = now;
                    handlePingCycle();
                }

                // Poll for outgoing messages with a timeout so we can also check pings
                AgentMessage message = outgoingQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }

                String json = message.toJson();
                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

                synchronized (outputStream) {
                    // Write 4-byte length prefix (big-endian)
                    outputStream.writeInt(jsonBytes.length);
                    // Write JSON bytes
                    outputStream.write(jsonBytes);
                    outputStream.flush();
                }

                lastActivityTimestamp.set(System.currentTimeMillis());

                // Track sent PINGs
                if (AgentMessage.Type.PING.name().equals(message.getType())) {
                    lastPingSent.set(System.currentTimeMillis());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (running.get()) {
                notifyError("Writer error: " + e.getMessage());
            }
        } finally {
            if (running.get()) {
                disconnect();
            }
        }
    }

    // --- Internal: Ping Cycle ---

    private void handlePingCycle() {
        synchronized (pingLock) {
            if (pingRetries.get() >= MAX_PING_RETRIES) {
                notifyError("No PONG received after " + MAX_PING_RETRIES + " retries");
                disconnect();
                return;
            }

            AgentMessage ping = AgentMessage.createPing();
            outgoingQueue.offer(ping);
            pingRetries.incrementAndGet();
            lastPingSent.set(System.currentTimeMillis());

            try {
                pingLock.wait(PONG_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- Internal: State management ---

    private void setState(ConnectionState newState) {
        ConnectionState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            notifyStateChange(oldState, newState);
        }
    }

    private void notifyStateChange(ConnectionState oldState, ConnectionState newState) {
        MessageListener listener = messageListener.get();
        if (listener != null) {
            listener.onConnectionStateChanged(this, newState, oldState);
        }
    }

    private void notifyError(String error) {
        state.compareAndSet(ConnectionState.ACTIVE, ConnectionState.ERROR);
        MessageListener listener = messageListener.get();
        if (listener != null) {
            listener.onError(this, error);
        }
    }
}
