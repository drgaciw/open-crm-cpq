import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { LoseReasonDialogComponent } from './lose-reason-dialog.component';

describe('LoseReasonDialogComponent', () => {
  let fixture: ComponentFixture<LoseReasonDialogComponent>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [LoseReasonDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: dialogClose } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoseReasonDialogComponent);
    fixture.detectChanges();
  });

  it('applies cdkTextareaAutosize with min/max rows to the reason textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(8);
  });

  it('closes with reason when valid', () => {
    fixture.componentInstance.form.patchValue({ reason: 'Budget cut' });
    fixture.componentInstance.onSubmit();
    expect(dialogClose).toHaveBeenCalledWith('Budget cut');
  });
});
