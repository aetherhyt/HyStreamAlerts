package io.patronian.HyStreamerAlerts.manager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AlertDataManager {
    
    private final Path dataFile;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Map<UUID, String> playerBroadcastIds = new HashMap<>();
    
    public AlertDataManager(Path dataFolder) {
        this.dataFile = dataFolder.resolve("alerts.json");
    }
    
    public void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        
        try {
            String content = Files.readString(dataFile, StandardCharsets.UTF_8);
            parseJson(content);
            System.out.println("[HyStreamerAlerts] Loaded " + enabledPlayers.size() + " player alert settings");
        } catch (IOException e) {
            System.out.println("[HyStreamerAlerts] Failed to load alert data: " + e.getMessage());
        }
    }
    
    public void save() {
        try {
            // Ensure parent directory exists
            Files.createDirectories(dataFile.getParent());
            
            String json = toJson();
            Files.writeString(dataFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[HyStreamerAlerts] Failed to save alert data: " + e.getMessage());
        }
    }
    
    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }
    
    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(playerId);
        } else {
            enabledPlayers.remove(playerId);
        }
        save();
    }
    
    public Set<UUID> getEnabledPlayers() {
        return new HashSet<>(enabledPlayers);
    }
    
    /**
     * Gets the Botrix broadcast ID for a player.
     * 
     * @param playerId The player's UUID
     * @return The broadcast ID or null if not set
     */
    public String getBroadcastId(UUID playerId) {
        return playerBroadcastIds.get(playerId);
    }
    
    /**
     * Sets the Botrix broadcast ID for a player.
     * 
     * @param playerId The player's UUID
     * @param broadcastId The Botrix broadcast ID (bid)
     */
    public void setBroadcastId(UUID playerId, String broadcastId) {
        if (broadcastId == null || broadcastId.isEmpty()) {
            playerBroadcastIds.remove(playerId);
        } else {
            playerBroadcastIds.put(playerId, broadcastId);
        }
        save();
    }
    
    /**
     * Gets all players with configured broadcast IDs.
     * 
     * @return Map of player UUIDs to broadcast IDs
     */
    public Map<UUID, String> getAllBroadcastIds() {
        return new HashMap<>(playerBroadcastIds);
    }
    
    /**
     * Checks if a player has a broadcast ID configured.
     * 
     * @param playerId The player's UUID
     * @return true if the player has a broadcast ID
     */
    public boolean hasBroadcastId(UUID playerId) {
        return playerBroadcastIds.containsKey(playerId);
    }
    
    private void parseJson(String json) {
        enabledPlayers.clear();
        playerBroadcastIds.clear();
        
        // Parse enabledPlayers array
        int enabledStart = json.indexOf("\"enabledPlayers\"");
        if (enabledStart != -1) {
            int arrayStart = json.indexOf("[", enabledStart);
            int arrayEnd = findMatchingBracket(json, arrayStart);
            
            if (arrayStart != -1 && arrayEnd != -1) {
                String arrayContent = json.substring(arrayStart + 1, arrayEnd);
                parseUuidArray(arrayContent, enabledPlayers);
            }
        }
        
        // Parse broadcastIds object
        int broadcastStart = json.indexOf("\"broadcastIds\"");
        if (broadcastStart != -1) {
            int objStart = json.indexOf("{", broadcastStart);
            int objEnd = findMatchingBrace(json, objStart);
            
            if (objStart != -1 && objEnd != -1) {
                String objContent = json.substring(objStart + 1, objEnd);
                parseBroadcastIds(objContent);
            }
        }
    }
    
    private int findMatchingBracket(String json, int start) {
        if (start == -1 || json.charAt(start) != '[') return -1;
        int depth = 1;
        for (int i = start + 1; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    private int findMatchingBrace(String json, int start) {
        if (start == -1 || json.charAt(start) != '{') return -1;
        int depth = 1;
        for (int i = start + 1; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    private void parseUuidArray(String arrayContent, Set<UUID> target) {
        int index = 0;
        while (index < arrayContent.length()) {
            int quoteStart = arrayContent.indexOf("\"", index);
            if (quoteStart == -1) break;
            
            int quoteEnd = arrayContent.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) break;
            
            String uuidStr = arrayContent.substring(quoteStart + 1, quoteEnd);
            try {
                target.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUIDs
            }
            
            index = quoteEnd + 1;
        }
    }
    
    private void parseBroadcastIds(String objContent) {
        // Parse key-value pairs like "uuid": "broadcastId"
        int index = 0;
        while (index < objContent.length()) {
            // Find key
            int keyStart = objContent.indexOf("\"", index);
            if (keyStart == -1) break;
            
            int keyEnd = objContent.indexOf("\"", keyStart + 1);
            if (keyEnd == -1) break;
            
            String key = objContent.substring(keyStart + 1, keyEnd);
            
            // Find value
            int valueStart = objContent.indexOf("\"", keyEnd + 1);
            if (valueStart == -1) break;
            
            int valueEnd = objContent.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) break;
            
            String value = objContent.substring(valueStart + 1, valueEnd);
            
            try {
                UUID playerId = UUID.fromString(key);
                playerBroadcastIds.put(playerId, value);
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUIDs
            }
            
            index = valueEnd + 1;
        }
    }
    
    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        // Write enabledPlayers array
        sb.append("  \"enabledPlayers\": [\n");
        int i = 0;
        for (UUID uuid : enabledPlayers) {
            sb.append("    \"").append(uuid.toString()).append("\"");
            if (i < enabledPlayers.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }
        sb.append("  ],\n");
        
        // Write broadcastIds object
        sb.append("  \"broadcastIds\": {\n");
        i = 0;
        for (Map.Entry<UUID, String> entry : playerBroadcastIds.entrySet()) {
            sb.append("    \"").append(entry.getKey().toString()).append("\": \"")
              .append(entry.getValue()).append("\"");
            if (i < playerBroadcastIds.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }
        sb.append("  }\n");
        
        sb.append("}");
        return sb.toString();
    }
}
