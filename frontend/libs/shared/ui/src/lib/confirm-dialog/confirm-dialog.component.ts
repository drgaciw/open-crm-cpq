import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  confirmColor?: 'primary' | 'accent' | 'warn';
}

/**
 * Confirmation dialog component for user confirmations
 *
 * @example
 * constructor(private dialog: MatDialog) {}
 *
 * openConfirmDialog(): void {
 *   const dialogRef = this.dialog.open(ConfirmDialogComponent, {
 *     width: '400px',
 *     data: {
 *       title: 'Delete Item',
 *       message: 'Are you sure you want to delete this item? This action cannot be undone.',
 *       confirmText: 'Delete',
 *       cancelText: 'Cancel',
 *       confirmColor: 'warn'
 *     }
 *   });
 *
 *   dialogRef.afterClosed().subscribe(result => {
 *     if (result === true) {
 *       // User confirmed
 *     }
 *   });
 * }
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="confirm-dialog">
      <h2 mat-dialog-title class="dialog-title">{{ data.title }}</h2>

      <mat-dialog-content class="dialog-content">
        <p class="dialog-message">{{ data.message }}</p>
      </mat-dialog-content>

      <mat-dialog-actions class="dialog-actions">
        <button
          mat-button
          (click)="onCancel()"
          class="cancel-button"
        >
          {{ data.cancelText || 'Cancel' }}
        </button>
        <button
          mat-raised-button
          [color]="data.confirmColor || 'primary'"
          (click)="onConfirm()"
          class="confirm-button"
        >
          {{ data.confirmText || 'Confirm' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .confirm-dialog {
      min-width: 300px;
    }

    .dialog-title {
      margin: 0;
      padding: 24px 24px 16px;
      font-size: 20px;
      font-weight: 600;
      color: #212121;
    }

    .dialog-content {
      padding: 0 24px;
      overflow: visible;
    }

    .dialog-message {
      margin: 0;
      color: #616161;
      font-size: 14px;
      line-height: 1.6;
      white-space: pre-wrap;
    }

    .dialog-actions {
      padding: 16px 24px 24px;
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin: 0;
    }

    .cancel-button {
      color: #757575;
    }

    .confirm-button {
      min-width: 80px;
    }
  `],
})
export class ConfirmDialogComponent {
  protected readonly data: ConfirmDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent>);

  onConfirm(): void {
    this.dialogRef.close(true);
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }
}
