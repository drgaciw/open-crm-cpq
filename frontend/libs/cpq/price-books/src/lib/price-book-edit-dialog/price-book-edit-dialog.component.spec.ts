import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { PriceBookEditDialogComponent } from './price-book-edit-dialog.component';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import {
  PriceBookDto,
  PriceBookUpdateRequest,
} from '@frontend/shared-models';

describe('PriceBookEditDialogComponent', () => {
  let fixture: ComponentFixture<PriceBookEditDialogComponent>;
  let component: PriceBookEditDialogComponent;
  let updateSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  const existingBook: PriceBookDto = {
    id: 3,
    uuid: 'pb-uuid-3',
    name: 'APAC Promo',
    region: 'APAC',
    currency: 'USD',
    isDefault: false,
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z',
  };

  const updatedBook: PriceBookDto = {
    ...existingBook,
    name: 'APAC Promo Updated',
    region: 'APAC',
    currency: 'USD',
    isDefault: true,
    effectiveDate: '2026-09-15',
    expirationDate: '2026-12-31',
    updatedAt: '2026-08-30T00:00:00Z',
  };

  beforeEach(async () => {
    updateSpy = vi.fn();
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [PriceBookEditDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: PriceBookService, useValue: { update: updateSpy } },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { book: existingBook },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookEditDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('prefills form from the book', () => {
    expect(component.form.getRawValue()).toEqual({
      name: 'APAC Promo',
      region: 'APAC',
      currency: 'USD',
      isDefault: false,
      effectiveDate: '',
      expirationDate: '',
    });
  });

  it('requires name', () => {
    component.form.patchValue({ name: '' });
    expect(component.form.valid).toBe(false);
    component.form.patchValue({ name: 'APAC Promo Updated' });
    expect(component.form.valid).toBe(true);
  });

  it('updates via PriceBookService.update and closes with the book', () => {
    updateSpy.mockReturnValue(of(updatedBook));
    component.form.patchValue({
      name: '  APAC Promo Updated  ',
      region: ' APAC ',
      currency: 'usd',
      isDefault: true,
      effectiveDate: '2026-09-15',
      expirationDate: '2026-12-31',
    });

    component.onSubmit();

    expect(updateSpy).toHaveBeenCalledWith(3, {
      name: 'APAC Promo Updated',
      region: 'APAC',
      currency: 'USD',
      isDefault: true,
      effectiveDate: '2026-09-15',
      expirationDate: '2026-12-31',
    } satisfies PriceBookUpdateRequest);
    expect(dialogClose).toHaveBeenCalledWith(updatedBook);
    expect(component.saving()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('omits empty optional strings but always sends isDefault', () => {
    updateSpy.mockReturnValue(of(updatedBook));
    component.form.patchValue({
      name: 'Minimal',
      region: '',
      currency: '',
      isDefault: false,
      effectiveDate: '',
      expirationDate: '',
    });

    component.onSubmit();

    expect(updateSpy).toHaveBeenCalledWith(3, {
      name: 'Minimal',
      isDefault: false,
    });
  });

  it('shows PRICE_BOOK_PUBLISHED_IMMUTABLE when update fails', () => {
    updateSpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'Published price books are immutable',
          errorCode: 'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        },
      })),
    );
    component.form.patchValue({ name: 'Nope' });

    component.onSubmit();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
    expect(component.saving()).toBe(false);
  });
});
