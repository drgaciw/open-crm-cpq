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

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/rule-sets/99');
    req.flush(
      { message: 'Rule set not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Rule set not found [RESOURCE_NOT_FOUND]');
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
});
