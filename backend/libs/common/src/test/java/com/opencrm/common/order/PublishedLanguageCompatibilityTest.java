package com.opencrm.common.order;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Old strict consumers read additive producer fields while hashing the original JSON tree. */
class PublishedLanguageCompatibilityTest {
 private final ObjectMapper jackson2 = new ObjectMapper().findAndRegisterModules()
         .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 private final tools.jackson.databind.json.JsonMapper jackson3 = tools.jackson.databind.json.JsonMapper.builder()
         .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
 private static final String LINE = """
  {"sourceLineId":"line-1","productId":"product","description":"Monthly service","fulfillmentType":"SERVICE",
   "chargeType":"RECURRING","quantity":2,"uom":"EA","unitPrice":"10.00","discount":"0.01","tax":"0.02",
   "total":"20.01","billingFrequency":"MONTHLY","startDate":"2026-01-01","endDate":"2027-01-01",
   "futureLineEvidence":{"authority":"approved-source","version":2}}
  """;
 private static final String SNAPSHOT = """
  {"schemaVersion":1,"snapshotId":"snapshot","quoteId":"00000000-0000-0000-0000-000000000001",
   "quoteNumber":"Q-1","acceptedRevision":1,"scopeId":"pilot","customerId":"customer",
   "acceptedAt":"2026-09-01T00:00:00Z","currency":"USD","subtotal":"20.00","discount":"0.01",
   "tax":"0.02","total":"20.01","address":"approved address","approvalReference":"approval",
   "taxEvidence":"tax","configurationReference":"configuration","futureSnapshotEvidence":["v2"],"lines":[%s]}
  """.formatted(LINE);
 @Test void strictJackson2And3ReadAdditiveEventSnapshotAndLineWithoutChangingHashInput() throws Exception {
  String original = jackson2.readTree(SNAPSHOT).toString();
  String envelope = "{\"eventId\":\"accepted\",\"snapshot\":" + original + ",\"snapshotHash\":\"" + hash(original) + "\",\"futureEnvelope\":true}";
  var oldConsumer = jackson2.readValue(envelope,AcceptedSnapshotEvent.class);
  var currentConsumer = jackson3.readValue(envelope,AcceptedSnapshotEvent.class);
  assertEquals(oldConsumer,currentConsumer);
  assertEquals("20.01",oldConsumer.snapshot().lines().getFirst().total());
  // These are the existing listener boundaries: extract the exact compact snapshot tree before decoding.
  assertEquals(oldConsumer.snapshotHash(),hash(jackson2.readTree(envelope).get("snapshot").toString()));
  assertEquals(oldConsumer.snapshotHash(),hash(jackson3.readTree(envelope).get("snapshot").toString()));
  assertNotEquals(oldConsumer.snapshotHash(),hash(jackson2.writeValueAsString(oldConsumer.snapshot())),
          "A compatibility DTO must never replace the original hash input");
  String tampered=original.replace("approved-source","altered-source");
  assertNotEquals(oldConsumer.snapshotHash(),hash(tampered),"Even ignored additive fields remain hash-protected");
 }
 @Test void strictConsumersReadAdditiveActivationRequestIncludingNestedLine() throws Exception {
  String payload="{\"eventId\":\"request\",\"operationKey\":\"operation\",\"orderId\":\"order\",\"scopeId\":\"pilot\",\"customerId\":\"customer\",\"quoteId\":\"00000000-0000-0000-0000-000000000001\",\"acceptedRevision\":1,\"line\":"+LINE+",\"evidenceReference\":\"delivery\",\"completedAt\":\"2026-09-01T00:00:00Z\",\"futureRequest\":{\"trace\":1}}";
  var oldConsumer=jackson2.readValue(payload,ActivationRequest.class);
  assertEquals(oldConsumer,jackson3.readValue(payload,ActivationRequest.class));
  assertEquals("MONTHLY",oldConsumer.line().billingFrequency());assertEquals("2026-01-01",oldConsumer.line().startDate().toString());
 }
 @Test void strictConsumersReadAdditiveActivationAcknowledgment() throws Exception {
  String payload="{\"eventId\":\"ack\",\"operationKey\":\"operation\",\"orderId\":\"order\",\"scopeId\":\"pilot\",\"subscriptionId\":\"subscription\",\"status\":\"ACKNOWLEDGED\",\"evidenceReference\":\"contract\",\"occurredAt\":\"2026-09-01T00:00:00Z\",\"futureAck\":true}";
  var oldConsumer=jackson2.readValue(payload,ActivationAcknowledgment.class);
  assertEquals(oldConsumer,jackson3.readValue(payload,ActivationAcknowledgment.class));assertEquals("ACKNOWLEDGED",oldConsumer.status());
 }
 @Test void unknownSchemaVersionIsPreservedForExistingDomainRejection() throws Exception {
  String unsupported=SNAPSHOT.replace("\"schemaVersion\":1","\"schemaVersion\":99");
  assertEquals(99,jackson2.readValue(unsupported,AcceptedOrderSnapshot.class).schemaVersion());
  assertEquals(99,jackson3.readValue(unsupported,AcceptedOrderSnapshot.class).schemaVersion());
  assertTrue(jackson2.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
  assertTrue(jackson3.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
 }
 private static String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
