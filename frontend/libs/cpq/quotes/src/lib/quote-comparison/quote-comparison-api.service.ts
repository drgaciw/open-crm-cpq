import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuoteComparison } from './quote-comparison.model';

@Injectable({ providedIn: 'root' })
export class QuoteComparisonApiService {
  private readonly http = inject(HttpClient);

  compare(quoteId: number): Observable<QuoteComparison> {
    return this.http.get<QuoteComparison>(`/api/v1/quotes/${quoteId}/comparison`);
  }

  markPrimary(quoteId: number): Observable<QuoteComparison> {
    return this.http.post<QuoteComparison>(`/api/v1/quotes/${quoteId}/primary`, {});
  }
}
