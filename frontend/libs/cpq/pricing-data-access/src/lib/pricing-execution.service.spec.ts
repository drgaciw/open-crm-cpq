import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PricingExecutionService } from './pricing-execution.service';
import {
  PricingContext,
  PricingResult,
  BulkPricingSimulationResponse,
  PricingAuditLogDto,
} from '@frontend/shared-models';

const ctx: PricingContext = {
  customerExternalId: 'cust-1',
  quoteExternalId: 'quote-1',
  lineItems: [{ productExternalId: 'prod-1', quantity: 10 }],
};

function result(partial: Partial<PricingResult> = {}): PricingResult {
  return {
    success: true,
    approvalState: 'COMPLIANT',
    ...partial,
  };
}

describe('PricingExecutionService', () => {
  let service: PricingExecutionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PricingExecutionService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PricingExecutionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('execute POSTs /api/v1/pricing/execute and sets lastResult', async () => {
    const promise = firstValueFrom(service.execute(ctx));
    const req = httpMock.expectOne('/api/v1/pricing/execute');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(ctx);
    req.flush(result({ executionTimeMs: 12 }));
    await expect(promise).resolves.toMatchObject({ success: true });
    expect(service.lastResult()?.executionTimeMs).toBe(12);
    expect(service.loading()).toBe(false);
  });

  it('simulate POSTs /api/v1/pricing/simulate and appends simulationResults', async () => {
    const promise = firstValueFrom(service.simulate(ctx));
    const req = httpMock.expectOne('/api/v1/pricing/simulate');
    expect(req.request.method).toBe('POST');
    req.flush(result());
    await promise;
    expect(service.simulationResults()).toHaveLength(1);
  });

  it('simulateBulk POSTs /api/v1/pricing/simulate/bulk', async () => {
    const response: BulkPricingSimulationResponse = {
      totalRequests: 2,
      successfulRequests: 2,
      failedRequests: 0,
      results: [result(), result({ approvalState: 'DEAL_DESK_REQUIRED' })],
    };
    const promise = firstValueFrom(
      service.simulateBulk({ contexts: [ctx, ctx] }),
    );
    const req = httpMock.expectOne('/api/v1/pricing/simulate/bulk');
    expect(req.request.method).toBe('POST');
    req.flush(response);
    await expect(promise).resolves.toMatchObject({ totalRequests: 2 });
    expect(service.simulationResults()).toHaveLength(2);
    expect(service.lastResult()?.approvalState).toBe('DEAL_DESK_REQUIRED');
  });

  it('getAuditTrail GETs /api/v1/pricing/audit/:quoteExternalId', () => {
    const logs: PricingAuditLogDto[] = [
      {
        id: 1,
        uuid: 'a1',
        quoteExternalId: 'quote-1',
        inputSnapshot: '{}',
        outputSnapshot: '{}',
        executedAt: '2026-01-01T00:00:00Z',
      },
    ];
    service.getAuditTrail('quote-1');
    const req = httpMock.expectOne('/api/v1/pricing/audit/quote-1');
    expect(req.request.method).toBe('GET');
    req.flush(logs);
    expect(service.auditTrail()).toEqual(logs);
  });
});
