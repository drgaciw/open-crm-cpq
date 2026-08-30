export { CPQ_QUOTES_LIB } from './lib/quotes';
export { QuoteService } from './lib/quote.service';
export { QUOTES_ROUTES } from './lib/quotes.routes';
export { QuotesListComponent } from './lib/quotes-list/quotes-list.component';
export { QuotesCreateComponent } from './lib/quotes-create/quotes-create.component';
export { QuotesDetailComponent } from './lib/quotes-detail/quotes-detail.component';
export type {
  QuoteListItem,
  QuoteListStatus,
  QuoteListDealType,
} from './lib/quotes.model';
// MOCK_QUOTES / findMockQuote stay in quotes.mock.ts (Vitest only).
