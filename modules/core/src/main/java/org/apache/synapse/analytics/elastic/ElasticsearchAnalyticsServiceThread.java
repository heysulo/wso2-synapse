package org.apache.synapse.analytics.elastic;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.analytics.AbstractExternalAnalyticsServiceThread;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ElasticsearchAnalyticsServiceThread extends AbstractExternalAnalyticsServiceThread {

    private static final Log log = LogFactory.getLog(ElasticsearchAnalyticsServiceThread.class);
    private static ElasticsearchAnalyticsServiceThread instance = null;
    private final String analyticsDataPrefix;
    private final Queue<JsonObject> analyticsQueue = new ConcurrentLinkedQueue<>();
    private int maximumPublishRate;

    private ElasticsearchAnalyticsServiceThread() {
        this.maximumPublishRate = 1000; // TODO: Load from Synapse configuration
        this.enabled = true;
        this.analyticsDataPrefix = "SYNAPSE_ANALYTICS_DATA";
    }

    public static synchronized ElasticsearchAnalyticsServiceThread getInstance() {
        if (instance == null) {
            instance = new ElasticsearchAnalyticsServiceThread();
        }
        return instance;
    }

    @Override
    public void run() {
        log.info("Thread spawned.");
        this.state = ServiceState.RUNNING;
        while (!this.isShuttingDown()) {
            Instant startTime = Instant.now();
            Instant endTime = null;
            int processedCountInLastSecond = 0;

            while (processedCountInLastSecond < this.maximumPublishRate && this.isRunning()) {
                JsonObject analytic = this.analyticsQueue.poll();
                if (analytic == null) {
                    endTime = Instant.now();
                    break;
                }

                processedCountInLastSecond += 1;
                String logOutput = this.analyticsDataPrefix + " " + analytic.toString();
                log.info(logOutput);
                if (Duration.between(startTime, Instant.now()).toMillis() > 1000) {
                    log.debug(
                            String.format("Failed to reach the specified rate[%d]. Current: %d",
                                    this.maximumPublishRate, processedCountInLastSecond));
                    startTime = Instant.now();
                    processedCountInLastSecond = 0;
                }
            }

            // Rate management
            long sleepTimeTillNextIteration;
            if (endTime == null) {
                endTime = Instant.now();
            }
            sleepTimeTillNextIteration = 1000 - Duration.between(startTime, endTime).toMillis();
            if (sleepTimeTillNextIteration > 0) {
                try {
                    Thread.sleep(sleepTimeTillNextIteration);
                } catch (InterruptedException e) {
                    log.warn("Thread interrupted", e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException();
                }
            }

        }

        if (this.state.equals(ServiceState.SHUTTING_DOWN)) {
            log.info("Shutting down thread");
        } else {
            log.warn("Thread is shutting down");
        }
        if (!this.analyticsQueue.isEmpty()) {
            log.warn(String.format("%d analytic(s) were discarded without publishing", this.analyticsQueue.size()));
            this.analyticsQueue.clear();
        }
        this.state = ServiceState.NOT_RUNNING;
    }

    public void setMaximumPublishRate(int rate) {
        log.debug(String.format("Changing publish rate from %d to %d", this.maximumPublishRate, rate));
        this.maximumPublishRate = rate;
    }


    @Override
    public void requestShutdown() {
        log.debug("Thread shutdown requested from thread: "
                + Thread.currentThread().getName());
        super.requestShutdown();
    }


    @Override
    public void publish(JsonObject data) {
        getInstance().analyticsQueue.offer(data);
    }


}
