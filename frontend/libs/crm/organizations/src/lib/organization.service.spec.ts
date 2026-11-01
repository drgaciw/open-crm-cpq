import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrganizationService } from './organization.service';
import { DealDto, OrganizationDto, QuoteDto, SpringPageResponse } from '@frontend/shared-models';

function org(
  partial: Partial<OrganizationDto> & Pick<OrganizationDto, 'id' | 'name'>,
): OrganizationDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('OrganizationService', () => {
  let service: OrganizationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OrganizationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrganizationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list sets organizations from page content', () => {
    const page: SpringPageResponse<OrganizationDto> = {
      content: [org({ id: 1, name: 'Acme' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    service.list(0, 20, 'name,asc');
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/organizations' &&
        r.params.get('page') === '0' &&
        r.params.get('sort') === 'name,asc',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);

    expect(service.organizations()).toEqual(page.content);
    expect(service.loading()).toBe(false);
  });

  it('list error.set appends errorCode as message [CODE]', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/organizations');
    req.flush(
      { message: 'Failed to load organizations', errorCode: 'ORGS_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe('Failed to load organizations [ORGS_UNAVAILABLE]');
  });

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/organizations/99');
    req.flush(
      { message: 'Organization not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Organization not found [RESOURCE_NOT_FOUND]');
  });

  it('search keeps /organizations and forwards page, size, and sort', () => {
    const search = service.search as (
      query: string,
      page?: number,
      size?: number,
      sort?: string,
    ) => void;
    search.call(service, 'acme', 1, 10, 'name,desc');
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/organizations/search' &&
        r.params.get('q') === 'acme' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10' &&
        r.params.get('sort') === 'name,desc',
    );
    req.flush({ content: [], number: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('list forwards industry, territory, and owner filters on /organizations', () => {
    const list = service.list as (
      page?: number,
      size?: number,
      sort?: string,
      filters?: { industry?: string; territory?: string; ownerId?: string },
    ) => void;
    list.call(service, 0, 20, 'name,asc', {
      industry: 'Technology',
      territory: 'West',
      ownerId: 'ada',
    });
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/organizations' &&
        r.params.get('industry') === 'Technology' &&
        r.params.get('territory') === 'West' &&
        r.params.get('ownerId') === 'ada',
    );
    req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('loadRelatedDeals calls /api/v1/deals and keeps only this account', () => {
    const api = service as OrganizationService & {
      loadRelatedDeals?: (id: number) => void;
      relatedDeals?: () => DealDto[];
    };
    expect(typeof api.loadRelatedDeals).toBe('function');
    api.loadRelatedDeals!(7);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/deals' && r.params.get('organizationId') === '7',
    );
    const page: SpringPageResponse<DealDto> = {
      content: [
        deal({ id: 3, name: 'Renewal', organizationId: 7 }),
        deal({ id: 4, name: 'Other', organizationId: 8 }),
      ],
      number: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    };
    req.flush(page);
    expect(api.relatedDeals!().map((d) => d.id)).toEqual([3]);
  });

  it('loadRelatedQuotes calls /api/v1/quotes with the account uuid', () => {
    const api = service as OrganizationService & {
      loadRelatedQuotes?: (account: OrganizationDto) => void;
      relatedQuotes?: () => QuoteDto[];
    };
    expect(typeof api.loadRelatedQuotes).toBe('function');
    const account = org({ id: 7, name: 'Acme', uuid: 'uuid-7' });
    api.loadRelatedQuotes!(account);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/quotes' && r.params.get('customerExternalId') === 'uuid-7',
    );
    const page: SpringPageResponse<QuoteDto> = {
      content: [
        quote({ id: 9, name: 'Q-9', customerExternalId: 'uuid-7' }),
        quote({ id: 10, name: 'Q-10', customerExternalId: 'other' }),
      ],
      number: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    };
    req.flush(page);
    expect(api.relatedQuotes!().map((q) => q.id)).toEqual([9]);
  });

  it('hides hierarchy when the parent API is absent', () => {
    const api = service as OrganizationService & {
      loadHierarchy?: (id: number) => void;
      hierarchyAvailable?: () => boolean;
    };
    expect(typeof api.loadHierarchy).toBe('function');
    api.loadHierarchy!(7);
    const req = httpMock.expectOne('/api/v1/organizations/7/hierarchy');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    expect(api.hierarchyAvailable!()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('shows hierarchy when the parent API returns a tree', () => {
    const api = service as OrganizationService & {
      loadHierarchy?: (id: number) => void;
      hierarchyAvailable?: () => boolean;
      hierarchy?: () => { parent: { id: number; name: string } | null; children: { name: string }[] } | null;
    };
    expect(typeof api.loadHierarchy).toBe('function');
    api.loadHierarchy!(7);
    const req = httpMock.expectOne('/api/v1/organizations/7/hierarchy');
    req.flush({
      parent: { id: 1, name: 'Root' },
      ancestors: [{ id: 1, name: 'Root' }],
      children: [{ id: 3, name: 'Sub' }],
    });
    expect(api.hierarchyAvailable!()).toBe(true);
    expect(api.hierarchy!()?.parent?.name).toBe('Root');
    expect(api.hierarchy!()?.children[0].name).toBe('Sub');
  });

  it('linkParent surfaces an actionable cycle error', () => {
    const api = service as OrganizationService & {
      linkParent?: (id: number, parentId: number) => void;
      hierarchyError?: () => string | null;
    };
    expect(typeof api.linkParent).toBe('function');
    api.linkParent!(7, 7);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/organizations/7/parent' && r.method === 'PUT',
    );
    expect(req.request.body).toEqual({ parentId: 7 });
    req.flush(
      { message: 'Hierarchy cycle detected', errorCode: 'HIERARCHY_CYCLE' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(api.hierarchyError!()).toContain('This parent would create a cycle');
  });

  it('hides merge candidates when the merge API is absent', () => {
    const api = service as OrganizationService & {
      loadMergeCandidates?: (id: number) => void;
      mergeAvailable?: () => boolean;
    };
    expect(typeof api.loadMergeCandidates).toBe('function');
    api.loadMergeCandidates!(7);
    const req = httpMock.expectOne('/api/v1/organizations/7/merge-candidates');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    expect(api.mergeAvailable!()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('lists merge candidates when the merge API responds', () => {
    const api = service as OrganizationService & {
      loadMergeCandidates?: (id: number) => void;
      mergeAvailable?: () => boolean;
      mergeCandidates?: () => { id: number; name: string }[] | null;
    };
    expect(typeof api.loadMergeCandidates).toBe('function');
    api.loadMergeCandidates!(7);
    const req = httpMock.expectOne('/api/v1/organizations/7/merge-candidates');
    req.flush([{ id: 4, name: 'Dup Co', reason: 'Same domain' }]);
    expect(api.mergeAvailable!()).toBe(true);
    expect(api.mergeCandidates!()?.[0].name).toBe('Dup Co');
  });
});

function deal(
  partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name'>,
): DealDto {
  return {
    uuid: `d-${partial.id}`,
    stage: 'PROSPECTING',
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

function quote(
  partial: Partial<QuoteDto> & Pick<QuoteDto, 'id' | 'name' | 'customerExternalId'>,
): QuoteDto {
  return {
    uuid: `q-${partial.id}`,
    status: 'DRAFT',
    durationInMonths: 12,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}
