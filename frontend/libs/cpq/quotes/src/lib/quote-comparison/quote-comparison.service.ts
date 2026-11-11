import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { QuoteDto } from '@frontend/shared-models';

export interface ComparisonColumn {
  id: number;
  quoteNumber: string;
  name: string;
  primary: boolean;
  status: string;
  currency: string;
  subtotal: number;
  listTotal: number;
  discountTotal: number;
  discountPercent: number | null;
  tax: number;
  grandTotal: number;
  netAmount: number;
  marginAmount: number | null;
  marginPercent: number | null;
  billingFrequencies: string[];
  termMonths: number;
  validUntil: string | null;
  paymentTerms: string | null;
  lineCount: number;
  delta: {
    comparableCurrency: boolean;
    subtotal: number | null;
    discountTotal: number | null;
    tax: number | null;
    grandTotal: number | null;
    grandTotalPercent: number | null;
  };
  skuDiff: { added: string[]; removed: string[]; changed: string[] };
}
export interface QuoteComparison {
  dealExternalId: string;
  baselineQuoteId: number | null;
  quotes: ComparisonColumn[];
  unavailableFields: string[];
}

@Injectable({ providedIn: 'root' })
export class QuoteComparisonService {
  private readonly http = inject(HttpClient);

  compare(dealExternalId: string, quoteIds: number[] = []) {
    let params = new HttpParams().set('dealExternalId', dealExternalId);
    if (quoteIds.length) params = params.set('quoteIds', quoteIds.join(','));
    return this.http.get<QuoteComparison>('/api/v1/quotes/compare', { params });
  }

  markPrimary(id: number) {
    return this.http.post<QuoteDto & { primary: boolean }>(`/api/v1/quotes/${id}/primary`, {});
  }
}
