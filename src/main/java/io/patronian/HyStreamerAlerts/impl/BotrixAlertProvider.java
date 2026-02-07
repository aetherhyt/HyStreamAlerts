package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import io.patronian.HyStreamerAlerts.HyStreamerAlertsPlugin;
import io.patronian.HyStreamerAlerts.api.AlertHandler;
import io.patronian.HyStreamerAlerts.api.AlertProvider;

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

public class BotrixAlertProvider implements AlertProvider {

    private static final String BOTRIX_WS_URL = "wss://sub2.botrix.live/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long RECONNECT_DELAY_MS = 5000;
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, BotrixConnection> activeConnections = new ConcurrentHashMap<>();
    private AlertHandler alertHandler;

    public BotrixAlertProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void setAlertHandler(AlertHandler handler) {
        this.alertHandler = handler;
    }

    @Override
    public void connect(@Nonnull UUID playerId, @Nonnull String connectionId, @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        disconnect(playerId);
        
        BotrixConnection connection = new BotrixConnection(playerId, connectionId, playerRefSupplier);
        activeConnections.put(connectionId, connection);
        connection.connect();
    }

    @Override
    public void disconnect(@Nonnull UUID playerId) {
        activeConnections.values().removeIf(conn -> {
            if (conn.playerId.equals(playerId)) {
                conn.disconnect();
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean isConnected(@Nonnull UUID playerId) {
        return activeConnections.values().stream()
                .anyMatch(conn -> conn.playerId.equals(playerId) && conn.isConnected());
    }

    @Override
    public void shutdown() {
        for (BotrixConnection connection : activeConnections.values()) {
            connection.disconnect();
        }
        activeConnections.clear();
        scheduler.shutdownNow();
    }

    @Override
    public String getProviderName() {
        return "Botrix";
    }

    private class BotrixConnection implements WebSocket.Listener {
        private final UUID playerId;
        private final String broadcastId;
        private final Supplier<PlayerRef> playerRefSupplier;
        private WebSocket webSocket;
        private volatile boolean shouldReconnect = true;
        private final StringBuilder messageBuffer = new StringBuilder();

        BotrixConnection(UUID playerId, String broadcastId, Supplier<PlayerRef> playerRefSupplier) {
            this.playerId = playerId;
            this.broadcastId = broadcastId;
            this.playerRefSupplier = playerRefSupplier;
        }

        void connect() {
            if (!shouldReconnect) return;
            sendDebug("Attempting connection to Alert WebSocket...");
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .buildAsync(URI.create(BOTRIX_WS_URL), this)
                        .whenComplete((ws, error) -> {
                            if (error != null) {
                                sendDebug("Connection failed: " + error.getMessage());
                                scheduleReconnect();
                            } else {
                                this.webSocket = ws;
                            }
                        });
            } catch (Exception e) {
                sendDebug("Exception during connect: " + e.getMessage());
                scheduleReconnect();
            }
        }

        void disconnect() {
            sendDebug("Disconnecting...");
            shouldReconnect = false;
            if (webSocket != null) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
                } catch (Exception ignored) {}
                webSocket = null;
            }
        }
        
        private void sendDebug(String message) {
            String prefix = "[HyStreamerAlerts-DEBUG] ";
            System.out.println(prefix + message);
            
            if (HyStreamerAlertsPlugin.getInstance().isDebugMode()) {
                 PlayerRef player = playerRefSupplier.get();
                 if (player != null && player.isValid()) {
                     player.sendMessage(Message.raw("\u00A78[Debug][BotrixAlert] \u00A77" + message));
                 }
            }
        }

        boolean isConnected() {
            return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
        }

        private void scheduleReconnect() {
            if (shouldReconnect) {
                scheduler.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            String authMessage = "{\"type\":\"AUTH\",\"bid\":\"" + broadcastId + "\"}";
            sendDebug("Sending Alerts Auth: " + authMessage);
            webSocket.sendText(authMessage, true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String fullMessage = messageBuffer.toString();
                // Avoid spamming PING/PONG logs unless critical
                if (!fullMessage.contains("\"type\":\"PING\"") && !fullMessage.contains("\"type\":\"PONG\"")) {
                    sendDebug("Rx: " + fullMessage);
                }
                processMessage(fullMessage);
                messageBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
        
        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.sendPong(message);
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            sendDebug("Alert WebSocket Error: " + error.getMessage());
            this.webSocket = null;
            scheduleReconnect();
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            sendDebug("Alert WebSocket Closed: " + statusCode + " - " + reason);
            this.webSocket = null;
            scheduleReconnect();
            return null;
        }

        private void processMessage(String message) {
            try {
                String type = JsonUtils.extractJsonValue(message, "type");
                if ("PING".equals(type)) {
                    long timestamp = System.currentTimeMillis();
                    String pongMessage = "{\"type\":\"PONG\",\"time\":" + timestamp + "}";
                    if (webSocket != null) webSocket.sendText(pongMessage, true);
                } else if ("MSG".equals(type)) {
                    sendDebug("Alert Received! " + message);
                    handleAlertMessage(message);
                }
            } catch (Exception e) {
                 sendDebug("Error processing alert: " + e.getMessage());
            }
        }

        private void handleAlertMessage(String message) {
            if (alertHandler == null) return;

            String content = JsonUtils.extractJsonValue(message, "content");
            String nickName = JsonUtils.extractJsonValue(message, "nick_name");
            String platform = JsonUtils.extractJsonValue(message, "platform");
            String amountStr = JsonUtils.extractJsonValue(message, "amount");

            if (content == null || nickName == null) return;

            PlayerRef player = playerRefSupplier.get();
            System.out.println(player);
            if (player == null) return;
            System.out.println(content);
            switch (content) {
                case "!follow":
                    alertHandler.onFollow(player, nickName, platform);
                    break;
                case "!sub":
                    int amount = 1;
                    try { amount = Integer.parseInt(amountStr); } catch (Exception ignored) {}
                    alertHandler.onSubscribe(player, nickName, amount, platform);
                    break;
                case "!gift":
                    int giftAmount = 1;
                    try { giftAmount = Integer.parseInt(amountStr); } catch (Exception ignored) {}
                    alertHandler.onGiftSub(player, nickName, giftAmount, platform);
                    break;
                case "!donation":
                case "!tip":
                    alertHandler.onDonation(player, nickName, amountStr, platform);
                    break;
                case "!raid":
                    int viewers = 0;
                    try { viewers = Integer.parseInt(amountStr); } catch (Exception ignored) {}
                    alertHandler.onRaid(player, nickName, viewers, platform);
                    break;
            }
        }
    }
}
