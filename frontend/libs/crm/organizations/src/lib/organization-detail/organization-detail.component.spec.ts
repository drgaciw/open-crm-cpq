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
    uuid: '550e8400-e29b-41d4-a716-446655440000',
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

  it('loads the new account and clears stale data when the id signal input changes', () => {
    const orgService = TestBed.inject(OrganizationService);
    const getByIdSpy = vi.spyOn(orgService, 'getById');
    const oldAccount = httpMock.expectOne('/api/v1/organizations/7');
    const oldContacts = httpMock.expectOne((r) => r.url === '/api/v1/organizations/7/contacts');
    const oldDeals = httpMock.expectOne((r) => r.url === '/api/v1/deals');
    const oldHierarchy = httpMock.expectOne('/api/v1/organizations/7/hierarchy');
    getByIdSpy.mockClear();
    fixture.componentInstance.parentIdDraft.set('42');
    fixture.componentRef.setInput('id', '8');
    fixture.detectChanges();
    expect(getByIdSpy).toHaveBeenCalledWith(8);
    expect(fixture.componentInstance.parentIdDraft()).toBe('');
    fixture.componentInstance.onSetParent();
    httpMock.expectNone((request) => request.method === 'PUT' && request.url === '/api/v1/organizations/8/parent');
    expect(oldAccount.cancelled).toBe(true);
    expect(oldContacts.cancelled).toBe(true);
    expect(oldDeals.cancelled).toBe(true);
    expect(oldHierarchy.cancelled).toBe(true);
    httpMock.expectOne('/api/v1/organizations/8').flush({ ...account, id: 8, uuid: '550e8400-e29b-41d4-a716-446655440001', name: 'Other' });
    httpMock.expectOne((r) => r.url === '/api/v1/organizations/8/contacts').flush({ content: [] });
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/8/hierarchy').flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Other');
    expect(fixture.nativeElement.textContent).not.toContain('Acme Account');
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
      (r) => r.url === '/api/v1/quotes' && r.params.get('customerExternalId') === '550e8400-e29b-41d4-a716-446655440000',
    );
    quotes.flush({
      content: [
        {
          id: 9,
          uuid: 'q-9',
          name: 'Platform quote',
          customerExternalId: '550e8400-e29b-41d4-a716-446655440000',
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
    TestBed.inject(OrganizationService).relatedQuotesTruncated.set(true);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="account-quotes"]')?.textContent).toContain('Showing up to 250 matching quotes');
    expect(el.querySelector('[data-testid="account-quotes"]')?.textContent).toContain('Some quotes with older or differently formatted customer references may not appear');
    expect(browseDeals).toBeTruthy();
    expect(browseQuotes).toBeTruthy();
    expect(el.textContent).toContain('Archive');
  });

  it('does not fetch or expose merge without the server capability contract', () => {
    flushAccount();
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectNone('/api/v1/organizations/7/merge-candidates');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="account-merge"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="account-quotes"]')?.textContent).toContain('No quotes found for this account ID.');
    httpMock.expectNone((request) => request.method === 'POST' && request.url.endsWith('/merge'));
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
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const hierarchy = el.querySelector('[data-testid="account-hierarchy"]');
    expect(hierarchy).toBeTruthy();
    expect(hierarchy?.querySelector('a[href="/organizations/1"]')?.textContent).toContain('Root');
    expect(hierarchy?.querySelector('a[href="/organizations/3"]')?.textContent).toContain('Sub');

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

  it('explains why an account with children cannot be archived', () => {
    flushAccount();
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush({ parent: null, ancestors: [], children: [] });
    fixture.detectChanges();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    fixture.componentInstance.onDelete();
    httpMock.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/organizations/7')
      .flush({ message: 'This account has active child accounts. Hierarchy management is unavailable; contact an administrator', errorCode: 'HIERARCHY_HAS_CHILDREN' },
        { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('This account has active child accounts. Hierarchy management is unavailable; contact an administrator');
    confirm.mockRestore();
  });

  it('UI-CRM-SLDS: uses shared record page header with breadcrumb and highlights', async () => {
    flushAccount();
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush(
      { message: 'Not found' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-page-header')).toBeTruthy();
    expect(el.querySelector('.detail-header')).toBeNull();
    expect(el.querySelector('nav[aria-label="Breadcrumb"]')?.textContent).toContain('Accounts');
    expect(el.querySelector('.page-header-highlights')?.textContent).toContain('Territory');
    expect(el.querySelector('h1.page-header-title')?.textContent?.trim()).toBe('Acme Account');
  });

  it('does not update state or show an error when a delete request fails after the component is destroyed', () => {
    flushAccount();
    httpMock.expectOne((r) => r.url === '/api/v1/deals').flush({ content: [] });
    httpMock.expectOne((r) => r.url === '/api/v1/quotes').flush({ content: [] });
    httpMock.expectOne('/api/v1/organizations/7/hierarchy').flush(
      { message: 'Not found' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const orgService = TestBed.inject(OrganizationService);
    const errorBefore = orgService.error();
    const selectedIdBefore = orgService.selectedOrganization()?.id;
    const loadingBefore = orgService.loading();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    try {
      fixture.componentInstance.onDelete();
      const pendingDelete = httpMock.expectOne(
        (r) => r.method === 'DELETE' && r.url === '/api/v1/organizations/7',
      );
      fixture.destroy();
      if (!pendingDelete.cancelled) {
        pendingDelete.flush(
          { message: 'Archive failed', errorCode: 'ARCHIVE_FAILED' },
          { status: 500, statusText: 'Server Error' },
        );
      }
      expect(orgService.error()).toBe(errorBefore);
      expect(orgService.error()).toBeNull();
      expect(orgService.selectedOrganization()?.id).toBe(selectedIdBefore);
      expect(orgService.loading()).toBe(loadingBefore);
      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Could not archive this account');
      expect(fixture.nativeElement.textContent).not.toContain('Archive failed');
    } finally {
      confirm.mockRestore();
    }
  });

  function flushAccount(): void {
    httpMock.expectOne('/api/v1/organizations/7').flush(account);
    httpMock
      .expectOne((r) => r.url === '/api/v1/organizations/7/contacts')
      .flush({ content: [] });
    fixture.detectChanges();
  }
});
