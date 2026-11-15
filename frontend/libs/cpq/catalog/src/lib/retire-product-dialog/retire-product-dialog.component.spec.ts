import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RetireProductDialogComponent } from './retire-product-dialog.component';

describe('RetireProductDialogComponent (DRG-755)', () => {
  let close: ReturnType<typeof vi.fn>;
  let component: RetireProductDialogComponent;

  beforeEach(async () => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [RetireProductDialogComponent],
      providers: [provideNoopAnimations(), { provide: MatDialogRef, useValue: { close } }],
    }).compileComponents();
    component = TestBed.createComponent(RetireProductDialogComponent).componentInstance;
  });

  it('requires a reason that is not only whitespace (the API requires one)', () => {
    component.form.setValue({ reason: '   ' });
    expect(component.form.get('reason')?.hasError('required')).toBe(true);
    component.onSubmit();
    expect(close).not.toHaveBeenCalled();
  });

  it('closes with the trimmed reason', () => {
    component.form.setValue({ reason: '  End of life  ' });
    component.onSubmit();
    expect(close).toHaveBeenCalledWith('End of life');
  });
});
