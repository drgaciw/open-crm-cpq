package com.opencrm.product.bundle;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.product.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductBundleService {

    private final ProductBundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ProductRepository productRepository;
    private final ProductBundleMapper bundleMapper;

    public ProductBundleDto create(ProductBundleCreateRequest request) {
        log.debug("Creating bundle for product ID: {}", request.getProductId());

        productRepository.findByIdAndDeletedFalse(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        ProductBundle bundle = bundleMapper.toEntity(request);
        ProductBundle saved = bundleRepository.save(bundle);
        log.info("Created bundle with ID: {}", saved.getId());

        return bundleMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProductBundleDto getById(Long id) {
        ProductBundle bundle = bundleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductBundle", id));
        return bundleMapper.toDto(bundle);
    }

    @Transactional(readOnly = true)
    public List<ProductBundleDto> getByProductId(Long productId) {
        return bundleRepository.findByProductIdAndDeletedFalse(productId)
                .stream().map(bundleMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductBundleDto> list(Pageable pageable) {
        return bundleRepository.findByDeletedFalse(pageable).map(bundleMapper::toDto);
    }

    public void delete(Long id) {
        ProductBundle bundle = bundleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductBundle", id));
        bundle.softDelete();
        bundleRepository.save(bundle);
        log.info("Deleted bundle ID: {}", id);
    }

    public ProductBundleDto addItem(Long bundleId, BundleItemCreateRequest request) {
        log.debug("Adding item to bundle ID: {}", bundleId);

        ProductBundle bundle = bundleRepository.findByIdAndDeletedFalse(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductBundle", bundleId));

        productRepository.findByIdAndDeletedFalse(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        if (bundle.getMaxItems() != null && bundle.getItems().size() >= bundle.getMaxItems()) {
            throw new BusinessException(
                    "Bundle has reached maximum items (" + bundle.getMaxItems() + ")",
                    "BUNDLE_MAX_ITEMS_EXCEEDED"
            );
        }

        BundleItem item = BundleItem.builder()
                .bundle(bundle)
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .optional(request.isOptional())
                .sortOrder(request.getSortOrder())
                .build();

        bundle.getItems().add(item);
        ProductBundle saved = bundleRepository.save(bundle);
        log.info("Added item to bundle ID: {}", bundleId);

        return bundleMapper.toDto(saved);
    }

    public void removeItem(Long bundleId, Long itemId) {
        ProductBundle bundle = bundleRepository.findByIdAndDeletedFalse(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductBundle", bundleId));

        BundleItem item = bundleItemRepository.findByIdAndDeletedFalse(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("BundleItem", itemId));

        if (bundle.getMinItems() != null && bundle.getItems().size() <= bundle.getMinItems()) {
            throw new BusinessException(
                    "Bundle requires minimum " + bundle.getMinItems() + " items",
                    "BUNDLE_MIN_ITEMS_REQUIRED"
            );
        }

        item.softDelete();
        bundleItemRepository.save(item);
        log.info("Removed item {} from bundle {}", itemId, bundleId);
    }
}
