import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  ContactDto,
  ContactCreateRequest,
  ContactUpdateRequest,
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
export class ContactService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/contacts';

  readonly contacts = signal<ContactDto[]>([]);
  readonly selectedContact = signal<ContactDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0, size: 20, totalElements: 0, totalPages: 0,
  });

  readonly isEmpty = computed(() => this.contacts().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'lastName,asc'): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams()
      .set('page', page).set('size', size).set('sort', sort);
    this.http.get<SpringPageResponse<ContactDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.contacts.set(p.content ?? p);
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

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http.get<ContactDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (c) => this.selectedContact.set(c),
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        }
      });
  }

  create(request: ContactCreateRequest): Observable<ContactDto> {
    return this.http.post<ContactDto>(this.basePath, request);
  }

  update(id: number, request: ContactUpdateRequest): Observable<ContactDto> {
    return this.http.put<ContactDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  search(query: string): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('q', query);
    // Interceptor rebuilds List+PageInfo into SpringPageResponse
    this.http.get<SpringPageResponse<ContactDto>>(`${this.basePath}/search`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.contacts.set(p.content ?? p);
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0, size: p.size ?? 20,
              totalElements: p.totalElements ?? 0, totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        }
      });
  }
}
