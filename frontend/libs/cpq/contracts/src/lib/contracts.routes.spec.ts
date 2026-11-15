import { ASSETS_ROUTES, CONTRACTS_ROUTES } from './contracts.routes';
import { ContractListComponent } from './contract-list/contract-list.component';
import { ContractDetailComponent } from './contract-detail/contract-detail.component';
import { AssetListComponent } from './asset-list/asset-list.component';

describe('contracts routes', () => {
  it('maps the contract list and detail, and the asset list', () => {
    expect(CONTRACTS_ROUTES.map((r) => [r.path, r.component])).toEqual([
      ['', ContractListComponent],
      [':id', ContractDetailComponent],
    ]);
    expect(ASSETS_ROUTES.map((r) => [r.path, r.component])).toEqual([['', AssetListComponent]]);
  });
});
