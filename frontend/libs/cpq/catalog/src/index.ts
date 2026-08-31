export { CPQ_CATALOG_LIB } from './lib/catalog';
export { CATALOG_ROUTES } from './lib/catalog.routes';
export { CatalogListComponent } from './lib/catalog-list/catalog-list.component';
export { CatalogDetailComponent } from './lib/catalog-detail/catalog-detail.component';
export { ProductService } from './lib/product.service';
export { ProductOptionService } from './lib/product-option.service';
export { ProductAttributeService } from './lib/product-attribute.service';
export type {
  CatalogProduct,
  CatalogProductCategory,
  CatalogProductStatus,
} from './lib/catalog.model';
// MOCK_CATALOG_PRODUCTS / findMockCatalogProduct stay in catalog.mock.ts (Vitest only).
