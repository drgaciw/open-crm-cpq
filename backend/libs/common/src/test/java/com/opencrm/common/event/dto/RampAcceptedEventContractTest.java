package com.opencrm.common.event.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Wire preservation only; money validation is service-owned. */
class RampAcceptedEventContractTest {
 private final ObjectMapper mapper=new ObjectMapper();
 @Test void preservesAdditiveV1RateAndMutationFacts() throws Exception {
  String payload="""
   {"eventType":"QuoteAccepted","eventVersion":1,"quoteId":"quote-ramp",
    "pricing":{"arr":12000,"exitArr":24000},
    "subscriptionChange":{"targetSubscriptionNumber":"SUB-7","observedRevision":7,"effectiveDate":"2027-07-15"},
    "lineItems":[{"lineUuid":"line-1","sourceSubscriptionLineUuid":"prior-line",
      "hybridMigration":{"pairId":"pair-1","role":"DECLINING","startMonth":7,"months":6,"bookValue":10000},
      "ramp":[{"sequence":1,"startMonth":1,"months":12,"quantity":10,"netUnitPrice":100,
        "netMonthly":1000,"periodAmount":9000,"discountRate":0,"explanationCodes":["MIGRATION_CREDIT"],
        "grossPeriodAmount":12000,"migrationCredit":3000}],
      "initialProration":{"startDate":"2027-07-15","endDate":"2027-07-31",
        "billingPeriodStart":"2027-07-01","billingPeriodEnd":"2027-07-31",
        "quantity":10,"netUnitPrice":100,"netMonthly":1000,"periodAmount":548.39}}]}
   """;
  JsonNode expected=mapper.readTree(payload);
  JsonNode actual=mapper.readTree(mapper.writeValueAsString(mapper.readValue(payload,QuoteAcceptedEvent.class)));
  assertThat(actual.path("eventVersion").intValue()).isEqualTo(1);
  assertThat(actual.path("subscriptionChange")).isEqualTo(expected.path("subscriptionChange"));
  assertThat(actual.path("pricing").path("exitArr")).isEqualTo(expected.path("pricing").path("exitArr"));
  for(String field:new String[]{"sourceSubscriptionLineUuid","hybridMigration","ramp","initialProration"})
   assertThat(actual.path("lineItems").get(0).path(field)).as(field).isEqualTo(expected.path("lineItems").get(0).path(field));
 }
 @Test void legacyShapeOmitsAdditionsWithoutChangingExistingNestedNulls() throws Exception {
  QuoteAcceptedEvent legacy=mapper.readValue("""
   {"quoteId":"legacy","pricing":{"totalAmount":900,"currencyCode":"USD"},"lineItems":[{"lineUuid":"old-line","quantity":2}]}
   """,QuoteAcceptedEvent.class);
  JsonNode json=mapper.valueToTree(legacy);
  assertThat(json.has("subscriptionChange")).isFalse();
  assertThat(json.path("pricing").has("exitArr")).isFalse();
  assertThat(json.path("pricing").get("arr").isNull()).isTrue();
  for(String field:new String[]{"ramp","sourceSubscriptionLineUuid","hybridMigration","initialProration"})
   assertThat(json.path("lineItems").get(0).has(field)).as(field).isFalse();
 }
}
