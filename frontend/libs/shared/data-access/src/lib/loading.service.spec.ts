import { TestBed } from '@angular/core/testing';
import { LoadingService } from './loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoadingService]
    });
    service = TestBed.inject(LoadingService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should initialize with no active requests', () => {
    expect(service.loading()).toBe(false);
    expect(service.getRequestCount()).toBe(0);
  });

  it('should report loading while requests are active', () => {
    service.startRequest();
    expect(service.getRequestCount()).toBe(1);
    expect(service.loading()).toBe(true);

    service.startRequest();
    expect(service.getRequestCount()).toBe(2);
    expect(service.loading()).toBe(true);
  });

  it('should decrement request count when endRequest is called', () => {
    service.startRequest();
    service.startRequest();
    expect(service.getRequestCount()).toBe(2);

    service.endRequest();
    expect(service.getRequestCount()).toBe(1);
    expect(service.loading()).toBe(true);

    service.endRequest();
    expect(service.getRequestCount()).toBe(0);
    expect(service.loading()).toBe(false);
  });

  it('should not go below 0 when endRequest is called more times than startRequest', () => {
    service.startRequest();
    service.endRequest();
    service.endRequest();
    service.endRequest();

    expect(service.getRequestCount()).toBe(0);
    expect(service.loading()).toBe(false);
  });

  it('should reset request count to 0', () => {
    service.startRequest();
    service.startRequest();
    service.startRequest();
    expect(service.getRequestCount()).toBe(3);

    service.reset();
    expect(service.getRequestCount()).toBe(0);
    expect(service.loading()).toBe(false);
  });

  it('should provide a readonly boolean loading signal', () => {
    const loadingSignal = service.loading;

    service.startRequest();
    expect(loadingSignal()).toBe(true);

    service.startRequest();
    expect(loadingSignal()).toBe(true);

    service.endRequest();
    expect(loadingSignal()).toBe(true);

    service.endRequest();
    expect(loadingSignal()).toBe(false);
  });
});
