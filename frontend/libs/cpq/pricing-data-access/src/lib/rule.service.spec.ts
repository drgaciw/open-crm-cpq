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
});
