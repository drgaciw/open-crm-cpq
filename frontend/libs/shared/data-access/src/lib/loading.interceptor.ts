import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoadingService } from './loading.service';

/**
 * Interceptor that tracks in-flight HTTP requests using the LoadingService.
 *
 * This interceptor automatically:
 * - Increments the loading counter when a request starts
 * - Decrements the loading counter when a request completes (success or error)
 *
 * The LoadingService can then be used to display a global loading indicator.
 *
 * @example
 * // In app.config.ts:
 * provideHttpClient(
 *   withInterceptors([
 *     loadingInterceptor,
 *     apiResponseInterceptor,
 *     errorInterceptor
 *   ])
 * )
 */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);

  // Increment the request counter
  loadingService.startRequest();

  // Process the request and decrement the counter when it completes
  return next(req).pipe(
    finalize(() => {
      // This will be called whether the request succeeds or fails
      loadingService.endRequest();
    })
  );
};
