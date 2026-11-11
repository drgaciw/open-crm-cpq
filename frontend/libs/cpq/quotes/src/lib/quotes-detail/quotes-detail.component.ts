import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  AdvisorySuggestionComponent,
  ConfirmDialogComponent,
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import {
  APPROVAL_TIER_LABELS,
  JevSuggestionEnvelope,
  LineItemCreateRequest,
  QuoteLineItemDto,
} from '@frontend/shared-models';
import { QuoteService } from '../quote.service';
import { QuoteDocumentsPanelComponent } from '../quote-documents-panel/quote-documents-panel.component';
import { QuoteHeaderEditComponent } from '../quote-header-edit/quote-header-edit.component';
import { QuoteRampEditorComponent } from '../quote-ramp-editor/quote-ramp-editor.component';
import { rampLineAllowsSchedule, validateRampSchedule } from '../quote-ramp-editor/quote-ramp-editor.component';

function httpErrorMessage(err: unknown): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || 'An error occurred';
  if (code === 'RAMP_SCHEDULE_STALE') {
    return 'The ramp schedule has changed. Check its periods against the quote term, save, and Calculate before continuing. [RAMP_SCHEDULE_STALE]';
  }
  if (code === 'APPROVAL_SUPERSEDED') {
    return 'This approval was replaced after the quote changed. Refresh the quote and use the current request in Approvals. [APPROVAL_SUPERSEDED]';
  }
  if (code === 'RAMP_APPROVAL_REQUIRED') {
    return 'The current ramp approval is not complete. Refresh the quote and resolve the current request in Approvals before sending or accepting. [RAMP_APPROVAL_REQUIRED]';
  }
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

