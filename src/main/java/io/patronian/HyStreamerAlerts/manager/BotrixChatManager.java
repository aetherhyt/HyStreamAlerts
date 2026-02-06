package io.patronian.HyStreamerAlerts.manager;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Manages WebSocket connections to Botrix/Pusher for real-time chat overlay.
 * Connects to ws-us2.pusher.com.
 */
public class BotrixChatManager {
    
    // Pusher/Botrix App Key: 32cbd69e4b950bf97679
    private static final String PUSHER_WS_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=7.4.0";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ChatConnection> activeConnections = new ConcurrentHashMap<>();
    
    public BotrixChatManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    public void connectPlayer(@Nonnull UUID playerId, @Nonnull String chatId, 
                              @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        disconnectPlayer(playerId);
        
        ChatConnection connection = new ChatConnection(playerId, chatId, playerRefSupplier);
        activeConnections.put(chatId, connection);
        connection.connect();
        
        System.out.println("[HyStreamerAlerts] Connecting player " + playerId + " to Botrix Chat (ID: " + chatId + ")");
    }
    
    public void disconnectPlayer(@Nonnull UUID playerId) {
        activeConnections.values().removeIf(conn -> {
            if (conn.playerId.equals(playerId)) {
                conn.disconnect();
                return true;
            }
            return false;
        });
    }
    
