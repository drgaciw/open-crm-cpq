import {
  Component,
  ChangeDetectionStrategy,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

const COPIED_RESET_MS = 1500;

/**
 * Compact copy-to-clipboard icon button backed by `@angular/cdk/clipboard`
 * with polite screen-reader feedback via `LiveAnnouncer`.
 */
@Component({
  selector: 'app-copy-button',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      mat-icon-button
      type="button"
      class="copy-button"
      [class.copy-button--copied]="isCopied()"
      [matTooltip]="isCopied() ? copiedTooltip() : tooltip()"
      [attr.aria-label]="isCopied() ? 'Copied ' + label() : 'Copy ' + label()"
      (click)="onCopy($event)"
    >
      <mat-icon aria-hidden="true">{{ isCopied() ? 'check' : 'content_copy' }}</mat-icon>
    </button>
  `,
  styles: [`
    :host {
      display: inline-flex;
      vertical-align: middle;
    }

    .copy-button {
      color: var(--crm-color-muted);
    }

    .copy-button:hover {
      color: var(--crm-color-brand);
    }

    .copy-button--copied,
    .copy-button--copied:hover {
      color: var(--crm-color-success);
    }
  `],
})
export class CopyButtonComponent {
  private readonly clipboard = inject(Clipboard);
  private readonly liveAnnouncer = inject(LiveAnnouncer);
  private readonly destroyRef = inject(DestroyRef);

  value = input.required<string>();
  label = input<string>('value');
  tooltip = input<string>('Copy to clipboard');
  copiedTooltip = input<string>('Copied');

  copied = output<boolean>();

  protected readonly isCopied = signal(false);

  private resetTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.resetTimer !== null) {
        clearTimeout(this.resetTimer);
        this.resetTimer = null;
      }
    });
  }

  onCopy(event: Event): void {
    event.stopPropagation();

    const success = this.clipboard.copy(this.value());
    if (success) {
      if (this.resetTimer !== null) {
        clearTimeout(this.resetTimer);
      }
      this.isCopied.set(true);
      void this.liveAnnouncer.announce(`Copied ${this.label()} to clipboard`, 'polite');
      this.copied.emit(true);

      this.resetTimer = setTimeout(() => {
        this.isCopied.set(false);
        this.resetTimer = null;
      }, COPIED_RESET_MS);
    } else {
      this.copied.emit(false);
    }
  }
}
