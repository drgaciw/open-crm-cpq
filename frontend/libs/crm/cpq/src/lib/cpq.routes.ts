import { Routes } from '@angular/router';
import { CatalogHomeComponent } from './catalog/catalog-home.component';
import { ConfigurationHomeComponent } from './configuration/configuration-home.component';
import { PricingHomeComponent } from './pricing/pricing-home.component';
import { QuoteHomeComponent } from './quotes/quote-home.component';

export const CPQ_ROUTES: Routes = [
  { path: '', redirectTo: 'quotes', pathMatch: 'full' },
  { path: 'catalog', component: CatalogHomeComponent },
  { path: 'configuration', component: ConfigurationHomeComponent },
  { path: 'pricing', component: PricingHomeComponent },
  { path: 'quotes', component: QuoteHomeComponent }
];
