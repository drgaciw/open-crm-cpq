import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { RuleSetService } from './rule-set.service';
import { RuleSetDto, SpringPageResponse } from '@frontend/shared-models';

function ruleSet(
  partial: Partial<RuleSetDto> & Pick<RuleSetDto, 'id' | 'name' | 'status'>,
): RuleSetDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('RuleSetService', () => {
  let service: RuleSetService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RuleSetService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RuleSetService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listByStatus GETs /api/v1/rule-sets?status=', () => {
    service.listByStatus('ACTIVE');
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/rule-sets' && r.params.get('status') === 'ACTIVE',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [ruleSet({ id: 1, name: 'Default', status: 'ACTIVE' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    } satisfies SpringPageResponse<RuleSetDto>);
    expect(service.ruleSets()[0].status).toBe('ACTIVE');
  });

  it('keeps the active page when a superseded unfiltered response arrives last', () => {
    service.list();
    const earlier = httpMock.expectOne((req) => req.url === '/api/v1/rule-sets' && !req.params.has('status'));

    service.listByStatus('ACTIVE', 1, 5);
    const latest = httpMock.expectOne((req) => req.url === '/api/v1/rule-sets' && req.params.get('status') === 'ACTIVE');
    const active = ruleSet({ id: 1, name: 'Active rules', status: 'ACTIVE' });
    latest.flush({ content: [active], number: 1, size: 5, totalElements: 6, totalPages: 2 });
    if (!earlier.cancelled) {
      const draft = ruleSet({ id: 2, name: 'Draft rules', status: 'DRAFT' });
      earlier.flush({ content: [active, draft], number: 0, size: 20, totalElements: 2, totalPages: 1 });
    }

    expect(service.ruleSets()).toEqual([active]);
    expect(service.pagination()).toEqual({ page: 1, size: 5, totalElements: 6, totalPages: 2 });
    expect(service.loading()).toBe(false);
  });

  it('keeps the unfiltered page loading when a superseded status request fails', () => {
    service.listByStatus('ACTIVE');
    const earlier = httpMock.expectOne((req) => req.url === '/api/v1/rule-sets' && req.params.get('status') === 'ACTIVE');

    service.list();
    const latest = httpMock.expectOne((req) => req.url === '/api/v1/rule-sets' && !req.params.has('status'));
    if (!earlier.cancelled) {
      earlier.flush({ message: 'Old list failed' }, { status: 503, statusText: 'Service Unavailable' });
    }

    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    latest.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(service.isEmpty()).toBe(true);
    expect(service.error()).toBeNull();
  });

  it('list error.set appends errorCode as message [CODE]', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/rule-sets');
    req.flush(
      { message: 'Failed to load rule sets', errorCode: 'RULE_SETS_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe(
      'Failed to load rule sets [RULE_SETS_UNAVAILABLE]',
    );
  });

  it('getById detailError appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/rule-sets/99');
    req.flush(
      { message: 'Rule set not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.detailError()).toBe('Rule set not found [RESOURCE_NOT_FOUND]');
  });

  it('distinguishes an unavailable detail read from a missing rule set', () => {
    const path = '/api/v1/rule-sets/99';
    service.getById(99);
    httpMock.expectOne(path).flush(
      { message: 'Rule set temporarily unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.detailReadUnavailableId()).toBe(99);

    service.getById(99);
    expect(service.detailReadUnavailableId()).toBeNull();
    httpMock.expectOne(path).flush(
      { message: 'Rule set not found' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.detailReadUnavailableId()).toBeNull();
  });

  it('activate and deprecate POST lifecycle paths', async () => {
    const activate = firstValueFrom(service.activate(8));
    httpMock.expectOne('/api/v1/rule-sets/8/activate').flush(
      ruleSet({ id: 8, name: 'RS', status: 'ACTIVE' }),
    );
    await expect(activate).resolves.toMatchObject({ status: 'ACTIVE' });

    const deprecate = firstValueFrom(service.deprecate(8));
    httpMock.expectOne('/api/v1/rule-sets/8/deprecate').flush(
      ruleSet({ id: 8, name: 'RS', status: 'DEPRECATED' }),
    );
    await expect(deprecate).resolves.toMatchObject({ status: 'DEPRECATED' });
  });

  it('retains the latest detail when the previous selection response arrives last', () => {
    service.getById(4);
    const previous = httpMock.expectOne('/api/v1/rule-sets/4');
    service.getById(8);
    const current = httpMock.expectOne('/api/v1/rule-sets/8');
    const selected = ruleSet({ id: 8, name: 'Set eight', status: 'DRAFT' });
    current.flush(selected);
    if (!previous.cancelled) {
      previous.flush(ruleSet({ id: 4, name: 'Set four', status: 'DRAFT' }));
    }
    expect(service.selectedRuleSet()).toEqual(selected);
  });

  it('keeps a previous detail failure separate from the current detail and collection', () => {
    service.getById(4);
    const previous = httpMock.expectOne('/api/v1/rule-sets/4');
    service.getById(8);
    const current = httpMock.expectOne('/api/v1/rule-sets/8');
    service.list();
    const collection = httpMock.expectOne(request => request.url === '/api/v1/rule-sets');
    if (!previous.cancelled) {
      previous.flush({ message: 'Previous detail failed' }, { status: 503, statusText: 'Service Unavailable' });
    }
    const listLoading = service.loading();
    const currentDetailFailure = service.detailReadUnavailableId();
    const collectionError = service.error();
    current.flush(ruleSet({ id: 8, name: 'Set eight', status: 'DRAFT' }));
    collection.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(listLoading).toBe(true);
    expect(currentDetailFailure).toBeNull();
    expect(collectionError).toBeNull();
  });

});
