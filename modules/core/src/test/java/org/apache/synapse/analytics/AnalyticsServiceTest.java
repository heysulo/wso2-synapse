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

import com.damnhandy.uri.template.UriTemplate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SequenceType;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.rest.RestRequestHandler;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.config.xml.endpoints.HTTPEndpointFactory;
import org.apache.synapse.core.axis2.*;
import org.apache.synapse.endpoints.EndpointDefinition;
import org.apache.synapse.endpoints.HTTPEndpoint;
import org.apache.synapse.mediators.TestUtils;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

import javax.xml.stream.XMLStreamException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Properties;

public class AnalyticsServiceTest extends TestCase {

    private static final String SERVER_INFO_SERVER_NAME = "wso2.dev";
    private static final String SERVER_INFO_HOST_NAME = "dev.local";
    private static final String SERVER_INFO_IP_ADDRESS = "1.2.3.4";
    private static final String SERVER_INFO_PUBLISHER_ID = SERVER_INFO_HOST_NAME;

    private static final String TEST_API_NAME = "TestAPI";
    private static final String TEST_API_CONTEXT = "/test";
    private static final String TEST_API_URL = "/test/admin?search=wso2";
    private static final String TEST_API_METHOD = "GET";
    private static final String TEST_API_PROTOCOL = "https";

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private final SimpleAnalyticsService service = new SimpleAnalyticsService();
    private boolean oneTimeSetupComplete = false;
    private Axis2MessageContext messageContext = null;
    private Axis2SynapseEnvironment synapseEnvironment = null;

    private void oneTimeSetup() throws Exception {
        if (oneTimeSetupComplete) {
            return;
        }

        ServerConfigurationInformation sysConfig = new ServerConfigurationInformation();
        sysConfig.setServerName(SERVER_INFO_SERVER_NAME);
        sysConfig.setHostName(SERVER_INFO_HOST_NAME);
        sysConfig.setIpAddress(SERVER_INFO_IP_ADDRESS);
        AnalyticsPublisher.init(sysConfig);

        synapseEnvironment = PowerMockito.mock(Axis2SynapseEnvironment.class);
        ConfigurationContext axis2ConfigurationContext = new ConfigurationContext(new AxisConfiguration());
        axis2ConfigurationContext.getAxisConfiguration().addParameter(SynapseConstants.SYNAPSE_ENV, synapseEnvironment);
        Mockito.when(synapseEnvironment.getAxis2ConfigurationContext()).thenReturn(axis2ConfigurationContext);

        messageContext = (Axis2MessageContext) getMessageContext();

        oneTimeSetupComplete = true;
    }

