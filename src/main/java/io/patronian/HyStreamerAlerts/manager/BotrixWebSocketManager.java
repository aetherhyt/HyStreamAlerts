package io.patronian.HyStreamerAlerts.manager;

import com.hypixel.hytale.server.core.universe.PlayerRef;

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
 * Manages WebSocket connections to Botrix for real-time stream alerts.
 * Connects to wss://sub2.botrix.live/ and handles the Botrix alert protocol.
 */
public class BotrixWebSocketManager {
    
    private static final String BOTRIX_WS_URL = "wss://sub2.botrix.live/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long RECONNECT_DELAY_MS = 5000;
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PlayerConnection> activeConnections = new ConcurrentHashMap<>();
    
    public BotrixWebSocketManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Connects a player to their Botrix alert stream.
     * 
     * @param playerId The player's UUID
     * @param broadcastId The Botrix broadcast ID (bid)
     * @param playerRefSupplier Supplier to get the player reference for sending alerts
     */
    public void connectPlayer(@Nonnull UUID playerId, @Nonnull String broadcastId, 
                              @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        // Disconnect existing connection if any
        disconnectPlayer(playerId);
        
        PlayerConnection connection = new PlayerConnection(playerId, broadcastId, playerRefSupplier);
        activeConnections.put(broadcastId, connection);
        connection.connect();
        
        System.out.println("[HyStreamerAlerts] Connecting player " + playerId + " to Botrix with bid: " + broadcastId);
    }
    
    /**
     * Disconnects a player from their Botrix alert stream.
     * 
     * @param playerId The player's UUID
     */
    public void disconnectPlayer(@Nonnull UUID playerId) {
        activeConnections.values().removeIf(conn -> {
            if (conn.playerId.equals(playerId)) {
                conn.disconnect();
                return true;
            }
            return false;
        });
    }
    
    /**
     * Disconnects a player by their broadcast ID.
     * 
     * @param broadcastId The Botrix broadcast ID
     */
    public void disconnectByBroadcastId(@Nonnull String broadcastId) {
        PlayerConnection conn = activeConnections.remove(broadcastId);
        if (conn != null) {
            conn.disconnect();
        }
    }
    
    /**
     * Shuts down all connections and the manager.
     */
    public void shutdown() {
        System.out.println("[HyStreamerAlerts] Shutting down Botrix WebSocket manager...");
        
        for (PlayerConnection connection : activeConnections.values()) {
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
        
        System.out.println("[HyStreamerAlerts] Botrix WebSocket manager shut down");
    }
    
    /**
     * Checks if a player is currently connected.
     * 
     * @param playerId The player's UUID
     * @return true if player has an active connection
     */
    public boolean isConnected(@Nonnull UUID playerId) {
        return activeConnections.values().stream()
                .anyMatch(conn -> conn.playerId.equals(playerId) && conn.isConnected());
    }
    
    /**
     * Represents a WebSocket connection for a single player.
     */
    private class PlayerConnection implements WebSocket.Listener {
        
        private final UUID playerId;
        private final String broadcastId;
        private final Supplier<PlayerRef> playerRefSupplier;
        private WebSocket webSocket;
        private volatile boolean shouldReconnect = true;
        private final StringBuilder messageBuffer = new StringBuilder();
        
        PlayerConnection(UUID playerId, String broadcastId, Supplier<PlayerRef> playerRefSupplier) {
            this.playerId = playerId;
            this.broadcastId = broadcastId;
            this.playerRefSupplier = playerRefSupplier;
        }
        
        void connect() {
            if (!shouldReconnect) return;
            
            try {
                // Connect to Botrix WebSocket (auth is sent via message after connection)
                httpClient.newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .buildAsync(URI.create(BOTRIX_WS_URL), this)
                        .whenComplete((ws, error) -> {
                            if (error != null) {
                                System.out.println("[HyStreamerAlerts] Failed to connect to Botrix for " + playerId + ": " + error.getMessage());
                                scheduleReconnect();
                            } else {
                                this.webSocket = ws;
                                System.out.println("[HyStreamerAlerts] Connected to Botrix for player " + playerId);
                            }
                        });
            } catch (Exception e) {
                System.out.println("[HyStreamerAlerts] Error connecting to Botrix: " + e.getMessage());
                scheduleReconnect();
            }
        }
        
        void disconnect() {
            shouldReconnect = false;
            if (webSocket != null) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
                } catch (Exception ignored) {
                    // Ignore close errors
                }
                webSocket = null;
            }
        }
        
        boolean isConnected() {
            return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
        }
        
        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("[HyStreamerAlerts] WebSocket opened for " + playerId);
            
            // Send AUTH message to authenticate with Botrix
            String authMessage = "{\"type\":\"AUTH\",\"bid\":\"" + broadcastId + "\"}";
            webSocket.sendText(authMessage, true);
            System.out.println("[HyStreamerAlerts] [DEBUG] SENT AUTH: " + authMessage);
            
            webSocket.request(1);
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                
                // Debug log the raw message
                System.out.println("[HyStreamerAlerts] [DEBUG] RAW WS MESSAGE: " + message);
                
