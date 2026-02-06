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
import io.patronian.HyStreamerAlerts.api.AlertProvider;
import io.patronian.HyStreamerAlerts.api.ChatProvider;
import io.patronian.HyStreamerAlerts.manager.AlertDataManager;

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
        addSubCommand(new SaSetChatCommand());
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
        playerRef.sendMessage(Message.raw("/sa setchat <id> - Set Botrix Chat ID"));
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
            AlertProvider alertProvider = plugin.getServiceRegistry().getDefaultAlertProvider();
            ChatProvider chatProvider = plugin.getServiceRegistry().getDefaultChatProvider();
            
            boolean somethingDone = false;

            if (dataManager.hasBroadcastId(playerId) && alertProvider != null) {
                if (!alertProvider.isConnected(playerId)) {
                    String broadcastId = dataManager.getBroadcastId(playerId);
                    alertProvider.connect(playerId, broadcastId, () -> playerRef);
                    playerRef.sendMessage(Message.raw("Connecting to Alerts (" + alertProvider.getProviderName() + ")..."));
                    somethingDone = true;
                } else {
                    playerRef.sendMessage(Message.raw("Already connected to Alerts."));
                }
            } else if (alertProvider != null) {
                playerRef.sendMessage(Message.raw("No broadcast ID set! Use /sa setbid <broadcast_id>"));
            }
            
            if (dataManager.hasChatId(playerId) && chatProvider != null) {
                if (!chatProvider.isConnected(playerId)) {
                    String chatId = dataManager.getChatId(playerId);
                    chatProvider.connect(playerId, chatId, () -> playerRef);
                    playerRef.sendMessage(Message.raw("Connecting to Chat (" + chatProvider.getProviderName() + ")..."));
                    somethingDone = true;
                } else {
                    playerRef.sendMessage(Message.raw("Already connected to Chat."));
                }
            } else if (chatProvider != null) {
                playerRef.sendMessage(Message.raw("No Chat ID set! Use /sa setchat <chat_id>"));
            }

            if (somethingDone) {
                dataManager.setEnabled(playerId, true);
                playerRef.sendMessage(Message.raw("Streamer alerts enabled!"));
            } else if (!dataManager.isEnabled(playerId)) {
                 dataManager.setEnabled(playerId, true);
                 playerRef.sendMessage(Message.raw("Streamer alerts enabled!"));
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
            AlertProvider alertProvider = plugin.getServiceRegistry().getDefaultAlertProvider();
            ChatProvider chatProvider = plugin.getServiceRegistry().getDefaultChatProvider();
            
            boolean enabled = dataManager.isEnabled(playerId);
            boolean hasBid = dataManager.hasBroadcastId(playerId);
            boolean hasChat = dataManager.hasChatId(playerId);
            boolean connected = alertProvider != null && alertProvider.isConnected(playerId);
            boolean chatConnected = chatProvider != null && chatProvider.isConnected(playerId);
            
            playerRef.sendMessage(Message.raw("=== Streamer Alerts Status ==="));
            playerRef.sendMessage(Message.raw("Alerts Enabled: " + (enabled ? "Yes" : "No")));
            playerRef.sendMessage(Message.raw("Broadcast ID: " + (hasBid ? dataManager.getBroadcastId(playerId) : "Not set")));
            playerRef.sendMessage(Message.raw("Chat ID: " + (hasChat ? dataManager.getChatId(playerId) : "Not set")));
            playerRef.sendMessage(Message.raw("Alert Service: " + (connected ? "Connected" : "Disconnected")));
            playerRef.sendMessage(Message.raw("Chat Service: " + (chatConnected ? "Connected" : "Disconnected")));
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
            AlertProvider alertProvider = plugin.getServiceRegistry().getDefaultAlertProvider();
            ChatProvider chatProvider = plugin.getServiceRegistry().getDefaultChatProvider();
            
            boolean somethingDone = false;

            if (dataManager.hasBroadcastId(playerId) && alertProvider != null) {
                if (!alertProvider.isConnected(playerId)) {
                    String broadcastId = dataManager.getBroadcastId(playerId);
                    alertProvider.connect(playerId, broadcastId, () -> playerRef);
                    playerRef.sendMessage(Message.raw("Connecting to Alerts..."));
                    somethingDone = true;
                } else {
                    playerRef.sendMessage(Message.raw("Already connected to Alerts."));
                }
            } else if (alertProvider != null) {
                 playerRef.sendMessage(Message.raw("No broadcast ID set! Use /sa setbid <broadcast_id>"));
            }
            
            if (dataManager.hasChatId(playerId) && chatProvider != null) {
                if (!chatProvider.isConnected(playerId)) {
                    String chatId = dataManager.getChatId(playerId);
                    chatProvider.connect(playerId, chatId, () -> playerRef);
                    playerRef.sendMessage(Message.raw("Connecting to Chat..."));
                    somethingDone = true;
                } else {
                    playerRef.sendMessage(Message.raw("Already connected to Chat."));
                }
            } else if (chatProvider != null) {
                 playerRef.sendMessage(Message.raw("No Chat ID set! Use /sa setchat <chat_id>"));
            }

            if (somethingDone) {
                dataManager.setEnabled(playerId, true);
            }
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
            AlertProvider alertProvider = HyStreamerAlertsPlugin.getInstance().getServiceRegistry().getDefaultAlertProvider();
            ChatProvider chatProvider = HyStreamerAlertsPlugin.getInstance().getServiceRegistry().getDefaultChatProvider();
            
            if (alertProvider != null) alertProvider.disconnect(playerId);
            if (chatProvider != null) chatProvider.disconnect(playerId);
            
            playerRef.sendMessage(Message.raw("Disconnected from services"));
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
     * /sa setchat <chat_id> - Set Botrix Chat ID
     */
    public static class SaSetChatCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> chatIdArg;
        
        public SaSetChatCommand() {
            super("setchat", "Set Botrix Chat ID");
            this.chatIdArg = withRequiredArg("chatId", "Your Botrix Chat ID (e.g. 54870857)", ArgTypes.STRING);
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID playerId = playerRef.getUuid();
            AlertDataManager dataManager = HyStreamerAlertsPlugin.getInstance().getAlertDataManager();
            
            String chatId = ctx.get(chatIdArg);
            
            if (chatId == null || chatId.trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Usage: /sa setchat <chat_id>"));
                return;
            }
            
            dataManager.setChatId(playerId, chatId.trim());
            playerRef.sendMessage(Message.raw("Botrix Chat ID set: " + chatId));
            playerRef.sendMessage(Message.raw("Use /sa connect to start receiving chat"));
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
            playerRef.sendMessage(Message.raw("/sa setchat <id> - Set Botrix Chat ID"));
            playerRef.sendMessage(Message.raw("/sa connect - Connect to Botrix"));
            playerRef.sendMessage(Message.raw("/sa disconnect - Disconnect from Botrix"));
            playerRef.sendMessage(Message.raw("/sa status - Show current status"));
        }
    }
}
