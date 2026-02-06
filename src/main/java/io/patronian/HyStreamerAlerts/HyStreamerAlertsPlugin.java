package io.patronian.HyStreamerAlerts;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.patronian.HyStreamerAlerts.commands.HyStreamerAlertsCommands;
import io.patronian.HyStreamerAlerts.manager.AlertDataManager;
import io.patronian.HyStreamerAlerts.impl.BotrixAlertProvider;
import io.patronian.HyStreamerAlerts.impl.BotrixChatProvider;
import io.patronian.HyStreamerAlerts.impl.HytaleAlertHandler;
import io.patronian.HyStreamerAlerts.impl.HytaleChatHandler;
import io.patronian.HyStreamerAlerts.impl.KickAlertProvider;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HyStreamerAlertsPlugin extends JavaPlugin {
    
    private static HyStreamerAlertsPlugin instance;
    private StreamerServiceRegistry serviceRegistry;
    private AlertDataManager alertDataManager;
    
    public HyStreamerAlertsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }
    
    @Override
    public void setup() {
        initServices();
        registerCommands();
    }
    
    @Override
    public void shutdown() {
        if (serviceRegistry != null) {
            serviceRegistry.shutdownAll();
        }
        if (alertDataManager != null) {
            alertDataManager.save();
        }
        System.out.println("[HyStreamerAlerts] Plugin shutdown complete");
    }
    
    public static HyStreamerAlertsPlugin getInstance() {
        return instance;
    }
    
    public StreamerServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
    
    public AlertDataManager getAlertDataManager() {
        return alertDataManager;
    }

    private void registerCommands(){
        this.getCommandRegistry().registerCommand(new HyStreamerAlertsCommands());
    }

    private void initServices(){
        // Initialize and load alert data
        Path dataFolder = Paths.get("plugins", "HyStreamerAlerts");
        alertDataManager = new AlertDataManager(dataFolder);
        alertDataManager.load();
        
        // Initialize Service Registry
        serviceRegistry = new StreamerServiceRegistry();

        // Initialize Botrix Provider and Handler
        BotrixAlertProvider botrixAlerts = new BotrixAlertProvider();
        botrixAlerts.setAlertHandler(new HytaleAlertHandler());
        serviceRegistry.registerAlertProvider("botrix", botrixAlerts);
        System.out.println("[HyStreamerAlerts] Botrix Alert Provider initialized");

        BotrixChatProvider botrixChat = new BotrixChatProvider();
        botrixChat.setChatHandler(new HytaleChatHandler());
        serviceRegistry.registerChatProvider("botrix", botrixChat);
        System.out.println("[HyStreamerAlerts] Botrix Chat Provider initialized");

        // Initialize Kick Provider (Server)
        KickAlertProvider kickAlerts = new KickAlertProvider();
        kickAlerts.setAlertHandler(new HytaleAlertHandler());
        try {
            kickAlerts.startServer();
            serviceRegistry.registerAlertProvider("kick", kickAlerts);
            System.out.println("[HyStreamerAlerts] Kick Alert Provider initialized");
        } catch (IOException e) {
            System.out.println("[HyStreamerAlerts] Failed to start Kick webhook server: " + e.getMessage());
        }
    }
}
