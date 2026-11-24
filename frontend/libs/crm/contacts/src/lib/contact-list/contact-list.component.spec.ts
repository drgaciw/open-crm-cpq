import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of } from 'rxjs';
import { ContactListComponent } from './contact-list.component';
import { ContactService } from '../contact.service';
import { ContactDto, PaginationMeta } from '@frontend/shared-models';

function contact(partial: Partial<ContactDto> & Pick<ContactDto, 'id' | 'firstName' | 'lastName'>): ContactDto {
  return {
    uuid: `u-${partial.id}`,
    email: partial.email ?? 'a@example.com',
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('ContactListComponent shared-ui wiring', () => {
  let fixture: ComponentFixture<ContactListComponent>;
  let component: ContactListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let searchSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let dialogOpenSpy: ReturnType<typeof vi.fn>;

  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const contacts = signal<ContactDto[]>([]);
  const loading = signal(false);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    listSpy = vi.fn();
    searchSpy = vi.fn();
    deleteSpy = vi.fn(() => of(void 0));
    dialogOpenSpy = vi.fn(() => ({ afterClosed: () => of(undefined) }));
    error.set(null);
    contacts.set([]);
    loading.set(false);

    await TestBed.configureTestingModule({
      imports: [ContactListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: ContactService,
          useValue: {
            contacts,
            loading,
            error,
            pagination,
            hasConfirmedPage: signal(false),
            isEmpty: computed(() => contacts().length === 0 && !loading()),
            list: listSpy,
            search: searchSpy,
            delete: deleteSpy,
          },
        },
        {
          provide: MatDialog,
          useValue: { open: dialogOpenSpy },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ngOnInit loads the contact list', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'lastName,asc');
  });

  it('onSearch with term calls ContactService.search', () => {
    component.onSearch('  ada  ');
    expect(searchSpy).toHaveBeenCalledWith('ada');
  });

  it('onSearch with blank term reloads list', () => {
    listSpy.mockClear();
    component.onSearch('   ');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'lastName,asc');
    expect(searchSpy).not.toHaveBeenCalled();
  });

  it('onDelete uses ConfirmDialog then deletes', () => {
    dialogOpenSpy.mockReturnValueOnce({ afterClosed: () => of(true) });
    listSpy.mockClear();

    component.onDelete(contact({ id: 12, firstName: 'Ada', lastName: 'Lovelace' }));

    expect(dialogOpenSpy).toHaveBeenCalled();
    expect(deleteSpy).toHaveBeenCalledWith(12);
    expect(listSpy).toHaveBeenCalled();
  });

  it('onDelete does nothing when confirm is cancelled', () => {
    dialogOpenSpy.mockReturnValueOnce({ afterClosed: () => of(false) });
    component.onDelete(contact({ id: 12, firstName: 'Ada', lastName: 'Lovelace' }));

    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('shows error banner and hides empty-state when load fails', () => {
    error.set('List failed [CONTACT_LIST_DENIED]');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.error-banner[role="alert"]');
    expect(banner?.textContent).toContain('List failed [CONTACT_LIST_DENIED]');
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeNull();
  });

  it('clearError dismisses the banner', () => {
    error.set('List failed [CONTACT_LIST_DENIED]');
    fixture.detectChanges();

    component.clearError();
    fixture.detectChanges();

    expect(error()).toBeNull();
    expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
  });

  it('retry reloads the list', () => {
    listSpy.mockClear();
    error.set('List failed [CONTACT_LIST_DENIED]');
    component.retry();
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'lastName,asc');
  });

  // Found by the Jev + Playwright MCP UAT (2026-11-15): every row menu was named "Contact actions".
  it('names each row actions button after its contact', () => {
    contacts.set([
      contact({ id: 1, firstName: 'Ada', lastName: 'Lovelace' }),
      contact({ id: 2, firstName: 'Alan', lastName: 'Turing' }),
    ]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();
    const labels = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button[aria-label^="Actions for"]'),
    ).map((b) => b.getAttribute('aria-label'));
    expect(labels).toEqual(['Actions for Ada Lovelace', 'Actions for Alan Turing']);
  });
});

describe('ContactListComponent list result messaging', () => {
  let fixture: ComponentFixture<ContactListComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  const ada = contact({ id: 1, firstName: 'Ada', lastName: 'Lovelace' });
  const page = (content: ContactDto[]) => ({
    content, number: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ContactListComponent],
      providers: [provideRouter([]), provideNoopAnimations(), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ContactListComponent);
    http = TestBed.inject(HttpTestingController);
    host = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows no matching contacts and keeps the header action when a search is empty', () => {
    http.expectOne((req) => req.url === '/api/v1/contacts').flush(page([ada]));
    fixture.detectChanges();
    expect(host.querySelector('app-data-table')?.textContent).toContain('Ada Lovelace');

    fixture.componentInstance.onSearch('  unmatched  ');
    http.expectOne((req) => req.url === '/api/v1/contacts/search' && req.params.get('q') === 'unmatched')
      .flush(page([]));
    fixture.detectChanges();

    const card = host.querySelector('app-empty-state');
    expect(card?.textContent).toContain('No matching contacts');
    expect(card?.querySelector('button')).toBeNull();
    expect(host.querySelector('app-page-header')?.textContent).toContain('New Contact');
  });

  it('does not show an empty dataset after dismissing a failed initial request', () => {
    http.expectOne((req) => req.url === '/api/v1/contacts')
      .flush({ message: 'Contacts unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    const dismiss = Array.from(host.querySelectorAll<HTMLButtonElement>('[role="alert"] button'))
      .find((button) => button.textContent?.trim() === 'Dismiss');
    expect(dismiss).toBeTruthy();
    dismiss?.click();
    fixture.detectChanges();

    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(host.querySelector('app-empty-state')).toBeNull();
    expect(host.querySelector('app-data-table')).toBeNull();
    expect(host.querySelector('app-page-header')?.textContent).toContain('New Contact');
  });

  it('preserves previously loaded contacts when a search failure is dismissed', () => {
    http.expectOne((req) => req.url === '/api/v1/contacts').flush(page([ada]));
    fixture.componentInstance.onSearch('new search');
    http.expectOne((req) => req.url === '/api/v1/contacts/search')
      .flush({ message: 'Search unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    fixture.componentInstance.clearError();
    fixture.detectChanges();

    expect(host.querySelector('app-data-table')?.textContent).toContain('Ada Lovelace');
    expect(host.querySelector('app-empty-state')).toBeNull();
  });

  it('offers the first-contact action after the cleared list returns an empty page', () => {
    http.expectOne((req) => req.url === '/api/v1/contacts').flush(page([]));
    fixture.componentInstance.onSearch('unmatched');
    http.expectOne((req) => req.url === '/api/v1/contacts/search').flush(page([]));
    fixture.componentInstance.onSearch('   ');
    http.expectOne((req) => req.url === '/api/v1/contacts').flush(page([]));
    fixture.detectChanges();

    const card = host.querySelector('app-empty-state');
    expect(card?.textContent).toContain('No contacts yet');
    expect(card?.querySelector('button')?.textContent?.trim()).toBe('Create Contact');
  });
});
