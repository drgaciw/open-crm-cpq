import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  ProductDto,
  ProductCreateRequest,
  ProductUpdateRequest,
  CatalogProductType,
  ProductCategory,
  ProductStatus,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

/** H1-shaped banner text: `message [CODE]` when errorCode is present. */
function httpErrorMessage(err: unknown): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || 'An error occurred';
  if (code) return `${message} [${code}]`;
  return message;
}

/**
 * Catalog product HTTP service.
 * Signal-state pattern matches {@link DealService} (HttpClient + signals; not BaseCrudService).
 * Base path: `/api/v1/products` (product-service via gateway).
 */
@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/products';

  readonly products = signal<ProductDto[]>([]);
  readonly selectedProduct = signal<ProductDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.products().length === 0 && !this.loading());

  list(
    page = 0,
    size = 20,
    sort = 'name,asc',
    filters?: {
      productType?: CatalogProductType;
      category?: ProductCategory;
      status?: ProductStatus;
    },
  ): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (filters?.productType) {
      params = params.set('productType', filters.productType);
    }
    if (filters?.category) {
      params = params.set('category', filters.category);
    }
    if (filters?.status) {
      params = params.set('status', filters.status);
    }

    this.http
      .get<SpringPageResponse<ProductDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.products.set(p.content ?? (p as unknown as ProductDto[]));
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
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<ProductDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (product) => this.selectedProduct.set(product),
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  getBySku(sku: string): Observable<ProductDto> {
    return this.http.get<ProductDto>(`${this.basePath}/sku/${encodeURIComponent(sku)}`);
  }

  create(request: ProductCreateRequest): Observable<ProductDto> {
    return this.http.post<ProductDto>(this.basePath, request);
  }

  update(id: number, request: ProductUpdateRequest): Observable<ProductDto> {
    return this.http.put<ProductDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string, page = 0, size = 20): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams()
      .set('q', query)
      .set('page', page)
      .set('size', size);
    this.http
      .get<SpringPageResponse<ProductDto>>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.products.set(p.content ?? (p as unknown as ProductDto[]));
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
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  activate(id: number): Observable<ProductDto> {
    return this.http.post<ProductDto>(`${this.basePath}/${id}/activate`, {});
  }

  deprecate(id: number): Observable<ProductDto> {
    return this.http.post<ProductDto>(`${this.basePath}/${id}/deprecate`, {});
  }

  retire(id: number, reason: string): Observable<ProductDto> {
    const params = new HttpParams().set('reason', reason);
    return this.http.post<ProductDto>(`${this.basePath}/${id}/retire`, {}, { params });
  }
}
