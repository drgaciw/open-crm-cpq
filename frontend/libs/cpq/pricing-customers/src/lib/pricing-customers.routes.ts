import { Routes } from '@angular/router';
import { PricingCustomerListComponent } from './pricing-customer-list/pricing-customer-list.component';
import { PricingCustomerDetailComponent } from './pricing-customer-detail/pricing-customer-detail.component';

export const PRICING_CUSTOMERS_ROUTES: Routes = [
  { path: '', component: PricingCustomerListComponent },
  { path: ':id', component: PricingCustomerDetailComponent },
];
