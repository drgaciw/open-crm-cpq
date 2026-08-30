import { Routes } from '@angular/router';
import { QuotesListComponent } from './quotes-list/quotes-list.component';
import { QuotesDetailComponent } from './quotes-detail/quotes-detail.component';

export const QUOTES_ROUTES: Routes = [
  { path: '', component: QuotesListComponent },
  { path: ':id', component: QuotesDetailComponent },
];
