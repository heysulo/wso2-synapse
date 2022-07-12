package org.apache.synapse.analytics.elastic;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.analytics.AnalyticsConstants;
import org.apache.synapse.analytics.AnalyticsService;
import org.apache.synapse.config.SynapsePropertiesLoader;

public final class ElasticsearchAnalyticsService implements AnalyticsService {

    private static final Log log = LogFactory.getLog(ElasticsearchAnalyticsService.class);
    private static ElasticsearchAnalyticsService instance = null;
    boolean enabled = false;
    private String analyticsDataPrefix;

    private ElasticsearchAnalyticsService() {
        loadConfigurations();
    }

    public static synchronized ElasticsearchAnalyticsService getInstance() {
        if (instance == null) {
            instance = new ElasticsearchAnalyticsService();
        }
        return instance;
    }

    private void loadConfigurations() {
        this.enabled = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.ELASTICSEARCH_ENABLED, false);
        this.analyticsDataPrefix = SynapsePropertiesLoader.getPropertyValue(
                AnalyticsConstants.ELASTICSEARCH_PREFIX, "SYNAPSE_ANALYTICS_DATA");
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void publish(JsonObject analytic) {
        if (!isEnabled()) {
            return;
        }

        String logOutput = this.analyticsDataPrefix + " " + analytic;
        log.info(logOutput);
    }
}
