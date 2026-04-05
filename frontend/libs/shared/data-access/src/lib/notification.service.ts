import { Injectable, inject } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

/**
 * Service that wraps MatSnackBar to provide consistent notification UI across the application.
 *
 * Provides methods for showing success, error, info, and warning messages.
 *
 * @example
 * ```typescript
 * import { inject } from '@angular/core';
 * import { NotificationService } from '@frontend/shared-data-access';
 *
 * export class MyComponent {
 *   private notificationService = inject(NotificationService);
 *
 *   onSave() {
 *     this.notificationService.success('Saved successfully!');
 *   }
 *
 *   onError() {
 *     this.notificationService.error('Failed to save. Please try again.');
 *   }
 * }
 * ```
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private snackBar = inject(MatSnackBar);

  private readonly defaultConfig: MatSnackBarConfig = {
    duration: 5000,
    horizontalPosition: 'end',
    verticalPosition: 'top',
  };

  /**
   * Shows a success notification with a green background.
   *
   * @param message - The message to display
   * @param duration - Optional duration in milliseconds (default: 5000)
   */
  success(message: string, duration?: number): void {
    this.snackBar.open(message, 'Close', {
      ...this.defaultConfig,
      duration: duration ?? this.defaultConfig.duration,
      panelClass: ['snackbar-success'],
    });
  }

  /**
   * Shows an error notification with a red background.
   *
   * @param message - The error message to display
   * @param duration - Optional duration in milliseconds (default: 7000 for errors)
   */
  error(message: string, duration?: number): void {
    this.snackBar.open(message, 'Close', {
      ...this.defaultConfig,
      duration: duration ?? 7000,
      panelClass: ['snackbar-error'],
    });
  }

  /**
   * Shows an info notification with a blue background.
   *
   * @param message - The info message to display
   * @param duration - Optional duration in milliseconds (default: 5000)
   */
  info(message: string, duration?: number): void {
    this.snackBar.open(message, 'Close', {
      ...this.defaultConfig,
      duration: duration ?? this.defaultConfig.duration,
      panelClass: ['snackbar-info'],
    });
  }

  /**
   * Shows a warning notification with an orange background.
   *
   * @param message - The warning message to display
   * @param duration - Optional duration in milliseconds (default: 6000)
   */
  warning(message: string, duration?: number): void {
    this.snackBar.open(message, 'Close', {
      ...this.defaultConfig,
      duration: duration ?? 6000,
      panelClass: ['snackbar-warning'],
    });
  }

  /**
   * Dismisses all currently displayed notifications.
   */
  dismiss(): void {
    this.snackBar.dismiss();
  }
}
