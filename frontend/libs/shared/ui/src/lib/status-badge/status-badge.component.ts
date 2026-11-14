import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';
import {
  LeadStatus,
  DealStage,
  QuoteStatus,
  QUOTE_STATUS_LABELS,
  CRM_STATUS_TONES,
  CrmStatusTone,
  STAGE_LABELS,
} from '@frontend/shared-models';

export type StatusType = 'lead' | 'deal' | 'product' | 'quote';

export type ProductStatusBadge =
  | 'DRAFT'
  | 'ACTIVE'
  | 'DEPRECATED'
  | 'RETIRED';

interface StatusConfig {
  color: string;
  backgroundColor: string;
  label: string;
}

function fromTone(tone: CrmStatusTone, label: string): StatusConfig {
  return {
    color: tone.ink,
    backgroundColor: tone.container,
    label,
  };
}

/**
 * Color-coded badge for lead, deal, product, and quote statuses.
 * Colors resolve through UI-CRM-SLDS `--crm-*` tokens (see CRM_STATUS_TONES).
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule, MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-chip
      [style.color]="statusConfig().color"
      [style.background-color]="statusConfig().backgroundColor"
      [style.border]="'1px solid ' + statusConfig().color"
      class="status-badge"
    >
      {{ statusConfig().label }}
    </mat-chip>
  `,
  styles: [`
    .status-badge {
      font-weight: 500;
      font-size: 12px;
      padding: 4px 12px;
      height: auto;
      min-height: 24px;
      border-radius: 12px;
      letter-spacing: 0.5px;
    }

    :host ::ng-deep .status-badge .mat-mdc-chip-action-label {
      padding: 0;
    }
  `],
})
export class StatusBadgeComponent {
  status = input.required<string>();
  type = input.required<StatusType>();

  statusConfig = computed(() => {
    const statusValue = this.status();
    const typeValue = this.type();

    if (typeValue === 'lead') {
      return this.getLeadStatusConfig(statusValue as LeadStatus);
    }
    if (typeValue === 'product') {
      return this.getProductStatusConfig(statusValue as ProductStatusBadge);
    }
    if (typeValue === 'quote') {
      return this.getQuoteStatusConfig(statusValue as QuoteStatus);
    }
    return this.getDealStageConfig(statusValue as DealStage);
  });

  private getLeadStatusConfig(status: LeadStatus): StatusConfig {
    const configs: Record<LeadStatus, StatusConfig> = {
      NEW: fromTone(CRM_STATUS_TONES.info, 'New'),
      CONTACTED: fromTone(CRM_STATUS_TONES.infoStrong, 'Contacted'),
      QUALIFIED: fromTone(CRM_STATUS_TONES.accent, 'Qualified'),
      UNQUALIFIED: fromTone(CRM_STATUS_TONES.warning, 'Unqualified'),
      CONVERTED: fromTone(CRM_STATUS_TONES.success, 'Converted'),
      LOST: fromTone(CRM_STATUS_TONES.danger, 'Lost'),
    };

    return configs[status] || fromTone(CRM_STATUS_TONES.muted, status);
  }

  private getDealStageConfig(stage: DealStage): StatusConfig {
    const configs: Record<DealStage, StatusConfig> = {
      PROSPECTING: fromTone(CRM_STATUS_TONES.info, STAGE_LABELS['PROSPECTING'] ?? 'Prospecting'),
      QUALIFICATION: fromTone(CRM_STATUS_TONES.infoStrong, STAGE_LABELS['QUALIFICATION'] ?? 'Qualification'),
      NEEDS_ANALYSIS: fromTone(CRM_STATUS_TONES.teal, STAGE_LABELS['NEEDS_ANALYSIS'] ?? 'Needs Analysis'),
      PROPOSAL: fromTone(CRM_STATUS_TONES.warning, STAGE_LABELS['PROPOSAL'] ?? 'Proposal'),
      NEGOTIATION: fromTone(CRM_STATUS_TONES.accent, STAGE_LABELS['NEGOTIATION'] ?? 'Negotiation'),
      WON: fromTone(CRM_STATUS_TONES.success, STAGE_LABELS['WON'] ?? 'Won'),
      LOST: fromTone(CRM_STATUS_TONES.danger, STAGE_LABELS['LOST'] ?? 'Lost'),
    };

    return configs[stage] || fromTone(CRM_STATUS_TONES.muted, stage);
  }

  private getProductStatusConfig(status: ProductStatusBadge): StatusConfig {
    const configs: Record<ProductStatusBadge, StatusConfig> = {
      DRAFT: fromTone(CRM_STATUS_TONES.muted, 'Draft'),
      ACTIVE: fromTone(CRM_STATUS_TONES.success, 'Active'),
      DEPRECATED: fromTone(CRM_STATUS_TONES.warning, 'Deprecated'),
      RETIRED: fromTone(CRM_STATUS_TONES.danger, 'Retired'),
    };

    return configs[status] || fromTone(CRM_STATUS_TONES.muted, status);
  }

  private getQuoteStatusConfig(status: QuoteStatus): StatusConfig {
    const configs: Record<QuoteStatus, StatusConfig> = {
      DRAFT: fromTone(CRM_STATUS_TONES.muted, QUOTE_STATUS_LABELS.DRAFT),
      IN_REVIEW: fromTone(CRM_STATUS_TONES.info, QUOTE_STATUS_LABELS.IN_REVIEW),
      PENDING_APPROVAL: fromTone(CRM_STATUS_TONES.warning, QUOTE_STATUS_LABELS.PENDING_APPROVAL),
      APPROVED: fromTone(CRM_STATUS_TONES.success, QUOTE_STATUS_LABELS.APPROVED),
      SENT: fromTone(CRM_STATUS_TONES.teal, QUOTE_STATUS_LABELS.SENT),
      ACCEPTED: fromTone(CRM_STATUS_TONES.successStrong, QUOTE_STATUS_LABELS.ACCEPTED),
      EXPIRED: fromTone(CRM_STATUS_TONES.neutral, QUOTE_STATUS_LABELS.EXPIRED),
      CANCELLED: fromTone(CRM_STATUS_TONES.muted, QUOTE_STATUS_LABELS.CANCELLED),
      REJECTED: fromTone(CRM_STATUS_TONES.danger, QUOTE_STATUS_LABELS.REJECTED),
    };

    return configs[status] || fromTone(CRM_STATUS_TONES.muted, status);
  }
}
