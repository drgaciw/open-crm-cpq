import { Routes } from '@angular/router';
import { ContractListComponent } from './contract-list/contract-list.component';
import { ContractDetailComponent } from './contract-detail/contract-detail.component';
import { AssetListComponent } from './asset-list/asset-list.component';

/** `/contracts` (read-only, DRG-761). */
export const CONTRACTS_ROUTES: Routes = [
  { path: '', component: ContractListComponent },
  { path: ':id', component: ContractDetailComponent },
];

/** `/assets` (read-only; needs InstalledBase.Read). */
export const ASSETS_ROUTES: Routes = [{ path: '', component: AssetListComponent }];
