// Isolated UI test host. It does not supply an identity or fabricate API responses.
import '@angular/compiler';
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, RouterOutlet } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { ORDERS_ROUTES } from '../src/lib/orders.routes';

@Component({
  selector: 'orders-browser-harness',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class OrdersBrowserHarness {}
bootstrapApplication(OrdersBrowserHarness, {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter([
      { path: 'orders', children: ORDERS_ROUTES },
      { path: '', redirectTo: 'orders', pathMatch: 'full' },
    ]),
    provideHttpClient(withInterceptors([apiResponseInterceptor])),
  ],
}).catch(console.error);
