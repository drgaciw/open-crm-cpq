import { Routes } from '@angular/router';
import { QuotesListComponent } from './quotes-list/quotes-list.component';
import { QuotesCreateComponent } from './quotes-create/quotes-create.component';
import { QuotesDetailComponent } from './quotes-detail/quotes-detail.component';
import { provideQuotesListWebMcpTools } from './quotes-list/quotes-list-webmcp';
import { QuoteComparisonComponent } from './quote-comparison/quote-comparison.component';

export const QUOTES_ROUTES: Routes = [
  {
    path: '',
    component: QuotesListComponent,
    providers: [provideQuotesListWebMcpTools()],
  },
  { path: 'new', component: QuotesCreateComponent },
  { path: ':id/compare', component: QuoteComparisonComponent },
  { path: ':id', component: QuotesDetailComponent },
];
