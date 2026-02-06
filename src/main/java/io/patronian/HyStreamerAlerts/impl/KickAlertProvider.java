package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.patronian.HyStreamerAlerts.api.AlertHandler;
import io.patronian.HyStreamerAlerts.api.AlertProvider;

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

public class KickAlertProvider implements AlertProvider {
    
    private HttpServer server;
    private final Map<UUID, Supplier<PlayerRef>> playerRefSuppliers = new HashMap<>();
    private AlertHandler alertHandler;
    private int port = 8080;

    public void setPort(int port) {
        this.port = port;
    }

    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/webhook/kick", new KickWebhookHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[HyStreamerAlerts] Kick webhook server started on port " + port);
    }

    @Override
    public void setAlertHandler(AlertHandler handler) {
        this.alertHandler = handler;
    }

    @Override
    public void connect(@Nonnull UUID playerId, @Nonnull String connectionId, @Nonnull Supplier<PlayerRef> playerRefSupplier) {
        // ConnectionId not really used for Kick webhook registration as it pushes to us, 
        // but we might need to map streamer ID from payload to player.
        // Assuming connectionId IS the streamer ID or we map by UUID if logic permits.
        // Original code didn't use connectionId for Kick, it just registered the player.
        // But wait, the webhook payload has "streamer_id".
        // Let's assume for now we map just by player ID if possible, or we need to know the streamer ID.
        // The original code used `registerPlayer` with just UUID and Supplier.
        // But the processing logic did: `UUID.fromString(streamerIdStr);` and looked up the supplier.
        // So the Map key MUST be the streamer ID (which is a UUID).
        
        // So `playerId` in the method signature is the Hytale Player UUID.
        // `connectionId` should be the Kick Streamer UUID (as string).
        
        try {
            UUID streamerId = UUID.fromString(connectionId);
             // We map the STREAMER ID to the supplier.
             // We store it.
             // Wait, the original code used `playerId` as key?
             /*
                public void registerPlayer(UUID playerId, Supplier<PlayerRef> playerRefSupplier) {
                    playerRefSuppliers.put(playerId, playerRefSupplier);
                }
                ...
                UUID streamerId = UUID.fromString(streamerIdStr);
                Supplier<PlayerRef> playerRefSupplier = playerRefSuppliers.get(streamerId);
             */
             // So if the map logic from original code is correct, the Hytale Player UUID MUST matched the Streamer ID?
             // Or the user registers with their Streamer ID.
             // Ideally we map StreamerID -> HytalePlayer.
             
             playerRefSuppliers.put(streamerId, playerRefSupplier);
             
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid Kick Streamer UUID provided: " + connectionId);
        }
    }

    @Override
    public void disconnect(@Nonnull UUID playerId) {
        // This is tricky if we keyed by streamer ID.
        // Requires defined bi-directional map or iteration.
        // For simplicity, we iterate.
        playerRefSuppliers.values().removeIf(sup -> {
            PlayerRef ref = sup.get();
            return ref != null && ref.getUuid().equals(playerId);
        });
    }

    @Override
    public boolean isConnected(@Nonnull UUID playerId) {
        // Check if map has value for this player
        return playerRefSuppliers.values().stream().anyMatch(sup -> {
             PlayerRef ref = sup.get();
             return ref != null && ref.getUuid().equals(playerId);
        });
    }

    @Override
    public void shutdown() {
        if (server != null) {
            server.stop(0);
        }
        playerRefSuppliers.clear();
    }

    @Override
    public String getProviderName() {
        return "Kick";
    }

    private class KickWebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
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
        if (alertHandler == null) return;

        try {
            String eventType = JsonUtils.extractJsonValue(payload, "event_type");
            String username = JsonUtils.extractJsonValue(payload, "username");
            String streamerIdStr = JsonUtils.extractJsonValue(payload, "streamer_id");
            
            if (eventType == null || username == null || streamerIdStr == null) return;
            
            UUID streamerId;
            try { streamerId = UUID.fromString(streamerIdStr); } catch (Exception e) { return; }
            
            Supplier<PlayerRef> playerRefSupplier = playerRefSuppliers.get(streamerId);
            if (playerRefSupplier == null) return;
            
            PlayerRef player = playerRefSupplier.get();
            if (player == null || !player.isValid()) return;
            
            switch (eventType.toLowerCase()) {
                case "follow":
                case "follower":
                case "channel.follow":
                    alertHandler.onFollow(player, username, "Kick");
                    break;
                case "subscribe":
                case "subscription":
                case "channel.subscribe":
                    alertHandler.onSubscribe(player, username, 1, "Kick");
                    break;
                // Add more mappings if Kick supports them
            }
        } catch (Exception e) {
            System.out.println("[HyStreamerAlerts] Error processing webhook: " + e.getMessage());
        }
    }
}
