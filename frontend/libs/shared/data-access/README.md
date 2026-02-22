# Shared Data Access Library

This library provides a comprehensive set of services and interceptors for managing HTTP communication, state management, and API interactions in the Angular 21 CRM frontend.

## Features

- **API Response Interceptor**: Automatically unwraps backend `ApiResponse<T>` envelopes
- **Error Interceptor**: Global error handling with user-friendly notifications
- **Loading Service**: Signal-based loading state tracking
- **Notification Service**: Consistent UI notifications using Material Snackbar
- **Base CRUD Service**: Generic service with signal-based state management for CRUD operations

## Installation & Setup

### 1. Configure HTTP Interceptors

In your `app.config.ts`:

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { apiResponseInterceptor, errorInterceptor } from '@frontend/shared-data-access';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withInterceptors([
        apiResponseInterceptor,
        errorInterceptor
      ])
    ),
    provideAnimations() // Required for Material Snackbar
  ]
};
```

### 2. Add Snackbar Styles

In your global styles file (`styles.css` or `styles.scss`), add custom snackbar styling:

```css
/* Snackbar customization */
.snackbar-success {
  --mdc-snackbar-container-color: #4caf50;
  --mdc-snackbar-supporting-text-color: white;
}

.snackbar-error {
  --mdc-snackbar-container-color: #f44336;
  --mdc-snackbar-supporting-text-color: white;
}

.snackbar-info {
  --mdc-snackbar-container-color: #2196f3;
  --mdc-snackbar-supporting-text-color: white;
}

.snackbar-warning {
  --mdc-snackbar-container-color: #ff9800;
  --mdc-snackbar-supporting-text-color: white;
}
```

## Usage

### API Response Interceptor

The `apiResponseInterceptor` automatically handles the backend's `ApiResponse<T>` envelope:

**Backend Response:**
```json
{
  "success": true,
  "message": "Contact retrieved successfully",
  "data": {
    "id": "123",
    "firstName": "John",
    "lastName": "Doe"
  },
  "timestamp": "2026-02-06T10:00:00Z"
}
```

**What Your Service Receives:**
```typescript
{
  "id": "123",
  "firstName": "John",
  "lastName": "Doe"
}
```

The interceptor extracts the `.data` property automatically, so your services work with clean DTOs.

### Error Interceptor

The `errorInterceptor` catches all HTTP errors and displays Material Snackbar notifications. No manual error handling needed in most cases:

```typescript
// Errors are automatically shown to users via snackbar
this.contactService.create(contactData).subscribe({
  next: (contact) => {
    // Handle success
  }
  // No error handler needed - interceptor handles it
});
```

### Loading Service

Track global loading state across your application:

```typescript
import { Component, inject } from '@angular/core';
import { LoadingService } from '@frontend/shared-data-access';

@Component({
  selector: 'app-root',
  template: `
    @if (isLoading()) {
      <mat-progress-bar mode="indeterminate"></mat-progress-bar>
    }
    <router-outlet />
  `
})
export class AppComponent {
  private loadingService = inject(LoadingService);
  isLoading = this.loadingService.loading;
}
```

### Notification Service

Display consistent notifications throughout your app:

```typescript
import { Component, inject } from '@angular/core';
import { NotificationService } from '@frontend/shared-data-access';

@Component({
  selector: 'app-my-component',
  template: `...`
})
export class MyComponent {
  private notificationService = inject(NotificationService);

  onSave() {
    this.notificationService.success('Contact saved successfully!');
  }

  onDelete() {
    this.notificationService.warning('Are you sure you want to delete?');
  }

  onError() {
    this.notificationService.error('Failed to save contact');
  }

  onInfo() {
    this.notificationService.info('Loading contacts...');
  }
}
```

### Base CRUD Service

Create type-safe services with built-in state management:

#### Step 1: Define Your Models

```typescript
// contact.model.ts
export interface Contact {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
}

export interface CreateContactRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
}

export interface UpdateContactRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
}
```

#### Step 2: Create Your Service

```typescript
// contact.service.ts
import { Injectable } from '@angular/core';
import { BaseCrudService } from '@frontend/shared-data-access';
import { Contact, CreateContactRequest, UpdateContactRequest } from './contact.model';

@Injectable({ providedIn: 'root' })
export class ContactService extends BaseCrudService<
  Contact,
  CreateContactRequest,
  UpdateContactRequest
