package io.patronian.HyStreamerAlerts;

import io.patronian.HyStreamerAlerts.api.AlertProvider;
import io.patronian.HyStreamerAlerts.api.ChatProvider;
import io.patronian.HyStreamerAlerts.impl.BotrixAlertProvider;
import io.patronian.HyStreamerAlerts.impl.BotrixChatProvider;
import io.patronian.HyStreamerAlerts.impl.KickAlertProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry to hold available streamer service providers.
 */
public class StreamerServiceRegistry {
    
    private final Map<String, AlertProvider> alertProviders = new HashMap<>();
    private final Map<String, ChatProvider> chatProviders = new HashMap<>();
    
    // Default providers
    private AlertProvider defaultAlertProvider;
    private ChatProvider defaultChatProvider;

    public void registerAlertProvider(String name, AlertProvider provider) {
        alertProviders.put(name.toLowerCase(), provider);
        if (defaultAlertProvider == null) defaultAlertProvider = provider;
    }
    
    public void registerChatProvider(String name, ChatProvider provider) {
        chatProviders.put(name.toLowerCase(), provider);
        if (defaultChatProvider == null) defaultChatProvider = provider;
    }
    
    public AlertProvider getAlertProvider(String name) {
        return alertProviders.get(name.toLowerCase());
    }
    
    public ChatProvider getChatProvider(String name) {
        return chatProviders.get(name.toLowerCase());
    }
    
    public AlertProvider getDefaultAlertProvider() {
        return defaultAlertProvider;
    }
    
    public ChatProvider getDefaultChatProvider() {
        return defaultChatProvider;
    }
    
    public void shutdownAll() {
        alertProviders.values().forEach(AlertProvider::shutdown);
        chatProviders.values().forEach(ChatProvider::shutdown);
    }
}
