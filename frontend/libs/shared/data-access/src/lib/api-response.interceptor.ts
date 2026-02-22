import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { ApiResponse, ApiError } from '@frontend/shared-models';

/**
 * Interceptor that unwraps the ApiResponse envelope from backend responses.
 *
 * For successful responses:
 * - Extracts the .data property from ApiResponse<T>
 * - Returns the unwrapped data to the application layer
 *
 * For failed responses:
 * - Throws an error with the message from the ApiResponse
 * - Includes errorCode and traceId for debugging
 *
 * @example
 * // In app.config.ts:
 * provideHttpClient(
 *   withInterceptors([apiResponseInterceptor, errorInterceptor])
 * )
 */
export const apiResponseInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    map((event) => {
      // Only process HttpResponse events
      if (event instanceof HttpResponse) {
        const body = event.body;

        // Check if the response body is an ApiResponse
        if (body && typeof body === 'object' && 'success' in body && 'timestamp' in body) {
          const apiResponse = body as ApiResponse<unknown>;

          // If the response indicates failure, throw an error
          if (!apiResponse.success) {
            const error = new ApiError(apiResponse.message || 'An error occurred');
            error.errorCode = apiResponse.errorCode;
            error.traceId = apiResponse.traceId;
            error.errors = apiResponse.errors;
            error.timestamp = apiResponse.timestamp;
            throw error;
          }

          // For successful responses, unwrap and return the data.
          // If pagination metadata is present and data is an array,
          // wrap into a SpringPageResponse shape so services can read pagination.
          let unwrapped: unknown = apiResponse.data;
          if (apiResponse.pagination && Array.isArray(apiResponse.data)) {
            const p = apiResponse.pagination;
            unwrapped = {
              content: apiResponse.data,
              number: p.page,
              size: p.size,
              totalElements: p.totalElements,
              totalPages: p.totalPages,
            };
          }

          return event.clone({ body: unwrapped });
        }
      }

      // Return the event as-is if it's not an HttpResponse or doesn't have ApiResponse structure
      return event;
    })
  );
};
