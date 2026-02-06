package io.patronian.HyStreamerAlerts.api;

/**
 * Interface for providers that source chat messages.
 */
public interface ChatProvider extends StreamerConnector {
    void setChatHandler(ChatHandler handler);
}
