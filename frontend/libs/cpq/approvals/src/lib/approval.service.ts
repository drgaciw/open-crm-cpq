import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import {
  ApprovalRequestDto,
  ApprovalResponseRequest,
} from '@frontend/shared-models';

/**
 * Approval inbox HTTP service against quote-service BFF.
 * Paths: `/api/v1/quotes/approvals/**` (never approval-routing `/api/v1/approvals/**`).
 * Signal-state pattern matches {@link QuoteService}.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalService {
  private readonly http = inject(HttpClient);
  /** Quote BFF base — do not point at approval-routing. */
  private readonly basePath = '/api/v1/quotes/approvals';

  readonly pending = signal<ApprovalRequestDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly isEmpty = computed(
    () => this.pending().length === 0 && !this.loading(),
  );

  listPending(): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<ApprovalRequestDto[]>(`${this.basePath}/pending`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (rows) => this.pending.set(rows ?? []),
        error: (err) => {
          this.error.set(
            err?.error?.message || err?.message || 'An error occurred',
          );
        },
      });
  }

  approve(
    id: number,
    request: ApprovalResponseRequest = {},
  ): Observable<ApprovalRequestDto> {
    return this.http
      .post<ApprovalRequestDto>(`${this.basePath}/${id}/approve`, request)
      .pipe(tap(() => this.removeFromPending(id)));
  }

  reject(
    id: number,
    request: ApprovalResponseRequest = {},
  ): Observable<ApprovalRequestDto> {
    return this.http
      .post<ApprovalRequestDto>(`${this.basePath}/${id}/reject`, request)
      .pipe(tap(() => this.removeFromPending(id)));
  }

  private removeFromPending(id: number): void {
    this.pending.update((rows) => rows.filter((r) => r.id !== id));
  }
}
