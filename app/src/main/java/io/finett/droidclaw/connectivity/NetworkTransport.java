package io.finett.droidclaw.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkTransport implements Transport {

    private static final int DEFAULT_PORT = 9876;

    private final AtomicReference<ServerSocket> serverSocket;
    private final AtomicReference<Thread> acceptThread;
    private final AtomicBoolean serverRunning;

    public NetworkTransport() {
        this.serverSocket = new AtomicReference<>(null);
        this.acceptThread = new AtomicReference<>(null);
        this.serverRunning = new AtomicBoolean(false);
    }

    @Override
    public String getType() {
        return TYPE_TCP;
    }

    @Override
    public String getDisplayName() {
        return "WiFi";
    }

    @Override
    public String getLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "127.0.0.1";
            }
            for (NetworkInterface netIf : Collections.list(interfaces)) {
                if (netIf.isLoopback() || !netIf.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = netIf.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (addr instanceof java.net.Inet4Address) {
                        String hostAddress = addr.getHostAddress();
                        // Filter out link-local addresses
                        if (hostAddress != null && !hostAddress.startsWith("169.254.")) {
                            return hostAddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Fallback to loopback
        }
        return "127.0.0.1";
    }

    @Override
    public void startServer(int port, AcceptCallback callback) throws IOException {
        if (serverRunning.get()) {
            stopServer();
        }

        int actualPort = port > 0 ? port : DEFAULT_PORT;
        ServerSocket ss = new ServerSocket(actualPort);
        serverSocket.set(ss);
        serverRunning.set(true);

        Thread thread = new Thread(() -> {
            while (serverRunning.get() && !ss.isClosed()) {
                try {
                    Socket client = ss.accept();
                    if (!serverRunning.get()) {
                        try {
                            client.close();
                        } catch (IOException ignored) {
                        }
                        break;
                    }
                    String remoteAddress = client.getInetAddress().getHostAddress();
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
                    callback.onAccepted(in, out, remoteAddress);
                } catch (IOException e) {
                    if (serverRunning.get()) {
                        callback.onError(new IOException("Accept error: " + e.getMessage()));
                    }
                    break;
                }
            }
        }, "net-server-accept");
        thread.setDaemon(true);
        thread.start();
        acceptThread.set(thread);
    }

    @Override
    public void stopServer() {
        serverRunning.set(false);
        ServerSocket ss = serverSocket.getAndSet(null);
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        Thread thread = acceptThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isServerRunning() {
        ServerSocket ss = serverSocket.get();
        return ss != null && !ss.isClosed() && serverRunning.get();
    }

    @Override
    public AgentConnection connect(String address, int port) throws IOException {
        // Parse "ip:port" format (e.g., "192.168.1.5:9876")
        String host;
        int remotePort;
        if (address.contains(":")) {
            int colonIndex = address.lastIndexOf(':');
            host = address.substring(0, colonIndex);
            String portStr = address.substring(colonIndex + 1);
            try {
                remotePort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid port in address: " + address);
            }
        } else {
            host = address;
            remotePort = port > 0 ? port : DEFAULT_PORT;
        }

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, remotePort), 5000); // 5 second timeout

        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        return new AgentConnection(in, out, this, address);
    }

    @Override
    public void startDiscovery(DiscoveryCallback callback) {
        // TODO: Add mDNS discovery using JmDNS library
        callback.onError(new UnsupportedOperationException("Network discovery requires manual IP entry"));
    }

    @Override
    public void stopDiscovery() {
        // No-op for now
    }

    @Override
    public boolean supportsDiscovery() {
        return false;
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        };
    }

    @Override
    public boolean isAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiInfo != null && wifiInfo.isConnected();
    }
}
