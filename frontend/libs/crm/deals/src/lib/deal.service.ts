import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  DealDto,
  DealCreateRequest,
  DealUpdateRequest,
  DealStage,
  PipelineSummaryDto,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

@Injectable({ providedIn: 'root' })
export class DealService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/deals';

  readonly deals = signal<DealDto[]>([]);
  readonly selectedDeal = signal<DealDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pipelineSummary = signal<PipelineSummaryDto[] | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0, size: 20, totalElements: 0, totalPages: 0,
  });

  readonly isEmpty = computed(() => this.deals().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'createdAt,desc', stage?: DealStage): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    if (stage) params = params.set('stage', stage);

    this.http.get<SpringPageResponse<DealDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.deals.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0, size: p.size ?? size,
              totalElements: p.totalElements ?? 0, totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        }
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<DealDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (d) => this.selectedDeal.set(d),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
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
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('q', query);
    this.http.get<DealDto[]>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (deals) => this.deals.set(deals),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        }
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

  loadPipelineSummary(): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<PipelineSummaryDto[]>(`${this.basePath}/pipeline`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (summary) => this.pipelineSummary.set(summary),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        }
      });
  }
}
