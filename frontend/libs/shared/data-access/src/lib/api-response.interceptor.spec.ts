import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { ApiError, ApiResponse } from '@frontend/shared-models';
import { apiResponseInterceptor } from './api-response.interceptor';

describe('apiResponseInterceptor', () => {
  const request = new HttpRequest('GET', '/api/v1/test');

  it('should unwrap successful ApiResponse data', async () => {
    const responseBody: ApiResponse<{ id: string; name: string }> = {
      success: true,
      message: 'Loaded',
      data: { id: '1', name: 'Test' },
      timestamp: '2026-05-11T00:00:00Z',
    };
    const next: HttpHandlerFn = () => of(new HttpResponse({ body: responseBody }));

    const response = await firstValueFrom(apiResponseInterceptor(request, next));

    expect(response).toBeInstanceOf(HttpResponse);
    expect((response as HttpResponse<unknown>).body).toEqual({ id: '1', name: 'Test' });
  });

  it('should map paginated ApiResponse arrays to Spring page shape', async () => {
    const responseBody: ApiResponse<Array<{ id: string }>> = {
      success: true,
      message: 'Loaded',
      data: [{ id: '1' }],
      pagination: {
        page: 2,
        size: 10,
        totalElements: 21,
        totalPages: 3,
        hasNext: false,
        hasPrevious: true,
      },
      timestamp: '2026-05-11T00:00:00Z',
    };
    const next: HttpHandlerFn = () => of(new HttpResponse({ body: responseBody }));

    const response = await firstValueFrom(apiResponseInterceptor(request, next));

    expect((response as HttpResponse<unknown>).body).toEqual({
      content: [{ id: '1' }],
      number: 2,
      size: 10,
      totalElements: 21,
      totalPages: 3,
    });
  });

  it('should throw ApiError for failed ApiResponse envelopes', async () => {
    const responseBody: ApiResponse<never> = {
      success: false,
      message: 'Validation failed',
      errorCode: 'VALIDATION_ERROR',
      traceId: 'trace-123',
      errors: ['name is required'],
      timestamp: '2026-05-11T00:00:00Z',
    };
    const next: HttpHandlerFn = () => of(new HttpResponse({ body: responseBody }));

    await expect(firstValueFrom(apiResponseInterceptor(request, next))).rejects.toMatchObject({
      name: 'ApiError',
      message: 'Validation failed',
      errorCode: 'VALIDATION_ERROR',
      traceId: 'trace-123',
      errors: ['name is required'],
    } satisfies Partial<ApiError>);
  });

  it('should pass through non-envelope responses unchanged', async () => {
    const rawBody = { id: '1', name: 'Already unwrapped' };
    const next: HttpHandlerFn = () => of(new HttpResponse({ body: rawBody }));

    const response = await firstValueFrom(apiResponseInterceptor(request, next));

    expect((response as HttpResponse<unknown>).body).toBe(rawBody);
  });
});
