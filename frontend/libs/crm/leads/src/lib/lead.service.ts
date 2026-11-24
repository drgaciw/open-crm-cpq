import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, finalize, map, of, switchMap, takeUntil } from 'rxjs';
import {
  LeadDto,
  LeadCreateRequest,
  LeadUpdateRequest,
  LeadStatus,
  LeadPriority,
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

@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/leads';
  private readonly listSuperseded = new Subject<void>();

  readonly leads = signal<LeadDto[]>([]);
  readonly selectedLead = signal<LeadDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0, size: 20, totalElements: 0, totalPages: 0,
  });

  readonly isEmpty = computed(() => this.leads().length === 0 && !this.loading());

  list(
    page = 0,
    size = 20,
    sort = 'createdAt,desc',
    status?: LeadStatus,
    priority?: LeadPriority,
    query?: string,
  ): void {
    let params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    if (status) params = params.set('status', status);
    if (priority) params = params.set('priority', priority);
    if (query) params = params.set('q', query);
    this.loadPage(this.basePath, params, size);
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<LeadDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (lead) => this.selectedLead.set(lead),
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        }
      });
  }

  create(request: LeadCreateRequest): Observable<LeadDto> {
    return this.http.post<LeadDto>(this.basePath, request);
  }

  update(id: number, request: LeadUpdateRequest): Observable<LeadDto> {
    return this.http.put<LeadDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string): void {
    this.loadPage(`${this.basePath}/search`, new HttpParams().set('q', query), 20);
  }

  private loadPage(path: string, params: HttpParams, size: number): void {
    // Let the previous request's finalizer run before starting the latest list or search.
    this.listSuperseded.next();
    this.error.set(null);
    this.loading.set(true);
    this.fetchPage(path, params)
      .pipe(takeUntil(this.listSuperseded), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.leads.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0, size: p.size ?? size,
              totalElements: p.totalElements ?? 0, totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        }
      });
  }

  private fetchPage(path: string, params: HttpParams): Observable<SpringPageResponse<LeadDto>> {
    const requestedPage = Number(params.get('page') ?? 0);
    return this.http.get<SpringPageResponse<LeadDto>>(path, { params }).pipe(
      switchMap((page) => {
        const lastPage = page.totalPages - 1;
        if (page.content?.length === 0 && page.totalElements > 0 && lastPage >= 0 && lastPage < requestedPage) {
          // Keep the same query and move strictly backward if concurrent changes remove more pages.
          return this.fetchPage(path, params.set('page', lastPage));
        }
        return of(page);
      }),
    );
  }

  markContacted(id: number): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.basePath}/${id}/contact`, {});
  }

  qualify(id: number): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.basePath}/${id}/qualify`, {});
  }

  /** POST convert returns LeadDto with convertedDealId (not DealDto). */
  convertToDeal(id: number): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.basePath}/${id}/convert`, {});
  }

  markLost(id: number, reason: string): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.basePath}/${id}/lose`, { reason });
  }

  /**
   * GET /api/v1/leads/stats — count of leads per status (after ApiResponse unwrap).
   */
  getStatusCounts(): Observable<Partial<Record<LeadStatus, number>>> {
    return this.http
      .get<Record<string, number>>(`${this.basePath}/stats`)
      .pipe(map((raw) => raw as Partial<Record<LeadStatus, number>>));
  }
}
