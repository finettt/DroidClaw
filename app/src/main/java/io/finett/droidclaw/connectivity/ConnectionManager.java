package io.finett.droidclaw.connectivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for active agent-to-agent connections.
 * <p>
 * Tracks connections by their unique connection ID and by peer address.
 * Provides lookup, removal, and bulk disconnect operations.
 * Thread-safe for concurrent access.
 * </p>
 */
public class ConnectionManager {

    private static ConnectionManager instance;

    private final ConcurrentHashMap<String, AgentConnection> connectionsById;
    private final ConcurrentHashMap<String, String> addressToConnectionId;

    private ConnectionManager() {
        connectionsById = new ConcurrentHashMap<>();
        addressToConnectionId = new ConcurrentHashMap<>();
    }

    /**
     * Returns the singleton ConnectionManager instance.
     */
    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    /**
     * Register a connection and return its connection ID.
     * <p>
     * If the peer address is already tracked, the old connection is not
     * replaced and the new connection is not added. Returns null in that case.
     * </p>
     *
     * @param connection the connection to register
     * @return the connection's ID, or null if a connection with the same
     *         peer address already exists
     */
    public String addConnection(AgentConnection connection) {
        String connId = connection.getConnectionId();
        String address = connection.getPeerAddress();

        if (address != null && !address.isEmpty()) {
            synchronized (this) {
                if (addressToConnectionId.containsKey(address)) {
                    return null;
                }
                connectionsById.put(connId, connection);
                addressToConnectionId.put(address, connId);
            }
        } else {
            connectionsById.put(connId, connection);
        }

        return connId;
    }

    /**
     * Look up a connection by its connection ID.
     *
     * @param connectionId the connection ID
     * @return the connection, or null if not found
     */
    public AgentConnection getConnection(String connectionId) {
        return connectionsById.get(connectionId);
    }

    /**
     * Look up a connection by the peer's address.
     *
     * @param address the peer address
     * @return the connection, or null if not found
     */
    public AgentConnection getByAddress(String address) {
        String connectionId = addressToConnectionId.get(address);
        if (connectionId != null) {
            return connectionsById.get(connectionId);
        }
        return null;
    }

    /**
     * Remove and return a connection by its ID.
     *
     * @param connectionId the connection ID to remove
     * @return the removed connection, or null if not found
     */
    public AgentConnection removeConnection(String connectionId) {
        AgentConnection removed = connectionsById.remove(connectionId);
        if (removed != null) {
            String address = removed.getPeerAddress();
            if (address != null) {
                synchronized (this) {
                    String mappedId = addressToConnectionId.get(address);
                    if (connectionId.equals(mappedId)) {
                        addressToConnectionId.remove(address);
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Returns a snapshot list of all active connections.
     */
    public List<AgentConnection> getAllConnections() {
        return new ArrayList<>(connectionsById.values());
    }

    /**
     * Returns the number of active connections.
     */
    public int getCount() {
        return connectionsById.size();
    }

    /**
     * Disconnect all active connections and clear the manager state.
     */
    public synchronized void disconnectAll() {
        List<AgentConnection> connections = getAllConnections();
        connectionsById.clear();
        addressToConnectionId.clear();
        for (AgentConnection conn : connections) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
                // best-effort disconnect of all connections
            }
        }
    }
}
