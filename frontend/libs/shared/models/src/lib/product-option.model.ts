/**
 * Product option group / option models.
 * Aligned to product-service (`com.opencrm.product.option.*`).
 */

/** Mirrors: com.opencrm.product.option.SelectionType */
export const SELECTION_TYPES = [
  'SINGLE',
  'MULTIPLE',
  'REQUIRED_SINGLE',
] as const;

export type SelectionType = (typeof SELECTION_TYPES)[number];

export const SELECTION_TYPE_LABELS: Record<SelectionType, string> = {
  SINGLE: 'Single',
  MULTIPLE: 'Multiple',
  REQUIRED_SINGLE: 'Required single',
};

export function isSelectionType(value: unknown): value is SelectionType {
  return (
    typeof value === 'string' &&
    (SELECTION_TYPES as readonly string[]).includes(value)
  );
}

/** Mirrors: com.opencrm.product.option.AdjustmentType */
export const ADJUSTMENT_TYPES = ['ABSOLUTE', 'PERCENTAGE'] as const;

export type AdjustmentType = (typeof ADJUSTMENT_TYPES)[number];

export const ADJUSTMENT_TYPE_LABELS: Record<AdjustmentType, string> = {
  ABSOLUTE: 'Absolute',
  PERCENTAGE: 'Percentage',
};

export function isAdjustmentType(value: unknown): value is AdjustmentType {
  return (
    typeof value === 'string' &&
    (ADJUSTMENT_TYPES as readonly string[]).includes(value)
  );
}

/**
 * Mirrors: com.opencrm.product.option.ProductOptionDto
 */
export interface ProductOptionDto {
  id: number;
  uuid: string;
  productId: number;
  name: string;
  description?: string;
  priceAdjustment?: number;
  adjustmentType?: AdjustmentType;
  defaultOption: boolean;
  sortOrder: number;
}

/**
 * Mirrors: com.opencrm.product.option.ProductOptionGroupDto
 * Returned by GET /api/v1/product-options/groups/product/{productId}
 */
export interface ProductOptionGroupDto {
  id: number;
  uuid: string;
  productId: number;
  name: string;
  description?: string;
  selectionType: SelectionType;
  minSelections: number;
  maxSelections?: number | null;
  sortOrder: number;
  options?: ProductOptionDto[];
}

/**
 * Mirrors: com.opencrm.product.option.OptionGroupCreateRequest
 * Sent to POST /api/v1/product-options/groups
 */
export interface OptionGroupCreateRequest {
  productId: number;
  name: string;
  description?: string;
  selectionType: SelectionType;
  minSelections?: number;
  maxSelections?: number | null;
  sortOrder?: number;
}

/**
 * Mirrors: com.opencrm.product.option.OptionCreateRequest
 * Sent to POST /api/v1/product-options/groups/{groupId}/options
 */
export interface OptionCreateRequest {
  productId?: number;
  name: string;
  description?: string;
  priceAdjustment?: number;
  adjustmentType?: AdjustmentType;
  defaultOption?: boolean;
  sortOrder?: number;
}
