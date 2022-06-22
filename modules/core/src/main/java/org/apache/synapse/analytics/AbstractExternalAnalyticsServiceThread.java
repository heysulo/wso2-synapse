package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

public abstract class AbstractExternalAnalyticsServiceThread extends Thread {

    protected ServiceState state = ServiceState.NOT_RUNNING;
    protected boolean enabled = false;

    public boolean isRunning() {
        return !this.state.equals(ServiceState.NOT_RUNNING);
    }

    public boolean isShuttingDown() {
        return this.state.equals(ServiceState.SHUTTING_DOWN);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void requestShutdown() {
        this.state = ServiceState.SHUTTING_DOWN;
    }

    public abstract void publish(JsonObject data);

    protected enum ServiceState {
        NOT_RUNNING,
        RUNNING,
        SHUTTING_DOWN
    }
}
