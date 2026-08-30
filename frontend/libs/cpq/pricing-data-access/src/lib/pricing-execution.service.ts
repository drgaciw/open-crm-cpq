import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import {
  PricingContext,
  PricingResult,
  BulkPricingSimulationRequest,
  BulkPricingSimulationResponse,
  PricingAuditLogDto,
} from '@frontend/shared-models';

/**
 * Pricing execute / simulate / audit HTTP service (non-CRUD).
 * Signal-state pattern matches DealService.
 * Base path: `/api/v1/pricing`.
 */
@Injectable({ providedIn: 'root' })
export class PricingExecutionService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/pricing';

  readonly lastResult = signal<PricingResult | null>(null);
  readonly simulationResults = signal<PricingResult[]>([]);
  readonly auditTrail = signal<PricingAuditLogDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  execute(ctx: PricingContext): Observable<PricingResult> {
    this.error.set(null);
    this.loading.set(true);
    return this.http.post<PricingResult>(`${this.basePath}/execute`, ctx).pipe(
      tap({
        next: (result) => this.lastResult.set(result),
        error: (err) => {
          this.error.set(err?.error?.message || err?.message || 'An error occurred');
        },
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  simulate(ctx: PricingContext): Observable<PricingResult> {
    this.error.set(null);
    this.loading.set(true);
    return this.http.post<PricingResult>(`${this.basePath}/simulate`, ctx).pipe(
      tap({
        next: (result) => {
          this.lastResult.set(result);
          this.simulationResults.update((prev) => [...prev, result]);
        },
        error: (err) => {
          this.error.set(err?.error?.message || err?.message || 'An error occurred');
        },
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  simulateBulk(req: BulkPricingSimulationRequest): Observable<BulkPricingSimulationResponse> {
    this.error.set(null);
    this.loading.set(true);
    return this.http
      .post<BulkPricingSimulationResponse>(`${this.basePath}/simulate/bulk`, req)
      .pipe(
        tap({
          next: (response) => {
            this.simulationResults.set(response.results ?? []);
            if (response.results?.length) {
              this.lastResult.set(response.results[response.results.length - 1]);
            }
          },
          error: (err) => {
            this.error.set(err?.error?.message || err?.message || 'An error occurred');
          },
        }),
        finalize(() => this.loading.set(false)),
      );
  }

  getAuditTrail(quoteExternalId: string): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<PricingAuditLogDto[]>(
        `${this.basePath}/audit/${encodeURIComponent(quoteExternalId)}`,
      )
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (logs) => this.auditTrail.set(logs ?? []),
        error: (err) => {
          this.error.set(err?.error?.message || err?.message || 'An error occurred');
        },
      });
  }

  clearResults(): void {
    this.lastResult.set(null);
    this.simulationResults.set([]);
    this.auditTrail.set([]);
    this.error.set(null);
  }
}
