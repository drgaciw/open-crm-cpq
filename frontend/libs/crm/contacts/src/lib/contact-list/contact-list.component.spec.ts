import { ComponentFixture, TestBed } from '@angular/core/testing';
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
});
