import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  DocumentType,
  QuoteDocumentDto,
} from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

function httpErrorMessage(err: unknown): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || 'An error occurred';
}

function triggerBlobDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'cpq-quote-documents-panel',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card class="docs-card">
      <mat-card-header>
        <mat-card-title>Documents</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (error(); as err) {
          <div class="error-banner" role="alert">
            <mat-icon>error_outline</mat-icon>
            <span>{{ err }}</span>
            <button mat-button type="button" (click)="clearError()">Dismiss</button>
          </div>
        }

        <form class="generate-row" [formGroup]="generateForm" (ngSubmit)="onGenerate()">
          <mat-form-field appearance="outline">
            <mat-label>Document type</mat-label>
            <mat-select formControlName="type">
              @for (t of documentTypes; track t) {
                <mat-option [value]="t">{{ typeLabel(t) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button
            mat-stroked-button
            color="primary"
            type="submit"
            [disabled]="busy() || quoteId() == null"
          >
            <mat-icon>picture_as_pdf</mat-icon>
            Generate
          </button>
        </form>

        @if (loading()) {
          <div class="loading" role="status" aria-live="polite">
            <mat-spinner diameter="32" />
          </div>
        } @else if (documents().length === 0) {
          <p class="empty-docs">No documents generated yet.</p>
        } @else {
          <div class="doc-table" role="table">
            <div class="doc-row doc-head" role="row">
              <span role="columnheader">Type</span>
              <span role="columnheader">File</span>
              <span role="columnheader">Size</span>
              <span role="columnheader">Created</span>
              <span role="columnheader">Actions</span>
            </div>
            @for (doc of documents(); track doc.id) {
              <div class="doc-row" role="row">
                <span role="cell">{{ typeLabel(doc.documentType) }}</span>
                <span role="cell">{{ doc.fileName || 'document.pdf' }}</span>
                <span role="cell">{{ formatSize(doc.fileSize) }}</span>
                <span role="cell">{{ doc.createdAt || '—' }}</span>
                <span role="cell">
                  <button
                    mat-button
                    color="primary"
                    type="button"
                    (click)="onDownload(doc)"
                    [disabled]="busy()"
                  >
                    <mat-icon>download</mat-icon>
                    Download
                  </button>
                </span>
              </div>
            }
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .docs-card {
        margin-top: 0;
      }

      .error-banner {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 12px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      .generate-row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        margin-top: 8px;
        margin-bottom: 8px;
      }

      .generate-row mat-form-field {
        min-width: 200px;
      }

      .loading {
        display: flex;
        justify-content: center;
        padding: 24px;
      }

      .empty-docs {
        margin: 12px 0 0;
        color: #757575;
      }

      .doc-table {
        margin-top: 12px;
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .doc-row {
        display: grid;
        grid-template-columns: 1.2fr 1.6fr 0.8fr 1.4fr 1fr;
        gap: 8px;
        align-items: center;
        padding: 8px 4px;
        border-bottom: 1px solid #eeeeee;
      }

      .doc-head {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
        border-bottom: 1px solid #e0e0e0;
      }

      @media (max-width: 768px) {
        .doc-row {
          grid-template-columns: 1fr 1fr;
        }
      }
    `,
  ],
})
export class QuoteDocumentsPanelComponent {
  private readonly quoteService = inject(QuoteService);
  private readonly fb = inject(FormBuilder);

  /** Quote id from parent detail route. */
  readonly quoteId = input<number | null>(null);

  readonly documentTypes = DOCUMENT_TYPES;
  readonly documents = signal<QuoteDocumentDto[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly generateForm = this.fb.nonNullable.group({
    type: ['PROPOSAL' as DocumentType],
  });

  private readonly loadOnQuoteId = effect(() => {
    const _id = this.quoteId();
    untracked(() => this.reload());
  });

  typeLabel(type: DocumentType): string {
    return DOCUMENT_TYPE_LABELS[type] ?? type;
  }

  formatSize(bytes?: number): string {
    if (bytes == null || !Number.isFinite(bytes)) {
      return '—';
    }
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  clearError(): void {
    this.error.set(null);
  }

  onGenerate(): void {
    const id = this.quoteId();
    if (id == null || this.busy()) {
      return;
    }
    const type = this.generateForm.controls.type.value;
    this.busy.set(true);
    this.error.set(null);
    this.quoteService.generateDocuments(id, type).subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(httpErrorMessage(err));
      },
    });
  }

  onDownload(doc: QuoteDocumentDto): void {
    const id = this.quoteId();
    if (id == null || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.quoteService.downloadDocument(id, doc.id).subscribe({
      next: (blob) => {
        triggerBlobDownload(blob, doc.fileName || 'document.pdf');
        this.busy.set(false);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(httpErrorMessage(err));
      },
    });
  }

  private reload(): void {
    const id = this.quoteId();
    if (id == null) {
      this.documents.set([]);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.quoteService.listDocuments(id).subscribe({
      next: (docs) => {
        this.documents.set(docs ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.documents.set([]);
        this.error.set(httpErrorMessage(err));
      },
    });
  }
}
