import { HttpParams } from '@angular/common/http';
import { PageRequest } from '@frontend/shared-models';

/**
 * Converts a PageRequest into HttpParams for Spring Boot pagination.
 */
export function toHttpParams(
  pageRequest?: PageRequest,
  extra?: Record<string, string | undefined>,
): HttpParams {
  let params = new HttpParams();

  if (pageRequest?.page !== undefined) {
    params = params.set('page', pageRequest.page.toString());
  }
  if (pageRequest?.size !== undefined) {
    params = params.set('size', pageRequest.size.toString());
  }
  if (pageRequest?.sort) {
    params = params.set('sort', pageRequest.sort);
  }

  if (extra) {
    for (const [key, value] of Object.entries(extra)) {
      if (value !== undefined && value !== '') {
        params = params.set(key, value);
      }
    }
  }

  return params;
}

/**
 * Parses URL search params back to a PageRequest.
 */
export function fromSearchParams(searchParams: URLSearchParams): PageRequest {
  return {
    page: searchParams.has('page')
      ? Number(searchParams.get('page'))
      : undefined,
    size: searchParams.has('size')
      ? Number(searchParams.get('size'))
      : undefined,
    sort: searchParams.get('sort') ?? undefined,
  };
}
