import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrganizationDetailComponent } from './organization-detail.component';
import { OrganizationService } from '../organization.service';
import { DealDto, OrganizationDto, QuoteDto } from '@frontend/shared-models';

describe('OrganizationDetailComponent account UX', () => {
  let fixture: ComponentFixture<OrganizationDetailComponent>;
  let httpMock: HttpTestingController;

  const account: OrganizationDto = {
    id: 7,
    uuid: 'uuid-7',
    name: 'Acme Account',
    industry: 'Technology',
    territory: 'West',
    ownerId: 'ada',
    createdAt: '',
    updatedAt: '',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrganizationDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        OrganizationService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrganizationDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
  }, 30000);

  afterEach(() => {
    httpMock.verify();
  });

  it('links related contacts, deals, and quotes without dead ends', () => {
    flushAccount();
    const deals = httpMock.expectOne(
      (r) => r.url === '/api/v1/deals' && r.params.get('organizationId') === '7',
    );
    deals.flush({
      content: [
        {
          id: 3,
          uuid: 'd-3',
          name: 'Renewal',
          organizationId: 7,
          stage: 'PROPOSAL',
          createdAt: '',
          updatedAt: '',
        } satisfies DealDto,
      ],
    });
    const quotes = httpMock.expectOne(
      (r) => r.url === '/api/v1/quotes' && r.params.get('customerExternalId') === 'uuid-7',
    );
    quotes.flush({
      content: [
        {
          id: 9,
          uuid: 'q-9',
          name: 'Platform quote',
          customerExternalId: 'uuid-7',
          status: 'DRAFT',
          durationInMonths: 12,
          createdAt: '',
          updatedAt: '',
        } satisfies QuoteDto,
      ],
    });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush(
      { message: 'Not found' },
      { status: 404, statusText: 'Not Found' },
    );
    httpMock.expectOne('/api/v1/organizations/7/merge-candidates').flush(
      { message: 'Not found' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="account-hierarchy"]')).toBeNull();
    expect(el.querySelector('[data-testid="account-merge"]')).toBeNull();

    const dealLink = el.querySelector('a[href="/deals/3"]');
    const quoteLink = el.querySelector('a[href="/quotes/9"]');
    const browseDeals = el.querySelector('a[href="/deals"]');
    const browseQuotes = el.querySelector('a[href="/quotes"]');
    expect(dealLink?.textContent).toContain('Renewal');
    expect(quoteLink?.textContent).toContain('Platform quote');
    expect(browseDeals).toBeTruthy();
    expect(browseQuotes).toBeTruthy();
    expect(el.textContent).toContain('Archive');
  });

  it('navigates hierarchy when the parent API is present and explains cycles', () => {
    flushAccount();
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush({
      parent: { id: 1, name: 'Root' },
      ancestors: [{ id: 1, name: 'Root' }],
      children: [{ id: 3, name: 'Sub' }],
    });
    httpMock.expectOne('/api/v1/organizations/7/merge-candidates').flush([
      { id: 4, name: 'Dup Co', reason: 'Same domain' },
    ]);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const hierarchy = el.querySelector('[data-testid="account-hierarchy"]');
    expect(hierarchy).toBeTruthy();
    expect(hierarchy?.querySelector('a[href="/organizations/1"]')?.textContent).toContain('Root');
    expect(hierarchy?.querySelector('a[href="/organizations/3"]')?.textContent).toContain('Sub');

    const merge = el.querySelector('[data-testid="account-merge"]');
    expect(merge?.textContent).toContain('Dup Co');
    expect(merge?.querySelector('a[href="/organizations/4"]')).toBeTruthy();

    const parentInput = el.querySelector(
      '[data-testid="account-parent-id"]',
    ) as HTMLInputElement;
    parentInput.value = '7';
    parentInput.dispatchEvent(new Event('input'));
    (el.querySelector('[data-testid="account-set-parent"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const put = httpMock.expectOne(
      (r) => r.url === '/api/v1/organizations/7/parent' && r.method === 'PUT',
    );
    expect(put.request.body).toEqual({ parentId: 7 });
    put.flush(
      { message: 'Hierarchy cycle detected', errorCode: 'HIERARCHY_CYCLE' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="hierarchy-error"]')?.textContent).toContain(
      'This parent would create a cycle',
    );
  });

  function flushAccount(): void {
    httpMock.expectOne('/api/v1/organizations/7').flush(account);
    httpMock
      .expectOne((r) => r.url === '/api/v1/organizations/7/contacts')
      .flush({ content: [] });
    fixture.detectChanges();
  }
});
