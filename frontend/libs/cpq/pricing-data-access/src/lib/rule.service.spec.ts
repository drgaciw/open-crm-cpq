import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { RuleService } from './rule.service';
import { RuleDto, RuleImpactDto, SpringPageResponse } from '@frontend/shared-models';

function rule(
  partial: Partial<RuleDto> & Pick<RuleDto, 'id' | 'ruleSetId' | 'name' | 'ruleType' | 'status'>,
): RuleDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('RuleService', () => {
  let service: RuleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RuleService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RuleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listByRuleSet GETs /api/v1/rules/by-rule-set/:id', () => {
    service.listByRuleSet(4);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/rules/by-rule-set/4',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [
        rule({
          id: 1,
          ruleSetId: 4,
          name: 'Volume',
          ruleType: 'PRICING',
          status: 'ACTIVE',
        }),
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    } satisfies SpringPageResponse<RuleDto>);
    expect(service.rules()).toHaveLength(1);
  });

  it('listByRuleSet error.set appends errorCode as message [CODE]', () => {
    service.listByRuleSet(4);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/rules/by-rule-set/4',
    );
    req.flush(
      { message: 'Failed to load rules', errorCode: 'RULES_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe('Failed to load rules [RULES_UNAVAILABLE]');
  });

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/rules/99');
    req.flush(
      { message: 'Rule not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Rule not found [RESOURCE_NOT_FOUND]');
  });

  it('validateSyntax POSTs /api/v1/rules/:id/validate', async () => {
    const promise = firstValueFrom(service.validateSyntax(12));
    const req = httpMock.expectOne('/api/v1/rules/12/validate');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await expect(promise).resolves.toBeNull();
  });

  it('analyzeImpact GETs /api/v1/rules/:id/impact', async () => {
    const impact: RuleImpactDto = {
      ruleId: 12,
      ruleName: 'Volume',
      activeRuleSet: true,
    };
    const promise = firstValueFrom(service.analyzeImpact(12));
    const req = httpMock.expectOne('/api/v1/rules/12/impact');
    expect(req.request.method).toBe('GET');
    req.flush(impact);
    await expect(promise).resolves.toMatchObject({ ruleId: 12 });
  });

  it('retains a same-set refresh and clears foreign rows and pagination immediately on switch', () => {
    const first = rule({ id: 1, ruleSetId: 4, name: 'Set four', ruleType: 'PRICING', status: 'DRAFT' });
    service.listByRuleSet(4);
    httpMock.expectOne(request => request.url === '/api/v1/rules/by-rule-set/4')
      .flush({ content: [first], number: 0, size: 20, totalElements: 1, totalPages: 1 });

    service.listByRuleSet(4, 0, 10);
    const olderPage = httpMock.expectOne(request => request.url === '/api/v1/rules/by-rule-set/4');
    expect(service.rules()).toEqual([first]);

    service.listByRuleSet(8);
    const latest = httpMock.expectOne(request => request.url === '/api/v1/rules/by-rule-set/8');
    const pendingRows = service.rules();
    const pendingTotal = service.pagination().totalElements;
    latest.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    if (!olderPage.cancelled) {
      olderPage.flush({ content: [first], number: 0, size: 10, totalElements: 1, totalPages: 1 });
    }

    expect(pendingRows).toEqual([]);
    expect(pendingTotal).toBe(0);
    expect(service.rules()).toEqual([]);
    expect(service.pagination().totalElements).toBe(0);
  });

  it('prevents a previous set failure from ending the current pending Rules read', () => {
    service.listByRuleSet(4);
    const previous = httpMock.expectOne(request => request.url === '/api/v1/rules/by-rule-set/4');
    service.listByRuleSet(8);
    const current = httpMock.expectOne(request => request.url === '/api/v1/rules/by-rule-set/8');
    if (!previous.cancelled) {
      previous.flush({ message: 'Previous set failed' }, { status: 503, statusText: 'Service Unavailable' });
    }
    const loading = service.loading();
    const failure = service.error();
    current.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(loading).toBe(true);
    expect(failure).toBeNull();
  });

});
