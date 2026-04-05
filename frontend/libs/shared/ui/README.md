# Shared UI Component Library

A comprehensive collection of reusable Angular 21 standalone components for the CRM frontend, built with Angular Material and modern Angular features.

## Features

- Modern Angular 21 with standalone components
- Signal-based inputs and outputs using `input()` and `output()`
- OnPush change detection for optimal performance
- Control flow syntax (@if, @for, @switch)
- Inline templates and styles
- Comprehensive TypeScript typing
- Full accessibility support
- Responsive design

## Components

### 1. DataTableComponent

Generic reusable data table with sorting, pagination, and loading states.

**Path:** `libs/shared/ui/src/lib/data-table/data-table.component.ts`

**Features:**

- Mat-table integration
- Sortable columns
- Pagination with page size options
- Loading spinner overlay
- Empty state handling
- Row click events
- Responsive design

**Usage:**

```typescript
import { DataTableComponent, TableColumn } from '@frontend/shared-ui';

@Component({
  template: `
    <app-data-table
      [columns]="columns"
      [data]="tableData"
      [loading]="isLoading"
      [pagination]="paginationMeta"
      (pageChange)="onPageChange($event)"
      (sortChange)="onSortChange($event)"
      (rowClick)="onRowClick($event)"
    />
  `
})
export class MyComponent {
  columns: TableColumn[] = [
    { key: 'id', header: 'ID', type: 'number' },
    { key: 'name', header: 'Name', type: 'text', sortable: true },
    { key: 'amount', header: 'Amount', type: 'currency', sortable: true },
    { key: 'date', header: 'Date', type: 'date', sortable: true },
  ];

  tableData = [...];
  isLoading = false;
  paginationMeta = { page: 0, size: 10, totalElements: 100, totalPages: 10 };

  onPageChange(event: PageEvent) {
    console.log('Page changed:', event);
  }

  onSortChange(event: SortChangeEvent) {
    console.log('Sort changed:', event);
  }

  onRowClick(row: any) {
    console.log('Row clicked:', row);
  }
}
```

### 2. ConfirmDialogComponent

MatDialog-based confirmation dialog for user confirmations.

**Path:** `libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.ts`

**Features:**

- Customizable title and message
- Configurable button text and colors
- Returns true/false on close
- Supports warn color for dangerous actions

**Usage:**

```typescript
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '@frontend/shared-ui';

@Component({...})
export class MyComponent {
  constructor(private dialog: MatDialog) {}

  openConfirmDialog() {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete Item',
        message: 'Are you sure you want to delete this item? This action cannot be undone.',
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === true) {
        // User confirmed
        this.deleteItem();
      }
    });
  }
}
```

### 3. StatusBadgeComponent

Color-coded badge for displaying lead statuses and deal stages.

**Path:** `libs/shared/ui/src/lib/status-badge/status-badge.component.ts`

**Features:**

- Predefined color schemes for lead statuses
- Predefined color schemes for deal stages
- Automatic label formatting
- Accessible and readable

**Lead Statuses:**

- NEW (Blue)
- CONTACTED (Light Blue)
- QUALIFIED (Purple)
- CONVERTED (Green)
- LOST (Red)

**Deal Stages:**

- PROSPECTING (Blue)
- QUALIFICATION (Light Blue)
- PROPOSAL (Orange)
- NEGOTIATION (Purple)
- CLOSED_WON (Green)
- CLOSED_LOST (Red)

**Usage:**

```typescript
import { StatusBadgeComponent } from '@frontend/shared-ui';

@Component({
  template: `
    <app-status-badge [status]="'NEW'" [type]="'lead'" />
    <app-status-badge [status]="'CLOSED_WON'" [type]="'deal'" />
  `,
})
export class MyComponent {}
```

### 4. EmptyStateComponent

Placeholder component for empty lists or tables.

**Path:** `libs/shared/ui/src/lib/empty-state/empty-state.component.ts`

**Features:**

- Customizable icon, title, and message
- Optional call-to-action button
- Responsive layout
- Centered design

**Usage:**

```typescript
import { EmptyStateComponent } from '@frontend/shared-ui';

@Component({
  template: ` <app-empty-state [icon]="'inbox'" [title]="'No leads found'" [message]="'Get started by creating your first lead.'" [actionLabel]="'Create Lead'" (action)="onCreateLead()" /> `,
})
export class MyComponent {
  onCreateLead() {
    console.log('Create lead action triggered');
  }
}
```

### 5. PageHeaderComponent

Page title bar with content projection for actions and search.

**Path:** `libs/shared/ui/src/lib/page-header/page-header.component.ts`

