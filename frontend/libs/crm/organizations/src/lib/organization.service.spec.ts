import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrganizationService } from './organization.service';
import { OrganizationDto, SpringPageResponse } from '@frontend/shared-models';

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
});
