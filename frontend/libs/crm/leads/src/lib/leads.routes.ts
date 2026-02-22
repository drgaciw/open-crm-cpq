import { Routes } from '@angular/router';
import { LeadListComponent } from './lead-list/lead-list.component';
import { LeadDetailComponent } from './lead-detail/lead-detail.component';
import { LeadFormComponent } from './lead-form/lead-form.component';

export const LEAD_ROUTES: Routes = [
  { path: '', component: LeadListComponent },
  { path: 'new', component: LeadFormComponent },
  { path: ':id', component: LeadDetailComponent },
  { path: ':id/edit', component: LeadFormComponent },
];
