import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let snackBarSpy: { open: ReturnType<typeof vi.fn>; dismiss: ReturnType<typeof vi.fn> };
  let liveAnnouncerSpy: { announce: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    const spy = { open: vi.fn(), dismiss: vi.fn() };
    const announcerSpy = {
      announce: vi.fn().mockResolvedValue(undefined),
      clear: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: MatSnackBar, useValue: spy },
        { provide: LiveAnnouncer, useValue: announcerSpy }
      ]
    });

    service = TestBed.inject(NotificationService);
    snackBarSpy = TestBed.inject(MatSnackBar) as unknown as {
      open: ReturnType<typeof vi.fn>;
      dismiss: ReturnType<typeof vi.fn>;
    };
    liveAnnouncerSpy = TestBed.inject(LiveAnnouncer) as unknown as {
      announce: ReturnType<typeof vi.fn>;
      clear: ReturnType<typeof vi.fn>;
    };
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

    it('should announce the message politely via LiveAnnouncer', () => {
      const message = 'Operation successful';
      service.success(message);

      expect(liveAnnouncerSpy.announce).toHaveBeenCalledWith(message, 'polite');
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

    it('should announce the message assertively via LiveAnnouncer', () => {
      const message = 'An error occurred';
      service.error(message);

      expect(liveAnnouncerSpy.announce).toHaveBeenCalledWith(message, 'assertive');
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

    it('should announce the message politely via LiveAnnouncer', () => {
      const message = 'Information message';
      service.info(message);

      expect(liveAnnouncerSpy.announce).toHaveBeenCalledWith(message, 'polite');
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

    it('should announce the message politely via LiveAnnouncer', () => {
      const message = 'Warning message';
      service.warning(message);

      expect(liveAnnouncerSpy.announce).toHaveBeenCalledWith(message, 'polite');
    });
  });

  describe('dismiss', () => {
    it('should call snackBar.dismiss', () => {
      service.dismiss();
      expect(snackBarSpy.dismiss).toHaveBeenCalled();
    });

    it('should not announce via LiveAnnouncer when dismissing', () => {
      service.dismiss();
      expect(liveAnnouncerSpy.announce).not.toHaveBeenCalled();
    });
  });
});
