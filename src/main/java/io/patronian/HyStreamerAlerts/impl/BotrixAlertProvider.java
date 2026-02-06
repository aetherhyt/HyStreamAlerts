package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.universe.PlayerRef;
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
            
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .buildAsync(URI.create(BOTRIX_WS_URL), this)
                        .whenComplete((ws, error) -> {
                            if (error != null) {
                                scheduleReconnect();
                            } else {
                                this.webSocket = ws;
                            }
                        });
            } catch (Exception e) {
                scheduleReconnect();
            }
        }

        void disconnect() {
            shouldReconnect = false;
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

        private void scheduleReconnect() {
            if (shouldReconnect) {
                scheduler.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            String authMessage = "{\"type\":\"AUTH\",\"bid\":\"" + broadcastId + "\"}";
            webSocket.sendText(authMessage, true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                processMessage(messageBuffer.toString());
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
            this.webSocket = null;
            scheduleReconnect();
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
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
                    handleAlertMessage(message);
                }
            } catch (Exception e) {
                // Log error
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
            if (player == null) return;

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
