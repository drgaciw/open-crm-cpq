import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import { ProductDto } from '@frontend/shared-models';
import { ProductService } from '../product.service';

@Component({
  selector: 'cpq-catalog-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (product(); as product) {
      <app-page-header [title]="product.name" [subtitle]="product.sku">
        <div actions>
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to catalog
          </button>
        </div>
      </app-page-header>

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Product details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Status</span>
                <app-status-badge [status]="product.status" type="product" />
              </div>
              <div class="info-item">
                <span class="label">Category</span>
                <span class="value">{{ product.category }}</span>
              </div>
              <div class="info-item">
                <span class="label">Product type</span>
                <span class="value">{{ product.productType }}</span>
              </div>
              <div class="info-item">
                <span class="label">List price</span>
                <span class="value">{{
                  (product.listPrice ?? 0)
                    | currencyFormat: product.currency || 'USD'
                }}</span>
              </div>
              <div class="info-item full-width">
                <span class="label">Description</span>
                <span class="value">{{ product.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Product not found"
        [message]="
          error() ||
          'This catalog item could not be loaded from the product catalog API.'
        "
        actionLabel="Back to catalog"
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

      .detail-body {
        padding: 0 32px 32px;
      }

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

      @media (max-width: 768px) {
        .detail-body {
          padding: 0 16px 24px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class CatalogDetailComponent implements OnInit {
  private readonly router = inject(Router);
  readonly service = inject(ProductService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());

  readonly product = computed<ProductDto | null>(() => {
    const selected = this.service.selectedProduct();
    if (!selected) {
      return null;
    }
    const raw = this.id();
    const numericId = Number(raw);
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.service.selectedProduct.set(null);
      this.service.error.set('Invalid product id');
      this.service.loading.set(false);
      return;
    }
    this.service.getById(numericId);
  }

  goBack(): void {
    void this.router.navigate(['/catalog']);
  }
}
