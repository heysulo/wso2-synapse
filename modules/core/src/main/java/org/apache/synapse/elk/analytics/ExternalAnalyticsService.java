package org.apache.synapse.elk.analytics;

import org.json.JSONObject;

public interface ExternalAnalyticsService {

    void publish(JSONObject data);
}
