export { CPQ_PRODUCT_BUNDLES_LIB } from './lib/product-bundles';
export { PRODUCT_BUNDLES_ROUTES } from './lib/product-bundles.routes';
export { ProductBundleService } from './lib/product-bundle.service';
export { ProductBundleListComponent } from './lib/product-bundle-list/product-bundle-list.component';
export { ProductBundleDetailComponent } from './lib/product-bundle-detail/product-bundle-detail.component';
export { ProductBundleCreateDialogComponent } from './lib/product-bundle-create-dialog/product-bundle-create-dialog.component';
export { BundleItemDialogComponent } from './lib/bundle-item-dialog/bundle-item-dialog.component';
export type { BundleItemDialogData } from './lib/bundle-item-dialog/bundle-item-dialog.component';
export { bundleHttpErrorMessage } from './lib/bundle-http-error';
export type {
  DiscountType,
  BundleItemDto,
  ProductBundleDto,
  ProductBundleCreateRequest,
  BundleItemCreateRequest,
} from './lib/product-bundle.model';
// MOCK_PRODUCT_BUNDLES / MOCK_BUNDLE_ITEMS stay in product-bundles.mock.ts (Vitest only).
