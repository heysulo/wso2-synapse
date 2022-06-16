package org.apache.synapse.analytics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SequenceType;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.analytics.elastic.ElasticsearchAnalyticsServiceThread;
import org.apache.synapse.api.API;
import org.apache.synapse.commons.CorrelationConstants;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.endpoints.EndpointDefinition;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTConstants;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

public class ExternalAnalyticsPublisher {
    private static final Log log = LogFactory.getLog(ExternalAnalyticsPublisher.class);
    private static final Collection<AbstractExternalAnalyticsServiceThread> registeredServices = new ArrayList<>();

    public static void spawnServices() {
        startService(ElasticsearchAnalyticsServiceThread.getInstance());
    }

    public static void shutdownServices() {
        registeredServices.forEach(service -> {
            log.info(String.format("shutting down external analytics service %s", service.getClass().getSimpleName()));
            if (service.isRunning()) {
                service.requestShutdown();
                try {
                    service.join(1500);
                } catch (InterruptedException e) {
                    log.warn(String.format("Failed to gracefully shutdown %s", service.getClass().getSimpleName()));
                }
            }
        });
    }

    private static void startService(AbstractExternalAnalyticsServiceThread service) {
        if (!service.isEnabled()) {
            return;
        }

        log.info(String.format("Spawning external analytics service %s", service.getClass().getSimpleName()));
        registeredServices.add(service);
        service.start();
    }

    public static void publishAnalytic(JSONObject data) {
        registeredServices.forEach(service -> service.publish(data));
    }

    public static void publishApiAnalytics(MessageContext synCtx) {
        JSONObject analytics = generateAnalyticsObject(synCtx, API.class);

        JSONObject apiDetails = new JSONObject();
        apiDetails.put("api", synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
        apiDetails.put("subRequestPath", synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
        apiDetails.put("apiContext", synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
        apiDetails.put("method", synCtx.getProperty(RESTConstants.REST_METHOD));
        apiDetails.put("transport", synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.put("apiDetails", apiDetails);

        publishAnalytic(analytics);
    }

    public static void publishSequenceMediatorAnalytics(MessageContext synCtx, SequenceMediator sequence) {
        JSONObject analytics = generateAnalyticsObject(synCtx, SequenceMediator.class);

        JSONObject sequenceDetails = new JSONObject();
        sequenceDetails.put("type", sequence.getSequenceType().toString());
        if (sequence.getSequenceType() == SequenceType.NAMED) {
            sequenceDetails.put("name", sequence.getName());
        } else {
            sequenceDetails.put("name", sequence.getSequenceNameForStatistics());
            switch (sequence.getSequenceType()) {
                case API_INSEQ:
                case API_OUTSEQ:
                case API_FAULTSEQ:
                    sequenceDetails.put("apiContext", synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
                    sequenceDetails.put("api", synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
                    sequenceDetails.put("subRequestPath", synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
                    sequenceDetails.put("method", synCtx.getProperty(RESTConstants.REST_METHOD));
                    break;
                case PROXY_INSEQ:
                case PROXY_OUTSEQ:
                case PROXY_FAULTSEQ:
                    sequenceDetails.put("proxyName", synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
                    break;
                case ANON:
                    break;
            }
        }

        analytics.put("sequenceDetails", sequenceDetails);
        publishAnalytic(analytics);
    }

    public static void publishProxyServiceAnalytics(MessageContext synCtx) {
        JSONObject analytics = generateAnalyticsObject(synCtx, ProxyService.class);
        analytics.put("transport", synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.put("isClientDoingREST", synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_REST));
        analytics.put("isClientDoingSOAP11", synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_SOAP11));

        JSONObject proxyServiceDetails = new JSONObject();
        proxyServiceDetails.put("name", synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
        analytics.put("proxyServiceDetails", proxyServiceDetails);

        publishAnalytic(analytics);
    }

    public static void publishEndpointAnalytics(MessageContext synCtx, EndpointDefinition endpointDef) {
        JSONObject analytics = generateAnalyticsObject(synCtx, Endpoint.class);

        JSONObject endpointDetails = new JSONObject();
        endpointDetails.put("name", endpointDef.leafEndpoint.getName());
        analytics.put("endpointDetails", endpointDetails);

        publishAnalytic(analytics);
    }

    public static void publishInboundEndpointAnalytics(MessageContext synCtx, InboundEndpoint endpointDef) {
        JSONObject analytics = generateAnalyticsObject(synCtx, InboundEndpoint.class);

        JSONObject inboundEndpointDetails = new JSONObject();
        inboundEndpointDetails.put("name", endpointDef.getName());
        inboundEndpointDetails.put("protocol", endpointDef.getProtocol());
        analytics.put("endpointDetails", inboundEndpointDetails);

        publishAnalytic(analytics);
    }

    private static JSONObject generateAnalyticsObject(MessageContext synCtx, Class<?> entityClass) {
        JSONObject analytics = new JSONObject();
        analytics.put("entityType", entityClass.getSimpleName());
        analytics.put("entityClassName", entityClass.getName());
        analytics.put("faultResponse", synCtx.isFaultResponse());
        analytics.put("messageId", synCtx.getMessageID());
        analytics.put("messageId", synCtx.getProperty(CorrelationConstants.CORRELATION_ID));
        analytics.put("latency", synCtx.getLatency());
        return analytics;
    }
}