    protected MessageContext getMessageContext() throws Exception {
        API api = new API(TEST_API_NAME, TEST_API_CONTEXT);
        String url = TEST_API_URL;
        Resource resource = new Resource();
        api.addResource(resource);

        SynapseConfiguration synapseConfig = new SynapseConfiguration();
        synapseConfig.addAPI(api.getName(), api);

        MessageContext synCtx = TestUtils.createSynapseMessageContext("<foo/>", synapseConfig);
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).
                getAxis2MessageContext();
        msgCtx.setIncomingTransportName(TEST_API_PROTOCOL);
        msgCtx.setProperty(Constants.Configuration.HTTP_METHOD, TEST_API_METHOD);
        msgCtx.setProperty(Constants.Configuration.TRANSPORT_IN_URL, url);
        msgCtx.setProperty(NhttpConstants.REST_URL_POSTFIX, url.substring(1));
        msgCtx.setConfigurationContext(new ConfigurationContext(new AxisConfiguration()));
        return synCtx;
    }

    @Override
    protected void setUp() throws Exception {
        oneTimeSetup();
        service.enableService();
        AnalyticsPublisher.registerService(service);
        AnalyticsPublisher.setNamedSequencesOnly(false);
    }

    @Override
    protected void tearDown() throws Exception {
        AnalyticsPublisher.deregisterService(service);
        service.clear();
    }

    public void testServiceEnabledState() {
        JsonObject analyticData = new JsonObject();
        analyticData.addProperty("testProperty", "HelloWorld");

        service.enableService();
        AnalyticsPublisher.publishAnalytic(analyticData);
        assertTrue(service.isEnabled());
        assertEquals(1, service.getAvailableAnalyticsCount());
        service.clear();

        service.disableService();
        AnalyticsPublisher.publishAnalytic(analyticData);
        assertFalse(service.isEnabled());
        assertEquals(0, service.getAvailableAnalyticsCount());
    }

    public void testBasicAnalyticsSchema() {
        JsonObject analyticData = new JsonObject();
        analyticData.addProperty("testProperty", "HelloWorld");

        AnalyticsPublisher.publishAnalytic(analyticData);
        assertTrue(service.isEnabled());
        assertEquals(1, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.NON_STANDARD);
    }

    public void testSequenceAnalytics() {
        SequenceMediator seq = new SequenceMediator();
        seq.setName("SequenceName");

        seq.mediate(messageContext);
        assertEquals(1, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.SEQUENCE);

        seq.setSequenceType(SequenceType.PROXY_INSEQ);
        AnalyticsPublisher.setNamedSequencesOnly(true);
        seq.mediate(messageContext);
        assertEquals(0, service.getAvailableAnalyticsCount());
    }

    public void testApiResourceAnalytics() {
        RestRequestHandler handler = new RestRequestHandler();
        handler.process(messageContext);
        assertEquals(1, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.API);
    }

    public void testEndpointAnalytics() throws XMLStreamException {
        HTTPEndpointFactory factory = new HTTPEndpointFactory();
        OMElement em = AXIOMUtil.stringToOM(
                "<endpoint><http method=\"GET\" uri-template=\"https://wso2.com\"/></endpoint>");
        EndpointDefinition ep1 = factory.createEndpointDefinition(em);
        HTTPEndpoint httpEndpoint = new HTTPEndpoint();
        httpEndpoint.setHttpMethod("GET");
        httpEndpoint.setDefinition(ep1);
        httpEndpoint.setUriTemplate(UriTemplate.fromTemplate("https://wso2.com"));
        httpEndpoint.init(synapseEnvironment);
        messageContext.setEnvironment(synapseEnvironment);
        httpEndpoint.send(messageContext);
        assertEquals(1, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.ENDPOINT);
    }

    public void testProxyServiceAnalytics() throws XMLStreamException, AxisFault {
        //create ProxyServiceMessageReceiver instance
        ProxyServiceMessageReceiver proxyServiceMessageReceiver = new ProxyServiceMessageReceiver();
        ProxyService proxyService = new ProxyService("TestProxy");
        //create an inSequence and set
        OMElement sequenceAsOM = AXIOMUtil.stringToOM("<inSequence xmlns=\"http://ws.apache.org/ns/synapse\">\n"
                + "         <property name=\"TEST\" scope=\"axis2\" type=\"STRING\" value=\"WSO2\"/>\n"
                + "      </inSequence>");
        proxyService.setTargetInLineInSequence(new SequenceMediatorFactory().
                createAnonymousSequence(sequenceAsOM, new Properties()));

        proxyServiceMessageReceiver.setProxy(proxyService);

        MessageContextCreatorForAxis2.setSynConfig(new SynapseConfiguration());
        MessageContextCreatorForAxis2.setSynEnv(synapseEnvironment);
        messageContext.setEnvironment(synapseEnvironment);

        //invoke
        proxyServiceMessageReceiver.receive(messageContext.getAxis2MessageContext());
        assertEquals(2, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.SEQUENCE);
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.PROXY_SERVICE);
    }

    private void verifySchema(JsonObject analytic, @NotNull AnalyticPayloadType payloadType) {
        assertNotNull(analytic);
        verifySchemaVersion(analytic.get("schemaVersion"));
        verifyServerInfo(analytic.get("serverInfo"));
        verifyTimestamp(analytic.get("timestamp"));

        JsonElement payloadElement = analytic.get("payload");
        switch (payloadType) {
            case PROXY_SERVICE:
                verifyProxyServicePayload(payloadElement);
                break;
            case ENDPOINT:
                verifyEndpointPayload(payloadElement);
                break;
            case API:
                verifyApiResourcePayload(payloadElement);
                break;
            case SEQUENCE:
                verifySequencePayload(payloadElement);
                break;
            default:
                assertTrue(payloadElement.isJsonObject());
        }
    }

    private void verifyServerInfo(JsonElement serverInfoElement) {
        assertNotNull(serverInfoElement);
        assertTrue(serverInfoElement.isJsonObject());

        JsonObject dataObject = serverInfoElement.getAsJsonObject();
        assertTrue(dataObject.has("hostname"));
        assertEquals(SERVER_INFO_HOST_NAME, dataObject.get("hostname").getAsString());
        assertTrue(dataObject.has("serverName"));
        assertEquals(SERVER_INFO_SERVER_NAME, dataObject.get("serverName").getAsString());
        assertTrue(dataObject.has("ipAddress"));
        assertEquals(SERVER_INFO_IP_ADDRESS, dataObject.get("ipAddress").getAsString());
        assertTrue(dataObject.has("publisherId"));
        assertEquals(SERVER_INFO_PUBLISHER_ID, dataObject.get("publisherId").getAsString());
    }

    private void verifyTimestamp(JsonElement timestampElement) {
        assertNotNull(timestampElement);

        try {
            Instant.parse(timestampElement.getAsString());
        } catch (DateTimeParseException e) {
            fail("timestamp should be in ISO8601 format. Found: " + timestampElement.getAsString());
        }
    }

    private void verifySchemaVersion(JsonElement schemaVersionElement) {
        assertNotNull(schemaVersionElement);
        assertEquals(CURRENT_SCHEMA_VERSION, schemaVersionElement.getAsInt());
    }

    private void verifySequencePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);
        assertTrue(payload.has("sequenceDetails"));
        assertTrue(payload.get("sequenceDetails").isJsonObject());

        JsonObject sequenceDetails = payload.get("sequenceDetails").getAsJsonObject();
        assertTrue(sequenceDetails.has("name"));
        assertTrue(sequenceDetails.has("type"));
    }

    private void verifyApiResourcePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has("remoteHost"));
        assertTrue(payload.has("contentType"));
        assertTrue(payload.has("httpMethod"));
        assertEquals(TEST_API_METHOD, payload.get("httpMethod").getAsString());

        assertTrue(payload.has("apiDetails"));
        assertTrue(payload.get("apiDetails").isJsonObject());

        JsonObject apiDetails = payload.get("apiDetails").getAsJsonObject();
        assertTrue(apiDetails.has("api"));
        assertEquals(TEST_API_NAME, apiDetails.get("api").getAsString());
        assertTrue(apiDetails.has("subRequestPath"));
        assertTrue(apiDetails.has("apiContext"));
        assertEquals(TEST_API_CONTEXT, apiDetails.get("apiContext").getAsString());
        assertTrue(apiDetails.has("method"));
        assertEquals(TEST_API_METHOD, apiDetails.get("method").getAsString());
        assertTrue(apiDetails.has("transport"));
    }

    private void verifyEndpointPayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has("endpointDetails"));
        JsonObject endpointDetails = payload.get("endpointDetails").getAsJsonObject();
        assertTrue(endpointDetails.has("name"));
    }

    private void verifyProxyServicePayload(JsonElement payloadElement) {
        assertNotNull(payloadElement);
        assertTrue(payloadElement.isJsonObject());

        JsonObject payload = payloadElement.getAsJsonObject();
        verifyCommonPayloadFields(payload);

        assertTrue(payload.has("proxyServiceDetails"));
        JsonObject proxyServiceDetails = payload.get("proxyServiceDetails").getAsJsonObject();
        assertTrue(proxyServiceDetails.has("name"));
    }

    private void verifyCommonPayloadFields(JsonObject payload) {
        assertTrue(payload.has("entityType"));
        assertTrue(payload.has("entityClassName"));
        assertTrue(payload.has("faultResponse"));
        assertTrue(payload.has("latency"));
        assertTrue(payload.has("metadata"));
    }

    enum AnalyticPayloadType {
        PROXY_SERVICE,
        ENDPOINT,
        API,
        SEQUENCE,
        NON_STANDARD
    }

}