    public void shutdown() {
        System.out.println("[HyStreamerAlerts] Shutting down Botrix Chat manager...");
        
        for (ChatConnection connection : activeConnections.values()) {
            connection.disconnect();
        }
        activeConnections.clear();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public boolean isConnected(@Nonnull UUID playerId) {
        return activeConnections.values().stream()
                .anyMatch(conn -> conn.playerId.equals(playerId) && conn.isConnected());
    }
    
    private class ChatConnection implements WebSocket.Listener {
        
        private final UUID playerId;
        private final String chatId; // This is the ID part, e.g. "54870857"
        private final Supplier<PlayerRef> playerRefSupplier;
        private WebSocket webSocket;
        private volatile boolean shouldReconnect = true;
        private ScheduledFuture<?> heartbeatTask;
        private final StringBuilder messageBuffer = new StringBuilder();
        
        ChatConnection(UUID playerId, String chatId, Supplier<PlayerRef> playerRefSupplier) {
            this.playerId = playerId;
            this.chatId = chatId;
            this.playerRefSupplier = playerRefSupplier;
        }
        
        void connect() {
            if (!shouldReconnect) return;
            
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .buildAsync(URI.create(PUSHER_WS_URL), this)
                        .whenComplete((ws, error) -> {
                            if (error != null) {
                                System.out.println("[HyStreamerAlerts] Failed to connect to Botrix Chat for " + playerId + ": " + error.getMessage());
                                scheduleReconnect();
                            } else {
                                this.webSocket = ws;
                                startHeartbeat();
                                System.out.println("[HyStreamerAlerts] Connected to Botrix Chat for player " + playerId);
                            }
                        });
            } catch (Exception e) {
                System.out.println("[HyStreamerAlerts] Error connecting to Botrix Chat: " + e.getMessage());
                scheduleReconnect();
            }
        }
        
        void disconnect() {
            shouldReconnect = false;
            stopHeartbeat();
            if (webSocket != null) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
                } catch (Exception ignored) {}
                webSocket = null;
            }
        }
        
        boolean isConnected() {
            return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
        }
        
        private void startHeartbeat() {
            stopHeartbeat();
            heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
                if (isConnected()) {
                    String ping = "{\"event\":\"pusher:ping\",\"data\":{}}";
                    webSocket.sendText(ping, true);
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
        
        private void stopHeartbeat() {
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                heartbeatTask.cancel(false);
            }
            heartbeatTask = null;
        }
        
        @Override
        public void onOpen(WebSocket webSocket) {
             // We wait for pusher:connection_established usually, but we can just subscribe immediately or wait.
             // Pusher usually sends connection_established first.
             webSocket.request(1);
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                
                System.out.println("[HyStreamerAlerts] [DEBUG] RAW WS: " + message);
                handlePusherMessage(message);
            }
            
            webSocket.request(1);
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[HyStreamerAlerts] Chat WebSocket closed for " + playerId + " (code: " + statusCode + ")");
            this.webSocket = null;
            stopHeartbeat();
            scheduleReconnect();
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("[HyStreamerAlerts] Chat WebSocket error for " + playerId + ": " + error.getMessage());
            this.webSocket = null;
            stopHeartbeat();
            scheduleReconnect();
        }
        
        private void scheduleReconnect() {
            if (shouldReconnect) {
                scheduler.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        private void handlePusherMessage(String json) {
            String eventName = extractJsonValue(json, "event");
            if (eventName == null) return;
            
            if (eventName.equals("pusher:connection_established")) {
                 String channel = "chatroom." + chatId + ".v2";
                 String subscribeMsg = "{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"" + channel + "\"}}";
                 if (webSocket != null) webSocket.sendText(subscribeMsg, true);
                 System.out.println("[HyStreamerAlerts] Subscribing to " + channel);
            } 
            else if (eventName.contains("ChatMessageEvent")) {
                String dataStr = extractJsonValue(json, "data");
                if (dataStr != null) {
                    try {
                        // Unescape the JSON string
                        String unescapedData = dataStr.replace("\\\"", "\"").replace("\\\\", "\\");
                        System.out.println("[HyStreamerAlerts] [DEBUG] Chat Event Data: " + unescapedData);

                        String content = extractJsonValue(unescapedData, "content");
                        String sender = extractJsonValue(unescapedData, "name");
                        
                        // Fallback locations for sender name
                        if (sender == null) sender = extractJsonValue(unescapedData, "nick_name");
                        if (sender == null) sender = extractJsonValue(unescapedData, "username");
                        if (sender == null) {
                             // Try to find nested sender object name manually if simple extraction failed
                             // searching for "sender":{"... "name":"FoundIt"
                             // This is a rough heuristic
                             int senderObj = unescapedData.indexOf("\"sender\":");
                             if (senderObj != -1) {
                                 String remaining = unescapedData.substring(senderObj);
                                 sender = extractJsonValue(remaining, "name");
                             }
                        }
                        
                        if (sender == null) sender = "Chat";
                        
                        if (content != null) {
                            displayOverlay(content, sender);
                        }
                    } catch (Exception e) {
                        System.out.println("[HyStreamerAlerts] Error parsing chat event: " + e.getMessage());
                    }
                }
            } else if (eventName.equals("pusher:ping")) {
                // Must pong
                if (webSocket != null) webSocket.sendText("{\"event\":\"pusher:pong\",\"data\":{}}", true);
            }
        }
        
        private void displayOverlay(String message, String author) {
            PlayerRef player = playerRefSupplier.get();
            if (player == null) return;
            
            // Format: [CustomUI] Username: Message
            // Using Hytale color codes: \u00A7
            // b = aqua, f = white, 7 = gray
            String formatted = String.format("\u00A7b[Stream] \u00A77%s: \u00A7f%s", author, message);
            player.sendMessage(Message.raw(formatted));
        }
        
        // Duplicated extractor for now to avoid dependency hell
        private String extractJsonValue(String json, String key) {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) {
                // Try with single quotes? standard JSON is double.
                // It might be nested inside escaped string.
                return null;
            }
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;
            
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= json.length()) return null;
            
            if (json.charAt(valueStart) == '"') {
                int valueEnd = valueStart + 1;
                // Simple string extraction handling escaped quotes
                while (valueEnd < json.length()) {
                    if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd-1) != '\\') {
                        break;
                    }
                    valueEnd++;
                }
                if (valueEnd >= json.length()) return null;
                return json.substring(valueStart + 1, valueEnd);
            } else {
                int valueEnd = valueStart;
                while (valueEnd < json.length() &&
                        json.charAt(valueEnd) != ',' &&
                        json.charAt(valueEnd) != '}' &&
                        !Character.isWhitespace(json.charAt(valueEnd))) {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd);
            }
        }
    }
}
