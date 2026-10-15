import { PlatformLocation } from '@angular/common';
import {
  HttpBackend,
  HttpClient,
  HttpInterceptorFn,
  HttpRequest,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { finalize, map, Observable, shareReplay, switchMap } from 'rxjs';

const CSRF_BOOTSTRAP_PATH = '/api/v1/csrf';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

/** Fetches the gateway's cookie once for concurrent first writes, without re-entering interceptors. */
@Injectable({ providedIn: 'root' })
export class GatewayCsrfBootstrap {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly tokens = inject(HttpXsrfTokenExtractor);
  private inFlight: Observable<string> | undefined;

  token(): Observable<string> {
    return (this.inFlight ??= this.http.get<void>(CSRF_BOOTSTRAP_PATH).pipe(
      map(() => {
        const token = this.tokens.getToken();
        if (!token) {
          throw new Error('Gateway did not provide a CSRF token');
        }
        return token;
      }),
      finalize(() => {
        this.inFlight = undefined;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    ));
  }
}

/** A first visit directly to a create form may not have made a safe gateway request yet. */
export const csrfBootstrapInterceptor: HttpInterceptorFn = (request, next) => {
  if (
    SAFE_METHODS.has(request.method) ||
    request.headers.has(CSRF_HEADER) ||
    /^Bearer\s/i.test(request.headers.get('Authorization') ?? '') ||
    !isGatewayApi(request, inject(PlatformLocation).href)
  ) {
    return next(request);
  }

  const token = inject(HttpXsrfTokenExtractor).getToken();
  if (token) {
    return next(request.clone({ setHeaders: { [CSRF_HEADER]: token } }));
  }

  // Angular's built-in XSRF interceptor already ran before this asynchronous bootstrap.
  return inject(GatewayCsrfBootstrap)
    .token()
    .pipe(
      switchMap((loadedToken) =>
        next(request.clone({ setHeaders: { [CSRF_HEADER]: loadedToken } })),
      ),
    );
};

function isGatewayApi(request: HttpRequest<unknown>, href: string): boolean {
  try {
    const location = new URL(href);
    const target = new URL(request.url, location);
    return (
      target.origin === location.origin && target.pathname.startsWith('/api/')
    );
  } catch {
    return false;
  }
}
