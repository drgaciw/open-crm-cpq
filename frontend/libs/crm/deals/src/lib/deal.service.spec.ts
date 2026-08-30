import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { DealService } from './deal.service';
import { DealDto, PipelineSummaryDto, SpringPageResponse } from '@frontend/shared-models';

function deal(partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name' | 'stage'>): DealDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('DealService', () => {
  let service: DealService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DealService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DealService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list sets deals from page content', () => {
    const page: SpringPageResponse<DealDto> = {
      content: [deal({ id: 1, name: 'Acme', stage: 'PROSPECTING' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    service.list(0, 20, 'createdAt,desc', 'PROSPECTING');
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/deals' &&
        r.params.get('page') === '0' &&
        r.params.get('stage') === 'PROSPECTING',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);

    expect(service.deals()).toEqual(page.content);
    expect(service.loading()).toBe(false);
  });

  it('advanceStage posts to /advance', async () => {
    const promise = firstValueFrom(service.advanceStage(5));
    const req = httpMock.expectOne('/api/v1/deals/5/advance');
    expect(req.request.method).toBe('POST');
    req.flush(deal({ id: 5, name: 'Moved', stage: 'QUALIFICATION' }));
    await expect(promise).resolves.toMatchObject({ id: 5, stage: 'QUALIFICATION' });
  });

  it('markWon posts to /win', async () => {
    const promise = firstValueFrom(service.markWon(8));
    const req = httpMock.expectOne('/api/v1/deals/8/win');
    expect(req.request.method).toBe('POST');
    req.flush(deal({ id: 8, name: 'Closed', stage: 'WON' }));
    await expect(promise).resolves.toMatchObject({ id: 8, stage: 'WON' });
  });

  it('markLost posts to /lose with reason body', async () => {
    const promise = firstValueFrom(service.markLost(3, 'Budget cut'));
    const req = httpMock.expectOne('/api/v1/deals/3/lose');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Budget cut' });
    req.flush(deal({ id: 3, name: 'Gone', stage: 'LOST', lostReason: 'Budget cut' }));
    await expect(promise).resolves.toMatchObject({ id: 3, stage: 'LOST' });
  });

  it('loadPipelineSummary GETs /pipeline without toggling loading', () => {
    service.loading.set(false);
    const summary: PipelineSummaryDto[] = [
      { stage: 'PROSPECTING', count: 2, totalAmount: 1000, weightedAmount: 100 },
      { stage: 'PROPOSAL', count: 1, totalAmount: 5000, weightedAmount: 2500 },
    ];

    service.loadPipelineSummary();
    expect(service.loading()).toBe(false);

    const req = httpMock.expectOne('/api/v1/deals/pipeline');
    expect(req.request.method).toBe('GET');
    req.flush(summary);

    expect(service.pipelineSummary()).toEqual(summary);
    expect(service.loading()).toBe(false);
  });
});
