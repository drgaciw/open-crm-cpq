/**
 * Lightweight quote shape for the presentational stub.
 * Mirrors QuoteDto fields used by list/detail UI (@frontend/shared-models).
 * HTTP quote service remains PR-FE-CPQ-03 / PR-FE-CPQ-06 full.
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
