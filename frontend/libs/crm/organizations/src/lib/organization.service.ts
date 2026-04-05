import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap, finalize } from 'rxjs';
import {
  OrganizationDto,
  OrganizationCreateRequest,
  OrganizationUpdateRequest,
  PaginationMeta,
} from '@frontend/shared-models';

/**
 * Spring Page response structure for type safety.
 * Used when the backend returns paginated data.
 */
interface SpringPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/organizations';

  // State
  readonly organizations = signal<OrganizationDto[]>([]);
  readonly selectedOrganization = signal<OrganizationDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(
    () => this.organizations().length === 0 && !this.loading(),
  );

  list(page = 0, size = 20, sort = 'name,asc'): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    this.http
      .get<OrganizationDto[] | SpringPage<OrganizationDto>>(this.basePath, {
        params,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          // Handle both plain array (from interceptor unwrapping) and Spring Page response
          const isPage =
            response && typeof response === 'object' && 'content' in response;
          this.organizations.set(isPage ? response.content : response);

          if (isPage) {
            this.pagination.set({
              page: response.number ?? 0,
              size: response.size ?? size,
              totalElements: response.totalElements ?? 0,
              totalPages: response.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        },
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<OrganizationDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (org) => this.selectedOrganization.set(org),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        },
      });
  }

  create(request: OrganizationCreateRequest): Observable<OrganizationDto> {
    return this.http.post<OrganizationDto>(this.basePath, request);
  }

  update(
    id: number,
    request: OrganizationUpdateRequest,
  ): Observable<OrganizationDto> {
    return this.http.put<OrganizationDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('q', query);
    this.http
      .get<OrganizationDto[]>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (orgs) => this.organizations.set(orgs),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        },
      });
  }
}
