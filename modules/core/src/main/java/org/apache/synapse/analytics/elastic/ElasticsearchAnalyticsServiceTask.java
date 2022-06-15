package org.apache.synapse.analytics.elastic;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.analytics.AbstractExternalAnalyticsServiceTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ElasticsearchAnalyticsServiceTask extends AbstractExternalAnalyticsServiceTask {

    private static final Log log = LogFactory.getLog(ElasticsearchAnalyticsServiceTask.class);
    private static ElasticsearchAnalyticsServiceTask instance = null;
    private final String analyticsDataPrefix;
    private final Queue<JsonObject> analyticsQueue = new ConcurrentLinkedQueue<>();
    private int maximumPublishRate;

    private ElasticsearchAnalyticsServiceTask() {
        setMaximumPublishRate(1000); // TODO: Load from Synapse configuration
        this.enabled = true;
        this.analyticsDataPrefix = "SYNAPSE_ANALYTICS_DATA";
    }

    public static synchronized ElasticsearchAnalyticsServiceTask getInstance() {
        if (instance == null) {
            instance = new ElasticsearchAnalyticsServiceTask();
        }
        return instance;
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

    public void setMaximumPublishRate(int rate) {
        this.maximumPublishRate = rate;
    }


    @Override
    public void publish(JsonObject data) {
        if (isShuttingDown() || !isEnabled()) {
            return;
        }

        this.analyticsQueue.offer(data);
    }
}
