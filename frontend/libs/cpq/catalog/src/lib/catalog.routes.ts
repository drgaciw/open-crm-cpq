import { Routes } from '@angular/router';
import { CatalogListComponent } from './catalog-list/catalog-list.component';
import { CatalogDetailComponent } from './catalog-detail/catalog-detail.component';

export const CATALOG_ROUTES: Routes = [
  { path: '', component: CatalogListComponent },
  { path: ':id', component: CatalogDetailComponent },
];
