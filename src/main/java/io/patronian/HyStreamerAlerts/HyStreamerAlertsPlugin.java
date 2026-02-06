package io.patronian.HyStreamerAlerts;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.patronian.HyStreamerAlerts.commands.HyStreamerAlertsCommands;
import io.patronian.HyStreamerAlerts.manager.AlertDataManager;
import io.patronian.HyStreamerAlerts.manager.BotrixWebSocketManager;
import io.patronian.HyStreamerAlerts.manager.BotrixChatManager;
import io.patronian.HyStreamerAlerts.manager.KickWebhookManager;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HyStreamerAlertsPlugin extends JavaPlugin {
    
    private static HyStreamerAlertsPlugin instance;
    private KickWebhookManager kickWebhookManager;
    private BotrixWebSocketManager botrixWebSocketManager;
    private BotrixChatManager botrixChatManager;
    private AlertDataManager alertDataManager;
    
    public HyStreamerAlertsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }
    
    @Override
    public void setup() {
        registerCommands();
        initAlertData();
    }
    
    @Override
    public void shutdown() {
        if (kickWebhookManager != null) {
            kickWebhookManager.stop();
        }
        if (botrixWebSocketManager != null) {
            botrixWebSocketManager.shutdown();
        }
        if (botrixChatManager != null) {
            botrixChatManager.shutdown();
        }
        if (alertDataManager != null) {
            alertDataManager.save();
        }
    }
    
    public static HyStreamerAlertsPlugin getInstance() {
        return instance;
    }
    
    public KickWebhookManager getKickWebhookManager() {
        return kickWebhookManager;
    }
    
    public BotrixWebSocketManager getBotrixWebSocketManager() {
        return botrixWebSocketManager;
    }

    public BotrixChatManager getBotrixChatManager() {
        return botrixChatManager;
    }
    
    public AlertDataManager getAlertDataManager() {
        return alertDataManager;
    }

    private void registerCommands(){
        this.getCommandRegistry().registerCommand(new HyStreamerAlertsCommands());
    }

    private void initAlertData(){
        // Initialize and load alert data
        Path dataFolder = Paths.get("plugins", "HyStreamerAlerts");
        alertDataManager = new AlertDataManager(dataFolder);
        alertDataManager.load();

        // Initialize Botrix WebSocket manager
        botrixWebSocketManager = new BotrixWebSocketManager();
        System.out.println("[HyStreamerAlerts] Botrix WebSocket manager initialized");

        // Initialize Botrix Chat manager
        botrixChatManager = new BotrixChatManager();
        System.out.println("[HyStreamerAlerts] Botrix Chat manager initialized");

        // Start the Kick webhook server on port 8080 (configurable) - kept for backwards compatibility
        kickWebhookManager = new KickWebhookManager(8080);
        try {
            kickWebhookManager.start();
        } catch (IOException e) {
            System.out.println("[HyStreamerAlerts] Failed to start webhook server: " + e.getMessage());
        }
    }
}
