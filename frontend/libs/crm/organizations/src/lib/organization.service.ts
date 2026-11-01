import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, finalize, takeUntil } from 'rxjs';
import {
  OrganizationDto,
  OrganizationCreateRequest,
  OrganizationUpdateRequest,
  PaginationMeta,
  ContactDto,
  DealDto,
  QuoteDto,
  SpringPageResponse,
} from '@frontend/shared-models';

/** Parent/child account returned by the hierarchy API when BC-12-02 is deployed. */
export interface AccountRef {
  id: number;
  name: string;
}

/** GET /api/v1/organizations/{id}/hierarchy. Absent (404) means the API is not deployed. */
export interface AccountHierarchy {
  parent: AccountRef | null;
  ancestors: AccountRef[];
  children: AccountRef[];
}

/** GET /api/v1/organizations/{id}/merge-candidates. Absent (404) means merge is not deployed. */
export interface MergeCandidate {
  id: number;
  name: string;
  reason?: string;
}

/** Optional list filters. Empty strings are omitted from the query. */
export interface AccountListFilters {
  industry?: string;
  territory?: string;
  ownerId?: string;
}

export const HIERARCHY_CYCLE_MESSAGE =
  'This parent would create a cycle. Choose an account that is not already below this one.';

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

function httpStatus(err: unknown): number | undefined {
  const status = (err as { status?: number } | null)?.status;
  return typeof status === 'number' ? status : undefined;
}

function isHierarchyCycle(err: unknown): boolean {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || '';
  return code === 'HIERARCHY_CYCLE' || /cycle/i.test(message);
}

function appendFilters(params: HttpParams, filters?: AccountListFilters): HttpParams {
  if (!filters) {
    return params;
  }
  if (filters.industry) {
    params = params.set('industry', filters.industry);
  }
  if (filters.territory) {
    params = params.set('territory', filters.territory);
  }
  if (filters.ownerId) {
    params = params.set('ownerId', filters.ownerId);
  }
  return params;
}

