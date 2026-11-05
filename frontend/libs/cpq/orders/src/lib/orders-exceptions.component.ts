import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { PendingOperations, definitiveRejection } from './pending-operations';
import { OrdersService, orderError } from './orders.service';
import { OrderException } from './orders.model';

@Component({
  selector: 'cpq-orders-exceptions',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './orders.css',
  template: `
    <header>
      <div>
        <a routerLink="/orders">All orders</a>
        <h1>Order exceptions</h1>
        <p>
          Assign ownership and reconcile uncertain outcomes before retrying.
        </p>
      </div>
      <button
        type="button"
        (click)="load(page())"
        [disabled]="pending() || loading()"
      >
        Reload exceptions
      </button>
    </header>
    <label
      >Status filter<select
        [(ngModel)]="status"
        (ngModelChange)="load(0)"
        [disabled]="pending()"
      >
        <option value="OPEN">Open</option>
        <option value="RESOLVED">Resolved</option>
      </select></label
    >
    @if (loading()) {
      <p role="status">Loading exceptions…</p>
    }
    @if (pending()) {
      <p role="status">Recording recovery command…</p>
    }
    @if (error()) {
      <p role="alert" class="error">{{ error() }}</p>
    }
    @if (signInRequired()) {
      <a href="/oauth2/authorization/oms">Sign in to OMS</a>
    }
    @if (notice()) {
      <p role="status" class="notice">{{ notice() }}</p>
    }
    @if (!loading() && !error() && !rows().length) {
      <p>No exceptions match this filter.</p>
    }
    <div class="table-wrap">
      <table>
        <caption>
          Exception queue
        </caption>
        <thead>
          <tr>
            <th>Order</th>
            <th>Category / severity</th>
            <th>Owner</th>
            <th>Reason / next action</th>
            <th>First / last seen</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.id) {
            <tr>
              <td>
                <a [routerLink]="['/orders', row.orderId]">{{ row.orderId }}</a>
              </td>
              <td>{{ row.category }} / {{ row.severity }}</td>
              <td>{{ row.owner || 'Unassigned' }}</td>
              <td>{{ row.reason }}<br />{{ row.nextAction }}</td>
              <td>
                {{ row.firstOccurredAt | date: 'medium' }}<br />{{
                  row.lastOccurredAt | date: 'medium'
                }}
              </td>
              <td>
                {{ row.status }}
                @if (blocked().has(row.id)) {
                  <span>Earlier command unresolved</span>
                }
              </td>
              <td>
                <button
                  type="button"
                  [disabled]="pending() || stale() || row.status !== 'OPEN'"
                  (click)="select(row)"
                >
                  Manage exception
                </button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    <nav aria-label="Exception pagination">
      <button
        type="button"
        [disabled]="page() === 0 || loading() || pending()"
        (click)="load(page() - 1)"
      >
        Previous</button
      ><span>Page {{ page() + 1 }} of {{ pages() || 1 }}</span
      ><button
        type="button"
        [disabled]="page() + 1 >= pages() || loading() || pending()"
        (click)="load(page() + 1)"
      >
        Next
      </button>
    </nav>
    @if (selected(); as item) {
      <section>
        <h2>Manage {{ item.category }}</h2>
        <p>Exception {{ item.id }} · version {{ item.version }}</p>
        <p>
          Assignment needs oms:assign. Recovery needs oms:recover; unknown
          outcomes must reconcile before retry.
        </p>
        @if (blocked().has(item.id)) {
          <p role="status">
            An earlier command is unresolved. Reload checks its original
            operation key. Ask an operator to reconcile before sending another
            recovery command.
          </p>
        }
        <fieldset
          [disabled]="
            pending() || stale() || loading() || blocked().has(item.id)
          "
        >
          <label
            >Recovery reason<textarea
              [(ngModel)]="reason"
              required
            ></textarea></label
          ><label>Owner<input [(ngModel)]="owner" /></label
          ><button type="button" (click)="assign()">Assign owner</button>
          <label
            >Resolution action<select [(ngModel)]="action">
              <option value="RECONCILE">Reconcile</option>
              <option value="RETRY">Retry</option>
              <option value="WAIVE">Waive</option>
            </select></label
          >
          <label class="check"
            ><input type="checkbox" [(ngModel)]="confirmed" /> I confirm the
            selected recovery action and reason.</label
          ><button type="button" (click)="resolve()">Apply recovery</button>
        </fieldset>
      </section>
    }
  `,
})
export class OrdersExceptionsComponent implements OnInit {
  private readonly api = inject(OrdersService);
  private readonly destroy = inject(DestroyRef);
  private readonly journal = inject(PendingOperations);
  private request?: Subscription;
  readonly rows = signal<OrderException[]>([]);
  readonly selected = signal<OrderException | null>(null);
  readonly loading = signal(false);
  readonly pending = signal(false);
  readonly stale = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly blocked = signal(new Set<string>());
  readonly signInRequired = signal(false);
  readonly page = signal(0);
  readonly pages = signal(0);
  status = 'OPEN';
  owner = '';
  reason = '';
  action = 'RECONCILE';
  confirmed = false;
  ngOnInit() {
    this.load(0);
  }
  load(page: number) {
    if (this.pending()) return;
    this.request?.unsubscribe();
    this.error.set('');
    this.loading.set(true);
    this.selected.set(null);
    this.rows.set([]);
    this.request = this.api
      .exceptions(page, this.status)
      .pipe(
        switchMap((data) => {
          const requests = data.content.flatMap((row) =>
            this.journal.read('exception', row.id).map((entry) =>
              this.api.operation('exception', row.id, entry.operationKey).pipe(
                map((outcome) => ({
                  id: row.id,
                  orderId: row.orderId,
                  outcome,
                })),
                catchError(() => of(null)),
              ),
            ),
          );
          return (requests.length ? forkJoin(requests) : of([])).pipe(
            map((outcomes) => ({ data, outcomes })),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: ({ data, outcomes }) => {
          this.signInRequired.set(false);
          try {
            for (const result of outcomes)
              if (
                result?.outcome.status === 'SUCCEEDED' &&
                result.outcome.orderId === result.orderId
              )
                this.journal.complete(
                  'exception',
                  result.id,
                  result.outcome.operationKey,
                );
            this.blocked.set(
              new Set(
                data.content
                  .filter(
                    (row) => this.journal.read('exception', row.id).length > 0,
                  )
                  .map((row) => row.id),
              ),
            );
          } catch {
            this.error.set(
              'Cannot update the operation journal. Ask an operator to reconcile.',
            );
            this.loading.set(false);
            this.stale.set(true);
            return;
          }
          this.rows.set(data.content);
          this.page.set(data.number);
          this.pages.set(data.totalPages);
          this.loading.set(false);
          this.stale.set(false);
        },
        error: (error) => {
          this.signInRequired.set(error?.status === 401);
          this.error.set(orderError(error));
          this.loading.set(false);
          this.stale.set(true);
        },
      });
  }
  select(row: OrderException) {
    this.selected.set(row);
    this.owner = row.owner || '';
    this.reason = '';
    this.confirmed = false;
    this.action = 'RECONCILE';
  }
  assign() {
    if (!this.owner.trim()) {
      this.error.set('Enter an owner before assigning this exception.');
      return;
    }
    this.run('assign', { owner: this.owner.trim() });
  }
  resolve() {
    if (!this.confirmed) {
      this.error.set(
        'Confirm the recovery action and reason before continuing.',
      );
      return;
    }
    if (
      this.action === 'RETRY' &&
      this.selected()?.category.includes('UNKNOWN')
    ) {
      this.error.set('Reconcile the unknown outcome before retrying.');
      return;
    }
    this.run('resolve', { action: this.action });
  }
  private run(action: 'assign' | 'resolve', extra: Record<string, unknown>) {
    const row = this.selected();
    if (
      !row ||
      this.pending() ||
      this.stale() ||
      this.loading() ||
      this.blocked().has(row.id)
    )
      return;
    if (!this.reason.trim()) {
      this.error.set('A recovery reason is required.');
      return;
    }
    this.pending.set(true);
    this.error.set('');
    this.notice.set('');
    const operationKey = crypto.randomUUID();
    try {
      this.journal.begin('exception', row.id, {
        operationKey,
        action,
        expectedVersion: row.version,
      });
      this.blocked.set(new Set([...this.blocked(), row.id]));
    } catch {
      this.pending.set(false);
      this.stale.set(true);
      this.error.set(
        'Cannot preserve this operation safely. No recovery command was sent.',
      );
      return;
    }
    this.api
      .exceptionCommand(row.id, action, {
        ...extra,
        expectedVersion: row.version,
        reason: this.reason.trim(),
        operationKey,
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (response) => {
          if (response.status !== 202) {
            try {
              this.journal.complete('exception', row.id, operationKey);
            } catch {
              /* Reload verifies persisted outcome. */
            }
          }
          this.pending.set(false);
          this.notice.set(
            response.status === 202
              ? 'Recovery command persisted; processing is pending.'
              : 'Recovery command recorded.',
          );
          this.load(this.page());
        },
        error: (error) => {
          if (definitiveRejection(error)) {
            try {
              this.journal.complete('exception', row.id, operationKey);
            } catch {
              /* Preserve gate if storage fails. */
            }
          }
          this.signInRequired.set(error?.status === 401);
          this.pending.set(false);
          this.stale.set(true);
          this.error.set(orderError(error));
        },
      });
  }
}
