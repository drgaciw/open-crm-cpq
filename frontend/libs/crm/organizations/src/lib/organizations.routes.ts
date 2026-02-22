import { Routes } from '@angular/router';
import { OrganizationListComponent } from './organization-list/organization-list.component';
import { OrganizationDetailComponent } from './organization-detail/organization-detail.component';
import { OrganizationFormComponent } from './organization-form/organization-form.component';

export const ORGANIZATION_ROUTES: Routes = [
  { path: '', component: OrganizationListComponent },
  { path: 'new', component: OrganizationFormComponent },
  { path: ':id', component: OrganizationDetailComponent },
  { path: ':id/edit', component: OrganizationFormComponent },
];
