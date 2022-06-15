package org.apache.synapse.elk.analytics;

public abstract class AbstractExternalAnalyticsService extends Thread implements ExternalAnalyticsService {

    protected PublisherState state = PublisherState.INACTIVE;

    public boolean isActive() {
        return this.state.equals(PublisherState.ACTIVE);
    }

    public boolean isShuttingDown() {
        return this.state.equals(PublisherState.SHUTTING_DOWN);
    }

    public void requestShutdown() {
        this.state = PublisherState.SHUTTING_DOWN;
    }

    protected enum PublisherState {
        INACTIVE,
        ACTIVE,
        SHUTTING_DOWN,
        NOT_RUNNING
    }
}
