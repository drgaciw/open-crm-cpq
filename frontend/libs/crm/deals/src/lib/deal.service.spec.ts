import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { DealService } from './deal.service';
import { dealActionErrorMessage } from './deal-action-error';
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

  it('keeps rows and pagination from the latest stage when an older stage completes last', () => {
    service.list(0, 20, 'createdAt,desc', 'NEGOTIATION');
    const older = httpMock.expectOne((r) => r.params.get('stage') === 'NEGOTIATION');
    service.list(1, 25, 'createdAt,desc', 'PROPOSAL');
    const latest = httpMock.expectOne((r) => r.params.get('stage') === 'PROPOSAL');
    const proposal = deal({ id: 2, name: 'Proposal', stage: 'PROPOSAL' });
    latest.flush({ content: [proposal], number: 1, size: 25, totalElements: 26, totalPages: 2 });

    if (!older.cancelled) {
      const negotiation = deal({ id: 1, name: 'Negotiation', stage: 'NEGOTIATION' });
      older.flush({ content: [negotiation], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    }

    expect(service.deals()).toEqual([proposal]);
    expect(service.pagination()).toEqual({ page: 1, size: 25, totalElements: 26, totalPages: 2 });
    expect(service.loading()).toBe(false);
  });

  it('keeps loading the current list when a superseded search fails', () => {
    service.search('old');
    const older = httpMock.expectOne((r) => r.url === '/api/v1/deals/search' && r.params.get('q') === 'old');
    service.list();
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/deals');

    if (!older.cancelled) {
      older.flush({ message: 'Old search unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    }

    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    latest.flush({ content: [], number: 0, totalElements: 0, totalPages: 0 });
    expect(service.loading()).toBe(false);
  });

  it('reports the current search error without showing a superseded list', () => {
    service.list();
    const older = httpMock.expectOne((r) => r.url === '/api/v1/deals');
    service.search('current');
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/deals/search' && r.params.get('q') === 'current');
    latest.flush(
      { message: 'Search unavailable', errorCode: 'DEAL_SEARCH_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    if (!older.cancelled) {
      older.flush([deal({ id: 1, name: 'Old list result', stage: 'PROSPECTING' })]);
    }

    expect(service.error()).toBe('Search unavailable [DEAL_SEARCH_UNAVAILABLE]');
    expect(service.deals()).toEqual([]);
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

  it('list error.set appends errorCode as message [CODE]', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/deals');
    req.flush(
      { message: 'Failed to load deals', errorCode: 'DEALS_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe('Failed to load deals [DEALS_UNAVAILABLE]');
  });

  it('list sends organizationId with stage', () => {
    service.list(0, 20, 'createdAt,desc', 'NEGOTIATION', 15);
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/deals' &&
        r.params.get('stage') === 'NEGOTIATION' &&
        r.params.get('organizationId') === '15',
    );
    req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(service.deals()).toEqual([]);
  });

  it('applyStageChange updates the shared deals signal and the selected deal', () => {
    service.deals.set([
      deal({ id: 1, name: 'A', stage: 'PROSPECTING', organizationName: 'Acme' }),
      deal({ id: 2, name: 'B', stage: 'PROPOSAL' }),
    ]);
    service.selectedDeal.set(deal({ id: 1, name: 'A', stage: 'PROSPECTING', organizationName: 'Acme' }));

    service.applyStageChange(deal({ id: 1, name: 'A', stage: 'QUALIFICATION' }));

    expect(service.deals().map((row) => row.stage)).toEqual(['QUALIFICATION', 'PROPOSAL']);
    expect(service.deals()[0].organizationName).toBe('Acme');
    expect(service.selectedDeal()?.stage).toBe('QUALIFICATION');
    expect(service.selectedDeal()?.organizationName).toBe('Acme');
  });

  it('dealActionErrorMessage explains OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE', () => {
    const message = dealActionErrorMessage(
      { errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE', message: 'blocked' },
      'Could not mark this opportunity as Won',
    );
    expect(message).toContain('Accept the quote');
    expect(message).toContain('[OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE]');
    expect(message).not.toContain('blocked');
  });

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/deals/99');
    req.flush(
      { message: 'Deal not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Deal not found [RESOURCE_NOT_FOUND]');
  });
});
