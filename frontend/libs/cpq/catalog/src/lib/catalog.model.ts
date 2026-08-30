/**
 * Lightweight catalog product shape for the presentational stub.
 * Mirrors product-service ProductDto fields used by list/detail UI.
 * Full shared-models export is PR-FE-CPQ-02.
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
