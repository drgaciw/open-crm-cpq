import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize, map } from 'rxjs';
import {
  LeadDto,
  LeadCreateRequest,
  LeadUpdateRequest,
  LeadStatus,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/leads';

  readonly leads = signal<LeadDto[]>([]);
  readonly selectedLead = signal<LeadDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0, size: 20, totalElements: 0, totalPages: 0,
  });

  readonly isEmpty = computed(() => this.leads().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'createdAt,desc', status?: LeadStatus): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    if (status) params = params.set('status', status);

    this.http.get<SpringPageResponse<LeadDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
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
          this.error.set(err?.error?.message || 'An error occurred');
        }
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<LeadDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (lead) => this.selectedLead.set(lead),
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
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
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('q', query);
    // Interceptor rebuilds List+PageInfo into SpringPageResponse
    this.http.get<SpringPageResponse<LeadDto>>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.leads.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0, size: p.size ?? 20,
              totalElements: p.totalElements ?? 0, totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(err?.error?.message || 'An error occurred');
        }
      });
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
