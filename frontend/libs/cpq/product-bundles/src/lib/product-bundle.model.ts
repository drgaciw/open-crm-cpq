/**
 * Colocated DTOs mirroring product-service ProductBundle* / BundleItem*.
 * Kept in this lib (not shared/models) to avoid Wave P shell/shared conflicts.
 */

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'NONE';

export interface BundleItemDto {
  id: number;
  uuid?: string;
  productId: number;
  quantity: number;
  optional: boolean;
  sortOrder: number;
}

export interface ProductBundleDto {
  id: number;
  uuid?: string;
  productId: number;
  name: string;
  description?: string | null;
  discountType?: DiscountType | null;
  discountValue?: number | null;
  minItems?: number | null;
  maxItems?: number | null;
  status?: string | null;
  items?: BundleItemDto[];
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

export interface ProductBundleCreateRequest {
  productId: number;
  name: string;
  description?: string;
  discountType?: DiscountType;
  discountValue?: number;
  minItems?: number;
  maxItems?: number;
}

export interface BundleItemCreateRequest {
  productId: number;
  quantity?: number;
  optional?: boolean;
  sortOrder?: number;
}
