package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractExternalAnalyticsServiceTask implements Runnable {

    protected final ScheduledExecutorService serviceExecutor = Executors.newScheduledThreadPool(1);
    protected boolean enabled = false;
    protected boolean shuttingDown = false;

    public boolean isEnabled() {
        return this.enabled;
    }

    public abstract void publish(JsonObject data);

    public void shutdown() {
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

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public void schedule() {
        serviceExecutor.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

}
