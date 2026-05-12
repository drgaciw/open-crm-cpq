import { HttpErrorResponse, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, throwError } from 'rxjs';
import { type Mock, vi } from 'vitest';
import { ApiError } from '@frontend/shared-models';
import { NotificationService } from './notification.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let notificationService: {
    error: Mock;
  };

  const request = new HttpRequest('GET', '/api/v1/test');

  beforeEach(() => {
    notificationService = {
      error: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: NotificationService, useValue: notificationService },
      ],
    });
  });

  const runInterceptorWith = (error: unknown) => {
    const next: HttpHandlerFn = () => throwError(() => error);

    return TestBed.runInInjectionContext(() => firstValueFrom(errorInterceptor(request, next)));
  };

  it('should notify a friendly message for not found responses', async () => {
    const error = new HttpErrorResponse({
      status: 404,
      statusText: 'Not Found',
      url: '/api/v1/test',
    });

    await expect(runInterceptorWith(error)).rejects.toBe(error);

    expect(notificationService.error).toHaveBeenCalledWith('The requested resource was not found.');
  });

  it('should include trace IDs from backend error bodies', async () => {
    const error = new HttpErrorResponse({
      status: 500,
      statusText: 'Server Error',
      url: '/api/v1/test',
      error: {
        message: 'Pricing service unavailable',
        traceId: 'trace-500',
      },
    });

    await expect(runInterceptorWith(error)).rejects.toBe(error);

    expect(notificationService.error).toHaveBeenCalledWith('Pricing service unavailable (Trace ID: trace-500)');
  });

  it('should preserve ApiError details from the API response interceptor', async () => {
    const error = new ApiError('Validation failed');
    error.traceId = 'trace-api';
    error.errors = ['name is required', 'email is invalid'];

    await expect(runInterceptorWith(error)).rejects.toBe(error);

    expect(notificationService.error).toHaveBeenCalledWith(
      'Validation failed (Trace ID: trace-api)\nname is required\nemail is invalid'
    );
  });
});
