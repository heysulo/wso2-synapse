package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

public interface AnalyticsService {

    /**
     * Indicates if the Analytics Service is enabled or not.
     *
     * @return true, if enabled.
     */
    boolean isEnabled();

    /**
     * Publish an analytic through the service.
     *
     * @param data analytic data.
     */
    void publish(JsonObject data);

}
