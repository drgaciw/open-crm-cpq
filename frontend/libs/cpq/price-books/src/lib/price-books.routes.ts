import { Routes } from '@angular/router';
import { PriceBookListComponent } from './price-book-list/price-book-list.component';
import { PriceBookDetailComponent } from './price-book-detail/price-book-detail.component';

export const PRICE_BOOKS_ROUTES: Routes = [
  { path: '', component: PriceBookListComponent },
  { path: ':id', component: PriceBookDetailComponent },
];