@Component({
  selector: 'cpq-quotes-detail',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    AdvisorySuggestionComponent,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
    QuoteDocumentsPanelComponent,
    QuoteHeaderEditComponent,
    QuoteRampEditorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !quote()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40" />
      </div>
    } @else if (error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="retry()">Retry</button>
        <button mat-button type="button" (click)="goBack()">Back to quotes</button>
      </div>
    } @else if (quote(); as quote) {
      <app-page-header
        [title]="quote.name"
        [subtitle]="quote.quoteNumber ?? 'Quote ' + quote.id"
      >
        <div actions class="header-actions">
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to quotes
          </button>
          @if (quote.dealExternalId?.trim()) {
            <a mat-stroked-button [routerLink]="['/quotes', quote.id, 'compare']">Compare alternatives</a>
          }
          <button
            mat-stroked-button
            type="button"
            (click)="onCalculate()"
            [disabled]="!canCalculate() || actionBusy()"
            [title]="
              canCalculate()
                ? 'Recalculate pricing for this quote'
                : 'Calculate is available for DRAFT or REJECTED quotes'
            "
          >
            <mat-icon>calculate</mat-icon>
            Calculate
          </button>
          <button
            mat-raised-button
            color="primary"
            type="button"
            (click)="onSubmit()"
            [disabled]="!canSubmit() || actionBusy()"
            [title]="
              canSubmit()
                ? 'Submit quote for review'
                : rampPricingBlocked() ? 'Check the ramp schedule and Calculate before submitting' : 'Submit is available only for DRAFT quotes'
            "
          >
            <mat-icon>send</mat-icon>
            Submit
          </button>
          <button
            mat-raised-button
            color="primary"
            type="button"
            (click)="onSend()"
            [disabled]="!canSend() || actionBusy()"
            [title]="
              canSend()
                ? 'Send approved quote to customer'
                : rampPricingBlocked() ? 'Check the ramp schedule and Calculate before sending' : rampApprovalBlocked() ? 'Resolve the current request in Approvals before sending' : 'Send is available only for APPROVED quotes'
            "
          >
            <mat-icon>outgoing_mail</mat-icon>
            Send
          </button>
          <button
            mat-raised-button
            color="accent"
            type="button"
            (click)="onAccept()"
            [disabled]="!canAccept() || actionBusy()"
            [title]="
              canAccept()
                ? 'Mark sent quote as accepted'
                : rampPricingBlocked() ? 'Check the ramp schedule and Calculate before accepting' : rampApprovalBlocked() ? 'Resolve the current request in Approvals before accepting' : 'Accept is available only for SENT quotes'
            "
          >
            <mat-icon>done_all</mat-icon>
            Accept
          </button>
          <button
            mat-stroked-button
            type="button"
            [matMenuTriggerFor]="moreActions"
            [disabled]="actionBusy()"
            aria-label="More quote actions"
            data-testid="quote-more-actions"
          >
            <mat-icon>more_vert</mat-icon>
            More
          </button>
          <mat-menu #moreActions="matMenu">
            <button
              mat-menu-item
              type="button"
              (click)="onApprove()"
              [disabled]="!canApproveReject() || actionBusy()"
              [title]="
                canApproveReject()
                  ? 'Approve this quote'
                  : 'Approve is available in review; pending approvals are decided in Approvals'
              "
            >
              <mat-icon>check_circle</mat-icon>
              <span>Approve</span>
            </button>
            <button
              mat-menu-item
              type="button"
              (click)="onReject()"
              [disabled]="!canApproveReject() || actionBusy()"
              [title]="
                canApproveReject()
                  ? 'Reject this quote (optional reason)'
                  : 'Reject is available in review; pending approvals are decided in Approvals'
              "
            >
              <mat-icon>cancel</mat-icon>
              <span>Reject</span>
            </button>
            <button
              mat-menu-item
              type="button"
              (click)="onExpire()"
              [disabled]="!canExpire() || actionBusy()"
              [title]="
                canExpire()
                  ? 'Mark sent quote as expired'
                  : 'Expire is available only for SENT quotes'
              "
            >
              <mat-icon>hourglass_empty</mat-icon>
              <span>Expire</span>
            </button>
            <button
              mat-menu-item
              type="button"
              (click)="onCancelQuote()"
              [disabled]="!canCancel() || actionBusy()"
              [title]="
                canCancel()
                  ? 'Cancel this quote'
                  : 'Cancel is unavailable for ACCEPTED or CANCELLED quotes'
              "
            >
              <mat-icon>block</mat-icon>
              <span>Cancel quote</span>
            </button>
            <button
              mat-menu-item
              type="button"
              (click)="onClone()"
              [disabled]="actionBusy()"
              title="Clone header and line items into a new DRAFT. Run Calculate for waterfall/ARR."
            >
              <mat-icon>content_copy</mat-icon>
              <span>Clone</span>
            </button>
            <button
              mat-menu-item
              type="button"
              (click)="onDelete()"
              [disabled]="!canDelete() || actionBusy()"
              [title]="
                canDelete()
                  ? 'Soft-delete this quote'
                  : 'Accepted quotes cannot be deleted'
              "
            >
              <mat-icon>delete</mat-icon>
              <span>Delete</span>
            </button>
          </mat-menu>
        </div>
      </app-page-header>

      @if (canSubmit()) {
        <section class="tier-suggestion" aria-label="Advisory approval tier suggestion">
          <button
            mat-stroked-button
            type="button"
            (click)="onSuggestTier()"
            [disabled]="tierSuggestionLoading()"
            title="Ask for an advisory suggestion. It never selects or submits a tier."
          >
            <mat-icon>tips_and_updates</mat-icon>
            Suggest approval tier (advisory)
          </button>
          @if (tierSuggestion(); as envelope) {
            @if (envelope.status === 'READY') {
              <app-advisory-suggestion
                [suggestion]="envelope.suggestion"
                heading="Suggested approval tier"
                [labels]="tierLabels" />
            } @else if (envelope.status === 'PENDING') {
              <p class="tier-suggestion-note" role="status">Evaluating — ask again in a moment.</p>
            } @else {
              <p class="tier-suggestion-note" role="status">No suggestion available.</p>
            }
          }
        </section>
      }

      @if (actionError(); as actionErr) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ actionErr }}</span>
          <button mat-button type="button" (click)="clearActionError()">Dismiss</button>
        </div>
      }

      <div class="detail-body">
        @if (rampPricingBlocked()) {
          <section class="ramp-feedback" role="alert" data-testid="ramp-stale-banner">
            <strong>Ramp schedule needs review and recalculation</strong>
            <p>Check the periods against the {{ quote.durationInMonths }}-month quote term, save the schedule, and Calculate. Submit, Send and Accept are unavailable until pricing is current.</p>
          </section>
        } @else if (quote.rampGovernance; as governance) {
          <section class="ramp-feedback" role="status" aria-live="polite" data-testid="ramp-governance-banner">
            <strong>Ramp approval policy</strong>
            @if (governance.governingPeriod !== null && governance.governingPeriod !== undefined) {
              <p>Governing period: {{ governance.governingPeriod }}{{ governingLineName() ? ' — ' + governingLineName() : '' }}.
                @if (governance.discountPercent !== null && governance.discountPercent !== undefined) { Discount: {{ governance.discountPercent }}%. }
              </p>
            }
            @if (quote.approvalTier) { <p>Approval tier: {{ tierLabels[quote.approvalTier] || quote.approvalTier }}.</p> }
            @if (quote.approvalState) { <p>Approval status: {{ quote.approvalState }}.</p> }
            @if (quote.approvalState === 'PENDING') { <p>Use the current request in Approvals to record a decision.</p> }
            @if (quote.status === 'PENDING_APPROVAL' && canEditLines()) { <p>Editing this ramp retires its current approval. Calculate the changed draft before resubmitting.</p> }
            @if (governance.stepDown) { <p>A decrease in quantity or unit price requires approval unless the product or rule permits it.</p> }
            @if (rampApprovalBlocked()) { <p>Resolve the current request in Approvals before sending or accepting this quote.</p> }
            @for (reason of governance.reasons ?? []; track $index) { <p>{{ reason }}</p> }
          </section>
        }
        <mat-card>
          <mat-card-header>
            <mat-card-title>Quote details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Status</span>
                <app-status-badge [status]="quote.status" type="quote" />
              </div>
              <div class="info-item">
                <span class="label">Deal type</span>
                <span class="value">{{ quote.dealType || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Customer</span>
                <span class="value">{{ quote.customerExternalId }}</span>
              </div>
              <div class="info-item">
                <span class="label">Deal</span>
                <span class="value">{{ quote.dealExternalId || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Total amount</span>
                <span class="value">{{
                  quote.totalAmount !== null && quote.totalAmount !== undefined
                    ? (quote.totalAmount | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">ARR</span>
                <span class="value">{{
                  quote.arr !== null && quote.arr !== undefined
                    ? (quote.arr | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Duration</span>
                <span class="value">{{ quote.durationInMonths }} months</span>
              </div>
              <div class="info-item">
                <span class="label">Region</span>
                <span class="value">{{ quote.region || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Line items</span>
                <span class="value">{{ lineItemCount() }}</span>
              </div>
              <div class="info-item full-width">
                <span class="label">Description</span>
                <span class="value">{{ quote.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <cpq-quote-header-edit
          [quote]="quote"
          [editable]="canEditLines()"
          (saved)="onHeaderSaved()"
        />

        <mat-card class="line-item-card">
          <mat-card-header>
            <mat-card-title>Line items</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (lineItems().length === 0) {
              <p class="empty-lines">No line items yet.</p>
            } @else {
              <div class="line-table" role="table">
                <div class="line-row line-head" role="row">
                  <span role="columnheader">Product</span>
                  <span role="columnheader">Name</span>
                  <span role="columnheader">Qty</span>
                  <span role="columnheader">List price</span>
                  <span role="columnheader">Net</span>
                  <span role="columnheader">Actions</span>
                </div>
                @for (line of lineItems(); track line.id) {
                  <div class="line-row" role="row">
                    @if (editingLineId() === line.id) {
                      <span role="cell">{{ line.productExternalId }}</span>
                      <span role="cell">
                        <mat-form-field appearance="outline" class="edit-field">
                          <mat-label>Name</mat-label>
                          <input matInput [formControl]="editForm.controls.productName" />
                        </mat-form-field>
                      </span>
                      <span role="cell">
                        <mat-form-field appearance="outline" class="edit-field narrow">
                          <mat-label>Qty</mat-label>
                          <input
                            matInput
                            type="number"
                            min="1"
                            [formControl]="editForm.controls.quantity"
                          />
                        </mat-form-field>
                      </span>
                      <span role="cell">
                        <mat-form-field appearance="outline" class="edit-field">
                          <mat-label>List price</mat-label>
                          <input
                            matInput
                            type="number"
                            min="0"
                            step="0.01"
                            [formControl]="editForm.controls.listPrice"
                          />
                        </mat-form-field>
                      </span>
                      <span role="cell">{{
                        line.netPrice !== null && line.netPrice !== undefined
                          ? (line.netPrice | currencyFormat: quote.currency || 'USD')
                          : '—'
                      }}</span>
                      <span role="cell" class="line-actions">
                        <button
                          mat-button
                          color="primary"
                          type="button"
                          (click)="onSaveLineEdit(line)"
                          [disabled]="editForm.invalid || actionBusy()"
                        >
                          Save
                        </button>
                        <button
                          mat-button
                          type="button"
                          (click)="cancelLineEdit()"
                          [disabled]="actionBusy()"
                        >
                          Cancel
                        </button>
                      </span>
                    } @else {
                      <span role="cell">{{ line.productExternalId }}</span>
                      <span role="cell">{{ line.productName || '—' }}</span>
                      <span role="cell">{{ line.quantity }}</span>
                      <span role="cell">{{
                        line.listPrice !== null && line.listPrice !== undefined
                          ? (line.listPrice | currencyFormat: quote.currency || 'USD')
                          : '—'
                      }}</span>
                      <span role="cell">{{
                        line.netPrice !== null && line.netPrice !== undefined
                          ? (line.netPrice | currencyFormat: quote.currency || 'USD')
                          : '—'
                      }}</span>
                      <span role="cell" class="line-actions">
                        @if (canEditLines()) {
                          <button
                            mat-button
                            type="button"
                            (click)="startLineEdit(line)"
                            [disabled]="actionBusy()"
                          >
                            Edit
                          </button>
                          <button
                            mat-button
                            color="warn"
                            type="button"
                            (click)="onRemoveLineItem(line)"
                            [disabled]="actionBusy()"
                          >
                            Remove
                          </button>
                        } @else {
                          <span class="muted">—</span>
                        }
                      </span>
                    }
                  </div>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>

        <cpq-quote-ramp-editor
          [quoteId]="numericId()"
          [lines]="lineItems()"
          [durationInMonths]="quote.durationInMonths"
          [quoteVersion]="quote.version ?? null"
          [currency]="quote.currency || 'USD'"
          [editable]="canEditLines()"
          [tcv]="quote.tcv ?? null"
          [acv]="quote.acv ?? null"
          [exitArr]="quote.exitArr ?? null"
        />

        @if (canEditLines()) {
          <mat-card class="line-item-card">
            <mat-card-header>
              <mat-card-title>Add line item</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <form class="line-form" [formGroup]="lineForm" (ngSubmit)="onAddLineItem()">
                <mat-form-field appearance="outline">
                  <mat-label>Product external ID</mat-label>
                  <input matInput formControlName="productExternalId" autocomplete="off" />
                  @if (
                    lineForm.controls.productExternalId.hasError('required') &&
                    lineForm.controls.productExternalId.touched
                  ) {
                    <mat-error>Required</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Product name</mat-label>
                  <input matInput formControlName="productName" autocomplete="off" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>List price</mat-label>
                  <input matInput type="number" formControlName="listPrice" min="0" step="0.01" />
                  @if (
                    lineForm.controls.listPrice.hasError('required') &&
                    lineForm.controls.listPrice.touched
                  ) {
                    <mat-error>Required</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Quantity</mat-label>
                  <input matInput type="number" formControlName="quantity" min="1" />
                </mat-form-field>
                <mat-form-field appearance="outline" class="notes-field">
                  <mat-label>Notes</mat-label>
                  <input matInput formControlName="notes" autocomplete="off" />
                </mat-form-field>
                <button
                  mat-stroked-button
                  color="primary"
                  type="submit"
                  [disabled]="lineForm.invalid || actionBusy()"
                >
                  <mat-icon>add</mat-icon>
                  Add line
                </button>
              </form>
            </mat-card-content>
          </mat-card>
        }

        <cpq-quote-documents-panel [quoteId]="numericId()" />

        <p class="clone-note" role="note">
          Clone creates a new DRAFT and copies the quote header and line items.
          Pricing waterfall and ARR are not copied — run Calculate on the clone.
        </p>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Quote not found"
        message="This quote could not be loaded from the API."
        actionLabel="Back to quotes"
        (action)="goBack()"
      />
    }
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }

      .header-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .tier-suggestion {
        display: grid;
        gap: 12px;
        max-width: 520px;
        margin-bottom: 16px;
        justify-items: start;
      }
      .tier-suggestion app-advisory-suggestion {
        justify-self: stretch;
      }
      .tier-suggestion-note {
        margin: 0;
        color: #616161;
        font-size: 13px;
      }
      .error-banner {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin: 24px 32px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      .detail-body {
        padding: 0 32px 32px;
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .ramp-feedback {
        padding: 16px;
        border-radius: 8px;
        background: #fff8e1;
        color: #5d4037;
      }
      .ramp-feedback p { margin: 8px 0 0; }

      .info-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 20px;
        margin-top: 16px;
      }

      .info-item {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .info-item.full-width {
        grid-column: 1 / -1;
      }

      .label {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .value {
        font-size: 15px;
        color: #212121;
      }

      .empty-lines {
        margin: 16px 0 0;
        color: #757575;
      }

      .line-table {
        margin-top: 12px;
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .line-row {
        display: grid;
        grid-template-columns: 1.2fr 1.2fr 0.6fr 1fr 1fr 1.2fr;
        gap: 8px;
        align-items: center;
        padding: 8px 4px;
        border-bottom: 1px solid #eeeeee;
      }

      .line-head {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
        border-bottom: 1px solid #e0e0e0;
      }

      .line-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
        align-items: center;
      }

      .muted {
        color: #9e9e9e;
      }

      .edit-field {
        width: 100%;
      }

      .edit-field.narrow {
        max-width: 88px;
      }

      .line-form {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        margin-top: 8px;
      }

      .line-form mat-form-field {
        flex: 1 1 160px;
        min-width: 140px;
      }

      .line-form .notes-field {
        flex: 2 1 220px;
      }

      .line-item-card {
        margin-top: 0;
      }

      .clone-note {
        margin: 0;
        font-size: 13px;
        color: #757575;
        line-height: 1.5;
      }

      @media (max-width: 768px) {
        .error-banner {
          margin: 16px;
        }

        .detail-body {
          padding: 0 16px 24px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }

        .line-row {
          grid-template-columns: 1fr 1fr;
        }
      }
    `,
  ],
})
export class QuotesDetailComponent {
  readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.quoteService.loading());
  readonly error = computed(() => this.quoteService.error());
  readonly quote = computed(() => this.quoteService.selectedQuote());
  readonly lineItems = computed(() => this.quote()?.lineItems ?? []);
  readonly lineItemCount = computed(() => this.lineItems().length);
  readonly rampPricingBlocked = computed(() => {
    const quote = this.quote();
    if (!quote) { return false; }
    return quote.rampScheduleStale === true || this.lineItems().some((line) => {
      const steps = line.schedule?.steps ?? [];
      return steps.length > 0 && validateRampSchedule(steps, quote.durationInMonths, rampLineAllowsSchedule(line)).length > 0;
    });
  });
  readonly governingLineName = computed(() => {
    const lineId = this.quote()?.rampGovernance?.governingLineItemId;
    const line = this.lineItems().find((item) => item.id === lineId);
    return line?.productName || line?.productExternalId || '';
  });
  readonly rampApprovalBlocked = computed(() => {
    const quote = this.quote();
    const hasRamp = quote?.rampGovernance != null || this.lineItems().some((line) => (line.schedule?.steps.length ?? 0) > 0);
    return hasRamp && (quote?.approvalState === 'PENDING' || quote?.approvalState === 'REJECTED');
  });

  readonly actionBusy = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Advisory only (ADR-015). Fetched on demand so an ordinary page load makes no extra request. */
  readonly tierSuggestion = signal<JevSuggestionEnvelope | null>(null);
  readonly tierSuggestionLoading = signal(false);
  readonly tierLabels: Record<string, string> = APPROVAL_TIER_LABELS;
  readonly editingLineId = signal<number | null>(null);

  /** Reload when route `:id` changes (e.g. after clone navigation). */
  private readonly loadOnIdChange = effect(() => {
    const _id = this.id();
    untracked(() => {
      this.tierSuggestion.set(null);
      this.load();
    });
  });

  /** Submit only from DRAFT (matches quote-service). */
  readonly canSubmit = computed(() => this.quote()?.status === 'DRAFT' && !this.rampPricingBlocked());

  /** A bound pending ramp may be amended; the server retires its approval and returns it to DRAFT. */
  readonly canEditLines = computed(() => {
    const quote = this.quote();
    return quote?.status === 'DRAFT' || quote?.status === 'REJECTED' ||
      (quote?.status === 'PENDING_APPROVAL' && quote.rampGovernance != null &&
        this.lineItems().some((line) => (line.schedule?.steps.length ?? 0) > 0));
  });

  /** Reprice after a commercial edit moves the pending ramp back to DRAFT. */
  readonly canCalculate = computed(() => this.quote()?.status === 'DRAFT' || this.quote()?.status === 'REJECTED');

  /**
   * Approve / Reject a review. A PENDING_APPROVAL quote is decided in the approvals inbox,
   * where approval-routing checks the approver's role (ADR-016 step 4); the quote API refuses it.
   */
  readonly canApproveReject = computed(() => this.quote()?.status === 'IN_REVIEW');

  readonly canSend = computed(() => this.quote()?.status === 'APPROVED' && !this.rampPricingBlocked() && !this.rampApprovalBlocked());

  readonly canAccept = computed(() => this.quote()?.status === 'SENT' && !this.rampPricingBlocked() && !this.rampApprovalBlocked());

  /** Expire only from SENT (matches quote-service). */
  readonly canExpire = computed(() => this.quote()?.status === 'SENT');

  /** Cancel when not ACCEPTED and not already CANCELLED. */
  readonly canCancel = computed(() => {
    const status = this.quote()?.status;
    return status != null && status !== 'ACCEPTED' && status !== 'CANCELLED';
  });

  /**
   * Soft-delete allowed for all statuses except ACCEPTED
   * (matches BE `QUOTE_ACCEPTED_NO_DELETE`). CANCELLED/EXPIRED/DRAFT allowed.
   */
  readonly canDelete = computed(() => {
    const status = this.quote()?.status;
    return status != null && status !== 'ACCEPTED';
  });

  readonly lineForm = this.fb.nonNullable.group({
    productExternalId: ['', Validators.required],
    productName: [''],
    listPrice: [null as number | null, Validators.required],
    quantity: [1 as number | null],
    notes: [''],
  });

  readonly editForm = this.fb.nonNullable.group({
    productName: [''],
    listPrice: [null as number | null, Validators.required],
    quantity: [1 as number | null, Validators.required],
  });

  goBack(): void {
    void this.router.navigate(['/quotes']);
  }

  retry(): void {
    this.load();
  }

  clearActionError(): void {
    this.actionError.set(null);
  }

  /** After header PUT succeeds, child already calls getById; clear line edit state. */
  onHeaderSaved(): void {
    this.actionError.set(null);
    this.editingLineId.set(null);
  }

  onCalculate(): void {
    this.runQuoteAction(
      () => this.canCalculate(),
      (id) => this.quoteService.calculate(id),
    );
  }

  onSuggestTier(): void {
    const id = this.quote()?.id;
    if (id == null || this.tierSuggestionLoading()) {
      return;
    }
    this.tierSuggestionLoading.set(true);
    this.quoteService.getTierSuggestion(id).subscribe({
      next: (envelope) => {
        this.tierSuggestion.set(envelope);
        this.tierSuggestionLoading.set(false);
      },
      // Advisory: a failure is "no suggestion", never an error banner.
      error: () => {
        this.tierSuggestion.set({ status: 'UNAVAILABLE', suggestion: null });
        this.tierSuggestionLoading.set(false);
      },
    });
  }

  onSubmit(): void {
    this.runQuoteAction(
      () => this.canSubmit(),
      (id) => this.quoteService.submit(id),
    );
  }

  onApprove(): void {
    this.runQuoteAction(
      () => this.canApproveReject(),
      (id) => this.quoteService.approve(id),
    );
  }

  onReject(): void {
    if (!this.canApproveReject() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const reasonRaw = window.prompt('Rejection reason (optional):');
    if (reasonRaw === null) {
      return;
    }
    const reason = reasonRaw.trim();
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.reject(numericId, reason || undefined).subscribe({
      next: (updated) => {
        this.quoteService.selectedQuote.set(updated);
        this.editingLineId.set(null);
        this.actionBusy.set(false);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  onSend(): void {
    this.runQuoteAction(
      () => this.canSend(),
      (id) => this.quoteService.send(id),
    );
  }

  onAccept(): void {
    this.runQuoteAction(
      () => this.canAccept(),
      (id) => this.quoteService.accept(id),
    );
  }

  onExpire(): void {
    if (!this.canExpire() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Expire quote',
        message: `Mark quote #${numericId} as expired? This applies only to SENT quotes.`,
        confirmText: 'Expire',
        cancelText: 'Keep',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.runQuoteAction(
        () => this.canExpire(),
        (id) => this.quoteService.expire(id),
      );
    });
  }

  onCancelQuote(): void {
    if (!this.canCancel() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Cancel quote',
        message: `Cancel quote #${numericId}? Accepted quotes cannot be cancelled.`,
        confirmText: 'Cancel quote',
        cancelText: 'Keep',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.runQuoteAction(
        () => this.canCancel(),
        (id) => this.quoteService.cancel(id),
      );
    });
  }

  onClone(): void {
    if (this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '480px',
      data: {
        title: 'Clone quote',
        message:
          `Clone quote #${numericId} into a new DRAFT?\n\n` +
          'The backend copies the quote header and line items. ' +
          'Pricing waterfall and ARR are not copied — run Calculate on the clone.',
        confirmText: 'Clone',
        cancelText: 'Cancel',
        confirmColor: 'primary' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionBusy.set(true);
      this.actionError.set(null);
      this.quoteService.cloneQuote(numericId).subscribe({
        next: (cloned) => {
          this.actionBusy.set(false);
          void this.router.navigate(['/quotes', cloned.id]);
        },
        error: (err) => {
          this.actionBusy.set(false);
          this.actionError.set(httpErrorMessage(err));
        },
      });
    });
  }

  /** Soft-delete via ConfirmDialog; blocked for ACCEPTED (BE QUOTE_ACCEPTED_NO_DELETE). */
  onDelete(): void {
    if (!this.canDelete() || this.actionBusy()) {
      return;
    }
    const quote = this.quote();
    const numericId = this.numericId();
    if (quote == null || numericId == null) {
      return;
    }
    const label = quote.quoteNumber ?? `Quote ${numericId}`;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Delete quote',
        message: `Soft-delete "${quote.name}" (${label})? It will be removed from the active quote list.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionBusy.set(true);
      this.actionError.set(null);
      this.quoteService.delete(numericId).subscribe({
        next: () => {
          this.actionBusy.set(false);
          this.quoteService.selectedQuote.set(null);
          void this.router.navigate(['/quotes']);
        },
        error: (err) => {
          this.actionBusy.set(false);
          this.actionError.set(httpErrorMessage(err));
        },
      });
    });
  }

  onAddLineItem(): void {
    if (!this.canEditLines() || this.lineForm.invalid || this.actionBusy()) {
      this.lineForm.markAllAsTouched();
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const raw = this.lineForm.getRawValue();
    const request: LineItemCreateRequest = {
      productExternalId: raw.productExternalId.trim(),
      listPrice: Number(raw.listPrice),
      quantity:
        raw.quantity != null && Number.isFinite(raw.quantity)
          ? Number(raw.quantity)
          : 1,
    };
    const productName = raw.productName.trim();
    if (productName) {
      request.productName = productName;
    }
    const notes = raw.notes.trim();
    if (notes) {
      request.notes = notes;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.addLineItem(numericId, request).subscribe({
      next: () => {
        this.lineForm.reset({
          productExternalId: '',
          productName: '',
          listPrice: null,
          quantity: 1,
          notes: '',
        });
        this.actionBusy.set(false);
        this.quoteService.getById(numericId);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  startLineEdit(line: QuoteLineItemDto): void {
    if (!this.canEditLines() || this.actionBusy()) {
      return;
    }
    this.editingLineId.set(line.id);
    this.editForm.setValue({
      productName: line.productName ?? '',
      listPrice: line.listPrice ?? null,
      quantity: line.quantity,
    });
  }

  cancelLineEdit(): void {
    this.editingLineId.set(null);
  }

  onSaveLineEdit(line: QuoteLineItemDto): void {
    if (!this.canEditLines() || this.editForm.invalid || this.actionBusy()) {
      this.editForm.markAllAsTouched();
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const raw = this.editForm.getRawValue();
    const request: LineItemCreateRequest = {
      productExternalId: line.productExternalId,
      listPrice: Number(raw.listPrice),
      quantity:
        raw.quantity != null && Number.isFinite(raw.quantity)
          ? Number(raw.quantity)
          : 1,
    };
    const productName = raw.productName.trim();
    if (productName) {
      request.productName = productName;
    }
    if (line.schedule) {
      request.schedule = line.schedule;
    }
    // This endpoint replaces the line. The inline editor only changes name, price and quantity.
    if (line.productType != null) { request.productType = line.productType; }
    if (line.lineItemType != null) { request.lineItemType = line.lineItemType; }
    if (line.billingFrequency != null) { request.billingFrequency = line.billingFrequency; }
    if (line.unitOfMeasure != null) { request.unitOfMeasure = line.unitOfMeasure; }
    if (line.notes != null) { request.notes = line.notes; }
    request.sortOrder = line.sortOrder;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.updateLineItem(numericId, line.id, request).subscribe({
      next: () => {
        this.editingLineId.set(null);
        this.actionBusy.set(false);
        this.quoteService.getById(numericId);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  onRemoveLineItem(line: QuoteLineItemDto): void {
    if (!this.canEditLines() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.removeLineItem(numericId, line.id).subscribe({
      next: () => {
        if (this.editingLineId() === line.id) {
          this.editingLineId.set(null);
        }
        this.actionBusy.set(false);
        this.quoteService.getById(numericId);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  private runQuoteAction(
    canRun: () => boolean,
    call: (id: number) => ReturnType<QuoteService['approve']>,
  ): void {
    if (!canRun() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    call(numericId).subscribe({
      next: (updated) => {
        this.quoteService.selectedQuote.set(updated);
        this.editingLineId.set(null);
        this.actionBusy.set(false);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  /** Exposed for documents panel binding and action handlers. */
  numericId(): number | null {
    const numericId = Number(this.id());
    return Number.isFinite(numericId) ? numericId : null;
  }

  private load(): void {
    this.actionError.set(null);
    this.editingLineId.set(null);
    const numericId = this.numericId();
    if (numericId == null) {
      this.quoteService.selectedQuote.set(null);
      this.quoteService.error.set('Invalid quote id');
      this.quoteService.loading.set(false);
      return;
    }
    this.quoteService.selectedQuote.set(null);
    this.quoteService.getById(numericId);
  }
}
