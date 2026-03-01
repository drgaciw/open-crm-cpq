package com.opencrm.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.opencrm.configuration", "com.opencrm.common", "com.opencrm.security"})
public class ProductConfigMgmtApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductConfigMgmtApplication.class, args);
    }
}
