import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let component: ComponentFixture<ConfirmDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent>>;
  let mockData: ConfirmDialogData;

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
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
    const confirmButton =
      component.nativeElement.querySelector('.confirm-button');
    expect(confirmButton.textContent?.trim()).toBe('Yes');
  });

  it('should display custom cancel button text', () => {
    const cancelButton =
      component.nativeElement.querySelector('.cancel-button');
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

    const confirmButton =
      component.nativeElement.querySelector('.confirm-button');
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
    const confirmButton =
      component.nativeElement.querySelector('.confirm-button');
    expect(confirmButton.getAttribute('ng-reflect-color')).toBe('primary');
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

    const confirmButton =
      component.nativeElement.querySelector('.confirm-button');
    expect(confirmButton.getAttribute('ng-reflect-color')).toBe('warn');
  });
});
