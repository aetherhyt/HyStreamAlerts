package io.patronian.HyStreamerAlerts.impl;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.patronian.HyStreamerAlerts.api.ChatHandler;

public class HytaleChatHandler implements ChatHandler {

    @Override
    public void onMessage(PlayerRef player, String sender, String message, String platform) {
        if (player == null || !player.isValid()) return;
        
        // Format: [Stream] Username: Message
        // Using Hytale color codes: \u00A7
        // b = aqua, f = white, 7 = gray
        String formatted = String.format("\u00A7b[Stream] \u00A77%s: \u00A7f%s", sender, message);
        player.sendMessage(Message.raw(formatted));
    }
}
