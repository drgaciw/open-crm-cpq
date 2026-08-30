/**
 * Lightweight quote shape for Vitest fixtures / local mapping.
 * Mirrors QuoteDto fields used by list/detail UI (@frontend/shared-models).
 * Runtime happy path uses QuoteDto via QuoteService HTTP.
 */
export type QuoteListStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'REJECTED';

export type QuoteListDealType = 'NEW' | 'AMENDMENT' | 'RENEWAL' | 'EXPANSION';

export interface QuoteListItem {
  id: number;
  uuid: string;
  quoteNumber: string;
  name: string;
  description?: string;
  customerExternalId: string;
  dealExternalId?: string;
  status: QuoteListStatus;
  dealType: QuoteListDealType;
  currency: string;
  region?: string;
  durationInMonths: number;
  totalAmount: number;
  arr?: number;
  tcv?: number;
  lineItemCount: number;
  createdAt: string;
}