                processMessage(message);
            }
            
            webSocket.request(1);
            return null;
        }
        
        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            // Respond to WebSocket-level ping
            webSocket.sendPong(message);
            webSocket.request(1);
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[HyStreamerAlerts] WebSocket closed for " + playerId + " (code: " + statusCode + ", reason: " + reason + ")");
            this.webSocket = null;
            scheduleReconnect();
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("[HyStreamerAlerts] WebSocket error for " + playerId + ": " + error.getMessage());
            this.webSocket = null;
            scheduleReconnect();
        }
        
        private void scheduleReconnect() {
            if (shouldReconnect) {
                scheduler.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
        
        private void processMessage(String message) {
            try {
                String type = extractJsonValue(message, "type");
                
                if (type == null) {
                    return;
                }
                
                switch (type) {
                    case "AUTH":
                        // AUTH message received, connection established
                        System.out.println("[HyStreamerAlerts] Authenticated with Botrix for " + playerId);
                        break;
                        
                    case "PING":
                        // Respond to Botrix PING with PONG + timestamp
                        sendPong();
                        break;
                        
                    case "PONG":
                        // Server acknowledgment, ignore
                        break;
                        
                    case "MSG":
                        // Alert message
                        handleAlertMessage(message);
                        break;
                        
                    default:
                        System.out.println("[HyStreamerAlerts] Unknown message type: " + type);
                        break;
                }
            } catch (Exception e) {
                System.out.println("[HyStreamerAlerts] Error processing message: " + e.getMessage());
            }
        }
        
        private void sendPong() {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                long timestamp = System.currentTimeMillis();
                String pongMessage = "{\"type\":\"PONG\",\"time\":" + timestamp + "}";
                webSocket.sendText(pongMessage, true);
            }
        }
        
        private void handleAlertMessage(String message) {
            String content = extractJsonValue(message, "content");
            String nickName = extractJsonValue(message, "nick_name");
            String platform = extractJsonValue(message, "platform");
            String amountStr = extractJsonValue(message, "amount");
            
            if (content == null || nickName == null) {
                return;
            }
            
            PlayerRef playerRef = playerRefSupplier.get();
            if (playerRef == null) {
                return;
            }
            
            // Check if alerts are enabled for this player
            if (!io.patronian.HyStreamerAlerts.commands.HyStreamerAlertsCommands.isEnabled(playerId)) {
                return;
            }
            
            switch (content) {
                case "!follow":
                    KickWebhookManager.showFollowAlert(playerRef, nickName);
                    System.out.println("[HyStreamerAlerts] Follow alert: " + nickName + " on " + platform);
                    break;
                    
                case "!sub":
                    int amount = 1;
                    if (amountStr != null) {
                        try {
                            amount = Integer.parseInt(amountStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    showSubscriptionAlert(playerRef, nickName, amount);
                    System.out.println("[HyStreamerAlerts] Sub alert: " + nickName + " (" + amount + " months) on " + platform);
                    break;
                    
                case "!gift":
                    int giftAmount = 1;
                    if (amountStr != null) {
                        try {
                            giftAmount = Integer.parseInt(amountStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    showGiftSubAlert(playerRef, nickName, giftAmount);
                    System.out.println("[HyStreamerAlerts] Gift sub alert: " + nickName + " gifted " + giftAmount + " on " + platform);
                    break;
                    
                case "!donation":
                case "!tip":
                    showDonationAlert(playerRef, nickName, amountStr);
                    System.out.println("[HyStreamerAlerts] Donation alert: " + nickName + " - " + amountStr);
                    break;
                    
                case "!raid":
                    int viewers = 0;
                    if (amountStr != null) {
                        try {
                            viewers = Integer.parseInt(amountStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    showRaidAlert(playerRef, nickName, viewers);
                    System.out.println("[HyStreamerAlerts] Raid alert: " + nickName + " with " + viewers + " viewers");
                    break;
                    
                default:
                    System.out.println("[HyStreamerAlerts] Unknown alert content: " + content);
                    break;
            }
        }
        
        private void showSubscriptionAlert(PlayerRef playerRef, String subscriberName, int months) {
            String subtitle = months > 1 
                    ? subscriberName + " subscribed for " + months + " months!"
                    : subscriberName + " just subscribed!";
            
            com.hypixel.hytale.server.core.util.EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    com.hypixel.hytale.server.core.Message.raw("New Subscriber!"),
                    com.hypixel.hytale.server.core.Message.raw(subtitle),
                    true
            );
        }
        
        private void showGiftSubAlert(PlayerRef playerRef, String gifterName, int amount) {
            String subtitle = amount > 1
                    ? gifterName + " gifted " + amount + " subs!"
                    : gifterName + " gifted a sub!";
            
            com.hypixel.hytale.server.core.util.EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    com.hypixel.hytale.server.core.Message.raw("Gift Subs!"),
                    com.hypixel.hytale.server.core.Message.raw(subtitle),
                    true
            );
        }
        
        private void showDonationAlert(PlayerRef playerRef, String donorName, String amount) {
            String subtitle = amount != null 
                    ? donorName + " donated " + amount + "!"
                    : donorName + " sent a donation!";
            
            com.hypixel.hytale.server.core.util.EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    com.hypixel.hytale.server.core.Message.raw("Donation!"),
                    com.hypixel.hytale.server.core.Message.raw(subtitle),
                    true
            );
        }
        
        private void showRaidAlert(PlayerRef playerRef, String raiderName, int viewers) {
            String subtitle = viewers > 0
                    ? raiderName + " is raiding with " + viewers + " viewers!"
                    : raiderName + " is raiding!";
            
            com.hypixel.hytale.server.core.util.EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    com.hypixel.hytale.server.core.Message.raw("Incoming Raid!"),
                    com.hypixel.hytale.server.core.Message.raw(subtitle),
                    true
            );
        }
        
        // Simple JSON value extractor (for basic parsing without external libraries)
        private String extractJsonValue(String json, String key) {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) {
                return null;
            }
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) {
                return null;
            }
            
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= json.length()) {
                return null;
            }
            
            if (json.charAt(valueStart) == '"') {
                int valueEnd = json.indexOf("\"", valueStart + 1);
                if (valueEnd == -1) {
                    return null;
                }
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
