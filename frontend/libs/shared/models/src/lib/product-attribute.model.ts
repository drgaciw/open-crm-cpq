/**
 * Product attribute definition / value models.
 * Aligned to product-service (`com.opencrm.product.attribute.*`).
 */

/** Mirrors: com.opencrm.product.attribute.AttributeDataType */
export const ATTRIBUTE_DATA_TYPES = [
  'STRING',
  'NUMBER',
  'BOOLEAN',
  'DATE',
  'ENUM',
] as const;

export type AttributeDataType = (typeof ATTRIBUTE_DATA_TYPES)[number];

export const ATTRIBUTE_DATA_TYPE_LABELS: Record<AttributeDataType, string> = {
  STRING: 'String',
  NUMBER: 'Number',
  BOOLEAN: 'Boolean',
  DATE: 'Date',
  ENUM: 'Enum',
};

export function isAttributeDataType(
  value: unknown,
): value is AttributeDataType {
  return (
    typeof value === 'string' &&
    (ATTRIBUTE_DATA_TYPES as readonly string[]).includes(value)
  );
}

/**
 * Mirrors: com.opencrm.product.attribute.ProductAttributeDto
 * Returned by GET /api/v1/product-attributes (paged)
 */
export interface ProductAttributeDto {
  id: number;
  uuid: string;
  name: string;
  displayName?: string;
  dataType: AttributeDataType;
  required: boolean;
  defaultValue?: string;
  validationRegex?: string;
  enumValues?: string;
  sortOrder: number;
}

/**
 * Mirrors: com.opencrm.product.attribute.AttributeValueDto
 * Returned by GET /api/v1/product-attributes/products/{productId}/values
 */
export interface AttributeValueDto {
  id: number;
  uuid: string;
  productId: number;
  attributeId: number;
  attributeName?: string;
  value?: string | null;
}

/**
 * Mirrors: com.opencrm.product.attribute.AttributeValueSetRequest
 * Sent to POST /api/v1/product-attributes/products/{productId}/values
 */
export interface AttributeValueSetRequest {
  attributeId: number;
  value?: string | null;
}
