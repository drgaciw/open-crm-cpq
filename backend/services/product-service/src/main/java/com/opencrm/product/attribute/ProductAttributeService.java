package com.opencrm.product.attribute;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductRepository productRepository;
    private final ProductAttributeMapper attributeMapper;
    private final AttributeValueValidator attributeValueValidator;

    public ProductAttributeDto createAttribute(AttributeCreateRequest request) {
        log.debug("Creating attribute: {}", request.getName());

        if (attributeRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new BusinessException(
                    "Attribute with name '" + request.getName() + "' already exists",
                    "DUPLICATE_ATTRIBUTE_NAME"
            );
        }

        ProductAttribute attribute = attributeMapper.toEntity(request);
        ProductAttribute saved = attributeRepository.save(attribute);
        log.info("Created attribute with ID: {}", saved.getId());

        return attributeMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProductAttributeDto getAttributeById(Long id) {
        ProductAttribute attribute = attributeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductAttribute", id));
        return attributeMapper.toDto(attribute);
    }

    @Transactional(readOnly = true)
    public Page<ProductAttributeDto> listAttributes(Pageable pageable) {
        return attributeRepository.findByDeletedFalse(pageable).map(attributeMapper::toDto);
    }

    public void deleteAttribute(Long id) {
        ProductAttribute attribute = attributeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductAttribute", id));
        attribute.softDelete();
        attributeRepository.save(attribute);
        log.info("Deleted attribute ID: {}", id);
    }

    public AttributeValueDto setAttributeValue(Long productId, AttributeValueSetRequest request) {
        log.debug("Setting attribute {} for product {}", request.getAttributeId(), productId);

        productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        ProductAttribute attribute = attributeRepository.findByIdAndDeletedFalse(request.getAttributeId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductAttribute", request.getAttributeId()));

        attributeValueValidator.validate(attribute, request.getValue());

        ProductAttributeValue attrValue = attributeValueRepository
                .findByProductIdAndAttributeIdAndDeletedFalse(productId, request.getAttributeId())
                .orElseGet(() -> ProductAttributeValue.builder()
                        .productId(productId)
                        .attributeId(request.getAttributeId())
                        .build());

        attrValue.setValue(request.getValue());
        ProductAttributeValue saved = attributeValueRepository.save(attrValue);

        return AttributeValueDto.builder()
                .id(saved.getId())
                .uuid(saved.getUuid())
                .productId(saved.getProductId())
                .attributeId(saved.getAttributeId())
                .attributeName(attribute.getName())
                .value(saved.getValue())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttributeValueDto> getAttributeValues(Long productId) {
        List<ProductAttributeValue> values = attributeValueRepository.findByProductIdAndDeletedFalse(productId);

        Set<Long> attributeIds = values.stream()
                .map(ProductAttributeValue::getAttributeId)
                .collect(Collectors.toSet());

        Map<Long, String> attributeNames = attributeRepository.findByIdInAndDeletedFalse(attributeIds).stream()
                .collect(Collectors.toMap(ProductAttribute::getId, ProductAttribute::getName));

        return values.stream().map(v -> {
            String attrName = attributeNames.get(v.getAttributeId());
            return AttributeValueDto.builder()
                    .id(v.getId())
                    .uuid(v.getUuid())
                    .productId(v.getProductId())
                    .attributeId(v.getAttributeId())
                    .attributeName(attrName)
                    .value(v.getValue())
                    .build();
        }).toList();
    }
}
