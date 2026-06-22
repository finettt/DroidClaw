package io.finett.droidclaw.connectivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * Protocol message exchanged between agent peers over an AgentConnection.
 * <p>
 * Messages use a length-prefixed JSON framing format: 4-byte big-endian
 * length prefix followed by UTF-8 JSON bytes.
 * </p>
 *
 * <h3>Message Types</h3>
 * <ul>
 *   <li>{@link Type#HELLO} — Handshake initiation with PeerInfo</li>
 *   <li>{@link Type#HELLO_ACK} — Handshake acknowledgement with PeerInfo</li>
 *   <li>{@link Type#CHAT} — Text chat between agents</li>
 *   <li>{@link Type#TOOL_CALL} — Remote tool invocation request</li>
 *   <li>{@link Type#TOOL_RESULT} — Result of a remote tool invocation</li>
 *   <li>{@link Type#FILE_TRANSFER_REQUEST} — File transfer initiation</li>
 *   <li>{@link Type#FILE_TRANSFER_ACCEPT} — File transfer accepted</li>
 *   <li>{@link Type#FILE_TRANSFER_REJECT} — File transfer rejected</li>
 *   <li>{@link Type#FILE_CHUNK} — Chunk of file data</li>
 *   <li>{@link Type#FILE_COMPLETE} — File transfer completed</li>
 *   <li>{@link Type#PING} — Keep-alive ping</li>
 *   <li>{@link Type#PONG} — Keep-alive pong response</li>
 *   <li>{@link Type#GOODBYE} — Graceful disconnection</li>
 *   <li>{@link Type#ERROR} — Error notification</li>
 * </ul>
 */
public class AgentMessage {

    /**
     * Enumeration of all supported agent-to-agent message types.
     */
    public enum Type {
        HELLO,
        HELLO_ACK,
        CHAT,
        TOOL_CALL,
        TOOL_RESULT,
        FILE_TRANSFER_REQUEST,
        FILE_TRANSFER_ACCEPT,
        FILE_TRANSFER_REJECT,
        FILE_CHUNK,
        FILE_COMPLETE,
        PING,
        PONG,
        GOODBYE,
        ERROR
    }

    @SerializedName("id")
    private final String id;

    @SerializedName("type")
    private final String type;

    @SerializedName("timestamp")
    private final long timestamp;

    @SerializedName("sender_id")
    private final String senderId;

    @SerializedName("sender_device")
    private final String senderDeviceName;

    @SerializedName("transport")
    private final String transportType;

    @SerializedName("payload")
    private final JsonObject payload;

    private AgentMessage(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.timestamp = builder.timestamp;
        this.senderId = builder.senderId;
        this.senderDeviceName = builder.senderDeviceName;
        this.transportType = builder.transportType;
        this.payload = builder.payload;
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderDeviceName() {
        return senderDeviceName;
    }

    public String getTransportType() {
        return transportType;
    }

    public JsonObject getPayload() {
        return payload;
    }

    // --- Factory methods ---

    /**
     * Creates a HELLO handshake message with local peer information.
     */
    public static AgentMessage createHandshake(PeerInfo localInfo, String transportType) {
        JsonObject payload = new JsonObject();
        payload.add("peer_info", localInfo.toJson() != null
                ? new Gson().toJsonTree(localInfo)
                : new JsonObject());

        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.HELLO.name())
                .timestamp(System.currentTimeMillis())
                .senderId(localInfo.getAgentId())
                .senderDeviceName(localInfo.getDeviceName())
                .transportType(transportType)
                .payload(payload)
                .build();
    }

    /**
     * Creates a HELLO_ACK handshake acknowledgement message.
     */
    public static AgentMessage createHandshakeAck(PeerInfo localInfo, String transportType) {
        JsonObject payload = new JsonObject();
        payload.add("peer_info", new Gson().toJsonTree(localInfo));

        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.HELLO_ACK.name())
                .timestamp(System.currentTimeMillis())
                .senderId(localInfo.getAgentId())
                .senderDeviceName(localInfo.getDeviceName())
                .transportType(transportType)
                .payload(payload)
                .build();
    }

    /**
     * Creates a CHAT message with text content.
     */
    public static AgentMessage createChat(String senderId, String content) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);

        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.CHAT.name())
                .timestamp(System.currentTimeMillis())
                .senderId(senderId)
                .payload(payload)
                .build();
    }

    /**
     * Creates a PING keep-alive message.
     */
    public static AgentMessage createPing() {
        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.PING.name())
                .timestamp(System.currentTimeMillis())
                .payload(new JsonObject())
                .build();
    }

    /**
     * Creates a PONG keep-alive response.
     */
    public static AgentMessage createPong() {
        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.PONG.name())
                .timestamp(System.currentTimeMillis())
                .payload(new JsonObject())
                .build();
    }

    /**
     * Creates a GOODBYE graceful disconnection message.
     */
    public static AgentMessage createGoodbye(String senderId) {
        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.GOODBYE.name())
                .timestamp(System.currentTimeMillis())
                .senderId(senderId)
                .payload(new JsonObject())
                .build();
    }

    /**
     * Creates an ERROR message with the given error description.
     */
    public static AgentMessage createError(String errorMessage) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", errorMessage);

        return new Builder()
                .id(UUID.randomUUID().toString())
                .type(Type.ERROR.name())
                .timestamp(System.currentTimeMillis())
                .payload(payload)
                .build();
    }

    // --- Serialization ---

    /**
     * Serializes this message to its JSON string representation.
     */
    public String toJson() {
        return getGson().toJson(this);
    }

    /**
     * Deserializes an AgentMessage from its JSON string representation.
     *
     * @param json the JSON string
     * @return the deserialized AgentMessage, or null if parsing fails
     */
    public static AgentMessage fromJson(String json) {
        return getGson().fromJson(json, AgentMessage.class);
    }

    private static Gson getGson() {
        return new GsonBuilder()
                .setLenient()
                .create();
    }

    // --- Builder ---

    public static class Builder {
        private String id;
        private String type;
        private long timestamp;
        private String senderId;
        private String senderDeviceName;
        private String transportType;
        private JsonObject payload;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder senderDeviceName(String senderDeviceName) {
            this.senderDeviceName = senderDeviceName;
            return this;
        }

        public Builder transportType(String transportType) {
            this.transportType = transportType;
            return this;
        }

        public Builder payload(JsonObject payload) {
            this.payload = payload;
            return this;
        }

        public AgentMessage build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            if (type == null) {
                throw new IllegalStateException("type must not be null");
            }
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
            if (payload == null) {
                payload = new JsonObject();
            }
            return new AgentMessage(this);
        }
    }
}
