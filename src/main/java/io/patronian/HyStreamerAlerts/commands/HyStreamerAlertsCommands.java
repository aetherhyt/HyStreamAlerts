package io.patronian.HyStreamerAlerts.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.patronian.HyStreamerAlerts.HyStreamerAlertsPlugin;
import io.patronian.HyStreamerAlerts.manager.AlertDataManager;
import io.patronian.HyStreamerAlerts.manager.BotrixWebSocketManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Main Streamer Alerts command with subcommands.
 * Usage: /sa <on|off|status|connect|disconnect|setbid|help>
 */
public class HyStreamerAlertsCommands extends AbstractPlayerCommand {
    
    public HyStreamerAlertsCommands() {
        super("sa", "Streamer Alerts - Use /sa help for commands");
        
        // Register subcommands
        addSubCommand(new SaOnCommand());
        addSubCommand(new SaOffCommand());
        addSubCommand(new SaStatusCommand());
        addSubCommand(new SaConnectCommand());
        addSubCommand(new SaDisconnectCommand());
        addSubCommand(new SaSetBidCommand());
        addSubCommand(new SaHelpCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // Show help when no subcommand is provided
        showHelp(playerRef);
    }
    
    private void showHelp(PlayerRef playerRef) {
        playerRef.sendMessage(Message.raw("=== Streamer Alerts Commands ==="));
        playerRef.sendMessage(Message.raw("/sa on - Enable alerts"));
        playerRef.sendMessage(Message.raw("/sa off - Disable alerts"));
        playerRef.sendMessage(Message.raw("/sa setbid <id> - Set Botrix broadcast ID"));
        playerRef.sendMessage(Message.raw("/sa connect - Connect to Botrix"));
        playerRef.sendMessage(Message.raw("/sa disconnect - Disconnect from Botrix"));
        playerRef.sendMessage(Message.raw("/sa status - Show current status"));
    }
    
    public static boolean isEnabled(UUID playerId) {
        return HyStreamerAlertsPlugin.getInstance().getAlertDataManager().isEnabled(playerId);
    }
    
    // ==================== SUBCOMMANDS ====================
    
    /**
     * /sa on - Enable streamer alerts
     */
    public static class SaOnCommand extends AbstractPlayerCommand {
        public SaOnCommand() {
            super("on", "Enable streamer alerts");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            HyStreamerAlertsPlugin plugin = HyStreamerAlertsPlugin.getInstance();
            AlertDataManager dataManager = plugin.getAlertDataManager();
            BotrixWebSocketManager botrixManager = plugin.getBotrixWebSocketManager();
            
            dataManager.setEnabled(playerId, true);
            playerRef.sendMessage(Message.raw("Streamer alerts enabled!"));
            
            // Auto-connect if broadcast ID is set
            if (dataManager.hasBroadcastId(playerId) && !botrixManager.isConnected(playerId)) {
                String broadcastId = dataManager.getBroadcastId(playerId);
                botrixManager.connectPlayer(playerId, broadcastId, () -> playerRef);
                playerRef.sendMessage(Message.raw("Connecting to Botrix alerts..."));
            }
        }
    }
    
    /**
     * /sa off - Disable streamer alerts
     */
    public static class SaOffCommand extends AbstractPlayerCommand {
        public SaOffCommand() {
            super("off", "Disable streamer alerts");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            HyStreamerAlertsPlugin.getInstance().getAlertDataManager().setEnabled(playerId, false);
            playerRef.sendMessage(Message.raw("Streamer alerts disabled!"));
        }
    }
    
    /**
     * /sa status - Show current alert status
     */
    public static class SaStatusCommand extends AbstractPlayerCommand {
        public SaStatusCommand() {
            super("status", "Show streamer alert status");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            HyStreamerAlertsPlugin plugin = HyStreamerAlertsPlugin.getInstance();
            AlertDataManager dataManager = plugin.getAlertDataManager();
            BotrixWebSocketManager botrixManager = plugin.getBotrixWebSocketManager();
            
            boolean enabled = dataManager.isEnabled(playerId);
            boolean hasBid = dataManager.hasBroadcastId(playerId);
            boolean connected = botrixManager.isConnected(playerId);
            
            playerRef.sendMessage(Message.raw("=== Streamer Alerts Status ==="));
            playerRef.sendMessage(Message.raw("Alerts Enabled: " + (enabled ? "Yes" : "No")));
            playerRef.sendMessage(Message.raw("Broadcast ID: " + (hasBid ? dataManager.getBroadcastId(playerId) : "Not set")));
            playerRef.sendMessage(Message.raw("Botrix Connection: " + (connected ? "Connected" : "Disconnected")));
        }
    }
    
    /**
     * /sa connect - Connect to Botrix WebSocket
     */
    public static class SaConnectCommand extends AbstractPlayerCommand {
        public SaConnectCommand() {
            super("connect", "Connect to Botrix alerts");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            HyStreamerAlertsPlugin plugin = HyStreamerAlertsPlugin.getInstance();
            AlertDataManager dataManager = plugin.getAlertDataManager();
            BotrixWebSocketManager botrixManager = plugin.getBotrixWebSocketManager();
            
            if (!dataManager.hasBroadcastId(playerId)) {
                playerRef.sendMessage(Message.raw("No broadcast ID set! Use /sa setbid <broadcast_id> first"));
                return;
            }
            if (botrixManager.isConnected(playerId)) {
                playerRef.sendMessage(Message.raw("Already connected to Botrix!"));
                return;
            }
            
            String broadcastId = dataManager.getBroadcastId(playerId);
            botrixManager.connectPlayer(playerId, broadcastId, () -> playerRef);
            dataManager.setEnabled(playerId, true);
            playerRef.sendMessage(Message.raw("Connecting to Botrix alerts..."));
        }
    }
    
    /**
     * /sa disconnect - Disconnect from Botrix WebSocket
     */
    public static class SaDisconnectCommand extends AbstractPlayerCommand {
        public SaDisconnectCommand() {
            super("disconnect", "Disconnect from Botrix alerts");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            HyStreamerAlertsPlugin.getInstance().getBotrixWebSocketManager().disconnectPlayer(playerId);
            playerRef.sendMessage(Message.raw("Disconnected from Botrix alerts"));
        }
    }
    
    /**
     * /sa setbid <broadcast_id> - Set Botrix broadcast ID
     */
    public static class SaSetBidCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> broadcastIdArg;
        
        public SaSetBidCommand() {
            super("setbid", "Set Botrix broadcast ID");
            this.broadcastIdArg = withRequiredArg("broadcastId", "Your Botrix broadcast ID", ArgTypes.STRING);
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            AlertDataManager dataManager = HyStreamerAlertsPlugin.getInstance().getAlertDataManager();
            
            String broadcastId = ctx.get(broadcastIdArg);
            
            if (broadcastId == null || broadcastId.trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Usage: /sa setbid <broadcast_id>"));
                return;
            }
            
            dataManager.setBroadcastId(playerId, broadcastId.trim());
            playerRef.sendMessage(Message.raw("Botrix broadcast ID set: " + broadcastId));
            playerRef.sendMessage(Message.raw("Use /sa connect to start receiving alerts"));
        }
    }
    
    /**
     * /sa help - Show help
     */
    public static class SaHelpCommand extends AbstractPlayerCommand {
        public SaHelpCommand() {
            super("help", "Show streamer alert commands");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            playerRef.sendMessage(Message.raw("=== Streamer Alerts Commands ==="));
            playerRef.sendMessage(Message.raw("/sa on - Enable alerts"));
            playerRef.sendMessage(Message.raw("/sa off - Disable alerts"));
            playerRef.sendMessage(Message.raw("/sa setbid <id> - Set Botrix broadcast ID"));
            playerRef.sendMessage(Message.raw("/sa connect - Connect to Botrix"));
            playerRef.sendMessage(Message.raw("/sa disconnect - Disconnect from Botrix"));
            playerRef.sendMessage(Message.raw("/sa status - Show current status"));
        }
    }
}
