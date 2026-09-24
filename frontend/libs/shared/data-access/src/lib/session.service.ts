import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/** The gateway's sign-out endpoint (ADR-016 step 3). */
export const LOGOUT_PATH = '/logout';

/** The CSRF cookie the gateway hands out (ADR-004); readable by script on purpose. */
export const XSRF_COOKIE = 'XSRF-TOKEN';

/** The form field Spring Security reads the CSRF token from. */
export const CSRF_FIELD = '_csrf';

/**
 * The browser session with the gateway (ADR-016). The SPA never holds a token; signing out ends the gateway session
 * and, through Entra's end-session endpoint, the Entra session.
 *
 * Sign-out is a form POST rather than an HttpClient call: it must be a page navigation, so the browser follows the
 * gateway's redirect to Entra and back. The CSRF token from the `XSRF-TOKEN` cookie goes in the `_csrf` field.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly document = inject(DOCUMENT);

  signOut(): void {
    const form = this.document.createElement('form');
    form.method = 'post';
    form.action = LOGOUT_PATH;
    form.style.display = 'none';
    const token = readCookie(this.document.cookie, XSRF_COOKIE);
    if (token) {
      const field = this.document.createElement('input');
      field.type = 'hidden';
      field.name = CSRF_FIELD;
      field.value = token;
      form.appendChild(field);
    }
    this.document.body.appendChild(form);
    form.submit();
  }
}

/** A cookie's decoded value from a `document.cookie` string, or null. */
export function readCookie(cookies: string, name: string): string | null {
  for (const part of cookies.split(';')) {
    const separator = part.indexOf('=');
    if (separator < 0) {
      continue;
    }
    if (part.slice(0, separator).trim() === name) {
      return decodeURIComponent(part.slice(separator + 1).trim());
    }
  }
  return null;
}
