package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractExternalAnalyticsServiceTask implements Runnable {

    protected final ScheduledExecutorService serviceExecutor = Executors.newScheduledThreadPool(1);
    protected boolean enabled = false;
    protected boolean shuttingDown = false;

    /**
     * Indicates if the Analytics Service is enabled or not
     * @return true, if enabled
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Publish an analytic through the service
     * @param data analytic data
     */
    public abstract void publish(JsonObject data);

    /**
     * Shutdown the Analytic Service. Once the service is requested to shut down it will stop accepting new analytics
     */
    public synchronized void shutdown() {
        shuttingDown = true;
        serviceExecutor.shutdown();
        try {
            if (!serviceExecutor.awaitTermination(1500, TimeUnit.MILLISECONDS)) {
                serviceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            serviceExecutor.shutdownNow();
        } finally {
            this.run(); // Flush unpublished
        }
    }

    /**
     * Indicates if the Analytics Service is shutting down or not
     * @return true, if shutting down
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * Start the Analytics Service
     */
    public synchronized void schedule() {
        shuttingDown = false;
        serviceExecutor.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

}
