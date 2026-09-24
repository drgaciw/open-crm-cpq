import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import {
  authRedirectInterceptor,
  isSameOriginPath,
  LOGIN_HINT_HEADER,
  LOGIN_NAVIGATOR,
} from './auth-redirect.interceptor';

describe('authRedirectInterceptor (ADR-016 step 3)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let navigated: string[];

  beforeEach(() => {
    sessionStorage.clear();
    navigated = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authRedirectInterceptor])),
        provideHttpClientTesting(),
        { provide: LOGIN_NAVIGATOR, useValue: (url: string) => navigated.push(url) },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  async function respond(status: number, headers: Record<string, string> = {}): Promise<HttpErrorResponse> {
    const result = firstValueFrom(http.get('/api/v1/quotes')).catch((error: HttpErrorResponse) => error);
    httpMock
      .expectOne('/api/v1/quotes')
      .flush({ success: false, message: 'Authentication required' }, { status, statusText: 'x', headers });
    return (await result) as HttpErrorResponse;
  }

  it('sends the browser to the login the gateway names on a 401, and still reports the error', async () => {
    const error = await respond(401, { [LOGIN_HINT_HEADER]: '/oauth2/authorization/entra' });

    expect(error.status).toBe(401);
    expect(navigated).toEqual(['/oauth2/authorization/entra']);
  });

  it('does nothing on a 401 without the hint (dev gateway, or no browser login)', async () => {
    await respond(401);

    expect(navigated).toEqual([]);
  });

  it('does nothing on other errors', async () => {
    await respond(403, { [LOGIN_HINT_HEADER]: '/oauth2/authorization/entra' });

    expect(navigated).toEqual([]);
  });

  it('never follows an off-site hint', async () => {
    await respond(401, { [LOGIN_HINT_HEADER]: '//evil.example/login' });
    await respond(401, { [LOGIN_HINT_HEADER]: 'https://evil.example/login' });

    expect(navigated).toEqual([]);
  });

  it('redirects at most once per 30 seconds, so a failing login cannot loop', async () => {
    await respond(401, { [LOGIN_HINT_HEADER]: '/oauth2/authorization/entra' });
    await respond(401, { [LOGIN_HINT_HEADER]: '/oauth2/authorization/entra' });

    expect(navigated).toEqual(['/oauth2/authorization/entra']);
  });

  it('accepts only same-origin paths', () => {
    expect(isSameOriginPath('/oauth2/authorization/entra')).toBe(true);
    expect(isSameOriginPath('//evil.example')).toBe(false);
    expect(isSameOriginPath('/\\evil.example')).toBe(false);
    expect(isSameOriginPath('https://evil.example')).toBe(false);
    expect(isSameOriginPath(null)).toBe(false);
  });
});
