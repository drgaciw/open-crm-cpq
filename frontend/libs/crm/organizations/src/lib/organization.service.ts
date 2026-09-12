import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  OrganizationDto,
  OrganizationCreateRequest,
  OrganizationUpdateRequest,
  PaginationMeta,
  ContactDto,
  SpringPageResponse,
} from '@frontend/shared-models';

/** Local H1 helper: message [CODE] when errorCode present (Wave U3). */
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

  /** Contacts for the organization detail view (GET .../organizations/:id/contacts). */
  readonly organizationContacts = signal<ContactDto[]>([]);
  readonly organizationContactsLoading = signal(false);

  readonly isEmpty = computed(() => this.organizations().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'name,asc'): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    this.http
      .get<SpringPageResponse<OrganizationDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.organizations.set(p.content ?? p);
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
        }
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
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        }
      });
  }

  create(request: OrganizationCreateRequest): Observable<OrganizationDto> {
    return this.http.post<OrganizationDto>(this.basePath, request);
  }

  update(id: number, request: OrganizationUpdateRequest): Observable<OrganizationDto> {
    return this.http.put<OrganizationDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('q', query);
    // Interceptor rebuilds List+PageInfo into SpringPageResponse
    this.http
      .get<SpringPageResponse<OrganizationDto>>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.organizations.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0,
              size: p.size ?? 20,
              totalElements: p.totalElements ?? 0,
              totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        }
      });
  }

  /**
   * GET /api/v1/organizations/{id}/contacts
   */
  loadOrganizationContacts(organizationId: number, page = 0, size = 50): void {
    this.error.set(null);
    this.organizationContactsLoading.set(true);
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', 'createdAt,desc');
    this.http
      .get<SpringPageResponse<ContactDto>>(`${this.basePath}/${organizationId}/contacts`, { params })
      .pipe(finalize(() => this.organizationContactsLoading.set(false)))
      .subscribe({
        next: (p) => this.organizationContacts.set(p.content ?? p),
        error: (err) => {
          this.organizationContacts.set([]);
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }
}
