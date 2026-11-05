package com.opencrm.order.execution;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class MessagingTrustConfigurationTest {
 @Test void ingressCannotEnableWithScopeClaimsAlone() {var e=new MockEnvironment().withProperty("oms.snapshot-consumer-enabled","true").withProperty("oms.trusted-source-scopes","pilot");assertThrows(IllegalStateException.class,()->new MessagingTrustConfiguration().verifiedOrderMessagingBoundary(e));}
 @Test void bothTrustedProducersAndAuthenticatedBrokerAreRequired() {var e=new MockEnvironment().withProperty("oms.snapshot-consumer-enabled","true").withProperty("oms.execution.callback-enabled","true").withProperty("oms.trusted-source-scopes","pilot").withProperty("oms.execution.allowed-scopes","pilot").withProperty("spring.kafka.properties.security.protocol","SASL_SSL").withProperty("oms.messaging.producer-acl-verified","true").withProperty("oms.messaging.trusted-producer-principals","quote-service");assertThrows(IllegalStateException.class,()->new MessagingTrustConfiguration().verifiedOrderMessagingBoundary(e));e.withProperty("oms.messaging.trusted-producer-principals","quote-service,contract-service");assertNotNull(new MessagingTrustConfiguration().verifiedOrderMessagingBoundary(e));}
 @Test void ingressIsDisabledByDefault() {assertNotNull(new MessagingTrustConfiguration().verifiedOrderMessagingBoundary(new MockEnvironment()));}
}
