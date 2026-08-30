import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';
import { LeadStatus, DealStage } from '@frontend/shared-models';

export type StatusType = 'lead' | 'deal' | 'product';

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

/**
 * Color-coded badge for lead statuses, deal stages, and catalog product statuses
 *
 * @example
 * <app-status-badge [status]="'NEW'" [type]="'lead'" />
 * <app-status-badge [status]="'WON'" [type]="'deal'" />
 * <app-status-badge [status]="'ACTIVE'" [type]="'product'" />
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
    return this.getDealStageConfig(statusValue as DealStage);
  });

  private getLeadStatusConfig(status: LeadStatus): StatusConfig {
    const configs: Record<LeadStatus, StatusConfig> = {
      NEW: {
        color: '#1976d2',
        backgroundColor: '#e3f2fd',
        label: 'New',
      },
      CONTACTED: {
        color: '#0288d1',
        backgroundColor: '#b3e5fc',
        label: 'Contacted',
      },
      QUALIFIED: {
        color: '#7b1fa2',
        backgroundColor: '#f3e5f5',
        label: 'Qualified',
      },
      UNQUALIFIED: {
        color: '#e65100',
        backgroundColor: '#fff3e0',
        label: 'Unqualified',
      },
      CONVERTED: {
        color: '#388e3c',
        backgroundColor: '#e8f5e9',
        label: 'Converted',
      },
      LOST: {
        color: '#d32f2f',
        backgroundColor: '#ffebee',
        label: 'Lost',
      },
    };

    return configs[status] || {
      color: '#757575',
      backgroundColor: '#f5f5f5',
      label: status,
    };
  }

  private getDealStageConfig(stage: DealStage): StatusConfig {
    const configs: Record<DealStage, StatusConfig> = {
      PROSPECTING: {
        color: '#1976d2',
        backgroundColor: '#e3f2fd',
        label: 'Prospecting',
      },
      QUALIFICATION: {
        color: '#0288d1',
        backgroundColor: '#b3e5fc',
        label: 'Qualification',
      },
      NEEDS_ANALYSIS: {
        color: '#00838f',
        backgroundColor: '#e0f7fa',
        label: 'Needs Analysis',
      },
      PROPOSAL: {
        color: '#f57c00',
        backgroundColor: '#fff3e0',
        label: 'Proposal',
      },
      NEGOTIATION: {
        color: '#7b1fa2',
        backgroundColor: '#f3e5f5',
        label: 'Negotiation',
      },
      WON: {
        color: '#388e3c',
        backgroundColor: '#e8f5e9',
        label: 'Won',
      },
      LOST: {
        color: '#d32f2f',
        backgroundColor: '#ffebee',
        label: 'Lost',
      },
    };

    return configs[stage] || {
      color: '#757575',
      backgroundColor: '#f5f5f5',
      label: stage,
    };
  }

  private getProductStatusConfig(status: ProductStatusBadge): StatusConfig {
    const configs: Record<ProductStatusBadge, StatusConfig> = {
      DRAFT: {
        color: '#757575',
        backgroundColor: '#f5f5f5',
        label: 'Draft',
      },
      ACTIVE: {
        color: '#388e3c',
        backgroundColor: '#e8f5e9',
        label: 'Active',
      },
      DEPRECATED: {
        color: '#f57c00',
        backgroundColor: '#fff3e0',
        label: 'Deprecated',
      },
      RETIRED: {
        color: '#d32f2f',
        backgroundColor: '#ffebee',
        label: 'Retired',
      },
    };

    return configs[status] || {
      color: '#757575',
      backgroundColor: '#f5f5f5',
      label: status,
    };
  }
}
