import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, Subject, finalize, takeUntil } from 'rxjs';
import { PaginationMeta, SpringPageResponse } from '@frontend/shared-models';
import {
  ProductBundleDto,
  ProductBundleCreateRequest,
  ProductBundleUpdateRequest,
  BundleItemCreateRequest,
  BundleItemUpdateRequest,
} from './product-bundle.model';
import { bundleHttpErrorMessage } from './bundle-http-error';

/**
 * Product bundle HTTP service.
 * Signal-state pattern matches PriceBookService / DealService.
 * Base path: `/api/v1/product-bundles` (product-service via gateway).
 */
@Injectable({ providedIn: 'root' })
export class ProductBundleService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/product-bundles';
  private readonly listSuperseded = new Subject<void>();

  readonly bundles = signal<ProductBundleDto[]>([]);
  readonly selectedBundle = signal<ProductBundleDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly detailReadFailure = signal<{ id: number; status: number; message: string } | null>(null);
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
    this.beginListRead();
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    this.http
      .get<SpringPageResponse<ProductBundleDto>>(this.basePath, { params })
      .pipe(takeUntil(this.listSuperseded), finalize(() => this.loading.set(false)))
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
            bundleHttpErrorMessage(err, 'An error occurred'),
          );
        },
      });
  }

  /**
   * Optional product filter via GET /product/{productId} (non-paginated list).
   */
  listByProductId(productId: number): void {
    this.beginListRead();
    this.http
      .get<ProductBundleDto[]>(`${this.basePath}/product/${productId}`)
      .pipe(takeUntil(this.listSuperseded), finalize(() => this.loading.set(false)))
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
            bundleHttpErrorMessage(err, 'An error occurred'),
          );
        },
      });
  }

  private beginListRead(): void {
    // Finalize the superseded page read before marking the current request as loading.
    this.listSuperseded.next();
    this.error.set(null);
    this.loading.set(true);
  }

  getById(id: number): void {
    this.detailReadFailure.set(null);
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<ProductBundleDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (bundle) => this.selectedBundle.set(bundle),
        error: (err) => {
          const message = bundleHttpErrorMessage(err, 'An error occurred');
          this.detailReadFailure.set({ id, status: err instanceof HttpErrorResponse ? err.status : 0, message });
          this.error.set(message);
        },
      });
  }

  create(request: ProductBundleCreateRequest): Observable<ProductBundleDto> {
    return this.http.post<ProductBundleDto>(this.basePath, request);
  }

  update(id: number, request: ProductBundleUpdateRequest): Observable<ProductBundleDto> {
    return this.http.put<ProductBundleDto>(`${this.basePath}/${id}`, request);
  }

  updateItem(
    bundleId: number,
    itemId: number,
    request: BundleItemUpdateRequest,
  ): Observable<ProductBundleDto> {
    return this.http.put<ProductBundleDto>(
      `${this.basePath}/${bundleId}/items/${itemId}`,
      request,
    );
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