> {
  protected override basePath = '/api/v1/contacts';
}
```

#### Step 3: Use in Components

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { ContactService } from './contact.service';

@Component({
  selector: 'app-contacts',
  template: `
    @if (contactService.loading()) {
      <mat-spinner></mat-spinner>
    }

    @for (contact of contactService.items(); track contact.id) {
      <div class="contact-card">
        <h3>{{ contact.firstName }} {{ contact.lastName }}</h3>
        <p>{{ contact.email }}</p>
        <button (click)="onEdit(contact.id)">Edit</button>
        <button (click)="onDelete(contact.id)">Delete</button>
      </div>
    }

    <mat-paginator
      [length]="contactService.pagination().totalElements"
      [pageSize]="contactService.pagination().size"
      [pageIndex]="contactService.pagination().page"
      (page)="onPageChange($event)">
    </mat-paginator>
  `
})
export class ContactsComponent implements OnInit {
  contactService = inject(ContactService);

  ngOnInit() {
    this.loadContacts();
  }

  loadContacts(page: number = 0, size: number = 20) {
    this.contactService.list({ page, size, sort: 'lastName,asc' }).subscribe();
  }

  onPageChange(event: PageEvent) {
    this.loadContacts(event.pageIndex, event.pageSize);
  }

  onCreate() {
    const newContact: CreateContactRequest = {
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane@example.com'
    };

    this.contactService.create(newContact).subscribe();
  }

  onEdit(id: string) {
    this.contactService.getById(id).subscribe(() => {
      // selectedItem signal is automatically updated
      const selected = this.contactService.selectedItem();
      // Open edit dialog with selected contact
    });
  }

  onUpdate(id: string) {
    const updates: UpdateContactRequest = {
      email: 'newemail@example.com'
    };

    this.contactService.update(id, updates).subscribe();
  }

  onDelete(id: string) {
    this.contactService.delete(id).subscribe();
  }

  onSearch(query: string) {
    this.contactService.search(query, { page: 0, size: 20 }).subscribe();
  }
}
```

## API Reference

### BaseCrudService

#### Signals

- `items: Signal<T[]>` - List of entities
- `selectedItem: Signal<T | null>` - Currently selected entity
- `loading: Signal<boolean>` - Loading state
- `pagination: Signal<PaginationMeta>` - Pagination metadata
- `hasNextPage: Signal<boolean>` - Computed: whether next page exists
- `hasPreviousPage: Signal<boolean>` - Computed: whether previous page exists

#### Methods

- `list(params?: PageRequest): Observable<T[]>` - Fetch paginated list
- `getById(id: string | number): Observable<T>` - Fetch single entity
- `create(request: TCreate): Observable<T>` - Create new entity
- `update(id: string | number, request: TUpdate): Observable<T>` - Update entity
- `delete(id: string | number): Observable<void>` - Delete entity
- `search(query: string, params?: PageRequest): Observable<T[]>` - Search entities
- `clearSelection(): void` - Clear selected item
- `clearItems(): void` - Clear items list
- `reset(): void` - Reset all state

### LoadingService

#### Signals

- `loading: Signal<number>` - Number of active requests (readonly)

#### Methods

- `startRequest(): void` - Increment request counter
- `endRequest(): void` - Decrement request counter
- `reset(): void` - Reset counter to 0
- `getRequestCount(): number` - Get current count

### NotificationService

#### Methods

- `success(message: string, duration?: number): void` - Show success notification
- `error(message: string, duration?: number): void` - Show error notification
- `info(message: string, duration?: number): void` - Show info notification
- `warning(message: string, duration?: number): void` - Show warning notification
- `dismiss(): void` - Dismiss all notifications

## Backend Integration

This library expects the backend to return responses in this format:

```typescript
interface ApiResponse<T> {
  success: boolean;
  message: string;
  errorCode?: string;
  traceId?: string;
  data?: T;
  errors?: string[];
  pagination?: PaginationMeta;
  timestamp: string;
}
```

For paginated endpoints, `data` should contain a Spring Boot Page:

```typescript
interface SpringPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
```

## Running unit tests

Run `nx test shared-data-access` to execute the unit tests.

Test files are provided for all services:

- `loading.service.spec.ts` - Tests for LoadingService
- `notification.service.spec.ts` - Tests for NotificationService
- `base-crud.service.spec.ts` - Tests for BaseCrudService

## Best Practices

1. **Always extend BaseCrudService** for entity-specific services
2. **Use signals in components** to automatically react to state changes
3. **Let interceptors handle errors** - only add custom error handling when needed
4. **Subscribe to operations** to trigger API calls, signals update automatically
5. **Use TypeScript generics** to maintain type safety across your services

## License

MIT
