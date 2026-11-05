package com.opencrm.order.execution;
import com.opencrm.common.event.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"oms.scope-id=pilot","oms.security.issuer-uri=https://issuer.test","oms.security.jwk-set-uri=https://issuer.test/jwks","oms.outbox-relay.enabled=true","oms.execution.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@org.junit.jupiter.api.Tag("integration")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ExecutionRelayBootIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {r.add("spring.datasource.url",()->postgres.getJdbcUrl()+"&currentSchema=order_management");r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
 @Autowired OrderOutboxRelay relay;
 @Autowired OutboxDeliveryStore deliveryStore;
 @Autowired OutboxEventRepository repository;
 @Autowired KafkaTemplate<String,String> kafka;
 @Test void enabledRelayHasRealDatabaseStoreRepositoryAndStringProducer() {
  assertNotNull(relay);assertNotNull(deliveryStore);assertEquals(0,repository.count());
  var config=kafka.getProducerFactory().getConfigurationProperties();assertEquals(StringSerializer.class,config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));assertEquals(StringSerializer.class,config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
 }
}
