package com.opencrm.product.catalog;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    public ProductDto create(ProductCreateRequest request) {
        log.debug("Creating product with SKU: {}", request.getSku());

        if (productRepository.existsBySkuAndDeletedFalse(request.getSku())) {
            throw new BusinessException(
                    "Product with SKU " + request.getSku() + " already exists",
                    "DUPLICATE_SKU"
            );
        }

        Product product = productMapper.toEntity(request);
        Product saved = productRepository.save(product);
        log.info("Created product with ID: {}", saved.getId());

        return productMapper.toDto(saved);
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toDto(product);
    }

    @Cacheable(value = "products-by-sku", key = "#sku")
    @Transactional(readOnly = true)
    public ProductDto getBySku(String sku) {
        Product product = productRepository.findBySkuAndDeletedFalse(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product with SKU: " + sku));
        return productMapper.toDto(product);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto update(Long id, ProductUpdateRequest request) {
        log.debug("Updating product ID: {}", id);

        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        productMapper.updateEntity(request, product);
        Product updated = productRepository.save(product);
        log.info("Updated product ID: {}", updated.getId());

        return productMapper.toDto(updated);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public void delete(Long id) {
        log.debug("Deleting product ID: {}", id);

        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.softDelete();
        productRepository.save(product);
        log.info("Deleted product ID: {}", id);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(Pageable pageable, ProductType productType, ProductCategory category, ProductStatus status) {
        Page<Product> products;

        if (productType != null) {
            products = productRepository.findByProductTypeAndDeletedFalse(productType, pageable);
        } else if (category != null) {
            products = productRepository.findByCategoryAndDeletedFalse(category, pageable);
        } else if (status != null) {
            products = productRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            products = productRepository.findByDeletedFalse(pageable);
        }

        return products.map(productMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> search(String query, Pageable pageable) {
        return productRepository.search(query, pageable).map(productMapper::toDto);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto activate(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (product.getStatus() == ProductStatus.RETIRED) {
            throw new BusinessException("Cannot activate a retired product", "INVALID_STATUS_TRANSITION");
        }

        product.setStatus(ProductStatus.ACTIVE);
        Product updated = productRepository.save(product);
        log.info("Activated product ID: {}", id);
        return productMapper.toDto(updated);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto deprecate(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Only active products can be deprecated", "INVALID_STATUS_TRANSITION");
        }

        product.setStatus(ProductStatus.DEPRECATED);
        Product updated = productRepository.save(product);
        log.info("Deprecated product ID: {}", id);
        return productMapper.toDto(updated);
    }
    
    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto retire(Long id, String reason) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // Use state machine method
        product.retire(reason);
        Product updated = productRepository.save(product);
        
        // Publish event via Transactional Outbox
        ProductRetiredEvent event = new ProductRetiredEvent(
                updated.getId().toString(),
                updated.getRetiredAt(),
                reason
        );
        outboxEventPublisher.publish(
                "Product",
                updated.getId().toString(),
                KafkaTopics.PRODUCT_STATUS_RETIRED,
                event
        );
        
        log.info("Retired product ID: {} with reason: {}", id, reason);
        return productMapper.toDto(updated);
    }
}
