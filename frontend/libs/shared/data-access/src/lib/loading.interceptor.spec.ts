import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { LoadingService } from './loading.service';
import { loadingInterceptor } from './loading.interceptor';

describe('loadingInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let loadingService: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
        LoadingService,
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    loadingService = TestBed.inject(LoadingService);
  });

  afterEach(async () => {
    httpMock.verify();
    await TestBed.resetTestingModule();
  });

  it('increments LoadingService count on request start, decrements on success', async () => {
    const startSpy = vi.spyOn(loadingService, 'startRequest');
    const endSpy = vi.spyOn(loadingService, 'endRequest');

    const responsePromise = firstValueFrom(http.get('/api/test'));

    expect(startSpy).toHaveBeenCalledTimes(1);
    expect(loadingService.getRequestCount()).toBe(1);
    expect(loadingService.loading()).toBe(true);

    const req = httpMock.expectOne('/api/test');
    req.flush({ ok: true });

    await responsePromise;

    expect(endSpy).toHaveBeenCalledTimes(1);
    expect(loadingService.getRequestCount()).toBe(0);
    expect(loadingService.loading()).toBe(false);
  });

  it('decrements on HTTP error (finalize)', async () => {
    const startSpy = vi.spyOn(loadingService, 'startRequest');
    const endSpy = vi.spyOn(loadingService, 'endRequest');

    const responsePromise = firstValueFrom(http.get('/api/test'));

    expect(startSpy).toHaveBeenCalledTimes(1);
    expect(loadingService.getRequestCount()).toBe(1);

    const req = httpMock.expectOne('/api/test');
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

    await expect(responsePromise).rejects.toBeTruthy();

    expect(endSpy).toHaveBeenCalledTimes(1);
    expect(loadingService.getRequestCount()).toBe(0);
    expect(loadingService.loading()).toBe(false);
  });
});
