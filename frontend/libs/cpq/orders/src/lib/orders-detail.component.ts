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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Subscription } from 'rxjs';
import { OrdersService, orderError } from './orders.service';
import {
  EvidenceKind,
  OrderTask,
  OrderTimeline,
  OrderView,
} from './orders.model';

@Component({
  selector: 'cpq-orders-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './orders.css',
  templateUrl: './orders-detail.component.html',
})
export class OrdersDetailComponent implements OnInit {
  private readonly api = inject(OrdersService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  private id = '';
  readonly order = signal<OrderView | null>(null);
  readonly tasks = signal<OrderTask[]>([]);
  readonly timeline = signal<OrderTimeline[]>([]);
  readonly loading = signal(false);
  readonly pending = signal(false);
  readonly stale = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly evidenceKinds: EvidenceKind[] = [
    'ALLOCATION',
    'SHIPMENT',
    'DELIVERY',
    'SERVICE',
    'FINANCE_ACK',
  ];
  commandReason = '';
  holdActive = true;
  cancelLine = '';
  cancelQuantity = 1;
  cancelReason = '';
  cancelConfirmed = false;
  evidenceLine = '';
  evidenceKind: EvidenceKind = 'ALLOCATION';
  evidenceQuantity = 1;
  evidenceReason = '';
  evidenceReference = '';
  evidenceAt = '';
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe((params) => {
        this.id = params.get('id') || '';
        this.order.set(null);
        this.notice.set('');
        this.load();
      });
  }
  load() {
    if (this.pending()) return;
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set('');
    this.stale.set(true);
    this.request = forkJoin({
      order: this.api.detail(this.id),
      tasks: this.api.plan(this.id),
      timeline: this.api.timeline(this.id),
    })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.order.set(data.order);
          this.tasks.set(data.tasks);
          this.timeline.set(data.timeline);
          this.loading.set(false);
          this.stale.set(false);
          this.cancelConfirmed = false;
        },
        error: (error) => {
          this.error.set(orderError(error));
          this.loading.set(false);
          this.order.set(null);
          this.tasks.set([]);
          this.timeline.set([]);
        },
      });
  }
  submit() {
    this.run('submit', {}, this.commandReason);
  }
  hold() {
    this.run('holds', { active: this.holdActive }, this.commandReason);
  }
  reconcile() {
    this.run('reconcile', {}, this.commandReason);
  }
  retry(task: OrderTask) {
    if (task.status.includes('UNKNOWN')) {
      this.error.set(
        'Reconcile the unknown outcome before retrying this task.',
      );
      return;
    }
    this.run(
      `tasks/${encodeURIComponent(task.id)}/retry`,
      {},
      this.commandReason,
    );
  }
  cancel() {
    const line = this.order()?.lines.find(
      (line) => line.id === this.cancelLine,
    );
    if (
      !this.cancelConfirmed ||
      !line ||
      !Number.isSafeInteger(this.cancelQuantity) ||
      this.cancelQuantity <= 0 ||
      this.cancelQuantity > line.remaining
    ) {
      this.error.set(
        'Choose a line, enter a positive whole quantity within its remaining quantity, and confirm cancellation.',
      );
      return;
    }
    this.run(
      'cancellation-requests',
      { lineId: this.cancelLine, quantity: this.cancelQuantity },
      this.cancelReason,
    );
  }
  recordEvidence() {
    if (
      !this.order()?.lines.some((line) => line.id === this.evidenceLine) ||
      !Number.isSafeInteger(this.evidenceQuantity) ||
      this.evidenceQuantity < 1 ||
      !this.evidenceReference.trim() ||
      !this.evidenceAt ||
      Number.isNaN(new Date(this.evidenceAt).getTime())
    ) {
      this.error.set(
        'Choose a line and provide a positive whole quantity, real external reference and occurrence time.',
      );
      return;
    }
    this.run(
      'evidence',
      {
        lineId: this.evidenceLine,
        kind: this.evidenceKind,
        quantity: this.evidenceQuantity,
        externalReference: this.evidenceReference.trim(),
        occurredAt: new Date(this.evidenceAt).toISOString(),
      },
      this.evidenceReason,
    );
  }
  private run(action: string, extra: Record<string, unknown>, reason: string) {
    const order = this.order();
    if (!order || this.pending() || this.loading() || this.stale()) return;
    if (!reason.trim()) {
      this.error.set('A reason is required for every order command.');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.pending.set(true);
    this.api
      .command(order.id, action, {
        ...extra,
        reason: reason.trim(),
        expectedVersion: order.version,
        operationKey: crypto.randomUUID(),
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (response) => {
          this.pending.set(false);
          this.notice.set(
            response.status === 202
              ? 'Command persisted; processing is pending. Reload to see the latest server state.'
              : 'Command recorded. The latest server state is shown below.',
          );
          this.load();
        },
        error: (error) => {
          this.pending.set(false);
          this.error.set(orderError(error));
          this.stale.set(true);
        },
      });
  }
}
