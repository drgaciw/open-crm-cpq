import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import {
  CurrencyFormatPipe,
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SortChangeEvent,
  TableColumn,
} from '@frontend/shared-ui';
import { PaginationMeta, SUBSCRIPTION_STATUS_LABELS, SubscriptionDto } from '@frontend/shared-models';
import { ContractService } from '../contract.service';

interface ContractRow {
  id: number;
  subscriptionNumber: string;
  customerId: string;
  planName: string;
  status: string;
  startDate: string;
  endDate: string;
  arr: string;
}

/** Read-only contract (subscription) list (DRG-761). Contracts are created by accepting a quote. */
@Component({
  selector: 'cpq-contract-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, PageHeaderComponent, DataTableComponent, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Contracts" subtitle="Customer contracts created from accepted quotes" />

    @if (error(); as message) {
      <div class="error-banner" role="alert">
        <mat-icon aria-hidden="true">error_outline</mat-icon>
        <span>{{ message }}</span>
        <button mat-button type="button" (click)="reload()">Retry</button>
      </div>
    }

    @if (!loading() && !error() && rows().length === 0) {
      <app-empty-state
        icon="description"
        title="No contracts yet"
        message="A contract is created when a customer accepts a quote."
      />
    } @else if (!error()) {
      <app-data-table
        [columns]="columns"
        [data]="rows()"
        [loading]="loading()"
        [pagination]="pagination()"
        (pageChange)="onPage($event)"
        (sortChange)="onSort($event)"
        (rowClick)="open($event)"
      />
    }
  `,
  styles: [
    `
      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }
    `,
  ],
})
export class ContractListComponent implements OnInit {
  private readonly service = inject(ContractService);
  private readonly router = inject(Router);
  private readonly money = new CurrencyFormatPipe();

  readonly columns: TableColumn[] = [
    { key: 'subscriptionNumber', header: 'Contract', sortable: true },
    { key: 'customerId', header: 'Customer', sortable: true },
    { key: 'planName', header: 'Plan', sortable: true },
    { key: 'status', header: 'Status', sortable: true },
    { key: 'startDate', header: 'Start', sortable: true },
    { key: 'endDate', header: 'End', sortable: true },
    { key: 'arr', header: 'ARR', sortable: true },
  ];

  readonly contracts = signal<SubscriptionDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
  private readonly sort = signal<string | undefined>(undefined);

  readonly rows = computed<ContractRow[]>(() => this.contracts().map((c) => this.toRow(c)));

  ngOnInit(): void {
    this.reload();
  }

  reload(page = this.pagination().page, size = this.pagination().size): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list(page, size, this.sort()).subscribe({
      next: (p) => {
        this.contracts.set(p.content ?? []);
        this.pagination.set({
          page: p.number ?? page,
          size: p.size ?? size,
          totalElements: p.totalElements ?? 0,
          totalPages: p.totalPages ?? 0,
        });
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.error.set(err?.error?.message || err?.message || 'Contracts could not be loaded.');
        this.loading.set(false);
      },
    });
  }

  onPage(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onSort(event: SortChangeEvent): void {
    this.sort.set(event.direction ? `${event.active},${event.direction}` : undefined);
    this.reload(0);
  }

  open(row: ContractRow): void {
    void this.router.navigate(['/contracts', row.id]);
  }

  private toRow(c: SubscriptionDto): ContractRow {
    return {
      id: c.id,
      subscriptionNumber: c.subscriptionNumber ?? `Contract ${c.id}`,
      customerId: c.customerId ?? '—',
      planName: c.planName ?? '—',
      status: SUBSCRIPTION_STATUS_LABELS[c.status] ?? c.status,
      startDate: c.startDate ?? '—',
      endDate: c.endDate ?? '—',
      arr: c.arr === undefined || c.arr === null ? '—' : this.money.transform(c.arr, c.currencyCode || 'USD'),
    };
  }
}
