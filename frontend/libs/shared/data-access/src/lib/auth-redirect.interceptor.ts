import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, InjectionToken } from '@angular/core';
import { catchError, throwError } from 'rxjs';

/** Header the gateway sets on a 401 when a browser login exists (ADR-016 step 3). */
export const LOGIN_HINT_HEADER = 'X-OpenCRM-Login';

/** Sends the browser to a login path. A token so tests can observe the navigation. */
export const LOGIN_NAVIGATOR = new InjectionToken<(url: string) => void>('LOGIN_NAVIGATOR', {
  providedIn: 'root',
  factory: () => (url: string) => window.location.assign(url),
});

const LAST_REDIRECT_KEY = 'opencrm.login-redirect-at';
const REDIRECT_COOLDOWN_MS = 30_000;

/**
 * ADR-016 step 3: the gateway is a backend-for-frontend. When the session is missing or expired, an API call gets
 * 401 with the login path in the `X-OpenCRM-Login` header, and this interceptor sends the browser there. The gateway
 * runs the Entra login and brings the browser back with a session cookie; the SPA never handles a token.
 *
 * - No header (the `dev` gateway, or a gateway without a browser login): nothing happens.
 * - Only same-origin paths are followed, so a forged header cannot send the user off-site.
 * - At most one redirect per 30 seconds, so a login that does not cure the 401 cannot loop.
 *
 * The error is always re-thrown for the caller and the error interceptor.
 */
export const authRedirectInterceptor: HttpInterceptorFn = (req, next) => {
  const navigate = inject(LOGIN_NAVIGATOR);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        const login = error.headers?.get(LOGIN_HINT_HEADER);
        if (isSameOriginPath(login) && !redirectedRecently()) {
          rememberRedirect();
          navigate(login);
        }
      }
      return throwError(() => error);
    })
  );
};

/** A path on this origin: starts with one slash, no scheme, no protocol-relative `//host`, no backslash tricks. */
export function isSameOriginPath(value: string | null | undefined): value is string {
  return !!value && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\');
}

function redirectedRecently(): boolean {
  try {
    const last = Number(sessionStorage.getItem(LAST_REDIRECT_KEY) ?? 0);
    return Date.now() - last < REDIRECT_COOLDOWN_MS;
  } catch {
    return false;
  }
}

function rememberRedirect(): void {
  try {
    sessionStorage.setItem(LAST_REDIRECT_KEY, String(Date.now()));
  } catch {
    // Storage unavailable (private mode): the redirect still happens, only the loop guard is lost.
  }
}
