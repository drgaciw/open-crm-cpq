import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subscription, finalize } from 'rxjs';
import {
  DealDto,
  DealCreateRequest,
  DealUpdateRequest,
  DealStage,
  PipelineSummaryDto,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';
import { dealActionErrorMessage } from './deal-action-error';

@Injectable({ providedIn: 'root' })
export class DealService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/deals';
  private listRequest?: Subscription;

  readonly deals = signal<DealDto[]>([]);
  readonly selectedDeal = signal<DealDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Changes only when the current list or search response is successfully applied. */
  readonly listResultVersion = signal(0);
  readonly pipelineSummary = signal<PipelineSummaryDto[] | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0, size: 20, totalElements: 0, totalPages: 0,
  });

  readonly isEmpty = computed(() => this.deals().length === 0 && !this.loading());

  /**
   * GET /api/v1/deals. Server query params are page, size, sort, stage, and organizationId.
   * Owner is not a parameter on this API; the list filters owner on the loaded page.
   */
  list(
    page = 0,
    size = 20,
    sort = 'createdAt,desc',
    stage?: DealStage,
    organizationId?: number,
  ): void {
    let params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    if (stage) params = params.set('stage', stage);
    if (organizationId != null) params = params.set('organizationId', String(organizationId));

    this.loadList(this.basePath, params, size);
  }

  /**
   * Writes a stage transition into the shared list and the open detail.
   * List and kanban both read {@link deals}, so they stay on the same client state.
   */
  applyStageChange(updated: DealDto): void {
    this.deals.update((rows) => {
      const index = rows.findIndex((row) => row.id === updated.id);
      if (index < 0) {
        return rows;
      }
      const next = rows.slice();
      next[index] = { ...rows[index], ...updated };
      return next;
    });
    const selected = this.selectedDeal();
    if (selected && selected.id === updated.id) {
      this.selectedDeal.set({ ...selected, ...updated });
    }
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<DealDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (d) => this.selectedDeal.set(d),
        error: (err) => {
          this.error.set(dealActionErrorMessage(err, 'An error occurred'));
        }
      });
  }

  create(request: DealCreateRequest): Observable<DealDto> {
    return this.http.post<DealDto>(this.basePath, request);
  }

  update(id: number, request: DealUpdateRequest): Observable<DealDto> {
    return this.http.put<DealDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string): void {
    const params = new HttpParams().set('q', query);
    this.loadList(`${this.basePath}/search`, params, 20);
  }

  private loadList(path: string, params: HttpParams, defaultPageSize: number): void {
    // List and search share the same state. Finish the older request before setting loading for this one.
    this.listRequest?.unsubscribe();
    this.error.set(null);
    this.loading.set(true);

    // Interceptor rebuilds List+PageInfo into SpringPageResponse.
    this.listRequest = this.http.get<SpringPageResponse<DealDto>>(path, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.deals.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0, size: p.size ?? defaultPageSize,
              totalElements: p.totalElements ?? 0, totalPages: p.totalPages ?? 0,
            });
          }
          this.listResultVersion.update(version => version + 1);
        },
        error: (err) => {
          this.error.set(dealActionErrorMessage(err, 'An error occurred'));
        },
      });
  }

  advanceStage(id: number): Observable<DealDto> {
    return this.http.post<DealDto>(`${this.basePath}/${id}/advance`, {});
  }

  markWon(id: number): Observable<DealDto> {
    return this.http.post<DealDto>(`${this.basePath}/${id}/win`, {});
  }

  markLost(id: number, reason: string): Observable<DealDto> {
    return this.http.post<DealDto>(`${this.basePath}/${id}/lose`, { reason });
  }

  /**
   * Loads stage totals for pipeline header/KPI use.
   * Does not toggle {@link loading} so it can run alongside {@link list} (e.g. kanban).
   */
  loadPipelineSummary(): void {
    this.http.get<PipelineSummaryDto[]>(`${this.basePath}/pipeline`).subscribe({
      next: (summary) => this.pipelineSummary.set(summary),
      error: (err) => {
        this.error.set(dealActionErrorMessage(err, 'An error occurred'));
      },
    });
  }
}
