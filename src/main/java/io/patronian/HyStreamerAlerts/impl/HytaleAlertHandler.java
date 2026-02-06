package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import io.patronian.HyStreamerAlerts.api.AlertHandler;

public class HytaleAlertHandler implements AlertHandler {

    @Override
    public void onFollow(PlayerRef player, String followerName, String platform) {
        showTitle(player, "New Follower!", followerName + " just followed!");
    }

    @Override
    public void onSubscribe(PlayerRef player, String subscriberName, int months, String platform) {
        String subtitle = months > 1 
                ? subscriberName + " subscribed for " + months + " months!"
                : subscriberName + " just subscribed!";
        
        showTitle(player, "New Subscriber!", subtitle);
    }

    @Override
    public void onGiftSub(PlayerRef player, String gifterName, int amount, String platform) {
        String subtitle = amount > 1
                ? gifterName + " gifted " + amount + " subs!"
                : gifterName + " gifted a sub!";
        
        showTitle(player, "Gift Subs!", subtitle);
    }

    @Override
    public void onDonation(PlayerRef player, String donorName, String amount, String platform) {
        String subtitle = amount != null 
                ? donorName + " donated " + amount + "!"
                : donorName + " sent a donation!";
        
        showTitle(player, "Donation!", subtitle);
    }

    @Override
    public void onRaid(PlayerRef player, String raiderName, int viewers, String platform) {
        String subtitle = viewers > 0
                ? raiderName + " is raiding with " + viewers + " viewers!"
                : raiderName + " is raiding!";
        
        showTitle(player, "Incoming Raid!", subtitle);
    }
    
    private void showTitle(PlayerRef player, String title, String subtitle) {
        if (player == null || player.isValid()) return;
        
        EventTitleUtil.showEventTitleToPlayer(
                player,
                Message.raw(title),
                Message.raw(subtitle),
                true
        );
    }
}
