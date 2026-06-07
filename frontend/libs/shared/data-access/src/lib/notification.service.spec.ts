import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let snackBarSpy: { open: ReturnType<typeof vi.fn>; dismiss: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    const spy = { open: vi.fn(), dismiss: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: MatSnackBar, useValue: spy }
      ]
    });

    service = TestBed.inject(NotificationService);
    snackBarSpy = TestBed.inject(MatSnackBar) as { open: ReturnType<typeof vi.fn>; dismiss: ReturnType<typeof vi.fn> };
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('success', () => {
    it('should call snackBar.open with success styling', () => {
      const message = 'Operation successful';
      service.success(message);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: 5000,
          panelClass: ['snackbar-success']
        })
      );
    });

    it('should use custom duration when provided', () => {
      const message = 'Success';
      const customDuration = 3000;
      service.success(message, customDuration);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: customDuration
        })
      );
    });
  });

  describe('error', () => {
    it('should call snackBar.open with error styling', () => {
      const message = 'An error occurred';
      service.error(message);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: 7000,
          panelClass: ['snackbar-error']
        })
      );
    });

    it('should use custom duration when provided', () => {
      const message = 'Error';
      const customDuration = 10000;
      service.error(message, customDuration);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: customDuration
        })
      );
    });
  });

  describe('info', () => {
    it('should call snackBar.open with info styling', () => {
      const message = 'Information message';
      service.info(message);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: 5000,
          panelClass: ['snackbar-info']
        })
      );
    });
  });

  describe('warning', () => {
    it('should call snackBar.open with warning styling', () => {
      const message = 'Warning message';
      service.warning(message);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        message,
        'Close',
        expect.objectContaining({
          duration: 6000,
          panelClass: ['snackbar-warning']
        })
      );
    });
  });

  describe('dismiss', () => {
    it('should call snackBar.dismiss', () => {
      service.dismiss();
      expect(snackBarSpy.dismiss).toHaveBeenCalled();
    });
  });
});
