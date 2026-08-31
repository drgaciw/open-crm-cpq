import { Routes } from '@angular/router';
import { RuleSetListComponent } from './rule-set-list/rule-set-list.component';
import { RuleSetDetailComponent } from './rule-set-detail/rule-set-detail.component';

export const RULE_SETS_ROUTES: Routes = [
  { path: '', component: RuleSetListComponent },
  { path: ':id', component: RuleSetDetailComponent },
];
