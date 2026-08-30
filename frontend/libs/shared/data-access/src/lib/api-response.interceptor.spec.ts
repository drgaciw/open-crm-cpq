import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { apiResponseInterceptor } from './api-response.interceptor';
import { ApiError, ApiResponse, SpringPageResponse } from '@frontend/shared-models';

describe('apiResponseInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiResponseInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('unwraps entity data from ApiResponse', async () => {
    const promise = firstValueFrom(http.get<{ id: number; title: string }>('/api/v1/leads/1'));
    const req = httpMock.expectOne('/api/v1/leads/1');
    const envelope: ApiResponse<{ id: number; title: string }> = {
      success: true,
      message: 'OK',
      data: { id: 1, title: 'Acme' },
      timestamp: '2026-01-01T00:00:00Z',
    };
    req.flush(envelope);

    await expect(promise).resolves.toEqual({ id: 1, title: 'Acme' });
  });

  it('rebuilds SpringPageResponse when data is array + pagination', async () => {
    const promise = firstValueFrom(
      http.get<SpringPageResponse<{ id: number }>>('/api/v1/leads/search?q=acme')
    );
    const req = httpMock.expectOne('/api/v1/leads/search?q=acme');
    const envelope: ApiResponse<{ id: number }[]> = {
      success: true,
      message: 'OK',
      data: [{ id: 1 }, { id: 2 }],
      pagination: {
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      },
      timestamp: '2026-01-01T00:00:00Z',
    };
    req.flush(envelope);

    const page = await promise;
    expect(page.content).toEqual([{ id: 1 }, { id: 2 }]);
    expect(page.number).toBe(0);
    expect(page.size).toBe(20);
    expect(page.totalElements).toBe(2);
    expect(page.totalPages).toBe(1);
  });

  it('leaves non-array data with pagination as-is (no double wrap)', async () => {
    const promise = firstValueFrom(http.get<unknown>('/api/v1/legacy/search?q=x'));
    const req = httpMock.expectOne('/api/v1/legacy/search?q=x');
    const pageObject = { content: [{ id: 9 }], number: 0, size: 20, totalElements: 1, totalPages: 1 };
    req.flush({
      success: true,
      message: 'OK',
      data: pageObject,
      pagination: {
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      },
      timestamp: '2026-01-01T00:00:00Z',
    });

    await expect(promise).resolves.toEqual(pageObject);
  });

  it('throws ApiError when success is false', async () => {
    const promise = firstValueFrom(http.get('/api/v1/leads/1'));
    const req = httpMock.expectOne('/api/v1/leads/1');
    req.flush({
      success: false,
      message: 'Not found',
      errorCode: 'NOT_FOUND',
      timestamp: '2026-01-01T00:00:00Z',
    });

    await expect(promise).rejects.toBeInstanceOf(ApiError);
  });
});
