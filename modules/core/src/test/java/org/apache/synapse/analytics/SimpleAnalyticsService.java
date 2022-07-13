/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.synapse.analytics;

import com.google.gson.JsonObject;

import java.util.LinkedList;
import java.util.Queue;

public class SimpleAnalyticsService implements AnalyticsService {
    private final Queue<JsonObject> analyticsStack = new LinkedList<>();
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
        analyticsStack.offer(data);
    }

    public JsonObject fetchAnalytic() {
        if (analyticsStack.isEmpty()) {
            return null;
        }

        return analyticsStack.poll();
    }

    public void clear() {
        analyticsStack.clear();
    }

    public int getAvailableAnalyticsCount() {
        return analyticsStack.size();
    }
}
