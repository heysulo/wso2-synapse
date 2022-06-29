package org.apache.synapse.analytics;

public class AnalyticsConstants {

    /**
     * Schema version of the analytic
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Unique identifier for the publisher that can be used to filter analytics if multiple micro integrators are
     * publishing data to the same Elasticsearch server
     */
    public static final String PUBLISHER_IDENTIFIER = "analytics.publisher.id";

    /**
     * Name of the Synapse configuration used to determine if analytics for APIs are disabled
     */
    public static final String PUBLISHER_DISABLED_API = "analytics.publisher.api.disabled";

    /**
     * Name of the Synapse configuration used to determine if analytics for ProxyServices are disabled
     */
    public static final String PUBLISHER_DISABLED_PROXY_SERVICE = "analytics.publisher.proxy_services.disabled";

    /**
     * Name of the Synapse configuration used to determine if analytics for Sequences are disabled
     */
    public static final String PUBLISHER_DISABLED_SEQUENCES = "analytics.publisher.sequences.disabled";

    /**
     * Name of the Synapse configuration used to determine if analytics for Endpoints are disabled
     */
    public static final String PUBLISHER_DISABLED_ENDPOINTS = "analytics.publisher.endpoints.disabled";

    /**
     * Name of the Synapse configuration used to determine if analytics for Inbound Endpoints are disabled
     */
    public static final String PUBLISHER_DISABLED_INBOUND_ENDPOINTS = "analytics.publisher.inbound_endpoints.disabled";

    /**
     * Name of the Synapse configuration used to determine if sequence analytics are published only for NAMED sequences
     */
    public static final String PUBLISHER_NAMED_SEQUENCES_ONLY = "analytics.publisher.sequences.named_only";

    /**
     * Name of the Synapse configuration used to determine the prefix Elasticsearch analytics are published with.
     * The purpose of this prefix is to distinguish log lines which hold analytics data from others
     */
    public static final String ELASTICSEARCH_PREFIX = "analytics.service.elasticsearch.prefix";

    /**
     * Name of the Synapse configuration used to determine if the Elasticsearch service is enabled
     */
    public static final String ELASTICSEARCH_ENABLED = "analytics.service.elasticsearch.enabled";
}
