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
    private static final Collection<AnalyticsService> registeredServices = new ArrayList<>();
    private static final JsonObject serverMetadata = new JsonObject();

    private static boolean analyticsDisabledForAPI;
    private static boolean analyticsDisabledForSequences;
    private static boolean analyticsDisabledForProxyServices;
    private static boolean analyticsDisabledForEndpoints;
    private static boolean analyticsDisabledForInboundEndpoints;
    private static boolean namedSequencesOnly;

    public static synchronized void init(ServerConfigurationInformation serverInfo) {
        prepareServerMetadata(serverInfo);
        loadConfigurations();
        prepareAnalyticServices();
    }

    private static void prepareServerMetadata(ServerConfigurationInformation serverInfo) {
        serverMetadata.addProperty(AnalyticsConstants.ServerMetadataFieldDef.HOST_NAME, serverInfo.getHostName());
        serverMetadata.addProperty(AnalyticsConstants.ServerMetadataFieldDef.SERVER_NAME, serverInfo.getServerName());
        serverMetadata.addProperty(AnalyticsConstants.ServerMetadataFieldDef.IP_ADDRESS, serverInfo.getIpAddress());

        String publisherId = SynapsePropertiesLoader.getPropertyValue(
                AnalyticsConstants.SynapseConfiguration.IDENTIFIER, serverInfo.getHostName());
        serverMetadata.addProperty(AnalyticsConstants.ServerMetadataFieldDef.PUBLISHER_ID, publisherId);
    }

    private static void loadConfigurations() {
        analyticsDisabledForAPI = !SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.API_ANALYTICS_ENABLED, true);
        analyticsDisabledForSequences = !SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.SEQUENCE_ANALYTICS_ENABLED, true);
        analyticsDisabledForProxyServices = !SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.PROXY_SERVICE_ANALYTICS_ENABLED, true);
        analyticsDisabledForEndpoints = !SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.ENDPOINT_ANALYTICS_ENABLED, true);
        analyticsDisabledForInboundEndpoints = !SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.INBOUND_ENDPOINT_ANALYTICS_ENABLED, true);
        AnalyticsPublisher.setNamedSequencesOnly(SynapsePropertiesLoader.getBooleanProperty(
                AnalyticsConstants.SynapseConfiguration.NAMED_SEQUENCES_ONLY, false));
    }

    private static void prepareAnalyticServices() {
        registerService(ElasticsearchAnalyticsService.getInstance());
    }

    public static void registerService(AnalyticsService service) {
        if (!service.isEnabled()) {
            return;
        }
        log.info(String.format("Registering analytics service %s", service.getClass().getSimpleName()));
        registeredServices.add(service);
    }

    public static void deregisterService(AnalyticsService service) {
        if (registeredServices.contains(service)) {
            log.info(String.format("Deregistering analytics service %s", service.getClass().getSimpleName()));
            registeredServices.remove(service);
        } else {
            log.warn(String.format("Failed to Deregister analytics service %s. Reason: Not found",
                    service.getClass().getSimpleName()));
        }
    }

    public static void publishAnalytic(JsonObject payload) {
        Instant analyticTimestamp = Instant.now();
        JsonObject analyticsEnvelope = new JsonObject();
        analyticsEnvelope.addProperty(AnalyticsConstants.EnvelopDef.TIMESTAMP, analyticTimestamp.toString());
        analyticsEnvelope.addProperty(AnalyticsConstants.EnvelopDef.SCHEMA_VERSION,
                AnalyticsConstants.SynapseConfiguration.SCHEMA_VERSION);
        analyticsEnvelope.add(AnalyticsConstants.EnvelopDef.SERVER_INFO, serverMetadata);
        analyticsEnvelope.add(AnalyticsConstants.EnvelopDef.PAYLOAD, payload);

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

        if (!(synCtx instanceof Axis2MessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-Axis2MessageContext message for ApiAnalytics");
            }
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, API.class);

        JsonObject apiDetails = new JsonObject();
        apiDetails.addProperty(AnalyticsConstants.EnvelopDef.API,
                (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
        apiDetails.addProperty(AnalyticsConstants.EnvelopDef.SUB_REQUEST_PATH,
                (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
        apiDetails.addProperty(AnalyticsConstants.EnvelopDef.API_CONTEXT,
                (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
        apiDetails.addProperty(AnalyticsConstants.EnvelopDef.METHOD,
                (String) synCtx.getProperty(RESTConstants.REST_METHOD));
        apiDetails.addProperty(AnalyticsConstants.EnvelopDef.TRANSPORT,
                (String) synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.add(AnalyticsConstants.EnvelopDef.API_DETAILS, apiDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    public static void publishSequenceMediatorAnalytics(MessageContext synCtx, SequenceMediator sequence) {
        if (analyticsDisabledForSequences) {
            return;
        }

        if (!(synCtx instanceof Axis2MessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-Axis2MessageContext message for SequenceMediatorAnalytics");
            }
            return;
        }

        if (isNamedSequencesOnly() && !SequenceType.NAMED.equals(sequence.getSequenceType())) {
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, SequenceMediator.class);

        JsonObject sequenceDetails = new JsonObject();
        sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_TYPE, sequence.getSequenceType().toString());
        if (sequence.getSequenceType() == SequenceType.NAMED) {
            sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_NAME, sequence.getName());
        } else {
            sequenceDetails.addProperty(
                    AnalyticsConstants.EnvelopDef.SEQUENCE_NAME, sequence.getSequenceNameForStatistics());
            switch (sequence.getSequenceType()) {
                case API_INSEQ:
                case API_OUTSEQ:
                case API_FAULTSEQ:
                    sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_API_CONTEXT,
                            (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT));
                    sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_API,
                            (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API));
                    sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_API_SUB_REQUEST_PATH,
                            (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH));
                    sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_API_METHOD,
                            (String) synCtx.getProperty(RESTConstants.REST_METHOD));
                    break;
                case PROXY_INSEQ:
                case PROXY_OUTSEQ:
                case PROXY_FAULTSEQ:
                    sequenceDetails.addProperty(AnalyticsConstants.EnvelopDef.SEQUENCE_PROXY_NAME,
                            (String) synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
                    break;
                case ANON:
                    break;
            }
        }

        analytics.add(AnalyticsConstants.EnvelopDef.SEQUENCE_DETAILS, sequenceDetails);
        publishAnalytic(analytics);
    }

    public static void publishProxyServiceAnalytics(MessageContext synCtx) {
        if (analyticsDisabledForProxyServices) {
            return;
        }

        if (!(synCtx instanceof Axis2MessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-Axis2MessageContext message for ProxyServiceAnalytics");
            }
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, ProxyService.class);

        analytics.addProperty(AnalyticsConstants.EnvelopDef.PROXY_SERVICE_TRANSPORT,
                (String) synCtx.getProperty(SynapseConstants.TRANSPORT_IN_NAME));
        analytics.addProperty(AnalyticsConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_REST,
                (boolean) synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_REST));
        analytics.addProperty(AnalyticsConstants.EnvelopDef.PROXY_SERVICE_IS_DOING_SOAP11,
                (boolean) synCtx.getProperty(SynapseConstants.IS_CLIENT_DOING_SOAP11));

        JsonObject proxyServiceDetails = new JsonObject();
        proxyServiceDetails.addProperty(AnalyticsConstants.EnvelopDef.PROXY_SERVICE_NAME,
                (String) synCtx.getProperty(SynapseConstants.PROXY_SERVICE));
        analytics.add(AnalyticsConstants.EnvelopDef.PROXY_SERVICE_DETAILS, proxyServiceDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    public static void publishEndpointAnalytics(MessageContext synCtx, EndpointDefinition endpointDef) {
        if (analyticsDisabledForEndpoints) {
            return;
        }

        if (!(synCtx instanceof Axis2MessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-Axis2MessageContext message for EndpointAnalytics");
            }
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, Endpoint.class);

        JsonObject endpointDetails = new JsonObject();
        endpointDetails.addProperty(AnalyticsConstants.EnvelopDef.ENDPOINT_NAME, endpointDef.leafEndpoint.getName());
        analytics.add(AnalyticsConstants.EnvelopDef.ENDPOINT_DETAILS, endpointDetails);

        publishAnalytic(analytics);
    }

    public static void publishInboundEndpointAnalytics(MessageContext synCtx, InboundEndpoint endpointDef) {
        if (analyticsDisabledForInboundEndpoints) {
            return;
        }

        if (endpointDef == null) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring analytics for unknown InboundEndpoint");
            }
            return;
        }

        if (!(synCtx instanceof Axis2MessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-Axis2MessageContext message for InboundEndpointAnalytics");
            }
            return;
        }

        JsonObject analytics = generateAnalyticsObject(synCtx, InboundEndpoint.class);

        JsonObject inboundEndpointDetails = new JsonObject();
        inboundEndpointDetails.addProperty(AnalyticsConstants.EnvelopDef.INBOUND_ENDPOINT_NAME, endpointDef.getName());
        inboundEndpointDetails.addProperty(AnalyticsConstants.EnvelopDef.INBOUND_ENDPOINT_PROTOCOL, endpointDef.getProtocol());
        analytics.add(AnalyticsConstants.EnvelopDef.INBOUND_ENDPOINT_DETAILS, inboundEndpointDetails);
        attachHttpProperties(analytics, synCtx);

        publishAnalytic(analytics);
    }

    private static JsonObject generateAnalyticsObject(MessageContext synCtx, Class<?> entityClass) {
        JsonObject analytics = new JsonObject();
        analytics.addProperty(AnalyticsConstants.EnvelopDef.ENTITY_TYPE, entityClass.getSimpleName());
        analytics.addProperty(AnalyticsConstants.EnvelopDef.ENTITY_CLASS_NAME, entityClass.getName());
        analytics.addProperty(AnalyticsConstants.EnvelopDef.FAULT_RESPONSE, synCtx.isFaultResponse());
        analytics.addProperty(AnalyticsConstants.EnvelopDef.MESSAGE_ID, synCtx.getMessageID());
        analytics.addProperty(AnalyticsConstants.EnvelopDef.CORRELATION_ID,
                (String) synCtx.getProperty(CorrelationConstants.CORRELATION_ID));
        analytics.addProperty(AnalyticsConstants.EnvelopDef.LATENCY, synCtx.getLatency());

        JsonObject metadata = new JsonObject();
        Axis2MessageContext axis2mc = (Axis2MessageContext) synCtx;
        for (Map.Entry<String, Object> entry : axis2mc.getAnalyticsMetadata().entrySet()) {
            Object value = entry.getValue();

            if (value == null) {
                continue; // Logstash fails at null
            }

            if (value instanceof Boolean) {
                metadata.addProperty(entry.getKey(), (Boolean) value);
            } else if (value instanceof Double) {
                metadata.addProperty(entry.getKey(), (Double) value);
            } else if (value instanceof Float) {
                metadata.addProperty(entry.getKey(), (Float) value);
            } else if (value instanceof Integer) {
                metadata.addProperty(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                metadata.addProperty(entry.getKey(), (Long) value);
            } else if (value instanceof Short) {
                metadata.addProperty(entry.getKey(), (Short) value);
            } else if (value instanceof JsonObject) {
                metadata.add(entry.getKey(), (JsonObject) value);
            } else {
                metadata.addProperty(entry.getKey(), value.toString());
            }
        }

        analytics.add(AnalyticsConstants.EnvelopDef.METADATA, metadata);
        return analytics;
    }

    private static void attachHttpProperties(JsonObject json, MessageContext synCtx) {

        org.apache.axis2.context.MessageContext axisCtx = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        if (axisCtx == null) {
            return;
        }

        json.addProperty(AnalyticsConstants.EnvelopDef.REMOTE_HOST,
                (String) axisCtx.getProperty(BridgeConstants.REMOTE_HOST));
        json.addProperty(AnalyticsConstants.EnvelopDef.CONTENT_TYPE,
                (String) axisCtx.getProperty(BridgeConstants.CONTENT_TYPE_HEADER));
        json.addProperty(AnalyticsConstants.EnvelopDef.HTTP_METHOD,
                (String) axisCtx.getProperty(BridgeConstants.HTTP_METHOD));
    }

    public static boolean isNamedSequencesOnly() {
        return namedSequencesOnly;
    }

    public static void setNamedSequencesOnly(boolean namedSequencesOnly) {
        AnalyticsPublisher.namedSequencesOnly = namedSequencesOnly;
    }
}
