package org.apache.synapse.elk.analytics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ElasticsearchAnalyticsPublisherThread extends AbstractExternalAnalyticsService {

    private static final Log log = LogFactory.getLog(ElasticsearchAnalyticsPublisherThread.class);
    private static ElasticsearchAnalyticsPublisherThread instance = null;
    private final String analyticsDataPrefix;
    private final Queue<JSONObject> analyticsQueue = new ConcurrentLinkedQueue<>();
    private final JSONObject analyticsEnvelope;
    private int maximumPublishRate;

    private ElasticsearchAnalyticsPublisherThread() {
        this.maximumPublishRate = 1000; // TODO: Load from Synapse configuration
        this.analyticsDataPrefix = "SYNAPSE_ANALYTICS_DATA";
        this.analyticsEnvelope = createAnalyticsEnvelop();
    }

    public static ElasticsearchAnalyticsPublisherThread getInstance() {
        if (instance == null) {
            instance = new ElasticsearchAnalyticsPublisherThread();
        }
        return instance;
    }

    private JSONObject createAnalyticsEnvelop() {
        JSONObject envelop = new JSONObject();
        envelop.put(AnalyticsEnvelopConstants.METADATA, "TBA"); // TODO: Setup
        return envelop;
    }

    @Override
    public void run() {
        log.info("Thread spawned.");
        this.state = PublisherState.ACTIVE;
        while (!this.isShuttingDown()) {
            Instant startTime = Instant.now();
            Instant endTime = null;
            int processedCountInLastSecond = 0;

            while (processedCountInLastSecond < this.maximumPublishRate && this.isActive()) {
                JSONObject analytic = this.analyticsQueue.poll();
                if (analytic == null) {
                    endTime = Instant.now();
                    break;
                }

                processedCountInLastSecond += 1;
                Instant analyticTimestamp = Instant.now();
                this.analyticsEnvelope.put(AnalyticsEnvelopConstants.TIMESTAMP, analyticTimestamp.toString());
                this.analyticsEnvelope.put(AnalyticsEnvelopConstants.TIMESTAMP_EPOC, analyticTimestamp.toEpochMilli());
                this.analyticsEnvelope.put(AnalyticsEnvelopConstants.PAYLOAD, analytic);
                String logOutput = this.analyticsDataPrefix + " " + analyticsEnvelope;
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

        if (this.state.equals(PublisherState.SHUTTING_DOWN)) {
            log.info("Shutting down thread");
        } else {
            log.warn("Thread is shutting down");
        }
        if (!this.analyticsQueue.isEmpty()) {
            log.warn(String.format("%d analytic(s) were discarded without publishing", this.analyticsQueue.size()));
            this.analyticsQueue.clear();
        }
        this.state = PublisherState.NOT_RUNNING;
    }

    public void setMaximumPublishRate(int rate) {
        log.debug(String.format("Changing publish rate from %d to %d", this.maximumPublishRate, rate));
        this.maximumPublishRate = rate;
    }


    @Override
    public void requestShutdown() {
        log.debug("ElasticsearchAnalyticsPublisherThread shutdown requested from thread: "
                + Thread.currentThread().getName());
        super.requestShutdown();
    }


    @Override
    public void publish(JSONObject data) {
        getInstance().analyticsQueue.offer(data);
    }


}
