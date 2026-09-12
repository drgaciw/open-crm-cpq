import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  RuleDto,
  RuleCreateRequest,
  RuleUpdateRequest,
  RuleImpactDto,
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
 * Pricing DRL rule HTTP service.
 * Signal-state pattern matches DealService (not BaseCrudService).
 * Base path: `/api/v1/rules` (no bare list — use {@link listByRuleSet}).
 */
@Injectable({ providedIn: 'root' })
export class RuleService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/rules';

  readonly rules = signal<RuleDto[]>([]);
  readonly selectedRule = signal<RuleDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.rules().length === 0 && !this.loading());

  listByRuleSet(ruleSetId: number, page = 0, size = 20): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('page', page).set('size', size);
    this.http
      .get<SpringPageResponse<RuleDto>>(`${this.basePath}/by-rule-set/${ruleSetId}`, {
        params,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.rules.set(p.content ?? (p as unknown as RuleDto[]));
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

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<RuleDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (rule) => this.selectedRule.set(rule),
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  create(request: RuleCreateRequest): Observable<RuleDto> {
    return this.http.post<RuleDto>(this.basePath, request);
  }

  update(id: number, request: RuleUpdateRequest): Observable<RuleDto> {
    return this.http.put<RuleDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  /** Validate DRL syntax without executing. */
  validateSyntax(id: number): Observable<void> {
    return this.http.post<void>(`${this.basePath}/${id}/validate`, {});
  }

  analyzeImpact(id: number): Observable<RuleImpactDto> {
    return this.http.get<RuleImpactDto>(`${this.basePath}/${id}/impact`);
  }
}
