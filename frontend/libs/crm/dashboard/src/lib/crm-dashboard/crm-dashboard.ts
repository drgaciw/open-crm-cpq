import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'lib-crm-dashboard',
  imports: [],
  templateUrl: './crm-dashboard.html',
  styleUrl: './crm-dashboard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrmDashboard {}
