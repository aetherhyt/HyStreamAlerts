package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.patronian.HyStreamerAlerts.api.ChatHandler;
import io.patronian.HyStreamerAlerts.api.ChatProvider;

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

public class BotrixChatProvider implements ChatProvider {
    
    private static final String PUSHER_WS_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=7.4.0";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long HEARTBEAT_INTERVAL_MS = 30000;

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ChatConnection> activeConnections = new ConcurrentHashMap<>();
    private ChatHandler chatHandler;

    public BotrixChatProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void setChatHandler(ChatHandler handler) {
        this.chatHandler = handler;
    }

    @Override
    public void connect(@Nonnull UUID playerId, @Nonnull String connectionId, @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        disconnect(playerId);
        ChatConnection connection = new ChatConnection(playerId, connectionId, playerRefSupplier);
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
        for (ChatConnection connection : activeConnections.values()) {
            connection.disconnect();
        }
        activeConnections.clear();
        scheduler.shutdownNow();
    }

    @Override
    public String getProviderName() {
        return "BotrixChat";
    }

    private class ChatConnection implements WebSocket.Listener {
        private final UUID playerId;
        private final String chatId;
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
                                scheduleReconnect();
                            } else {
                                this.webSocket = ws;
                                startHeartbeat();
                            }
                        });
            } catch (Exception e) {
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
                    if (webSocket != null) webSocket.sendText(ping, true);
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void stopHeartbeat() {
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                heartbeatTask.cancel(false);
            }
        }

        private void scheduleReconnect() {
            if (shouldReconnect) {
                scheduler.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
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
        public void onError(WebSocket webSocket, Throwable error) {
            this.webSocket = null;
            stopHeartbeat();
            scheduleReconnect();
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            this.webSocket = null;
            stopHeartbeat();
            scheduleReconnect();
            return null;
        }

        private void processMessage(String json) {
            String eventName = JsonUtils.extractJsonValue(json, "event");
            if (eventName == null) return;

            if (eventName.equals("pusher:connection_established")) {
                 String channel = "chatroom." + chatId + ".v2";
                 String subscribeMsg = "{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"" + channel + "\"}}";
                 if (webSocket != null) webSocket.sendText(subscribeMsg, true);
            } else if (eventName.contains("ChatMessageEvent")) {
                handleChatEvent(json);
            } else if (eventName.equals("pusher:ping")) {
                 if (webSocket != null) webSocket.sendText("{\"event\":\"pusher:pong\",\"data\":{}}", true);
            }
        }

        private void handleChatEvent(String json) {
            if (chatHandler == null) return;
            
            String dataStr = JsonUtils.extractJsonValue(json, "data");
            if (dataStr == null) return;
            
            try {
                String unescapedData = dataStr.replace("\\\"", "\"").replace("\\\\", "\\");
                String content = JsonUtils.extractJsonValue(unescapedData, "content");
                String sender = JsonUtils.extractJsonValue(unescapedData, "name");
                
                if (sender == null) sender = JsonUtils.extractJsonValue(unescapedData, "nick_name");
                if (sender == null) sender = JsonUtils.extractJsonValue(unescapedData, "username");
                if (sender == null) sender = "Chat";

                PlayerRef player = playerRefSupplier.get();
                if (content != null && player != null) {
                    chatHandler.onMessage(player, sender, content, "Botrix");
                }
            } catch (Exception e) {
                // Log failed parse
            }
        }
    }
}
