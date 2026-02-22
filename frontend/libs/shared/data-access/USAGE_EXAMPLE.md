# Usage Example: Complete Integration Guide

This guide shows how to integrate the shared-data-access library into your Angular 21 CRM application.

## Step 1: Configure Application

### app.config.ts

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import {
  loadingInterceptor,
  apiResponseInterceptor,
  errorInterceptor
} from '@frontend/shared-data-access';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(
      withInterceptors([
        loadingInterceptor,      // Track request count
        apiResponseInterceptor,  // Unwrap ApiResponse envelope
        errorInterceptor         // Handle errors with notifications
      ])
    )
  ]
};
```

## Step 2: Add Global Styles

### styles.css

```css
/* Material Snackbar Customization */
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

## Step 3: Create Domain Service

### libs/contacts/data-access/src/lib/contact.service.ts

```typescript
import { Injectable } from '@angular/core';
import { BaseCrudService } from '@frontend/shared-data-access';
import {
  Contact,
  CreateContactRequest,
  UpdateContactRequest
} from '@frontend/shared-models';

@Injectable({ providedIn: 'root' })
export class ContactService extends BaseCrudService<
  Contact,
  CreateContactRequest,
  UpdateContactRequest
> {
  protected override basePath = '/api/v1/contacts';

  // You can add custom methods here
  // The base methods (list, getById, create, update, delete, search) are inherited
}
```

## Step 4: Use in Component

### apps/crm/src/app/contacts/contacts.component.ts

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ContactService } from '@frontend/contacts-data-access';
import { NotificationService } from '@frontend/shared-data-access';

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatCardModule,
    MatPaginatorModule
  ],
  template: `
    <div class="contacts-container">
      <div class="header">
        <h1>Contacts</h1>
        <button mat-raised-button color="primary" (click)="onCreate()">
          Add Contact
        </button>
      </div>

      @if (contactService.loading()) {
        <div class="loading-container">
          <mat-spinner></mat-spinner>
        </div>
      }

      @if (!contactService.loading() && contactService.items().length === 0) {
        <div class="empty-state">
          <p>No contacts found.</p>
        </div>
      }

      <div class="contacts-grid">
        @for (contact of contactService.items(); track contact.id) {
          <mat-card class="contact-card">
            <mat-card-header>
              <mat-card-title>
                {{ contact.firstName }} {{ contact.lastName }}
              </mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p><strong>Email:</strong> {{ contact.email }}</p>
              @if (contact.phone) {
                <p><strong>Phone:</strong> {{ contact.phone }}</p>
              }
            </mat-card-content>
            <mat-card-actions>
              <button mat-button (click)="onEdit(contact.id)">Edit</button>
              <button mat-button color="warn" (click)="onDelete(contact.id)">
                Delete
              </button>
            </mat-card-actions>
          </mat-card>
        }
      </div>

      @if (contactService.items().length > 0) {
        <mat-paginator
          [length]="contactService.pagination().totalElements"
          [pageSize]="contactService.pagination().size"
          [pageIndex]="contactService.pagination().page"
          [pageSizeOptions]="[10, 20, 50, 100]"
          (page)="onPageChange($event)">
        </mat-paginator>
      }
    </div>
  `,
  styles: [`
    .contacts-container {
      padding: 24px;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }

    .loading-container {
      display: flex;
      justify-content: center;
      padding: 48px;
    }

    .empty-state {
      text-align: center;
      padding: 48px;
      color: #666;
    }

    .contacts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }

    .contact-card {
      height: 100%;
    }
  `]
})
export class ContactsComponent implements OnInit {
  contactService = inject(ContactService);
  private notificationService = inject(NotificationService);

  ngOnInit() {
    this.loadContacts();
  }

  loadContacts(page: number = 0, size: number = 20) {
    this.contactService
      .list({ page, size, sort: 'lastName,asc' })
      .subscribe({
        next: () => {
          // Items are automatically updated in the signal
          console.log('Contacts loaded successfully');
        }
        // Errors are handled by the error interceptor
      });
  }

  onPageChange(event: PageEvent) {
    this.loadContacts(event.pageIndex, event.pageSize);
  }

  onCreate() {
    // In a real app, you would open a dialog here
    const newContact = {
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane.smith@example.com',
      phone: '555-0123'
    };

    this.contactService.create(newContact).subscribe({
      next: () => {
        this.notificationService.success('Contact created successfully!');
      }
    });
  }

