package com.opencrm.order;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(excludeName = {
 "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration",
 "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration",
 "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration",
 "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration",
 "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration"
})
public class OrderManagementApplication {
 public static void main(String[] args) { SpringApplication.run(OrderManagementApplication.class, args); }
}
