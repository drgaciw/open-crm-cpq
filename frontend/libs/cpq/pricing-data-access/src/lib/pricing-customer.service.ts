import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  PricingCustomerDto,
  PricingCustomerCreateRequest,
  PricingCustomerUpdateRequest,
  CustomerSegment,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

/** Prefer message; append errorCode when present (H1 shape). */
function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

/**
 * Pricing customer HTTP service.
 * Signal-state pattern matches DealService (not BaseCrudService).
 * Base path: `/api/v1/pricing-customers`.
 */
@Injectable({ providedIn: 'root' })
export class PricingCustomerService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/pricing-customers';

  readonly customers = signal<PricingCustomerDto[]>([]);
  readonly selectedCustomer = signal<PricingCustomerDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.customers().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'name,asc', segment?: CustomerSegment): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (segment) {
      params = params.set('segment', segment);
    }

    this.http
      .get<SpringPageResponse<PricingCustomerDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.customers.set(p.content ?? (p as unknown as PricingCustomerDto[]));
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0,
              size: p.size ?? size,
              totalElements: p.totalElements ?? 0,
              totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  /** Convenience wrapper that lists with a segment filter. */
  listBySegment(segment: CustomerSegment, page = 0, size = 20): void {
    this.list(page, size, 'name,asc', segment);
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<PricingCustomerDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (customer) => this.selectedCustomer.set(customer),
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  create(request: PricingCustomerCreateRequest): Observable<PricingCustomerDto> {
    return this.http.post<PricingCustomerDto>(this.basePath, request);
  }

  update(id: number, request: PricingCustomerUpdateRequest): Observable<PricingCustomerDto> {
    return this.http.put<PricingCustomerDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }
}
