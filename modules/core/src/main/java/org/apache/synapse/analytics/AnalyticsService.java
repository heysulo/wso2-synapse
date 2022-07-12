package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

public interface AnalyticsService {

    /**
     * Indicates if the Analytics Service is enabled or not
     *
     * @return true, if enabled
     */
    public boolean isEnabled();

    /**
     * Publish an analytic through the service
     *
     * @param data analytic data
     */
    public void publish(JsonObject data);

}
