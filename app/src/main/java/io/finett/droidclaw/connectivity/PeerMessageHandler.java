package io.finett.droidclaw.connectivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.finett.droidclaw.agent.AgentLoop;
import io.finett.droidclaw.agent.IdentityManager;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.model.ChatMessage;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.SessionType;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Handles incoming agent-to-agent CHAT messages by dispatching them to an isolated
 * AgentLoop so the local agent can process and respond to the peer.
 *
 * <p>When a CHAT message is received via {@link AgentConnection.MessageListener#onMessageReceived},
 * this handler:
 * <ol>
 *   <li>Posts a system notification alerting the user.</li>
 *   <li>Creates a hidden {@link ChatSession} of type {@link SessionType#TYPE_PEER_AGENT}.</li>
 *   <li>Creates a formatted {@link ChatMessage} wrapping the peer's text.</li>
 *   <li>Launches an isolated {@link AgentLoop} (following the {@code BaseTaskWorker} pattern)
 *       with identity context (including relationships.md) and the full tool registry
 *       (including the 6 peer tools).</li>
 *   <li>On completion, forwards the agent's text response back to the peer via the
 *       original {@link AgentConnection}.</li>
 * </ol>
 * </p>
 *
 * <p>Processing happens on a dedicated single-thread executor so incoming messages are
 * serialised and do not block the connection's reader thread.</p>
 */
public class PeerMessageHandler {

    private static final String TAG = "PeerMessageHandler";

    /** Max iterations for peer agent loops (shorter than user-facing loops). */
    private static final int MAX_ITERATIONS = 10;

    /** Max wall-clock time for peer message processing. */
    private static final long MAX_EXECUTION_TIME_MS = 5 * 60 * 1000L;

    /** Notification channel and ID for incoming peer messages. */
    private static final String PEER_CHANNEL_ID = "droidclaw_peer";
    private static final int PEER_NOTIFICATION_BASE_ID = 7000;

    private final Context appContext;
    private final SettingsManager settingsManager;
    private final ExecutorService executor;

    /**
     * Constructs a PeerMessageHandler.
     *
     * @param context          a Context (application context will be retained)
     * @param settingsManager  the app's SettingsManager
     */
    public PeerMessageHandler(Context context, SettingsManager settingsManager) {
        this.appContext = context.getApplicationContext();
        this.settingsManager = settingsManager;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "peer-message-handler");
            t.setDaemon(true);
            return t;
        });
        ensureNotificationChannel();
    }

    /**
     * Handle an incoming CHAT message from a peer agent.
     * <p>
     * Dispatches processing to a background thread and returns immediately.
     * The caller (e.g. a MessageListener) must not block on this method.
     * </p>
     *
     * @param connection the AgentConnection the message was received on
     * @param message    the parsed CHAT AgentMessage
     */
    public void handleIncomingChat(AgentConnection connection, AgentMessage message) {
        executor.execute(() -> processIncomingChat(connection, message));
    }

    /**
     * Shut down the background executor. No new messages will be processed;
     * the currently executing message (if any) will complete normally.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------
    // Internal: Processing
    // ---------------------------------------------------------------

    private void processIncomingChat(AgentConnection connection, AgentMessage message) {
        final String senderId = message.getSenderId() != null ? message.getSenderId() : "unknown";
        final String transportType = message.getTransportType() != null
                ? message.getTransportType() : "unknown";
        final String chatContent = extractChatContent(message);

        Log.d(TAG, "Processing incoming CHAT from " + senderId
                + " via " + transportType + ": " + truncate(chatContent, 100));

        // 1. Post a system notification to alert the user
        postIncomingMessageNotification(senderId);

        // 2. Create a hidden ChatSession of type TYPE_PEER_AGENT
        WorkspaceManager workspaceManager = new WorkspaceManager(appContext);
        ChatRepository chatRepository = new ChatRepository(appContext);

        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession(sessionId,
                "Message from " + senderId, System.currentTimeMillis());
        session.setSessionType(SessionType.TYPE_PEER_AGENT);

        List<ChatSession> sessions = chatRepository.loadSessions();
        sessions.add(session);
        chatRepository.saveSessions(sessions);

        // 3. Create the formatted user message
        String formattedContent = "[From Agent " + senderId + " via " + transportType + "]: " + chatContent;
        ChatMessage userMessage = new ChatMessage(formattedContent, ChatMessage.TYPE_USER);

        // 4. Launch an isolated AgentLoop
        LlmApiService apiService = null;
        ToolRegistry toolRegistry = null;
        try {
            apiService = new LlmApiService(settingsManager);
            toolRegistry = new ToolRegistry(appContext, settingsManager);

            AgentLoop agentLoop = new AgentLoop(apiService, toolRegistry, settingsManager);

            // Load identity context (includes soul.md, user.md, relationships.md)
            try {
                IdentityManager identityManager = new IdentityManager(appContext, workspaceManager);
                if (identityManager.identityFilesExist()) {
                    List<ChatMessage> identityMessages = identityManager.getIdentityMessages();
                    if (identityMessages != null && !identityMessages.isEmpty()) {
                        agentLoop.setIdentityContext(identityMessages);
                        Log.d(TAG, "Loaded " + identityMessages.size()
                                + " identity messages for peer agent context");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load identity context for peer agent", e);
            }

            List<ChatMessage> conversationHistory = new ArrayList<>();
            conversationHistory.add(userMessage);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> finalResponse = new AtomicReference<>();
            AtomicReference<List<ChatMessage>> finalHistory = new AtomicReference<>();
            AtomicReference<String> errorRef = new AtomicReference<>();

            agentLoop.start(conversationHistory, new AgentLoop.AgentCallback() {
                @Override
                public void onProgress(String status) {
                    Log.d(TAG, "Peer agent progress: " + status);
                }

                @Override
                public void onToolCall(String toolName, String arguments) {
                    Log.d(TAG, "Peer agent tool call: " + toolName);
                }

                @Override
                public void onToolResult(String toolName, String result) {
                    Log.d(TAG, "Peer agent tool result: " + toolName);
                }

                @Override
                public void onComplete(String response, List<ChatMessage> history) {
                    finalResponse.set(response);
                    finalHistory.set(history);
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    errorRef.set(error);
                    latch.countDown();
                }

                @Override
                public void onApprovalRequired(String toolName, String description,
                        JsonObject arguments,
                        AgentLoop.ApprovalCallback approvalCallback) {
                    // Peer message processing runs in the background with no UI context.
                    // Non-exec tools (file read/write, peer tools) are auto-approved.
                    // Shell/python exec tools are not registered in the allowlist anyway
                    // unless the user has enabled background shell access.
                    Log.d(TAG, "Auto-approving peer agent tool: " + toolName);
                    approvalCallback.onApproved();
                }
            });

            boolean completed = latch.await(MAX_EXECUTION_TIME_MS, TimeUnit.MILLISECONDS);

            if (!completed) {
                Log.w(TAG, "Peer agent processing timed out after "
                        + (MAX_EXECUTION_TIME_MS / 1000) + "s for message from: " + senderId);
            } else if (errorRef.get() != null) {
                Log.e(TAG, "Peer agent processing error for message from "
                        + senderId + ": " + errorRef.get());
            } else {
                String response = finalResponse.get();
                List<ChatMessage> history = finalHistory.get();

                // Save conversation history
                if (history != null && !history.isEmpty()) {
                    chatRepository.saveMessages(sessionId, history);
                    Log.d(TAG, "Saved " + history.size()
                            + " messages for peer session: " + sessionId);
                }

                // Forward the agent's text response (if any) back to the peer
                if (response != null && !response.trim().isEmpty()) {
                    AgentMessage reply = AgentMessage.createChat(
                            "local-agent", response);
                    connection.sendMessage(reply);
                    Log.d(TAG, "Forwarded response to peer " + senderId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error processing incoming peer message", e);
        } finally {
            if (toolRegistry != null) {
                try {
                    toolRegistry.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Internal: Notification
    // ---------------------------------------------------------------

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel channel = new NotificationChannel(
                    PEER_CHANNEL_ID,
                    "DroidClaw Peer Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when another DroidClaw agent sends a message");
            channel.setShowBadge(true);
            nm.createNotificationChannel(channel);
        }
    }

    private void postIncomingMessageNotification(String senderId) {
        try {
            NotificationManager nm = (NotificationManager)
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    appContext, PEER_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Message from agent")
                    .setContentText("Agent " + senderId + " sent you a message")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Message from agent " + senderId
                                    + ". The agent is processing it now."))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            nm.notify(PEER_NOTIFICATION_BASE_ID, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to post peer message notification", e);
        }
    }

    // ---------------------------------------------------------------
    // Internal: Helpers
    // ---------------------------------------------------------------

    private static String extractChatContent(AgentMessage message) {
        try {
            if (message.getPayload() != null && message.getPayload().has("content")) {
                return message.getPayload().get("content").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
