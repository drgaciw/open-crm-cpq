import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, signal, computed, Signal } from '@angular/core';
import { Observable, tap, map } from 'rxjs';
import {
  Identifiable,
  PaginationMeta,
  PageRequest,
  SpringPageResponse,
} from '@frontend/shared-models';

/**
 * Abstract base service that provides CRUD operations with signal-based state management.
 *
 * Features:
 * - Signal-based reactive state (items, selectedItem, loading, pagination)
 * - Generic type support for entities, create DTOs, and update DTOs
 * - Automatic pagination handling from Spring Boot Page responses
 * - Standard CRUD operations: list, getById, create, update, delete, search
 * - HttpClient integration with automatic state updates
 *
 * @typeParam T - The entity type
 * @typeParam TCreate - The DTO type for create operations (defaults to T)
 * @typeParam TUpdate - The DTO type for update operations (defaults to Partial<T>)
 *
 * @example
 * ```typescript
 * export interface Contact {
 *   id: string;
 *   firstName: string;
 *   lastName: string;
 *   email: string;
 * }
 *
 * export interface CreateContactRequest {
 *   firstName: string;
 *   lastName: string;
 *   email: string;
 * }
 *
 * @Injectable({ providedIn: 'root' })
 * export class ContactService extends BaseCrudService<Contact, CreateContactRequest, Partial<Contact>> {
 *   protected override basePath = '/api/v1/contacts';
 * }
 * ```
 */
export abstract class BaseCrudService<
  T extends Identifiable,
  TCreate = T,
  TUpdate = Partial<T>,
