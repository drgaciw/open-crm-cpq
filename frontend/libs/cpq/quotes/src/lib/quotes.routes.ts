import { Routes } from '@angular/router';
import { QuotesListComponent } from './quotes-list/quotes-list.component';
import { QuotesCreateComponent } from './quotes-create/quotes-create.component';
import { QuotesDetailComponent } from './quotes-detail/quotes-detail.component';

export const QUOTES_ROUTES: Routes = [
  { path: '', component: QuotesListComponent },
  { path: 'new', component: QuotesCreateComponent },
  { path: ':id', component: QuotesDetailComponent },
];
