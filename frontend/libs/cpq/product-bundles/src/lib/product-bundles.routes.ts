import { Routes } from '@angular/router';
import { ProductBundleListComponent } from './product-bundle-list/product-bundle-list.component';
import { ProductBundleDetailComponent } from './product-bundle-detail/product-bundle-detail.component';

export const PRODUCT_BUNDLES_ROUTES: Routes = [
  { path: '', component: ProductBundleListComponent },
  { path: ':id', component: ProductBundleDetailComponent },
];
