import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, finalize, takeUntil } from 'rxjs';
import {
  RuleSetDto,
  RuleSetCreateRequest,
  RuleSetUpdateRequest,
  RuleSetStatus,
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
 * Rule set HTTP service.
 * Signal-state pattern matches DealService (not BaseCrudService).
 * Base path: `/api/v1/rule-sets`.
 */
@Injectable({ providedIn: 'root' })
export class RuleSetService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/rule-sets';
  private readonly listSuperseded = new Subject<void>();
  private readonly detailSuperseded = new Subject<void>();

  readonly ruleSets = signal<RuleSetDto[]>([]);
  readonly selectedRuleSet = signal<RuleSetDto | null>(null);
  readonly detailReadUnavailableId = signal<number | null>(null);
  readonly detailLoading = signal(false);
  readonly detailError = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.ruleSets().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'name,asc', status?: RuleSetStatus): void {
    // Finalize the superseded page read before marking the current request as loading.
    this.listSuperseded.next();
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (status) {
      params = params.set('status', status);
    }

    this.http
      .get<SpringPageResponse<RuleSetDto>>(this.basePath, { params })
      .pipe(takeUntil(this.listSuperseded), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.ruleSets.set(p.content ?? (p as unknown as RuleSetDto[]));
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

  listByStatus(status: RuleSetStatus, page = 0, size = 20): void {
    this.list(page, size, 'name,asc', status);
  }

  getById(id: number): void {
    this.detailSuperseded.next();
    this.detailReadUnavailableId.set(null);
    this.detailError.set(null);
    if (this.selectedRuleSet()?.id !== id) {
      this.selectedRuleSet.set(null);
    }
    if (!Number.isFinite(id)) {
      this.detailError.set('Invalid rule set id');
      return;
    }

    this.detailLoading.set(true);
    this.http
      .get<RuleSetDto>(`${this.basePath}/${id}`)
      .pipe(takeUntil(this.detailSuperseded), finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: (ruleSet) => this.selectedRuleSet.set(ruleSet),
        error: (err) => {
          if (err?.status === 404) {
            this.selectedRuleSet.set(null);
          } else {
            this.detailReadUnavailableId.set(id);
          }
          this.detailError.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  create(request: RuleSetCreateRequest): Observable<RuleSetDto> {
    return this.http.post<RuleSetDto>(this.basePath, request);
  }

  update(id: number, request: RuleSetUpdateRequest): Observable<RuleSetDto> {
    return this.http.put<RuleSetDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  activate(id: number): Observable<RuleSetDto> {
    return this.http.post<RuleSetDto>(`${this.basePath}/${id}/activate`, {});
  }

  deprecate(id: number): Observable<RuleSetDto> {
    return this.http.post<RuleSetDto>(`${this.basePath}/${id}/deprecate`, {});
  }
}
