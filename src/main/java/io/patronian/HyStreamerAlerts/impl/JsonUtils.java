package io.patronian.HyStreamerAlerts.impl;

public class JsonUtils {
    public static String extractJsonValue(String json, String key) {
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
