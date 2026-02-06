package io.patronian.HyStreamerAlerts.api;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Handles alert events triggered by an AlertProvider.
 */
public interface AlertHandler {
    void onFollow(PlayerRef player, String followerName, String platform);
    void onSubscribe(PlayerRef player, String subscriberName, int months, String platform);
    void onGiftSub(PlayerRef player, String gifterName, int amount, String platform);
    void onDonation(PlayerRef player, String donorName, String amount, String platform);
    void onRaid(PlayerRef player, String raiderName, int viewers, String platform);
}
