import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  effect,
  DestroyRef,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

/**
 * Debounced search input component with Material Design styling
 *
 * @example
 * <app-search-input
 *   [placeholder]="'Search leads...'"
 *   [debounceMs]="300"
 *   (searchChange)="onSearch($event)"
 * />
 */
@Component({
  selector: 'app-search-input',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field appearance="outline" class="search-field">
      <mat-icon matPrefix class="search-icon">search</mat-icon>
      <input
        matInput
        type="text"
        [placeholder]="placeholder()"
        [(ngModel)]="searchValue"
        (ngModelChange)="onSearchChange($event)"
        class="search-input"
      />
      @if (searchValue()) {
        <button
          matSuffix
          mat-icon-button
          (click)="clearSearch()"
          class="clear-button"
          aria-label="Clear search"
        >
          <mat-icon>close</mat-icon>
        </button>
      }
    </mat-form-field>
  `,
  styles: [`
    .search-field {
      width: 100%;
      max-width: 400px;
    }

    .search-icon {
      color: #757575;
      margin-right: 8px;
    }

    .search-input {
      font-size: 14px;
    }

    .clear-button {
      margin-right: -8px;
    }

    :host ::ng-deep .mat-mdc-form-field-infix {
      min-height: 48px;
      display: flex;
      align-items: center;
    }

    :host ::ng-deep .mat-mdc-text-field-wrapper {
      padding-bottom: 0;
    }

    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper {
      display: none;
    }

    @media (max-width: 768px) {
      .search-field {
        max-width: 100%;
      }
    }
  `],
})
export class SearchInputComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchSubject = new Subject<string>();

  // Inputs using signal-based API
  placeholder = input<string>('Search...');
  debounceMs = input<number>(300);

  // Outputs using signal-based API
  searchChange = output<string>();

  // Internal state
  searchValue = signal<string>('');

  constructor() {
    // Set up debounced search
    this.searchSubject
      .pipe(
        debounceTime(this.debounceMs()),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((searchTerm) => {
        this.searchChange.emit(searchTerm);
      });
  }

  onSearchChange(value: string): void {
    this.searchValue.set(value);
    this.searchSubject.next(value);
  }

  clearSearch(): void {
    this.searchValue.set('');
    this.searchSubject.next('');
  }
}
