import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CSRF_FIELD, LOGOUT_PATH, readCookie, SessionService } from './session.service';

describe('SessionService (ADR-016 sign-out)', () => {
  let submitted: HTMLFormElement[];

  beforeEach(() => {
    submitted = [];
    vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(function (this: HTMLFormElement) {
      submitted.push(this);
    });
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    const document = TestBed.inject(DOCUMENT);
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    document.querySelectorAll('form').forEach((form) => form.remove());
  });

  it('posts a form to /logout with the XSRF token as _csrf, so the browser follows the redirect to Entra', () => {
    const document = TestBed.inject(DOCUMENT);
    document.cookie = 'XSRF-TOKEN=abc%3D123; path=/';

    TestBed.inject(SessionService).signOut();

    expect(submitted).toHaveLength(1);
    const form = submitted[0];
    expect(form.method.toLowerCase()).toBe('post');
    expect(new URL(form.action).pathname).toBe(LOGOUT_PATH);
    const field = form.querySelector(`input[name="${CSRF_FIELD}"]`) as HTMLInputElement;
    expect(field.type).toBe('hidden');
    expect(field.value).toBe('abc=123');
  });

  it('still posts without a token: the gateway refuses it, rather than the SPA pretending to sign out', () => {
    TestBed.inject(SessionService).signOut();

    expect(submitted).toHaveLength(1);
    expect(submitted[0].querySelector(`input[name="${CSRF_FIELD}"]`)).toBeNull();
  });

  it('reads a cookie by exact name', () => {
    expect(readCookie('a=1; XSRF-TOKEN=t%20k; XSRF-TOKEN-OLD=x', 'XSRF-TOKEN')).toBe('t k');
    expect(readCookie('OTHER=1', 'XSRF-TOKEN')).toBeNull();
    expect(readCookie('', 'XSRF-TOKEN')).toBeNull();
  });
});
