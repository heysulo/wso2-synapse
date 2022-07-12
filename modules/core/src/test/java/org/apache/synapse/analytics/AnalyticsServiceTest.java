package org.apache.synapse.analytics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SequenceType;
import org.apache.synapse.ServerConfigurationInformation;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.api.API;
import org.apache.synapse.api.Resource;
import org.apache.synapse.api.rest.RestRequestHandler;
import org.apache.synapse.config.Entry;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;
import org.apache.synapse.mediators.TestUtils;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.rest.RESTRequestHandler;
import org.apache.synapse.transport.netty.BridgeConstants;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsServiceTest extends TestCase {

    private static final String SERVER_INFO_SERVER_NAME = "wso2.dev";
    private static final String SERVER_INFO_HOST_NAME = "dev.local";
    private static final String SERVER_INFO_IP_ADDRESS = "1.2.3.4";
    private static final String TEST_API = "TestAPI";
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private final SimpleAnalyticsService service = new SimpleAnalyticsService();
    private boolean oneTimeSetupComplete = false;
    private Axis2MessageContext messageContext = null;

    private void oneTimeSetup() throws Exception {
        if (oneTimeSetupComplete) {
            return;
        }

        ServerConfigurationInformation sysConfig = new ServerConfigurationInformation();
        sysConfig.setServerName(SERVER_INFO_SERVER_NAME);
        sysConfig.setHostName(SERVER_INFO_HOST_NAME);
        sysConfig.setIpAddress(SERVER_INFO_IP_ADDRESS);
        AnalyticsPublisher.init(sysConfig);


        messageContext = (Axis2MessageContext) getMessageContext();

        oneTimeSetupComplete = true;
    }

    protected MessageContext getMessageContext() throws Exception {
        API api = new API("TestAPI", "/test");
        String url = "/test/admin?search=wso2";
        Resource resource = new Resource();
        api.addResource(resource);

        SynapseConfiguration synapseConfig = new SynapseConfiguration();
        synapseConfig.addAPI(api.getName(), api);

        MessageContext synCtx = TestUtils.createSynapseMessageContext("<foo/>", synapseConfig);
        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).
                getAxis2MessageContext();
        msgCtx.setIncomingTransportName("https");
        msgCtx.setProperty(Constants.Configuration.HTTP_METHOD, "GET");
        msgCtx.setProperty(Constants.Configuration.TRANSPORT_IN_URL, url);
        msgCtx.setProperty(NhttpConstants.REST_URL_POSTFIX, url.substring(1));
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

    public void testApiResourceAnalytics() throws Exception {
        RestRequestHandler handler = new RestRequestHandler();
        handler.process(messageContext);
        assertEquals(1, service.getAvailableAnalyticsCount());
        verifySchema(service.fetchAnalytic(), AnalyticPayloadType.API);
    }

    private void verifySchema(JsonObject analytic, @NotNull AnalyticPayloadType payloadType) {
        assertNotNull(analytic);
        verifySchemaVersion(analytic.get("schemaVersion"));
        verifyServerInfo(analytic.get("serverInfo"));
        verifyTimestamp(analytic.get("timestamp"));

        JsonElement payloadElement = analytic.get("payload");
        switch (payloadType) {
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

        assertTrue(payload.has("apiDetails"));
        assertTrue(payload.get("apiDetails").isJsonObject());

        JsonObject apiDetails = payload.get("apiDetails").getAsJsonObject();
        assertTrue(apiDetails.has("api"));
        assertTrue(apiDetails.has("subRequestPath"));
        assertTrue(apiDetails.has("apiContext"));
        assertTrue(apiDetails.has("method"));
        assertTrue(apiDetails.has("transport"));
    }

    private void verifyCommonPayloadFields(JsonObject payload) {
        assertTrue(payload.has("entityType"));
        assertTrue(payload.has("entityClassName"));
        assertTrue(payload.has("faultResponse"));
        assertTrue(payload.has("latency"));
        assertTrue(payload.has("metadata"));
    }

    enum AnalyticPayloadType {
        API,
        SEQUENCE,
        NON_STANDARD
    }

}
