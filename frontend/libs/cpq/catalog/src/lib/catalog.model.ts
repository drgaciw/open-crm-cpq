/**
 * Lightweight catalog product shape for test fixtures / local mapping.
 * Mirrors product-service ProductDto fields used by list/detail UI.
 * Runtime happy path uses {@link ProductDto} via ProductService.
 */
export type CatalogProductStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'RETIRED';

export type CatalogProductCategory =
  | 'SUBSCRIPTION'
  | 'LICENSE'
  | 'SUPPORT'
  | 'SERVICES'
  | 'ADD_ON';

export interface CatalogProduct {
  id: number;
  uuid: string;
  sku: string;
  name: string;
  description?: string;
  category: CatalogProductCategory;
  status: CatalogProductStatus;
  listPrice: number;
  currency: string;
  productType: string;
}
