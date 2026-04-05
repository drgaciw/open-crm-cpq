import { Injectable, signal, computed } from '@angular/core';

/**
 * Service that tracks the number of in-flight HTTP requests using Angular signals.
 *
 * This service maintains a counter of active requests and provides a computed signal
 * that indicates whether any requests are currently in progress.
 *
 * Typically used with a loading interceptor to show/hide loading indicators.
 *
 * @example
 * ```typescript
 * import { inject } from '@angular/core';
 * import { LoadingService } from '@frontend/shared-data-access';
 *
 * export class MyComponent {
 *   private loadingService = inject(LoadingService);
 *   isLoading = this.loadingService.loading;
 *
 *   // In template:
 *   // @if (isLoading()) {
 *   //   <mat-spinner></mat-spinner>
 *   // }
 * }
 * ```
 */
@Injectable({
  providedIn: 'root',
})
export class LoadingService {
  /**
   * Internal counter for the number of active requests.
   * This is a writable signal that should not be exposed directly.
   */
  private requestCount = signal(0);

  /**
   * Public computed signal that indicates whether any requests are in progress.
   * Returns true if requestCount > 0, false otherwise.
   */
  readonly loading = computed(() => this.requestCount() > 0);

  /**
   * Increments the request counter when a new request starts.
   * Call this when an HTTP request begins.
   */
  startRequest(): void {
    this.requestCount.update((count) => count + 1);
  }

  /**
   * Decrements the request counter when a request completes.
   * Call this when an HTTP request finishes (success or error).
   *
   * The counter will never go below 0.
   */
  endRequest(): void {
    this.requestCount.update((count) => Math.max(0, count - 1));
  }

  /**
   * Resets the request counter to 0.
   * Useful for clearing the loading state in error scenarios.
   */
  reset(): void {
    this.requestCount.set(0);
  }

  /**
   * Returns the current number of active requests.
   * Primarily used for debugging and testing.
   */
  getRequestCount(): number {
    return this.requestCount();
  }
}
