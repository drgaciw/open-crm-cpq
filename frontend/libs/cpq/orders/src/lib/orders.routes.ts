import { Routes } from '@angular/router';
import { OrdersListComponent } from './orders-list.component';
import { OrdersDetailComponent } from './orders-detail.component';
import { OrdersExceptionsComponent } from './orders-exceptions.component';

export const ORDERS_ROUTES: Routes = [
  { path: '', component: OrdersListComponent },
  { path: 'exceptions', component: OrdersExceptionsComponent },
  { path: ':id', component: OrdersDetailComponent },
];
