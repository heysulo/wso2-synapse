package org.apache.synapse.analytics;

public class ExternalAnalyticsConstants {

    /**
     * Name of the Synapse configuration used to determine if external analytics for APIs are disabled
     */
    public static final String PUBLISHER_DISABLED_API = "external_analytics.publisher.api.disabled";

    /**
     * Name of the Synapse configuration used to determine if external analytics for ProxyServices are disabled
     */
    public static final String PUBLISHER_DISABLED_PROXY_SERVICE = "external_analytics.publisher.proxy_services.disabled";

    /**
     * Name of the Synapse configuration used to determine if external analytics for Sequences are disabled
     */
    public static final String PUBLISHER_DISABLED_SEQUENCES = "external_analytics.publisher.sequences.disabled";

    /**
     * Name of the Synapse configuration used to determine if external analytics for Endpoints are disabled
     */
    public static final String PUBLISHER_DISABLED_ENDPOINTS = "external_analytics.publisher.endpoints.disabled";

    /**
     * Name of the Synapse configuration used to determine if external analytics for Inbound Endpoints are disabled
     */
    public static final String PUBLISHER_DISABLED_INBOUND_ENDPOINTS = "external_analytics.publisher.inbound_endpoints.disabled";

    /**
     * Name of the Synapse configuration used to determine if sequence analytics are published only for NAMED sequences
     */
    public static final String PUBLISHER_NAMED_SEQUENCES_ONLY = "external_analytics.publisher.sequences.named_only";


    /**
     * Name of the Synapse configuration used to determine the prefix Elasticsearch analytics are published with.
     * The purpose of this prefix is to distinguish log lines which hold analytics data from others
     */
    public static final String ELASTICSEARCH_PREFIX = "external_analytics.elasticsearch.prefix";

    /**
     * Name of the Synapse configuration used to determine the maximum number of analytics Elasticsearch service will
     * publish within a second
     */
    public static final String ELASTICSEARCH_MAX_PUBLISH_RATE = "external_analytics.elasticsearch.rate.max";

    /**
     * Name of the Synapse configuration used to determine the maximum number of unpublished analytics to hold in memory
     * by Elasticsearch service in a given point of time. If this number is reached any new analytics will be discarded
     */
    public static final String ELASTICSEARCH_QUEUE_SIZE = "external_analytics.elasticsearch.queue.size";

    /**
     * Name of the Synapse configuration used to determine if the Elasticsearch service is enabled
     */
    public static final String ELASTICSEARCH_ENABLED = "external_analytics.elasticsearch.enabled";
}
