import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { QuoteRejectDialogComponent } from './quote-reject-dialog.component';

describe('QuoteRejectDialogComponent (H-CPQ-Q-005)', () => {
  let close: ReturnType<typeof vi.fn>;
  let component: QuoteRejectDialogComponent;

  beforeEach(async () => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [QuoteRejectDialogComponent],
      providers: [provideNoopAnimations(), { provide: MatDialogRef, useValue: { close } }],
    }).compileComponents();
    component = TestBed.createComponent(QuoteRejectDialogComponent).componentInstance;
  });

  it('closes with the trimmed optional reason', () => {
    component.form.setValue({ reason: '  No budget  ' });
    component.onSubmit();
    expect(close).toHaveBeenCalledWith({ reason: 'No budget' });
  });

  it('allows rejecting without a reason', () => {
    component.form.setValue({ reason: '   ' });
    component.onSubmit();
    expect(close).toHaveBeenCalledWith({ reason: '' });
  });
});
