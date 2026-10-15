import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
  withXhr,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { csrfBootstrapInterceptor } from './csrf-bootstrap.interceptor';

const ENDPOINT = '/api/v1/csrf';
const RESOURCE = '/api/v1/organizations';

describe('csrfBootstrapInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  const expireCookie = () => {
    document.cookie =
      'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  };
  beforeEach(() => {
    expireCookie();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXhr(),
          withInterceptors([csrfBootstrapInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    try {
      requests.verify();
    } finally {
      expireCookie();
      TestBed.resetTestingModule();
    }
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])(
    'bootstraps a fresh direct-form %s before issuing the mutation once',
    (method) => {
      http
        .request(method, RESOURCE, { body: { name: 'UAT-CSRF' } })
        .subscribe();
      requests.expectNone(RESOURCE);
      const bootstrap = requests.expectOne(ENDPOINT);
      expect(bootstrap.request.method).toBe('GET');
      document.cookie = 'XSRF-TOKEN=synthetic%3Dtoken; path=/';
      bootstrap.flush(null, { status: 204, statusText: 'No Content' });
      const mutation = requests.expectOne(RESOURCE);
      expect(mutation.request.method).toBe(method);
      expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe(
        'synthetic=token',
      );
      mutation.flush({ id: 1 });
    },
  );

  it('uses the existing gateway cookie without a bootstrap request', () => {
    document.cookie = 'XSRF-TOKEN=existing-token; path=/';
    http.post(RESOURCE, {}).subscribe();
    requests.expectNone(ENDPOINT);
    const mutation = requests.expectOne(RESOURCE);
    expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('existing-token');
    mutation.flush({});
  });

  it.each(['GET', 'HEAD', 'OPTIONS', 'TRACE'])(
    'does not bootstrap the safe %s method',
    (method) => {
      http.request(method, RESOURCE).subscribe();
      requests.expectNone(ENDPOINT);
      requests.expectOne(RESOURCE).flush(null);
    },
  );

  it.each([
    'https://unrelated.example/api/v1/organizations',
    '//unrelated.example/api/v1/organizations',
    '/logout',
  ])('does not bootstrap or disclose a token to %s', (url) => {
    http.post(url, {}).subscribe();
    requests.expectNone(ENDPOINT);
    const request = requests.expectOne(url);
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush({});
  });

  it.each([
    'https://unrelated.example/api/v1/organizations',
    '//unrelated.example/api/v1/organizations',
  ])('never discloses an existing gateway cookie to %s', (url) => {
    document.cookie = 'XSRF-TOKEN=private-synthetic-token; path=/';
    http.post(url, {}).subscribe();
    requests.expectNone(ENDPOINT);
    const request = requests.expectOne(url);
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush({});
  });
  it('does not bootstrap bearer-token requests', () => {
    http
      .post(
        RESOURCE,
        {},
        { headers: { Authorization: 'bEaReR synthetic-test-token' } },
      )
      .subscribe();
    requests.expectNone(ENDPOINT);
    requests.expectOne(RESOURCE).flush({});
  });

  it('preserves an explicitly supplied CSRF token', () => {
    http
      .post(RESOURCE, {}, { headers: { 'X-XSRF-TOKEN': 'caller-token' } })
      .subscribe();
    requests.expectNone(ENDPOINT);
    const mutation = requests.expectOne(RESOURCE);
    expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('caller-token');
    mutation.flush({});
  });

  it('blocks the original mutation when bootstrap fails, without replaying it', () => {
    const errors: unknown[] = [];
    http.post(RESOURCE, {}).subscribe({ error: (error) => errors.push(error) });
    requests
      .expectOne(ENDPOINT)
      .flush(
        { message: 'Unavailable' },
        { status: 503, statusText: 'Unavailable' },
      );
    requests.expectNone(RESOURCE);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toMatchObject({ status: 503 });
  });

  it('blocks the mutation when a successful bootstrap did not supply a cookie', () => {
    const errors: unknown[] = [];
    http.post(RESOURCE, {}).subscribe({ error: (error) => errors.push(error) });
    requests
      .expectOne(ENDPOINT)
      .flush(null, { status: 204, statusText: 'No Content' });
    requests.expectNone(RESOURCE);
    expect(errors).toHaveLength(1);
    expect(String(errors[0])).toContain('CSRF token');
  });

  it('shares one in-flight bootstrap across two concurrent mutations', () => {
    http.post(RESOURCE, { name: 'UAT-A' }).subscribe();
    http.post(RESOURCE, { name: 'UAT-B' }).subscribe();
    requests.expectNone(RESOURCE);
    const bootstrap = requests.expectOne(ENDPOINT);
    document.cookie = 'XSRF-TOKEN=shared-token; path=/';
    bootstrap.flush(null, { status: 204, statusText: 'No Content' });
    const mutations = requests.match(RESOURCE);
    expect(mutations).toHaveLength(2);
    for (const mutation of mutations) {
      expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('shared-token');
      mutation.flush({});
    }
  });

  it('allows a later user-requested mutation to bootstrap after an earlier bootstrap failed', () => {
    http.post(RESOURCE, {}).subscribe({ error: () => undefined });
    requests
      .expectOne(ENDPOINT)
      .flush(null, { status: 503, statusText: 'Unavailable' });
    requests.expectNone(RESOURCE);
    http.post(RESOURCE, {}).subscribe();
    const bootstrap = requests.expectOne(ENDPOINT);
    document.cookie = 'XSRF-TOKEN=retry-token; path=/';
    bootstrap.flush(null, { status: 204, statusText: 'No Content' });
    const mutation = requests.expectOne(RESOURCE);
    expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('retry-token');
    mutation.flush({});
  });
});
