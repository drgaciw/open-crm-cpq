import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import {
  PriceBookEntryDialogComponent,
  PriceBookEntryDialogData,
} from './price-book-entry-dialog.component';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import {
  PriceBookEntryCreateRequest,
  PriceBookEntryDto,
  PriceBookEntryUpdateRequest,
} from '@frontend/shared-models';

describe('PriceBookEntryDialogComponent', () => {
  let fixture: ComponentFixture<PriceBookEntryDialogComponent>;
  let component: PriceBookEntryDialogComponent;
  let createEntrySpy: ReturnType<typeof vi.fn>;
  let updateEntrySpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  const createdEntry: PriceBookEntryDto = {
    id: 99,
    uuid: 'pbe-uuid-99',
    priceBookId: 3,
    productExternalId: 'NEW-SKU',
    listPrice: 150,
    floorPrice: 100,
    currency: 'USD',
    createdAt: '',
    updatedAt: '',
  };

  async function setup(data: PriceBookEntryDialogData): Promise<void> {
    createEntrySpy = vi.fn();
    updateEntrySpy = vi.fn();
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [PriceBookEntryDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: PriceBookService,
          useValue: {
            createEntry: createEntrySpy,
            updateEntry: updateEntrySpy,
          },
        },
        { provide: MatDialogRef, useValue: { close: dialogClose } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookEntryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('requires productExternalId and listPrice on create', async () => {
    await setup({ bookId: 3, defaultCurrency: 'USD' });
    expect(component.isEdit).toBe(false);
    expect(component.form.valid).toBe(false);
    component.form.patchValue({
      productExternalId: 'SKU-1',
      listPrice: 10,
    });
    expect(component.form.valid).toBe(true);
  });

  it('creates via PriceBookService.createEntry and closes with the entry', async () => {
    await setup({ bookId: 3, defaultCurrency: 'usd' });
    createEntrySpy.mockReturnValue(of(createdEntry));
    component.form.patchValue({
      productExternalId: '  NEW-SKU  ',
      listPrice: 150,
      floorPrice: 100,
      currency: 'usd',
    });

    component.onSubmit();

    expect(createEntrySpy).toHaveBeenCalledWith(3, {
      productExternalId: 'NEW-SKU',
      listPrice: 150,
      floorPrice: 100,
      currency: 'USD',
    } satisfies PriceBookEntryCreateRequest);
    expect(dialogClose).toHaveBeenCalledWith(createdEntry);
    expect(component.saving()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it.each([
    { mode: 'create', action: 'Create', label: 'Creating price book entry' },
    { mode: 'edit', action: 'Save', label: 'Saving price book entry' },
  ])('names the disabled action and progress while $mode is pending', async ({ mode, action, label }) => {
    const isEdit = mode === 'edit';
    await setup({ bookId: 3, entry: isEdit ? createdEntry : undefined });
    const pending = new Subject<PriceBookEntryDto>();
    const request = isEdit ? updateEntrySpy : createEntrySpy;
    request.mockReturnValue(pending);
    component.form.patchValue({
      productExternalId: ' NEW-SKU ',
      listPrice: 150,
      floorPrice: 0,
      currency: 'gbp',
    });
    fixture.detectChanges();

    const view = fixture.nativeElement as HTMLElement;
    const [cancel, submit] = Array.from(view.querySelectorAll<HTMLButtonElement>('mat-dialog-actions button'));
    expect(submit.textContent?.trim()).toBe(action);
    submit.click();
    fixture.detectChanges();

    expect(cancel.disabled).toBe(true);
    expect(submit.disabled).toBe(true);
    expect(submit.getAttribute('aria-label')).toBe(action);
    expect(view.querySelector('mat-dialog-actions mat-spinner')?.getAttribute('aria-label')).toBe(label);
    expect(request).toHaveBeenCalledTimes(1);
    expect(dialogClose).not.toHaveBeenCalled();

    pending.next(createdEntry);
    pending.complete();

    expect(dialogClose).toHaveBeenCalledWith(createdEntry);
  });

  it('omits optional floorPrice and currency when empty on create', async () => {
    await setup({ bookId: 3 });
    createEntrySpy.mockReturnValue(of(createdEntry));
    component.form.patchValue({
      productExternalId: 'MINIMAL',
      listPrice: 42,
      floorPrice: null,
      currency: '',
    });

    component.onSubmit();

    expect(createEntrySpy).toHaveBeenCalledWith(3, {
      productExternalId: 'MINIMAL',
      listPrice: 42,
    });
  });

  it('updates via PriceBookService.updateEntry in edit mode', async () => {
    await setup({
      bookId: 3,
      entry: {
        id: 11,
        uuid: 'pbe-11',
        priceBookId: 3,
        productExternalId: 'OLD',
        listPrice: 100,
        floorPrice: 80,
        currency: 'USD',
        createdAt: '',
        updatedAt: '',
      },
    });
    updateEntrySpy.mockReturnValue(of(createdEntry));
    expect(component.isEdit).toBe(true);
    expect(component.form.getRawValue().productExternalId).toBe('OLD');

    component.form.patchValue({
      productExternalId: 'UPDATED',
      listPrice: 200,
      floorPrice: 150,
      currency: 'eur',
    });
    component.onSubmit();

    expect(updateEntrySpy).toHaveBeenCalledWith(3, 11, {
      productExternalId: 'UPDATED',
      listPrice: 200,
      floorPrice: 150,
      currency: 'EUR',
    } satisfies PriceBookEntryUpdateRequest);
    expect(dialogClose).toHaveBeenCalledWith(createdEntry);
  });

  it('surfaces DUPLICATE_PRICE_BOOK_ENTRY in the error banner', async () => {
    await setup({ bookId: 3 });
    createEntrySpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'Product NEW-SKU already exists in this price book',
          errorCode: 'DUPLICATE_PRICE_BOOK_ENTRY',
        },
      })),
    );
    component.form.patchValue({ productExternalId: 'NEW-SKU', listPrice: 10 });

    component.onSubmit();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toContain('DUPLICATE_PRICE_BOOK_ENTRY');
    expect(component.error()).toContain('already exists');
    expect(component.saving()).toBe(false);
  });

  it('surfaces OVERLAPPING_PRICE_BOOK_ENTRY_RANGE, the code the API now uses for a duplicate (UAT 2026-11-14)', async () => {
    await setup({ bookId: 3 });
    createEntrySpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'An entry for SKU NEW-SKU / USD / EACH already covers an overlapping effective date range [open-ended..open-ended]',
          errorCode: 'OVERLAPPING_PRICE_BOOK_ENTRY_RANGE',
        },
      })),
    );
    component.form.patchValue({ productExternalId: 'NEW-SKU', listPrice: 10 });

    component.onSubmit();

    expect(component.error()).toMatch(/\[OVERLAPPING_PRICE_BOOK_ENTRY_RANGE\]$/);
  });

  it('surfaces PRICE_BOOK_PUBLISHED_IMMUTABLE in the error banner', async () => {
    await setup({
      bookId: 1,
      entry: {
        id: 11,
        uuid: 'pbe-11',
        priceBookId: 1,
        productExternalId: 'CRM-SEAT-NAMED',
        listPrice: 120,
        createdAt: '',
        updatedAt: '',
      },
    });
    updateEntrySpy.mockReturnValue(
      throwError(() => ({
        errorCode: 'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        message: 'Published price books and their entries are immutable',
      })),
    );

    component.onSubmit();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
    expect(component.error()).toContain('immutable');
  });
});