**Features:**

- Title and subtitle display
- Content projection slots for actions
- Content projection slot for search
- Responsive design with mobile optimization

**Usage:**

```typescript
import { PageHeaderComponent } from '@frontend/shared-ui';

@Component({
  template: `
    <app-page-header [title]="'Leads'" [subtitle]="'Manage your sales leads'">
      <div actions>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon>
          New Lead
        </button>
      </div>
      <div search>
        <app-search-input (searchChange)="onSearch($event)" />
      </div>
    </app-page-header>
  `,
})
export class MyComponent {
  onCreate() {
    console.log('Create clicked');
  }

  onSearch(term: string) {
    console.log('Search term:', term);
  }
}
```

### 6. SearchInputComponent

Debounced search input with Material Design styling.

**Path:** `libs/shared/ui/src/lib/search-input/search-input.component.ts`

**Features:**

- Automatic debouncing (default 300ms)
- Clear button when text is entered
- Search icon prefix
- Configurable placeholder
- OnPush change detection

**Usage:**

```typescript
import { SearchInputComponent } from '@frontend/shared-ui';

@Component({
  template: ` <app-search-input [placeholder]="'Search leads...'" [debounceMs]="300" (searchChange)="onSearch($event)" /> `,
})
export class MyComponent {
  onSearch(searchTerm: string) {
    console.log('Search term:', searchTerm);
    // Perform search operation
  }
}
```

## Pipes

### 1. CurrencyFormatPipe

Formats deal amounts as currency with proper locale formatting.

**Path:** `libs/shared/ui/src/lib/pipes/currency-format.pipe.ts`

**Features:**

- Supports multiple currencies (USD, EUR, GBP, etc.)
- Configurable display format (symbol, code, symbol-narrow)
- Locale-aware formatting
- Handles null/undefined gracefully
- Falls back to basic formatting on error

**Usage:**

```typescript
import { CurrencyFormatPipe } from '@frontend/shared-ui';

@Component({
  template: `
    <p>Amount: {{ amount | currencyFormat }}</p>
    <p>EUR: {{ amount | currencyFormat: 'EUR' }}</p>
    <p>GBP: {{ amount | currencyFormat: 'GBP' : 'symbol-narrow' }}</p>
  `,
})
export class MyComponent {
  amount = 1234.56; // Displays as $1,234.56
}
```

### 2. DateAgoPipe

Transforms dates into relative time strings.

**Path:** `libs/shared/ui/src/lib/pipes/date-ago.pipe.ts`

**Features:**

- Human-readable relative dates
- Handles multiple time units (seconds to years)
- Supports Date objects and ISO strings
- Handles null/undefined gracefully
- Smart singular/plural handling

**Time Units:**

- just now (< 10 seconds)
- less than a minute ago (< 60 seconds)
- X minute(s) ago
- X hour(s) ago
- X day(s) ago
- X week(s) ago
- X month(s) ago
- X year(s) ago

**Usage:**

```typescript
import { DateAgoPipe } from '@frontend/shared-ui';

@Component({
  template: `
    <p>Created: {{ createdAt | dateAgo }}</p>
    <p>Updated: {{ updatedAt | dateAgo }}</p>
  `,
})
export class MyComponent {
  createdAt = '2024-01-15T10:30:00Z'; // Displays as "3 days ago"
  updatedAt = new Date(); // Displays as "just now"
}
```

## Installation

The library is already set up in the Nx monorepo. Simply import the components and pipes you need:

```typescript
import { DataTableComponent, ConfirmDialogComponent, StatusBadgeComponent, EmptyStateComponent, PageHeaderComponent, SearchInputComponent, CurrencyFormatPipe, DateAgoPipe } from '@frontend/shared-ui';
```

## Testing

All components and pipes include comprehensive test suites. Run tests with:

```bash
nx test shared-ui
```

## Accessibility

All components follow WCAG 2.1 AA guidelines:

- Proper ARIA attributes
- Keyboard navigation support
- Screen reader compatibility
- Sufficient color contrast
- Focus management

## Performance

All components use:

- OnPush change detection strategy
- Signal-based inputs/outputs for fine-grained reactivity
- Lazy loading where applicable
- Optimized rendering with control flow syntax

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Contributing

When adding new components:

1. Use Angular 21 standalone component syntax
2. Implement OnPush change detection
3. Use signal-based APIs (`input()`, `output()`)
4. Use control flow syntax (@if, @for, @switch)
5. Include inline templates and styles
6. Write comprehensive tests
7. Update the index.ts export file
8. Document usage with examples

## License

MIT
