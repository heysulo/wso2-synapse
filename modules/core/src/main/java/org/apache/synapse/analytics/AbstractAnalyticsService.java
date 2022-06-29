package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

public abstract class AbstractAnalyticsService {

    protected boolean enabled = false;

    /**
     * Indicates if the Analytics Service is enabled or not
     *
     * @return true, if enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Publish an analytic through the service
     *
     * @param data analytic data
     */
    public abstract void publish(JsonObject data);

}
