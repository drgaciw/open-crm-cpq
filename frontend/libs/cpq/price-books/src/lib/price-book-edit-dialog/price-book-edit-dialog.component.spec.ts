import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
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
    expirationDate: '2099-12-31',
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

  it('rejects a currency that is not three letters, with a visible message (H-CPQ-PR-011)', () => {
    const control = component.form.controls['currency'];
    for (const bad of ['US', 'U5D', '1234']) {
      control.setValue(bad);
      control.markAsTouched();
      expect(control.valid).toBe(false);
    }
    control.setValue('US');
    control.markAsTouched();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Currency must be 3 letters');
    for (const good of ['usd', 'EUR', '']) {
      control.setValue(good);
      expect(control.valid).toBe(true);
    }
  });

  it('prefills form from the book', () => {
    expect(component.form.getRawValue()).toEqual({
      name: 'APAC Promo',
      region: 'APAC',
      currency: 'USD',
      isDefault: false,
      effectiveDate: '',
      expirationDate: '2099-12-31',
    });
  });

  it('blocks a whitespace-only name before sending the update request', () => {
    updateSpy.mockReturnValue(of(existingBook));
    component.form.patchValue({ name: '   ' });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.form.invalid).toBe(true);
    expect(updateSpy).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Name is required');
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

  it('sends explicit clears for persisted region and expiration date', () => {
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
      clearRegion: true,
      clearExpirationDate: true,
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

  it('keeps Save and its progress indicator named during the pending update', () => {
    const pending = new Subject<PriceBookDto>();
    updateSpy.mockReturnValueOnce(pending);
    component.form.patchValue({ region: '', expirationDate: '' });

    component.onSubmit();
    fixture.detectChanges();

    const view = fixture.nativeElement as HTMLElement;
    const save = view.querySelector<HTMLButtonElement>('button.save-btn');
    expect(save?.disabled).toBe(true);
    expect(save?.getAttribute('aria-label')).toBe('Save');
    expect(save?.querySelector('mat-spinner')?.getAttribute('aria-label')).toBe('Saving price book');
    expect(updateSpy).toHaveBeenCalledWith(3, expect.objectContaining({
      clearRegion: true,
      clearExpirationDate: true,
    }));
    expect(dialogClose).not.toHaveBeenCalled();

    pending.next(updatedBook);
    pending.complete();
    expect(updateSpy).toHaveBeenCalledOnce();
    expect(dialogClose).toHaveBeenCalledWith(updatedBook);
  });

});
