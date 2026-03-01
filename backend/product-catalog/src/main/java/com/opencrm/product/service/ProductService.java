package com.opencrm.product.service;

import com.opencrm.product.domain.Product;
import com.opencrm.product.domain.ProductStatus;
import com.opencrm.product.repository.ProductRepository;
import com.opencrm.product.repository.OutboxEventRepository;
import com.opencrm.shared.outbox.OutboxEvent;
import com.opencrm.shared.kafka.KafkaTopics;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Product createProduct(Product product) {
        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.DRAFT);
        }
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        // Prevent updates if retired? The prompt says DRAFT->ACTIVE->RETIRED.
        if (product.getStatus() == ProductStatus.RETIRED) {
             throw new IllegalStateException("Cannot update retired product");
        }

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        if (productDetails.getBasePrice() != null) {
            product.setBasePrice(productDetails.getBasePrice());
        }
        
        return productRepository.save(product);
    }

    @Transactional
    public Product updateStatus(Long id, ProductStatus newStatus) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        if (product.getStatus() == ProductStatus.RETIRED && newStatus != ProductStatus.RETIRED) {
             throw new IllegalStateException("Cannot revive retired product");
        }
        
        if (newStatus == ProductStatus.RETIRED && product.getStatus() != ProductStatus.RETIRED) {
             // Publish event via Outbox
             String payload = String.format("{\"product_id\": \"%s\", \"retired_at\": \"%s\", \"reason_code\": \"MANUAL\"}", 
                 id, Instant.now().toString());
                 
             OutboxEvent event = OutboxEvent.builder()
                 .aggregateType("Product")
                 .aggregateId(id.toString())
                 .type(KafkaTopics.PRODUCT_STATUS_RETIRED)
                 .payload(payload)
                 .build();
             outboxEventRepository.save(event);
        }
        
        product.setStatus(newStatus);
        return productRepository.save(product);
    }
}
