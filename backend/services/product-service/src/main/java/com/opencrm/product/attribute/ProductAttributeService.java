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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductRepository productRepository;
    private final ProductAttributeMapper attributeMapper;

    public ProductAttributeDto createAttribute(AttributeCreateRequest request) {
        log.debug("Creating attribute: {}", request.getName());

        if (attributeRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new BusinessException(
                    "Attribute with name '" + request.getName() + "' already exists",
                    "DUPLICATE_ATTRIBUTE_NAME"
            );
        }

        validateAttributeDefinition(request);

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

        validateAttributeValue(attribute, request.getValue());

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
        return values.stream().map(v -> {
            String attrName = attributeRepository.findByIdAndDeletedFalse(v.getAttributeId())
                    .map(ProductAttribute::getName).orElse(null);
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

    private void validateAttributeDefinition(AttributeCreateRequest request) {
        if (request.getValidationRegex() != null && !request.getValidationRegex().isBlank()) {
            try {
                Pattern.compile(request.getValidationRegex());
            } catch (PatternSyntaxException ex) {
                throw new BusinessException("Attribute validation regex is invalid", "ATTRIBUTE_REGEX_INVALID");
            }
        }

        if (request.getDefaultValue() != null) {
            validateAttributeValue(
                    ProductAttribute.builder()
                            .name(request.getName())
                            .dataType(request.getDataType())
                            .required(request.isRequired())
                            .validationRegex(request.getValidationRegex())
                            .enumValues(request.getEnumValues())
                            .build(),
                    request.getDefaultValue()
            );
        }
    }

    private void validateAttributeValue(ProductAttribute attribute, String value) {
        if (value == null || value.isBlank()) {
            if (attribute.isRequired()) {
                throw new BusinessException(
                        "Attribute '" + attribute.getName() + "' requires a value",
                        "ATTRIBUTE_VALUE_REQUIRED"
                );
            }
            return;
        }

        switch (attribute.getDataType()) {
            case NUMBER -> validateNumber(attribute, value);
            case BOOLEAN -> validateBoolean(attribute, value);
            case DATE -> validateDate(attribute, value);
            case ENUM -> validateEnum(attribute, value);
            case STRING -> {
            }
        }

        if (attribute.getValidationRegex() != null
                && !attribute.getValidationRegex().isBlank()
                && !Pattern.matches(attribute.getValidationRegex(), value)) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' does not match validation rules",
                    "ATTRIBUTE_VALUE_REGEX_MISMATCH"
            );
        }
    }

    private void validateNumber(ProductAttribute attribute, String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' must be a number",
                    "ATTRIBUTE_VALUE_TYPE_INVALID"
            );
        }
    }

    private void validateBoolean(ProductAttribute attribute, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' must be true or false",
                    "ATTRIBUTE_VALUE_TYPE_INVALID"
            );
        }
    }

    private void validateDate(ProductAttribute attribute, String value) {
        try {
            LocalDate.parse(value);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' must be an ISO-8601 date",
                    "ATTRIBUTE_VALUE_TYPE_INVALID"
            );
        }
    }

    private void validateEnum(ProductAttribute attribute, String value) {
        if (attribute.getEnumValues() == null || attribute.getEnumValues().isBlank()) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' does not define enum values",
                    "ATTRIBUTE_ENUM_VALUES_REQUIRED"
            );
        }

        boolean allowed = List.of(attribute.getEnumValues().split(",")).stream()
                .map(String::trim)
                .anyMatch(value::equals);
        if (!allowed) {
            throw new BusinessException(
                    "Attribute '" + attribute.getName() + "' value is not allowed",
                    "ATTRIBUTE_VALUE_NOT_ALLOWED"
            );
        }
    }
}
