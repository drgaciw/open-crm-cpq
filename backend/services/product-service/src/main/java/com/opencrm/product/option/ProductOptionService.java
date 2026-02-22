package com.opencrm.product.option;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.product.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductOptionService {

    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductRepository productRepository;
    private final ProductOptionMapper optionMapper;

    public ProductOptionGroupDto createGroup(OptionGroupCreateRequest request) {
        log.debug("Creating option group for product ID: {}", request.getProductId());

        productRepository.findByIdAndDeletedFalse(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        ProductOptionGroup group = optionMapper.toEntity(request);
        ProductOptionGroup saved = optionGroupRepository.save(group);
        log.info("Created option group with ID: {}", saved.getId());

        return optionMapper.toGroupDto(saved);
    }

    @Transactional(readOnly = true)
    public ProductOptionGroupDto getGroupById(Long id) {
        ProductOptionGroup group = optionGroupRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductOptionGroup", id));
        return optionMapper.toGroupDto(group);
    }

    @Transactional(readOnly = true)
    public List<ProductOptionGroupDto> getGroupsByProductId(Long productId) {
        return optionGroupRepository.findByProductIdAndDeletedFalseOrderBySortOrderAsc(productId)
                .stream().map(optionMapper::toGroupDto).toList();
    }

    public ProductOptionGroupDto addOption(Long groupId, OptionCreateRequest request) {
        log.debug("Adding option to group ID: {}", groupId);

        ProductOptionGroup group = optionGroupRepository.findByIdAndDeletedFalse(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductOptionGroup", groupId));

        ProductOption option = ProductOption.builder()
                .optionGroup(group)
                .productId(request.getProductId())
                .name(request.getName())
                .description(request.getDescription())
                .priceAdjustment(request.getPriceAdjustment() != null ? request.getPriceAdjustment() : BigDecimal.ZERO)
                .adjustmentType(request.getAdjustmentType() != null ? request.getAdjustmentType() : AdjustmentType.ABSOLUTE)
                .defaultOption(request.isDefaultOption())
                .sortOrder(request.getSortOrder())
                .build();

        group.getOptions().add(option);
        ProductOptionGroup saved = optionGroupRepository.save(group);
        log.info("Added option to group ID: {}", groupId);

        return optionMapper.toGroupDto(saved);
    }

    public void deleteGroup(Long id) {
        ProductOptionGroup group = optionGroupRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductOptionGroup", id));
        group.softDelete();
        optionGroupRepository.save(group);
        log.info("Deleted option group ID: {}", id);
    }
}
