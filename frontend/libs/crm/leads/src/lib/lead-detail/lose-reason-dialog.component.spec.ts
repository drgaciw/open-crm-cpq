import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { LoseReasonDialogComponent } from './lose-reason-dialog.component';

describe('LoseReasonDialogComponent (H-CRM-017)', () => {
  let close: ReturnType<typeof vi.fn>;
  let component: LoseReasonDialogComponent;

  beforeEach(async () => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [LoseReasonDialogComponent],
      providers: [provideNoopAnimations(), { provide: MatDialogRef, useValue: { close } }],
    }).compileComponents();
    component = TestBed.createComponent(LoseReasonDialogComponent).componentInstance;
  });

  it('rejects a whitespace-only reason', () => {
    component.form.setValue({ reason: '   \n ' });
    expect(component.form.valid).toBe(false);
    expect(component.form.get('reason')?.hasError('required')).toBe(true);
    component.onSubmit();
    expect(close).not.toHaveBeenCalled();
  });

  it('closes with the trimmed reason', () => {
    component.form.setValue({ reason: '  Went with a competitor  ' });
    component.onSubmit();
    expect(close).toHaveBeenCalledWith('Went with a competitor');
  });
});
