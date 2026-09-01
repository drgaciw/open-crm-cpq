import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ConfigurationRuleService } from './configuration-rule.service';
import {
  ConfigRuleCreateRequest,
  ConfigurationRuleDto,
} from './configuration-rule.model';

function rule(
  partial: Partial<ConfigurationRuleDto> &
    Pick<ConfigurationRuleDto, 'id' | 'sourceProductId' | 'name' | 'ruleType'>,
): ConfigurationRuleDto {
  return {
    uuid: `cr-${partial.id}`,
    priority: 0,
    ...partial,
  };
}

describe('ConfigurationRuleService', () => {
  let service: ConfigurationRuleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ConfigurationRuleService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ConfigurationRuleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listByProduct GETs /api/v1/configuration/rules/product/:id', async () => {
    const promise = firstValueFrom(service.listByProduct(12));
    const req = httpMock.expectOne(
      '/api/v1/configuration/rules/product/12',
    );
    expect(req.request.method).toBe('GET');
    req.flush([
      rule({
        id: 1,
        sourceProductId: 12,
        name: 'Requires addon',
        ruleType: 'REQUIRES',
        targetProductId: 99,
      }),
    ]);
    await expect(promise).resolves.toEqual([
      expect.objectContaining({ id: 1, name: 'Requires addon' }),
    ]);
  });

  it('create POSTs ConfigRuleCreateRequest to /api/v1/configuration/rules', async () => {
    const body: ConfigRuleCreateRequest = {
      name: 'Exclude rival',
      ruleType: 'EXCLUDES',
      sourceProductId: 12,
      targetProductId: 44,
      conditionExpression: 'attr:tier == "pro"',
      priority: 10,
    };
    const promise = firstValueFrom(service.create(body));
    const req = httpMock.expectOne('/api/v1/configuration/rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(
      rule({
        id: 2,
        sourceProductId: 12,
        name: 'Exclude rival',
        ruleType: 'EXCLUDES',
        targetProductId: 44,
        conditionExpression: 'attr:tier == "pro"',
        priority: 10,
      }),
    );
    await expect(promise).resolves.toMatchObject({
      id: 2,
      name: 'Exclude rival',
      ruleType: 'EXCLUDES',
    });
  });

  it('delete DELETEs /api/v1/configuration/rules/:id', async () => {
    const promise = firstValueFrom(service.delete(5));
    const req = httpMock.expectOne('/api/v1/configuration/rules/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await expect(promise).resolves.toBeNull();
  });

  it('never calls pricing /api/v1/rules or configuration/validate', async () => {
    const promise = firstValueFrom(service.listByProduct(1));
    const req = httpMock.expectOne(
      '/api/v1/configuration/rules/product/1',
    );
    expect(req.request.url).not.toContain('/api/v1/rules');
    expect(req.request.url).not.toContain('/validate');
    req.flush([]);
    await promise;
  });
});
