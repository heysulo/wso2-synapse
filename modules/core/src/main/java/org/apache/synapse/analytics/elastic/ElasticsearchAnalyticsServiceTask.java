package org.apache.synapse.analytics.elastic;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.analytics.AbstractExternalAnalyticsServiceTask;
import org.apache.synapse.analytics.ExternalAnalyticsConstants;
import org.apache.synapse.config.SynapsePropertiesLoader;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ElasticsearchAnalyticsServiceTask extends AbstractExternalAnalyticsServiceTask {

    private static final Log log = LogFactory.getLog(ElasticsearchAnalyticsServiceTask.class);
    private static ElasticsearchAnalyticsServiceTask instance = null;
    private final Queue<JsonObject> analyticsQueue = new ConcurrentLinkedQueue<>();
    private String analyticsDataPrefix;
    private int maximumPublishRate;
    private int maximumQueueSize;

    private ElasticsearchAnalyticsServiceTask() {
        loadConfigurations();
    }

    public static synchronized ElasticsearchAnalyticsServiceTask getInstance() {
        if (instance == null) {
            instance = new ElasticsearchAnalyticsServiceTask();
        }
        return instance;
    }

    private void loadConfigurations() {
        this.maximumPublishRate = SynapsePropertiesLoader.getIntegerProperty(
                ExternalAnalyticsConstants.ELASTICSEARCH_MAX_PUBLISH_RATE, 100000);
        this.maximumQueueSize = SynapsePropertiesLoader.getIntegerProperty(
                ExternalAnalyticsConstants.ELASTICSEARCH_QUEUE_SIZE, Integer.MAX_VALUE - 1);
        this.enabled = SynapsePropertiesLoader.getBooleanProperty(
                ExternalAnalyticsConstants.ELASTICSEARCH_ENABLED, false);
        this.analyticsDataPrefix = SynapsePropertiesLoader.getPropertyValue(
                ExternalAnalyticsConstants.ELASTICSEARCH_PREFIX, "SYNAPSE_ANALYTICS_DATA");
    }

    @Override
    public void run() {
        int processedAnalyticsCount = 0;
        while (processedAnalyticsCount < this.maximumPublishRate || shuttingDown) {
            JsonObject analytic = this.analyticsQueue.poll();
            if (analytic == null) {
                break;
            }

            ++processedAnalyticsCount;
            String logOutput = this.analyticsDataPrefix + " " + analytic;
            log.info(logOutput);
        }
    }


    @Override
    public void publish(JsonObject data) {
        if (isShuttingDown() || !isEnabled()) {
            return;
        }

        if (this.analyticsQueue.size() >= this.maximumQueueSize) {
            log.warn("Discarding analytic data. Maximum queue size reached");
        }

        this.analyticsQueue.offer(data);
    }
}
