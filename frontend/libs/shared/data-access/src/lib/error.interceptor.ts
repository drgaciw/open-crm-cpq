import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '@frontend/shared-models';
import { NotificationService } from './notification.service';

/**
 * Interceptor that catches HTTP errors and displays user-friendly notifications.
 *
 * This interceptor handles:
 * - Network errors (0 status code)
 * - Client errors (4xx status codes)
 * - Server errors (5xx status codes)
 * - Custom error messages from the backend
 *
 * @example
 * // In app.config.ts:
 * provideHttpClient(
 *   withInterceptors([apiResponseInterceptor, errorInterceptor])
 * )
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      let errorMessage = 'An unexpected error occurred';

      if (error instanceof HttpErrorResponse) {
        // Handle HTTP errors
        if (error.status === 0) {
          // Network error or CORS issue
          errorMessage = 'Unable to connect to the server. Please check your internet connection.';
        } else if (error.status >= 400 && error.status < 500) {
          // Client errors (4xx)
          if (error.status === 401) {
            errorMessage = 'Unauthorized. Please log in again.';
          } else if (error.status === 403) {
            errorMessage = 'You do not have permission to perform this action.';
          } else if (error.status === 404) {
            errorMessage = 'The requested resource was not found.';
          } else if (error.status === 422) {
            errorMessage = 'Validation failed. Please check your input.';
          } else {
            errorMessage = error.error?.message || error.message || `Client error: ${error.status}`;
          }
        } else if (error.status >= 500) {
          // Server errors (5xx)
          errorMessage = error.error?.message || 'A server error occurred. Please try again later.';
        }

        // If the error has a traceId, append it for support purposes
        const traceId = error.error?.traceId;
        if (traceId) {
          errorMessage += ` (Trace ID: ${traceId})`;
        }
      } else if (error instanceof ApiError) {
        // Handle custom API errors thrown by apiResponseInterceptor
        errorMessage = error.message;

        if (error.traceId) {
          errorMessage += ` (Trace ID: ${error.traceId})`;
        }

        if (error.errors && error.errors.length > 0) {
          errorMessage += '\n' + error.errors.join('\n');
        }
      } else if (error instanceof Error) {
        errorMessage = error.message;
      }

      // Display the error notification
      notificationService.error(errorMessage);

      // Re-throw the error so it can be handled by the calling code if needed
      return throwError(() => error);
    })
  );
};
