package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ProductCatalogLifecycleEvent;
import com.opencrm.common.event.dto.ProductDefinedEvent;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.common.event.dto.ProductRevisedEvent;
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

        // DRG-418: SKU is unique per tenant, not globally.
        String tenantId = ProductTenantResolver.currentTenantId();
        if (productRepository.existsByTenantIdAndSkuAndDeletedFalse(tenantId, request.getSku())) {
            throw new BusinessException(
                    "Product with SKU " + request.getSku() + " already exists",
                    "DUPLICATE_SKU"
            );
        }

        Product product = productMapper.toEntity(request);
        product.setTenantId(tenantId);
        Product saved = productRepository.save(product);
        log.info("Created product with ID: {}", saved.getId());

        // DRG-418 / BC-01-01: DefineProduct emits ProductDefined.
        outboxEventPublisher.publish(
                "Product",
                saved.getId().toString(),
                EventContractRegistry.PRODUCT_DEFINED_V1,
                new ProductDefinedEvent(
                        saved.getId().toString(),
                        saved.getSku(),
                        saved.getName(),
                        saved.getUnitOfMeasure(),
                        saved.getStatus().name(),
                        saved.getEffectiveFrom(),
                        saved.getEffectiveTo(),
                        Instant.now()
                )
        );

        return productMapper.toDto(saved);
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toDto(product);
    }

    // DRG-418: SKU lookup is scoped to the caller's tenant, matching create's uniqueness scope; the cache key
    // includes the tenant so one tenant's cache entry is never served to another.
    @Cacheable(value = "products-by-sku", key = "T(com.opencrm.product.catalog.ProductTenantResolver).currentTenantId() + ':' + #sku")
    @Transactional(readOnly = true)
    public ProductDto getBySku(String sku) {
        String tenantId = ProductTenantResolver.currentTenantId();
        Product product = productRepository.findByTenantIdAndSkuAndDeletedFalse(tenantId, sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product with SKU: " + sku));
        return productMapper.toDto(product);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto update(Long id, ProductUpdateRequest request) {
        log.debug("Updating product ID: {}", id);

        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // DRG-418: the generic revise endpoint can still change status directly (pre-existing shape, kept for the
        // CRM product-detail form), but LifecycleState transitions must be legal even here, not only through the
        // dedicated activate/deprecate/retire endpoints.
        ProductStatus currentStatus = product.getStatus();
        productMapper.updateEntity(request, product);
        ProductStatus requestedStatus = request.getStatus();
        if (requestedStatus != null && requestedStatus != currentStatus
                && !Product.isLegalTransition(currentStatus, requestedStatus)) {
            throw new BusinessException(
                    "Cannot transition product from " + currentStatus + " to " + requestedStatus,
                    "INVALID_STATUS_TRANSITION"
            );
        }

        Product updated = productRepository.save(product);
        publishProductUpdated(updated, "UPDATED");

        // DRG-418 / BC-01-01: ReviseProduct emits ProductRevised.
        outboxEventPublisher.publish(
                "Product",
                updated.getId().toString(),
                EventContractRegistry.PRODUCT_REVISED_V1,
                new ProductRevisedEvent(
                        updated.getId().toString(),
                        updated.getSku(),
                        updated.getUnitOfMeasure(),
                        updated.getStatus().name(),
                        updated.getEffectiveFrom(),
                        updated.getEffectiveTo(),
                        Instant.now()
                )
        );

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

        // DRG-418: Draft -> Active only, enforced by the entity's state machine (Product.isLegalTransition).
        product.activate();
        Product updated = productRepository.save(product);
        publishProductUpdated(updated, "PUBLISHED");
        log.info("Activated product ID: {}", id);
        return productMapper.toDto(updated);
    }

    @CacheEvict(value = {"products", "products-by-sku"}, allEntries = true)
    public ProductDto deprecate(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // Active -> Deprecated only, enforced by the entity's state machine (Product.isLegalTransition).
        product.deprecate();
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
                EventContractRegistry.PRODUCT_RETIRED_V1,
                event
        );
        
        log.info("Retired product ID: {} with reason: {}", id, reason);
        return productMapper.toDto(updated);
    }

    private void publishProductUpdated(Product product, String changeType) {
        outboxEventPublisher.publish(
                "Product",
                product.getId().toString(),
                EventContractRegistry.PRODUCT_UPDATED_V1,
                new ProductCatalogLifecycleEvent(
                        product.getId().toString(),
                        changeType,
                        Instant.now()
                )
        );
    }
}
