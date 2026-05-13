import { CPQ_ROUTES } from './cpq.routes';
import { CatalogHomeComponent } from './catalog/catalog-home.component';
import { ConfigurationHomeComponent } from './configuration/configuration-home.component';
import { PricingHomeComponent } from './pricing/pricing-home.component';
import { QuoteHomeComponent } from './quotes/quote-home.component';

describe('CPQ_ROUTES', () => {
  it('scaffolds the catalog, configuration, pricing, and quote boundaries', () => {
    expect(CPQ_ROUTES).toEqual([
      { path: '', redirectTo: 'quotes', pathMatch: 'full' },
      { path: 'catalog', component: CatalogHomeComponent },
      { path: 'configuration', component: ConfigurationHomeComponent },
      { path: 'pricing', component: PricingHomeComponent },
      { path: 'quotes', component: QuoteHomeComponent }
    ]);
  });
});