> {
  protected http = inject(HttpClient);

  /**
   * The base API path for this resource.
   * Must be set by subclasses.
   * @example '/api/v1/contacts'
   */
  protected abstract basePath: string;

  /**
   * Signal containing the list of items.
   * Updated automatically by list() and search() operations.
   */
  private itemsSignal = signal<T[]>([]);
  readonly items: Signal<T[]> = this.itemsSignal.asReadonly();

  /**
   * Signal containing the currently selected item.
   * Updated automatically by getById() operation.
   */
  private selectedItemSignal = signal<T | null>(null);
  readonly selectedItem: Signal<T | null> =
    this.selectedItemSignal.asReadonly();

  /**
   * Signal indicating whether an operation is in progress.
   */
  private loadingSignal = signal<boolean>(false);
  readonly loading: Signal<boolean> = this.loadingSignal.asReadonly();

  /**
   * Signal containing pagination metadata.
   * Updated automatically when fetching paginated results.
   */
  private paginationSignal = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  readonly pagination: Signal<PaginationMeta> =
    this.paginationSignal.asReadonly();

  /**
   * Computed signal indicating whether there is a next page available.
   */
  readonly hasNextPage = computed(() => {
    const pagination = this.paginationSignal();
    return pagination.page < pagination.totalPages - 1;
  });

  /**
   * Computed signal indicating whether there is a previous page available.
   */
  readonly hasPreviousPage = computed(() => {
    const pagination = this.paginationSignal();
    return pagination.page > 0;
  });

  /**
   * Fetches a paginated list of items.
   *
   * @param params - Optional pagination and sorting parameters
   * @returns Observable that emits the array of items
   */
  list(params?: PageRequest): Observable<T[]> {
    this.loadingSignal.set(true);

    let httpParams = new HttpParams();
    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params?.size !== undefined) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    if (params?.sort) {
      httpParams = httpParams.set('sort', params.sort);
    }

    return this.http
      .get<SpringPageResponse<T>>(this.basePath, { params: httpParams })
      .pipe(
        tap({
          next: (response) => {
            this.itemsSignal.set(response.content);
            this.updatePaginationFromSpringPage(response);
            this.loadingSignal.set(false);
          },
          error: () => {
            this.loadingSignal.set(false);
          },
        }),
        map((response) => response.content),
      );
  }

  /**
   * Fetches a single item by its ID.
   *
   * @param id - The ID of the item to fetch
   * @returns Observable that emits the item
   */
  getById(id: string | number): Observable<T> {
    this.loadingSignal.set(true);

    return this.http.get<T>(`${this.basePath}/${id}`).pipe(
      tap({
        next: (item) => {
          this.selectedItemSignal.set(item);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      }),
    );
  }

  /**
   * Creates a new item.
   *
   * @param request - The data for creating the new item
   * @returns Observable that emits the created item
   */
  create(request: TCreate): Observable<T> {
    this.loadingSignal.set(true);

    return this.http.post<T>(this.basePath, request).pipe(
      tap({
        next: (item) => {
          // Add the new item to the beginning of the list
          this.itemsSignal.update((items) => [item, ...items]);
          this.selectedItemSignal.set(item);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      }),
    );
  }

  /**
   * Updates an existing item.
   *
   * @param id - The ID of the item to update
   * @param request - The data for updating the item
   * @returns Observable that emits the updated item
   */
  update(id: string | number, request: TUpdate): Observable<T> {
    this.loadingSignal.set(true);

    return this.http.put<T>(`${this.basePath}/${id}`, request).pipe(
      tap({
        next: (item) => {
          // Update the item in the list
          this.itemsSignal.update((items) =>
            items.map((existingItem) =>
              existingItem.id === id ? item : existingItem,
            ),
          );
          this.selectedItemSignal.set(item);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      }),
    );
  }

  /**
   * Deletes an item by its ID.
   *
   * @param id - The ID of the item to delete
   * @returns Observable that completes when the item is deleted
   */
  delete(id: string | number): Observable<void> {
    this.loadingSignal.set(true);

    return this.http.delete<void>(`${this.basePath}/${id}`).pipe(
      tap({
        next: () => {
          // Remove the item from the list
          this.itemsSignal.update((items) =>
            items.filter((item) => item.id !== id),
          );
          // Clear selection if the deleted item was selected
          if (this.selectedItemSignal()?.id === id) {
            this.selectedItemSignal.set(null);
          }
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        },
      }),
    );
  }

  /**
   * Searches for items using a query string.
   *
   * @param query - The search query
   * @param params - Optional pagination and sorting parameters
   * @returns Observable that emits the array of matching items
   */
  search(query: string, params?: PageRequest): Observable<T[]> {
    this.loadingSignal.set(true);

    let httpParams = new HttpParams().set('q', query);
    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params?.size !== undefined) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    if (params?.sort) {
      httpParams = httpParams.set('sort', params.sort);
    }

    return this.http
      .get<
        SpringPageResponse<T>
      >(`${this.basePath}/search`, { params: httpParams })
      .pipe(
        tap({
          next: (response) => {
            this.itemsSignal.set(response.content);
            this.updatePaginationFromSpringPage(response);
            this.loadingSignal.set(false);
          },
          error: () => {
            this.loadingSignal.set(false);
          },
        }),
        map((response) => response.content),
      );
  }

  /**
   * Clears the selected item.
   */
  clearSelection(): void {
    this.selectedItemSignal.set(null);
  }

  /**
   * Clears the items list.
   */
  clearItems(): void {
    this.itemsSignal.set([]);
  }

  /**
   * Resets all state to initial values.
   */
  reset(): void {
    this.itemsSignal.set([]);
    this.selectedItemSignal.set(null);
    this.loadingSignal.set(false);
    this.paginationSignal.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  }

  /**
   * Updates pagination metadata from a Spring Boot Page response.
   *
   * @param response - The Spring Boot Page response
   */
  private updatePaginationFromSpringPage(
    response: SpringPageResponse<T>,
  ): void {
    this.paginationSignal.set({
      page: response.number,
      size: response.size,
      totalElements: response.totalElements,
      totalPages: response.totalPages,
    });
  }
}