function asRows<T>(body: SpringPageResponse<T> | T[] | null | undefined): T[] {
  if (Array.isArray(body)) {
    return body;
  }
  return body?.content ?? [];
}

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/organizations';
  private readonly detailChanged = new Subject<void>();

  resetAccountDetail(): void {
    this.detailChanged.next();
    this.selectedOrganization.set(null);
    this.organizationContacts.set([]);
    this.relatedDeals.set([]);
    this.relatedQuotes.set([]);
    this.relatedDealsError.set(null);
    this.relatedQuotesError.set(null);
    this.hierarchy.set(null);
    this.hierarchyAvailable.set(false);
    this.hierarchyError.set(null);
    this.mergeCandidates.set(null);
    this.mergeAvailable.set(false);
    this.mergeError.set(null);
  }

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

  /** Deals whose organizationId matches the open account. */
  readonly relatedDeals = signal<DealDto[]>([]);
  readonly relatedDealsLoading = signal(false);
  readonly relatedDealsError = signal<string | null>(null);

  /** Quotes whose customerExternalId matches this account. */
  readonly relatedQuotes = signal<QuoteDto[]>([]);
  readonly relatedQuotesLoading = signal(false);
  readonly relatedQuotesError = signal<string | null>(null);

  /** Null until a hierarchy response arrives. False when the parent API 404s. */
  readonly hierarchy = signal<AccountHierarchy | null>(null);
  readonly hierarchyAvailable = signal(false);
  readonly hierarchyError = signal<string | null>(null);

  /** Null when the merge API is absent. An empty array means the API is present with no candidates. */
  readonly mergeCandidates = signal<MergeCandidate[] | null>(null);
  readonly mergeAvailable = signal(false);
  readonly mergeError = signal<string | null>(null);

  readonly isEmpty = computed(() => this.organizations().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'name,asc', filters?: AccountListFilters): void {
    this.error.set(null);
    this.loading.set(true);
    const params = appendFilters(
      new HttpParams().set('page', page).set('size', size).set('sort', sort),
      filters,
    );

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
      .pipe(takeUntil(this.detailChanged), finalize(() => this.loading.set(false)))
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

  search(
    query: string,
    page?: number,
    size?: number,
    sort?: string,
    filters?: AccountListFilters,
  ): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams().set('q', query);
    if (page !== undefined) {
      params = params.set('page', String(page));
    }
    if (size !== undefined) {
      params = params.set('size', String(size));
    }
    if (sort !== undefined) {
      params = params.set('sort', sort);
    }
    params = appendFilters(params, filters);
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
      .pipe(takeUntil(this.detailChanged), finalize(() => this.organizationContactsLoading.set(false)))
      .subscribe({
        next: (p) => this.organizationContacts.set(p.content ?? p),
        error: (err) => {
          this.organizationContacts.set([]);
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  /**
   * GET /api/v1/deals?organizationId=
   * The deals API does not filter server-side yet, so rows are narrowed here.
   * A failure leaves a section error and does not replace the account banner.
   */
  loadRelatedDeals(organizationId: number): void {
    this.relatedDealsError.set(null);
    this.relatedDealsLoading.set(true);
    const params = new HttpParams()
      .set('page', '0')
      .set('size', '100')
      .set('organizationId', String(organizationId));
    this.http
      .get<SpringPageResponse<DealDto>>('/api/v1/deals', { params })
      .pipe(takeUntil(this.detailChanged), finalize(() => this.relatedDealsLoading.set(false)))
      .subscribe({
        next: (body) => {
          this.relatedDeals.set(
            asRows(body).filter((deal) => deal.organizationId === organizationId),
          );
        },
        error: (err) => {
          this.relatedDeals.set([]);
          this.relatedDealsError.set(httpErrorMessage(err, 'Deals are unavailable'));
        },
      });
  }

  /**
   * GET /api/v1/quotes?customerExternalId={account.uuid}
   * Keeps rows that match the account uuid, id, or name so an unfiltered page is still useful.
   */
  loadRelatedQuotes(account: OrganizationDto): void {
    this.relatedQuotesError.set(null);
    this.relatedQuotesLoading.set(true);
    const keys = new Set(
      [account.uuid, String(account.id), account.name].filter((value) => !!value),
    );
    const params = new HttpParams()
      .set('page', '0')
      .set('size', '50')
      .set('customerExternalId', account.uuid);
    this.http
      .get<SpringPageResponse<QuoteDto>>('/api/v1/quotes', { params })
      .pipe(takeUntil(this.detailChanged), finalize(() => this.relatedQuotesLoading.set(false)))
      .subscribe({
        next: (body) => {
          this.relatedQuotes.set(
            asRows(body).filter((quote) => keys.has(quote.customerExternalId)),
          );
        },
        error: (err) => {
          this.relatedQuotes.set([]);
          this.relatedQuotesError.set(httpErrorMessage(err, 'Quotes are unavailable'));
        },
      });
  }

  /** GET /api/v1/organizations/{id}/hierarchy. A 404 hides the hierarchy UI. */
  loadHierarchy(id: number): void {
    this.hierarchyError.set(null);
    this.hierarchyAvailable.set(false);
    this.http.get<AccountHierarchy>(`${this.basePath}/${id}/hierarchy`).pipe(takeUntil(this.detailChanged)).subscribe({
      next: (tree) => {
        this.hierarchyAvailable.set(true);
        this.hierarchy.set({
          parent: tree?.parent ?? null,
          ancestors: tree?.ancestors ?? [],
          children: tree?.children ?? [],
        });
      },
      error: (err) => {
        this.hierarchy.set(null);
        this.hierarchyAvailable.set(false);
        if (httpStatus(err) !== 404) {
          this.hierarchyError.set(httpErrorMessage(err, 'Account hierarchy is unavailable'));
        }
      },
    });
  }

  /** PUT /api/v1/organizations/{id}/parent. Cycle responses become an actionable message. */
  linkParent(id: number, parentId: number | null): void {
    this.hierarchyError.set(null);
    this.http
      .put<AccountHierarchy>(`${this.basePath}/${id}/parent`, { parentId })
      .pipe(takeUntil(this.detailChanged))
      .subscribe({
        next: (tree) => {
          this.hierarchyAvailable.set(true);
          this.hierarchy.set({
            parent: tree?.parent ?? null,
            ancestors: tree?.ancestors ?? [],
            children: tree?.children ?? [],
          });
        },
        error: (err) => {
          this.hierarchyError.set(
            isHierarchyCycle(err)
              ? HIERARCHY_CYCLE_MESSAGE
              : httpErrorMessage(err, 'Could not update the parent account'),
          );
        },
      });
  }

  /** GET /api/v1/organizations/{id}/merge-candidates. A 404 hides the merge UI. */
  loadMergeCandidates(id: number): void {
    this.mergeError.set(null);
    this.mergeAvailable.set(false);
    this.http
      .get<MergeCandidate[] | SpringPageResponse<MergeCandidate>>(
        `${this.basePath}/${id}/merge-candidates`,
      )
      .pipe(takeUntil(this.detailChanged))
      .subscribe({
        next: (body) => {
          this.mergeAvailable.set(true);
          this.mergeCandidates.set(asRows(body));
        },
        error: (err) => {
          this.mergeCandidates.set(null);
          this.mergeAvailable.set(false);
          if (httpStatus(err) !== 404) {
            this.mergeError.set(httpErrorMessage(err, 'Merge candidates are unavailable'));
          }
        },
      });
  }

  /** POST /api/v1/organizations/{survivorId}/merge */
  mergeInto(survivorId: number, sourceId: number): void {
    this.mergeError.set(null);
    this.http
      .post<void>(`${this.basePath}/${survivorId}/merge`, { survivorId, sourceId })
      .pipe(takeUntil(this.detailChanged))
      .subscribe({
        next: () => this.loadMergeCandidates(survivorId),
        error: (err) => {
          this.mergeError.set(httpErrorMessage(err, 'Could not merge these accounts'));
        },
      });
  }
}
