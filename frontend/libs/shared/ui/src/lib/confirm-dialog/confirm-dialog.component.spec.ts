import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let component: ComponentFixture<ConfirmDialogComponent>;
  let mockDialogRef: MatDialogRef<ConfirmDialogComponent>;
  let mockData: ConfirmDialogData;

  beforeEach(async () => {
    mockDialogRef = { close: vi.fn() } as unknown as MatDialogRef<ConfirmDialogComponent>;
    mockData = {
      title: 'Confirm Action',
      message: 'Are you sure you want to proceed?',
      confirmText: 'Yes',
      cancelText: 'No',
      confirmColor: 'primary',
    };

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
      ],
    }).compileComponents();

    component = TestBed.createComponent(ConfirmDialogComponent);
    component.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display title from dialog data', () => {
    const title = component.nativeElement.querySelector('.dialog-title');
    expect(title.textContent).toContain('Confirm Action');
  });

  it('should display message from dialog data', () => {
    const message = component.nativeElement.querySelector('.dialog-message');
    expect(message.textContent).toContain('Are you sure you want to proceed?');
  });

  it('should display custom confirm button text', () => {
    const confirmButton = component.nativeElement.querySelector('.confirm-button');
    expect(confirmButton.textContent?.trim()).toBe('Yes');
  });

  it('should display custom cancel button text', () => {
    const cancelButton = component.nativeElement.querySelector('.cancel-button');
    expect(cancelButton.textContent?.trim()).toBe('No');
  });

  it('should use default confirm text when not provided', async () => {
    mockData = {
      title: 'Confirm',
      message: 'Proceed?',
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
      ],
    }).compileComponents();

    component = TestBed.createComponent(ConfirmDialogComponent);
    component.detectChanges();

    const confirmButton = component.nativeElement.querySelector('.confirm-button');
    expect(confirmButton.textContent?.trim()).toBe('Confirm');
  });

  it('should close dialog with true when confirm is clicked', () => {
    component.componentInstance.onConfirm();
    expect(mockDialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should close dialog with false when cancel is clicked', () => {
    component.componentInstance.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith(false);
  });

  it('should apply confirm button color from data', () => {
    const confirmButton = component.nativeElement.querySelector('.confirm-button');
    expect(component.componentInstance['data'].confirmColor || 'primary').toBe('primary');
  });

  it('should handle warn color for dangerous actions', async () => {
    mockData = {
      title: 'Delete Item',
      message: 'This cannot be undone.',
      confirmText: 'Delete',
      confirmColor: 'warn',
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
      ],
    }).compileComponents();

    component = TestBed.createComponent(ConfirmDialogComponent);
    component.detectChanges();

    const confirmButton = component.nativeElement.querySelector('.confirm-button');
    expect(component.componentInstance['data'].confirmColor).toBe('warn');
  });

  it('should set type="button" on cancel and confirm buttons', () => {
    const cancelButton = component.nativeElement.querySelector('.cancel-button') as HTMLButtonElement;
    const confirmButton = component.nativeElement.querySelector('.confirm-button') as HTMLButtonElement;
    expect(cancelButton.getAttribute('type')).toBe('button');
    expect(confirmButton.getAttribute('type')).toBe('button');
  });

  it('should put cdkFocusInitial on the confirm button', () => {
    const confirmButton = component.nativeElement.querySelector('.confirm-button') as HTMLButtonElement;
    expect(confirmButton.hasAttribute('cdkFocusInitial')).toBe(true);
  });

  it('should trap focus in the dialog overlay region', () => {
    const root = component.nativeElement.querySelector('.confirm-dialog') as HTMLElement;
    expect(root.hasAttribute('cdkTrapFocus')).toBe(true);
  });

  it('should associate dialog with title and message via aria-labelledby and aria-describedby', () => {
    const root = component.nativeElement.querySelector('.confirm-dialog') as HTMLElement;
    const title = component.nativeElement.querySelector('.dialog-title') as HTMLElement;
    const message = component.nativeElement.querySelector('.dialog-message') as HTMLElement;
    const labelledBy = root.getAttribute('aria-labelledby');
    const describedBy = root.getAttribute('aria-describedby');
    expect(labelledBy).toBeTruthy();
    expect(title.id).toBe(labelledBy);
    expect(describedBy).toBeTruthy();
    expect(message.id).toBe(describedBy);
  });
});
