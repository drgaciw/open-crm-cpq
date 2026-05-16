import { Injectable } from '@angular/core';
import { BaseCrudService } from '../base-crud.service';
import { CpqApiPaths } from './cpq-api-paths';
import { CpqResource, CpqResourceCreate, CpqResourceUpdate } from './cpq-resource.model';

@Injectable({ providedIn: 'root' })
export class CpqPricingClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.pricing;
}

@Injectable({ providedIn: 'root' })
export class CpqPricingCustomerClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.pricingCustomers;
}

@Injectable({ providedIn: 'root' })
export class CpqRuleSetClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.ruleSets;
}

@Injectable({ providedIn: 'root' })
export class CpqRuleClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.rules;
}
