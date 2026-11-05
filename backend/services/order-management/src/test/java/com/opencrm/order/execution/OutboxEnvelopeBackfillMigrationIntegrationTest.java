package com.opencrm.order.execution;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** V11 backfills outbox_events.status from the pre-V11 columns: published, and exhausted_at set by OutboxDeliveryStore. */
@Testcontainers
@org.junit.jupiter.api.Tag("integration")
class OutboxEnvelopeBackfillMigrationIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("oms");

 private static Flyway flyway(String target) {
  var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management");
  if (target!=null) config.target(target);
  return config.load();
 }

 private static void insert(JdbcTemplate db,String uuid,boolean published,int retries,boolean exhausted) {
  db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,published,retry_count,exhausted_at) VALUES (?,'Order',?,'oms.test.v1','{}',?,?,"+(exhausted?"now()":"NULL")+")",uuid,uuid,published,retries);
 }

 @Test void unpublishedExhaustedRowsBecomeFailedAndOthersKeepTheirMeaning() {
  flyway("10").migrate();
  var db=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword()));
  insert(db,"exhausted-unpublished",false,5,true);
  insert(db,"within-retries",false,2,false);
  insert(db,"published",true,1,false);
  insert(db,"published-and-exhausted",true,5,true);
  flyway(null).migrate();
  assertEquals("FAILED",status(db,"exhausted-unpublished"));
  assertEquals("PENDING",status(db,"within-retries"));
  assertEquals("PUBLISHED",status(db,"published"));
  assertEquals("PUBLISHED",status(db,"published-and-exhausted"),"published wins over exhausted");
 }

 private static String status(JdbcTemplate db,String uuid) {
  return db.queryForObject("SELECT status FROM outbox_events WHERE uuid=?",String.class,uuid);
 }
}
