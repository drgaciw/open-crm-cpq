import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { PriceBookCreateDialogComponent } from './price-book-create-dialog.component';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import {
  PriceBookCreateRequest,
  PriceBookDto,
} from '@frontend/shared-models';

describe('PriceBookCreateDialogComponent', () => {
  let fixture: ComponentFixture<PriceBookCreateDialogComponent>;
  let component: PriceBookCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  const createdBook: PriceBookDto = {
    id: 42,
    uuid: 'u-42',
    name: 'New NA Book',
    region: 'NA',
    currency: 'USD',
    isDefault: true,
    effectiveDate: '2026-01-01',
    expirationDate: '2026-12-31',
    createdAt: '',
    updatedAt: '',
  };

  beforeEach(async () => {
    createSpy = vi.fn();
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [PriceBookCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: PriceBookService, useValue: { create: createSpy } },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires name', () => {
    expect(component.form.valid).toBe(false);
    component.form.patchValue({ name: 'North America Standard' });
    expect(component.form.valid).toBe(true);
  });

  it('creates via PriceBookService and closes with the book', () => {
    createSpy.mockReturnValue(of(createdBook));
    component.form.patchValue({
      name: '  New NA Book  ',
      region: ' NA ',
      currency: 'usd',
      isDefault: true,
      effectiveDate: '2026-01-01',
      expirationDate: '2026-12-31',
    });

    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'New NA Book',
      region: 'NA',
      currency: 'USD',
      isDefault: true,
      effectiveDate: '2026-01-01',
      expirationDate: '2026-12-31',
    } satisfies PriceBookCreateRequest);
    expect(dialogClose).toHaveBeenCalledWith(createdBook);
    expect(component.saving()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('omits optional fields when empty and isDefault false', () => {
    createSpy.mockReturnValue(of(createdBook));
    component.form.patchValue({
      name: 'Minimal',
      region: '',
      currency: '',
      isDefault: false,
      effectiveDate: '',
      expirationDate: '',
    });

    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'Minimal',
    });
  });

  it('shows error banner when create fails and keeps dialog open', () => {
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Name already exists' } })),
    );
    component.form.patchValue({ name: 'Dup' });

    component.onSubmit();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toBe('Name already exists');
    expect(component.saving()).toBe(false);
  });

  it('appends known errorCode to banner as message [CODE]', () => {
    createSpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'Published price book is immutable',
          errorCode: 'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        },
      })),
    );
    component.form.patchValue({ name: 'Published' });

    component.onSubmit();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toBe(
      'Published price book is immutable [PRICE_BOOK_PUBLISHED_IMMUTABLE]',
    );
    expect(component.saving()).toBe(false);
  });
});
