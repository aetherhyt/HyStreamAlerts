package io.patronian.HyStreamerAlerts.api;

/**
 * Interface for providers that source alerts (e.g., Botrix, StreamElements).
 */
public interface AlertProvider extends StreamerConnector {
    void setAlertHandler(AlertHandler handler);
}