  onEdit(id: string) {
    this.contactService.getById(id).subscribe({
      next: () => {
        // selectedItem signal is automatically updated
        const selected = this.contactService.selectedItem();
        console.log('Editing contact:', selected);
        // In a real app, you would open a dialog here with the selected contact
      }
    });
  }

  onDelete(id: string) {
    // In a real app, you would show a confirmation dialog first
    this.contactService.delete(id).subscribe({
      next: () => {
        this.notificationService.success('Contact deleted successfully!');
      }
    });
  }

  onSearch(query: string) {
    if (query.trim()) {
      this.contactService.search(query, { page: 0, size: 20 }).subscribe();
    } else {
      this.loadContacts();
    }
  }
}
```

## Step 5: Root Layout with Global Loading Indicator

### apps/crm/src/app/app.component.ts

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { LoadingService } from '@frontend/shared-data-access';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    MatProgressBarModule,
    MatToolbarModule
  ],
  template: `
    <mat-toolbar color="primary">
      <span>CRM Application</span>
    </mat-toolbar>

    @if (loadingService.loading()) {
      <mat-progress-bar mode="indeterminate"></mat-progress-bar>
    }

    <main>
      <router-outlet />
    </main>
  `,
  styles: [`
    mat-progress-bar {
      position: fixed;
      top: 64px;
      left: 0;
      right: 0;
      z-index: 1000;
    }

    main {
      padding-top: 8px;
    }
  `]
})
export class AppComponent {
  loadingService = inject(LoadingService);
}
```

## Advanced Usage: Custom Service Methods

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseCrudService } from '@frontend/shared-data-access';
import { Contact, CreateContactRequest, UpdateContactRequest } from '@frontend/shared-models';

@Injectable({ providedIn: 'root' })
export class ContactService extends BaseCrudService<
  Contact,
  CreateContactRequest,
  UpdateContactRequest
> {
  protected override basePath = '/api/v1/contacts';

  /**
   * Find contacts by organization ID
   */
  findByOrganization(organizationId: string): Observable<Contact[]> {
    this.loadingSignal.set(true);

    return this.http
      .get<Contact[]>(`${this.basePath}/organization/${organizationId}`)
      .pipe(
        tap({
          next: (contacts) => {
            this.itemsSignal.set(contacts);
            this.loadingSignal.set(false);
          },
          error: () => {
            this.loadingSignal.set(false);
          }
        })
      );
  }

  /**
   * Bulk import contacts from CSV
   */
  importFromCsv(file: File): Observable<{ imported: number; failed: number }> {
    this.loadingSignal.set(true);

    const formData = new FormData();
    formData.append('file', file);

    return this.http
      .post<{ imported: number; failed: number }>(
        `${this.basePath}/import`,
        formData
      )
      .pipe(
        tap({
          next: () => {
            this.loadingSignal.set(false);
            // Refresh the list after import
            this.list().subscribe();
          },
          error: () => {
            this.loadingSignal.set(false);
          }
        })
      );
  }

  /**
   * Export contacts to CSV
   */
  exportToCsv(): Observable<Blob> {
    return this.http.get(`${this.basePath}/export`, {
      responseType: 'blob'
    });
  }
}
```

## Testing Your Service

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ContactService } from './contact.service';
import { SpringPageResponse } from '@frontend/shared-data-access';
import { Contact } from '@frontend/shared-models';

describe('ContactService', () => {
  let service: ContactService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ContactService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(ContactService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch contacts', (done) => {
    const mockResponse: SpringPageResponse<Contact> = {
      content: [
        {
          id: '1',
          firstName: 'John',
          lastName: 'Doe',
          email: 'john@example.com'
        }
      ],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0
    };

    service.list({ page: 0, size: 20 }).subscribe((contacts) => {
      expect(contacts.length).toBe(1);
      expect(service.items().length).toBe(1);
      expect(service.pagination().totalElements).toBe(1);
      done();
    });

    const req = httpMock.expectOne('/api/v1/contacts?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
```

## Summary

This setup provides:

1. **Automatic API response unwrapping** - Clean DTOs in your services
2. **Global error handling** - User-friendly notifications for all errors
3. **Global loading indicator** - Tracks all HTTP requests automatically
4. **Signal-based state** - Reactive, type-safe state management
5. **Pagination support** - Built-in handling of Spring Boot Page responses
6. **Type safety** - Full TypeScript support with generics
7. **Minimal boilerplate** - Just extend BaseCrudService and set the basePath
8. **Testable** - Easy to test with Angular's testing utilities

All you need to do is:
1. Configure interceptors in app.config.ts
2. Extend BaseCrudService for each entity
3. Inject the service and use the signals in your components
