import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import { PaginationMeta, SpringPageResponse } from '@frontend/shared-models';
import {
  ProductBundleDto,
  ProductBundleCreateRequest,
  BundleItemCreateRequest,
} from './product-bundle.model';

/**
 * Product bundle HTTP service.
 * Signal-state pattern matches PriceBookService / DealService.
 * Base path: `/api/v1/product-bundles` (product-service via gateway).
 */
@Injectable({ providedIn: 'root' })
export class ProductBundleService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/product-bundles';

  readonly bundles = signal<ProductBundleDto[]>([]);
  readonly selectedBundle = signal<ProductBundleDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(
    () => this.bundles().length === 0 && !this.loading(),
  );

  list(page = 0, size = 20, sort = 'name,asc'): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    this.http
      .get<SpringPageResponse<ProductBundleDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.bundles.set(p.content ?? (p as unknown as ProductBundleDto[]));
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
          this.error.set(
            err?.error?.message || err?.message || 'An error occurred',
          );
        },
      });
  }

  /**
   * Optional product filter via GET /product/{productId} (non-paginated list).
   */
  listByProductId(productId: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<ProductBundleDto[]>(`${this.basePath}/product/${productId}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (rows) => {
          const list = Array.isArray(rows) ? rows : [];
          this.bundles.set(list);
          this.pagination.set({
            page: 0,
            size: list.length || 20,
            totalElements: list.length,
            totalPages: 1,
          });
        },
        error: (err) => {
          this.error.set(
            err?.error?.message || err?.message || 'An error occurred',
          );
        },
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<ProductBundleDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (bundle) => this.selectedBundle.set(bundle),
        error: (err) => {
          this.error.set(
            err?.error?.message || err?.message || 'An error occurred',
          );
        },
      });
  }

  create(request: ProductBundleCreateRequest): Observable<ProductBundleDto> {
    return this.http.post<ProductBundleDto>(this.basePath, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  addItem(
    bundleId: number,
    request: BundleItemCreateRequest,
  ): Observable<ProductBundleDto> {
    return this.http.post<ProductBundleDto>(
      `${this.basePath}/${bundleId}/items`,
      request,
    );
  }

  removeItem(bundleId: number, itemId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.basePath}/${bundleId}/items/${itemId}`,
    );
  }
}
