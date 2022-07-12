package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

import java.util.Stack;

public class SimpleAnalyticsService implements AnalyticsService {
    private final Stack<JsonObject> analyticsStack = new Stack<>();
    private boolean enabled = false;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void enableService() {
        enabled = true;
    }

    public void disableService() {
        enabled = false;
    }

    @Override
    public void publish(JsonObject data) {
        analyticsStack.push(data);
    }

    public JsonObject fetchAnalytic() {
        if (analyticsStack.isEmpty()) {
            return null;
        }

        return analyticsStack.pop();
    }

    public void clear() {
        analyticsStack.clear();
    }

    public int getAvailableAnalyticsCount() {
        return analyticsStack.size();
    }
}
