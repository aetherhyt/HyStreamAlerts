package io.patronian.HyStreamerAlerts.manager;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.patronian.HyStreamerAlerts.commands.HyStreamerAlertsCommands;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class KickWebhookManager {
    
    private HttpServer server;
    private final int port;
    private final Map<UUID, Supplier<PlayerRef>> playerRefSuppliers = new HashMap<>();
    
    public KickWebhookManager(int port) {
        this.port = port;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/webhook/kick", new KickWebhookHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[HyStreamerAlerts] Kick webhook server started on port " + port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[HyStreamerAlerts] Kick webhook server stopped");
        }
    }
    
    public void registerPlayer(@Nonnull UUID playerId, @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        playerRefSuppliers.put(playerId, playerRefSupplier);
    }
    
    public void unregisterPlayer(@Nonnull UUID playerId) {
        playerRefSuppliers.remove(playerId);
    }
    
    public static void showFollowAlert(PlayerRef playerRef, String followerName) {
        if (HyStreamerAlertsCommands.isEnabled(playerRef.getUuid())) {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    Message.raw("New Follower!"),
                    Message.raw(followerName + " just followed!"),
                    true
            );
        }
    }
    
    public static void showSubscribeAlert(PlayerRef playerRef, String subscriberName) {
        if (HyStreamerAlertsCommands.isEnabled(playerRef.getUuid())) {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    Message.raw("New Subscriber!"),
                    Message.raw(subscriberName + " just subscribed!"),
                    true
            );
        }
    }
    
    private class KickWebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            // Read the request body
            String requestBody;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                requestBody = sb.toString();
            }
            
            // Process the webhook payload
            processWebhook(requestBody);
            
            sendResponse(exchange, 200, "OK");
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
    
    private void processWebhook(String payload) {
        // Parse the Kick webhook payload
        // Kick webhooks typically contain event type and data
        // This is a simplified parser - adjust based on actual Kick webhook format
        
        try {
            String eventType = extractJsonValue(payload, "event_type");
            String username = extractJsonValue(payload, "username");
            String streamerIdStr = extractJsonValue(payload, "streamer_id");
            
            if (eventType == null || username == null || streamerIdStr == null) {
                System.out.println("[HyStreamerAlerts] Invalid webhook payload received");
                return;
            }
            
            UUID streamerId;
            try {
                streamerId = UUID.fromString(streamerIdStr);
            } catch (IllegalArgumentException e) {
                System.out.println("[HyStreamerAlerts] Invalid streamer UUID in payload");
                return;
            }
            
            Supplier<PlayerRef> playerRefSupplier = playerRefSuppliers.get(streamerId);
            if (playerRefSupplier == null) {
                return;
            }
            
            PlayerRef playerRef = playerRefSupplier.get();
            if (playerRef == null) {
                return;
            }
            
            switch (eventType.toLowerCase()) {
                case "follow":
                case "follower":
                case "channel.follow":
                    showFollowAlert(playerRef, username);
                    break;
                case "subscribe":
                case "subscription":
                case "channel.subscribe":
                    showSubscribeAlert(playerRef, username);
                    break;
                default:
                    System.out.println("[HyStreamerAlerts] Unknown event type: " + eventType);
                    break;
            }
        } catch (Exception e) {
            System.out.println("[HyStreamerAlerts] Error processing webhook: " + e.getMessage());
        }
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
