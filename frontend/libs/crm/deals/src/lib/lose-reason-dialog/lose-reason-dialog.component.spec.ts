import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { DealLoseReasonDialogComponent } from './lose-reason-dialog.component';

describe('DealLoseReasonDialogComponent (H-CRM-018)', () => {
  let close: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<DealLoseReasonDialogComponent>;
  let component: DealLoseReasonDialogComponent;

  beforeEach(async () => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [DealLoseReasonDialogComponent],
      providers: [provideNoopAnimations(), { provide: MatDialogRef, useValue: { close } }],
    }).compileComponents();
    fixture = TestBed.createComponent(DealLoseReasonDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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

  it('applies cdkTextareaAutosize with min/max rows to the reason textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(8);
  });
});
