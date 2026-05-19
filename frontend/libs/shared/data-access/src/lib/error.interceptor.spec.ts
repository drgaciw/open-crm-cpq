import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { ApiResponse } from '@frontend/shared-models';
import { apiResponseInterceptor } from './api-response.interceptor';
import { errorInterceptor } from './error.interceptor';
import { NotificationService } from './notification.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let notificationSpy: { error: ReturnType<typeof vi.fn> };

  const timestamp = '2024-01-01T00:00:00Z';

  beforeEach(() => {
    notificationSpy = {
      error: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        // errorInterceptor must wrap apiResponseInterceptor so catchError receives ApiError throws from map()
        provideHttpClient(withInterceptors([errorInterceptor, apiResponseInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notificationSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(async () => {
    httpMock.verify();
    await TestBed.resetTestingModule();
  });

  it('shows notification for HttpErrorResponse status 0', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    req.error(new ProgressEvent('error'), { status: 0 });

    await expect(responsePromise).rejects.toMatchObject({ status: 0 });

    expect(notificationSpy.error).toHaveBeenCalledWith(
      'Unable to connect to the server. Please check your internet connection.'
    );
  });

  it('shows notification for HttpErrorResponse status 404', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    req.flush('Not found', { status: 404, statusText: 'Not Found' });

    await expect(responsePromise).rejects.toMatchObject({ status: 404 });

    expect(notificationSpy.error).toHaveBeenCalledWith(
      'The requested resource was not found.'
    );
  });

  it('shows notification for HttpErrorResponse status 500', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    req.flush({ message: 'Database unavailable' }, {
      status: 500,
      statusText: 'Internal Server Error',
    });

    await expect(responsePromise).rejects.toMatchObject({ status: 500 });

    expect(notificationSpy.error).toHaveBeenCalledWith('Database unavailable');
  });

  it('shows notification for ApiError from apiResponseInterceptor', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    const envelope: ApiResponse<unknown> = {
      success: false,
      message: 'Validation failed',
      traceId: 'trace-xyz',
      timestamp,
      errors: ['name is required', 'email is invalid'],
    };
    req.flush(envelope);

    await expect(responsePromise).rejects.toMatchObject({
      name: 'ApiError',
      message: 'Validation failed',
    });

    expect(notificationSpy.error).toHaveBeenCalledWith(
      'Validation failed (Trace ID: trace-xyz)\nname is required\nemail is invalid'
    );
  });

  it('re-throws error after notification', async () => {
    const responsePromise = firstValueFrom(http.get('/api/test'));

    const req = httpMock.expectOne('/api/test');
    req.flush('Not found', { status: 404, statusText: 'Not Found' });

    let caughtError: unknown;
    try {
      await responsePromise;
    } catch (error) {
      caughtError = error;
    }

    expect(notificationSpy.error).toHaveBeenCalled();
    expect(caughtError).toMatchObject({ status: 404 });
  });
});
