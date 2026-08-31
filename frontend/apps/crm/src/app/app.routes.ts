import { Route } from '@angular/router';
import { ShellComponent } from './layout/shell.component';

export const appRoutes: Route[] = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadChildren: () =>
          import('@frontend/crm-dashboard').then((m) => m.DASHBOARD_ROUTES),
      },
      {
        path: 'organizations',
        loadChildren: () =>
          import('@frontend/crm-organizations').then((m) => m.ORGANIZATION_ROUTES),
      },
      {
        path: 'contacts',
        loadChildren: () =>
          import('@frontend/crm-contacts').then((m) => m.CONTACT_ROUTES),
      },
      {
        path: 'leads',
        loadChildren: () =>
          import('@frontend/crm-leads').then((m) => m.LEAD_ROUTES),
      },
      {
        path: 'deals',
        loadChildren: () =>
          import('@frontend/crm-deals').then((m) => m.DEAL_ROUTES),
      },
      {
        path: 'catalog',
        loadChildren: () =>
          import('@frontend/cpq-catalog').then((m) => m.CATALOG_ROUTES),
      },
      {
        path: 'quotes',
        loadChildren: () =>
          import('@frontend/cpq-quotes').then((m) => m.QUOTES_ROUTES),
      },
      {
        path: 'approvals',
        loadChildren: () =>
          import('@frontend/cpq-approvals').then((m) => m.APPROVALS_ROUTES),
      },
      {
        path: 'price-books',
        loadChildren: () =>
          import('@frontend/cpq-price-books').then((m) => m.PRICE_BOOKS_ROUTES),
      },
      {
        path: 'pricing/simulator',
        loadChildren: () =>
          import('@frontend/cpq-pricing-simulator').then(
            (m) => m.SIMULATOR_ROUTES,
          ),
      },
    ],
  },
];
