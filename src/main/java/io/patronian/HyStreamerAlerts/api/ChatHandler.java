package io.patronian.HyStreamerAlerts.api;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Handles chat messages received from a ChatProvider.
 */
public interface ChatHandler {
    void onMessage(PlayerRef player, String sender, String message, String platform);
}
