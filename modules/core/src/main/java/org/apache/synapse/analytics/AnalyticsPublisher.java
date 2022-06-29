package org.apache.synapse.analytics;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SequenceType;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.analytics.elastic.ElasticsearchAnalyticsService;
import org.apache.synapse.api.API;
import org.apache.synapse.commons.CorrelationConstants;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.endpoints.EndpointDefinition;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.netty.BridgeConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class AnalyticsPublisher {
    private static final Log log = LogFactory.getLog(AnalyticsPublisher.class);
    private static final Collection<AbstractAnalyticsService> registeredServices = new ArrayList<>();
    private static final JsonObject serverInfo = new JsonObject();

    private static boolean analyticsDisabledForAPI;
    private static boolean analyticsDisabledForSequences;
    private static boolean analyticsDisabledForProxyServices;
    private static boolean analyticsDisabledForEndpoints;
    private static boolean analyticsDisabledForInboundEndpoints;
    private static boolean namedSequencesOnly;

    public static synchronized void init(ServerConfigurationInformation serverInfo) {
        prepareServerMetadata(serverInfo);
        loadConfigurations();
        startAnalyticServices();
    }

    private static void prepareServerMetadata(ServerConfigurationInformation serverInfo) {
        AnalyticsPublisher.serverInfo.addProperty("hostname", serverInfo.getHostName());
        AnalyticsPublisher.serverInfo.addProperty("serverName", serverInfo.getServerName());
        AnalyticsPublisher.serverInfo.addProperty("ipAddress", serverInfo.getIpAddress());

        String publisherId = SynapsePropertiesLoader.getPropertyValue(
                AnalyticsConstants.PUBLISHER_IDENTIFIER, serverInfo.getHostName());
        AnalyticsPublisher.serverInfo.addProperty("publisherId", publisherId);
    }

    private static void loadConfigurations() {
        analyticsDisabledForAPI = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_DISABLED_API, false);
        analyticsDisabledForSequences = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_DISABLED_SEQUENCES, false);
        analyticsDisabledForProxyServices = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_DISABLED_PROXY_SERVICE, false);
        analyticsDisabledForEndpoints = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_DISABLED_ENDPOINTS, false);
        analyticsDisabledForInboundEndpoints = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_DISABLED_INBOUND_ENDPOINTS, false);
        namedSequencesOnly = SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.PUBLISHER_NAMED_SEQUENCES_ONLY, false);
    }

    private static void startAnalyticServices() {
        startService(ElasticsearchAnalyticsService.getInstance());
    }

    private static void startService(AbstractAnalyticsService service) {
        if (!service.isEnabled()) {
            return;
        }
        log.info(String.format("Enabling analytics service %s", service.getClass().getSimpleName()));
        registeredServices.add(service);
    }

    private static void publishAnalytic(JsonObject payload) {
        Instant analyticTimestamp = Instant.now();
        JsonObject analyticsEnvelope = new JsonObject();
        analyticsEnvelope.addProperty("timestamp", analyticTimestamp.toString());
        analyticsEnvelope.addProperty("schemaVersion", AnalyticsConstants.SCHEMA_VERSION);
        analyticsEnvelope.add("serverInfo", serverInfo);
        analyticsEnvelope.add("payload", payload);

        registeredServices.forEach(service -> {
            if (service.isEnabled()) {
                service.publish(analyticsEnvelope);
            }
        });
    }

    public static void publishApiAnalytics(MessageContext synCtx) {
        if (analyticsDisabledForAPI) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, API.class);

        JsonObject apiDetails = new JsonObject();
        apiDetails.addProperty("api", (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
        apiDetails.addProperty("subRequestPath", (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
        apiDetails.addProperty("apiContext", (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
        apiDetails.addProperty("method", (String) synCtx.getProperty(RESTConstants.REST_METHOD));
        apiDetails.addProperty("transport", (String) synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.add("apiDetails", apiDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    public static void publishSequenceMediatorAnalytics(MessageContext synCtx, SequenceMediator sequence) {
        if (analyticsDisabledForSequences) {
            return;
        }

        if (namedSequencesOnly && !SequenceType.NAMED.equals(sequence.getSequenceType())) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, SequenceMediator.class);

        JsonObject sequenceDetails = new JsonObject();
        sequenceDetails.addProperty("type", sequence.getSequenceType().toString());
        if (sequence.getSequenceType() == SequenceType.NAMED) {
            sequenceDetails.addProperty("name", sequence.getName());
        } else {
            sequenceDetails.addProperty("name", sequence.getSequenceNameForStatistics());
            switch (sequence.getSequenceType()) {
                case API_INSEQ:
                case API_OUTSEQ:
                case API_FAULTSEQ:
                    sequenceDetails.addProperty("apiContext", (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
                    sequenceDetails.addProperty("api", (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
                    sequenceDetails.addProperty("subRequestPath", (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
                    sequenceDetails.addProperty("method", (String) synCtx.getProperty(RESTConstants.REST_METHOD));
                    break;
                case PROXY_INSEQ:
                case PROXY_OUTSEQ:
                case PROXY_FAULTSEQ:
                    sequenceDetails.addProperty("proxyName", (String) synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
                    break;
                case ANON:
                    break;
            }
        }

        analytics.add("sequenceDetails", sequenceDetails);
        publishAnalytic(analytics);
    }

    public static void publishProxyServiceAnalytics(MessageContext synCtx) {
        if (analyticsDisabledForProxyServices) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, ProxyService.class);

        analytics.addProperty("transport", (String) synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.addProperty("isClientDoingREST", (boolean) synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_REST));
        analytics.addProperty("isClientDoingSOAP11", (boolean) synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_SOAP11));

        JsonObject proxyServiceDetails = new JsonObject();
        proxyServiceDetails.addProperty("name", (String) synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
        analytics.add("proxyServiceDetails", proxyServiceDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    public static void publishEndpointAnalytics(MessageContext synCtx, EndpointDefinition endpointDef) {
        if (analyticsDisabledForEndpoints) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, Endpoint.class);

        JsonObject endpointDetails = new JsonObject();
        endpointDetails.addProperty("name", endpointDef.leafEndpoint.getName());
        analytics.add("endpointDetails", endpointDetails);

        publishAnalytic(analytics);
    }

    public static void publishInboundEndpointAnalytics(MessageContext synCtx, InboundEndpoint endpointDef) {
        if (analyticsDisabledForInboundEndpoints) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, InboundEndpoint.class);

        JsonObject inboundEndpointDetails = new JsonObject();
        inboundEndpointDetails.addProperty("name", endpointDef.getName());
        inboundEndpointDetails.addProperty("protocol", endpointDef.getProtocol());
        analytics.add("inboundEndpointDetails", inboundEndpointDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    private static JsonObject generateAnalyticsObject(MessageContext synCtx, Class<?> entityClass) {
        JsonObject analytics = new JsonObject();
        analytics.addProperty("entityType", entityClass.getSimpleName());
        analytics.addProperty("entityClassName", entityClass.getName());
        analytics.addProperty("faultResponse", synCtx.isFaultResponse());
        analytics.addProperty("messageId", synCtx.getMessageID());
        analytics.addProperty("correlation_id", (String) synCtx.getProperty(CorrelationConstants.CORRELATION_ID));
        analytics.addProperty("latency", synCtx.getLatency());

        JsonObject customAnalytics = new JsonObject();
        Axis2MessageContext axis2mc = (Axis2MessageContext) synCtx;
        for (Map.Entry<String, Object> entry : axis2mc.getAnalyticsMetadata().entrySet()) {
            Object value = entry.getValue();

            if (value == null) {
                continue; // Logstash fails at null
            }

            if (value instanceof Boolean) {
                customAnalytics.addProperty(entry.getKey(), (Boolean) value);
            } else if (value instanceof Double) {
                customAnalytics.addProperty(entry.getKey(), (Double) value);
            } else if (value instanceof Float) {
                customAnalytics.addProperty(entry.getKey(), (Float) value);
            } else if (value instanceof Integer) {
                customAnalytics.addProperty(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                customAnalytics.addProperty(entry.getKey(), (Long) value);
            } else if (value instanceof Short) {
                customAnalytics.addProperty(entry.getKey(), (Short) value);
            } else if (value instanceof JsonObject) {
                customAnalytics.add(entry.getKey(), (JsonObject) value);
            } else {
                customAnalytics.addProperty(entry.getKey(), value.toString());
            }
        }

        analytics.add("customAnalytics", customAnalytics);
        return analytics;
    }

    private static void attachHttpProperties(JsonObject json, MessageContext synCtx) {

        org.apache.axis2.context.MessageContext axisCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        if (axisCtx == null) {
            return;
        }

        json.addProperty("remoteHost", (String) axisCtx.getProperty(BridgeConstants.REMOTE_HOST));
        json.addProperty("contentType", (String) axisCtx.getProperty(BridgeConstants.CONTENT_TYPE_HEADER));
        json.addProperty("httpMethod", (String) axisCtx.getProperty(BridgeConstants.HTTP_METHOD));
    }

}
