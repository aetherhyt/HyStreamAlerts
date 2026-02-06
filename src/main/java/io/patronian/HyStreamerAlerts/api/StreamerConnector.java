package io.patronian.HyStreamerAlerts.api;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Common interface for connection-based streamer services.
 */
public interface StreamerConnector {
    
    /**
     * Connects a player to the service.
     * @param playerId The unique ID of the player.
     * @param connectionId The service-specific ID (e.g., broadcast ID, chat ID).
     * @param playerRefSupplier A supplier to retrieve the player reference, used for callbacks.
     */
    void connect(@Nonnull UUID playerId, @Nonnull String connectionId, @Nonnull Supplier<PlayerRef> playerRefSupplier);

    /**
     * Disconnects a player from the service.
     * @param playerId The unique ID of the player.
     */
    void disconnect(@Nonnull UUID playerId);

    /**
     * Checks if a player is connected.
     */
    boolean isConnected(@Nonnull UUID playerId);
    
    /**
     * Shuts down the entire service manager and all connections.
     */
    void shutdown();
    
    /**
     * Name of this provider (e.g. "Botrix").
     */
    String getProviderName();
}
