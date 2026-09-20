import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { OrdersService, orderError } from './orders.service';
import { ORDER_STATUSES, OrderView } from './orders.model';

@Component({
  selector: 'cpq-orders-list',
  standalone: true,
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './orders.css',
  template: `
    <header>
      <div>
        <h1>Orders</h1>
        <p class="muted">
          Track fulfillment from an accepted commercial snapshot.
        </p>
      </div>
      <a routerLink="exceptions">Exception queue</a>
    </header>
    <form class="filters" (ngSubmit)="load(0)">
      <label
        >Search orders<input
          name="q"
          [(ngModel)]="q"
          placeholder="Order or quote number"
      /></label>
      <label
        >Status<select name="status" [(ngModel)]="status">
          <option value="">All statuses</option>
          @for (s of statuses; track s) {
            <option [value]="s">{{ s }}</option>
          }
        </select></label
      >
      <label
        >Customer ID<input name="customer" [(ngModel)]="customerId"
      /></label>
      <button type="submit">Apply filters</button>
    </form>
    @if (error()) {
      <p role="alert" class="error">{{ error() }}</p>
      <button type="button" (click)="load(page())">Retry loading</button>
    }
    @if (loading()) {
      <p role="status">Loading orders…</p>
    }
    @if (!loading() && !error()) {
      @if (rows().length === 0) {
        <p>No orders match these filters.</p>
      } @else {
        <div class="table-wrap">
          <table>
            <caption>
              Orders in your authorized accounts
            </caption>
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Source quote</th>
                <th scope="col">Customer</th>
                <th scope="col">Status</th>
                <th scope="col">Accepted total</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.id) {
                <tr>
                  <td>
                    <a [routerLink]="[row.id]">{{ row.orderNumber }}</a>
                  </td>
                  <td>{{ row.quoteNumber }}</td>
                  <td>{{ row.customerId }}</td>
                  <td>
                    <span class="badge">{{ row.status }}</span>
                  </td>
                  <td>{{ row.snapshot.currency }} {{ row.snapshot.total }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      <nav aria-label="Order pagination">
        <button
          type="button"
          [disabled]="page() === 0"
          (click)="load(page() - 1)"
        >
          Previous</button
        ><span
          >Page {{ page() + 1 }} of {{ totalPages() || 1 }} ·
          {{ total() }} orders</span
        ><button
          type="button"
          [disabled]="page() + 1 >= totalPages()"
          (click)="load(page() + 1)"
        >
          Next
        </button>
      </nav>
    }
    <details>
      <summary>Create order from accepted quote</summary>
      <p>
        Use the persisted snapshot reference from an accepted quote. Commercial
        values are supplied by the source system.
      </p>
      <form (ngSubmit)="convert()">
        <fieldset [disabled]="converting()">
          <div class="grid">
            <label
              >Snapshot ID<input
                name="snapshotId"
                [(ngModel)]="snapshotId"
                required /></label
            ><label
              >Quote ID<input
                name="quoteId"
                [(ngModel)]="quoteId"
                required /></label
            ><label
              >Accepted revision<input
                type="number"
                min="1"
                step="1"
                name="revision"
                [(ngModel)]="revision"
                required
            /></label>
          </div>
          <button class="primary" type="submit">
            {{ converting() ? 'Creating order…' : 'Create order' }}
          </button>
        </fieldset>
      </form>
      @if (conversionError()) {
        <p role="alert" class="error">{{ conversionError() }}</p>
      }
    </details>
  `,
})
export class OrdersListComponent implements OnInit {
  private readonly api = inject(OrdersService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  private conversionFingerprint = '';
  private conversionKey = '';
  readonly statuses = ORDER_STATUSES;
  readonly rows = signal<OrderView[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly page = signal(0);
  readonly total = signal(0);
  readonly totalPages = signal(0);
  readonly converting = signal(false);
  readonly conversionError = signal('');
  q = '';
  status = '';
  customerId = '';
  snapshotId = '';
  quoteId = '';
  revision = 1;
  ngOnInit() {
    this.load(0);
  }
  load(page: number) {
    this.request?.unsubscribe();
    this.error.set('');
    this.rows.set([]);
    this.loading.set(true);
    this.request = this.api
      .list(page, {
        q: this.q,
        status: this.status,
        customerId: this.customerId,
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.rows.set(data.content);
          this.page.set(data.number);
          this.total.set(data.totalElements);
          this.totalPages.set(data.totalPages);
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(orderError(error));
          this.loading.set(false);
        },
      });
  }
  convert() {
    if (this.converting()) return;
    if (
      !this.snapshotId.trim() ||
      !this.quoteId.trim() ||
      !Number.isSafeInteger(this.revision) ||
      this.revision < 1
    ) {
      this.conversionError.set(
        'Provide the snapshot ID, quote ID and a positive whole accepted revision.',
      );
      return;
    }
    const source = {
      snapshotId: this.snapshotId.trim(),
      quoteId: this.quoteId.trim(),
      acceptedRevision: this.revision,
    };
    const fingerprint = JSON.stringify(source);
    if (fingerprint !== this.conversionFingerprint) {
      this.conversionFingerprint = fingerprint;
      this.conversionKey = crypto.randomUUID();
    }
    this.converting.set(true);
    this.conversionError.set('');
    this.api
      .convert(source, this.conversionKey)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (order) => {
          this.converting.set(false);
          void this.router.navigate(['/orders', order.id]);
        },
        error: (error) => {
          this.converting.set(false);
          this.conversionError.set(orderError(error));
        },
      });
  }
}
