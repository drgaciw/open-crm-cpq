import { Routes } from '@angular/router';
import { DealListComponent } from './deal-list/deal-list.component';
import { DealDetailComponent } from './deal-detail/deal-detail.component';
import { DealFormComponent } from './deal-form/deal-form.component';
import { DealKanbanComponent } from './deal-kanban/deal-kanban.component';

export const DEAL_ROUTES: Routes = [
  { path: '', component: DealListComponent },
  { path: 'kanban', component: DealKanbanComponent },
  { path: 'new', component: DealFormComponent },
  { path: ':id', component: DealDetailComponent },
  { path: ':id/edit', component: DealFormComponent },
];
