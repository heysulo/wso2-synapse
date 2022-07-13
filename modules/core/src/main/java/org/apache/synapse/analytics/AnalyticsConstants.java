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

public class AnalyticsConstants {

    /**
     * Synapse Configurations
     */
    public static class Configurations {
        /**
         * Schema version of the analytic.
         */
        public static final int SCHEMA_VERSION = 1;

        /**
         * Unique identifier for the publisher that can be used to filter analytics if multiple micro integrators are
         * publishing data to the same Elasticsearch server.
         */
        public static final String IDENTIFIER = "analytics.id";

        /**
         * Name of the Synapse configuration used to determine if analytics for APIs are enabled or disabled.
         */
        public static final String API_ANALYTICS_ENABLED = "analytics.api_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for ProxyServices are enabled or disabled.
         */
        public static final String PROXY_SERVICE_ANALYTICS_ENABLED = "analytics.proxy_service_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Sequences are enabled or disabled.
         */
        public static final String SEQUENCE_ANALYTICS_ENABLED = "analytics.sequence_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Endpoints are enabled or disabled.
         */
        public static final String ENDPOINT_ANALYTICS_ENABLED = "analytics.endpoint_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if analytics for Inbound Endpoints are enabled or disabled.
         */
        public static final String INBOUND_ENDPOINT_ANALYTICS_ENABLED = "analytics.inbound_endpoint_analytics.enabled";

        /**
         * Name of the Synapse configuration used to determine if sequence analytics are published only for NAMED sequences.
         */
        public static final String NAMED_SEQUENCES_ONLY = "analytics.sequence_analytics.named_only";

        /**
         * Name of the Synapse configuration used to determine the prefix Elasticsearch analytics are published with.
         * The purpose of this prefix is to distinguish log lines which hold analytics data from others.
         */
        public static final String ELASTICSEARCH_PREFIX = "analytics.service.elasticsearch.prefix";

        /**
         * Name of the Synapse configuration used to determine if the Elasticsearch service is enabled.
         */
        public static final String ELASTICSEARCH_ENABLED = "analytics.service.elasticsearch.enabled";
    }

}
