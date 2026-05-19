import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { ApiError, ApiResponse, SpringPageResponse } from '@frontend/shared-models';
import { apiResponseInterceptor } from './api-response.interceptor';

describe('apiResponseInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  const timestamp = '2024-01-01T00:00:00Z';

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

  afterEach(async () => {
    httpMock.verify();
    await TestBed.resetTestingModule();
  });

  it('unwraps successful ApiResponse envelope to .data', async () => {
    const responsePromise = firstValueFrom(http.get<{ id: string }>('/api/test'));

    const req = httpMock.expectOne('/api/test');
    const envelope: ApiResponse<{ id: string }> = {
      success: true,
      message: 'OK',
      timestamp,
      data: { id: '1' },
    };
    req.flush(envelope);

    const result = await responsePromise;
    expect(result).toEqual({ id: '1' });
  });

  it('throws ApiError when success=false', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    const envelope: ApiResponse<unknown> = {
      success: false,
      message: 'Something went wrong',
      errorCode: 'ERR_001',
      traceId: 'trace-abc',
      timestamp,
      errors: ['field is required'],
    };
    req.flush(envelope);

    await expect(responsePromise).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        name: 'ApiError',
        message: 'Something went wrong',
        errorCode: 'ERR_001',
        traceId: 'trace-abc',
        errors: ['field is required'],
      });
      return true;
    });
  });

  it('wraps paginated array responses into SpringPageResponse shape when pagination metadata present', async () => {
    const responsePromise = firstValueFrom(
      http.get<SpringPageResponse<{ id: string }>>('/api/items')
    );

    const req = httpMock.expectOne('/api/items');
    const envelope: ApiResponse<{ id: string }[]> = {
      success: true,
      message: 'OK',
      timestamp,
      data: [{ id: '1' }, { id: '2' }],
      pagination: {
        page: 0,
        size: 2,
        totalElements: 10,
        totalPages: 5,
        hasNext: true,
        hasPrevious: false,
      },
    };
    req.flush(envelope);

    const result = await responsePromise;
    expect(result).toEqual({
      content: [{ id: '1' }, { id: '2' }],
      number: 0,
      size: 2,
      totalElements: 10,
      totalPages: 5,
    });
  });

  it('passes through non-ApiResponse bodies unchanged', async () => {
    const plainBody = { foo: 'bar', count: 42 };
    const responsePromise = firstValueFrom(http.get<typeof plainBody>('/api/plain'));

    const req = httpMock.expectOne('/api/plain');
    req.flush(plainBody);

    const result = await responsePromise;
    expect(result).toEqual(plainBody);
  });
});
