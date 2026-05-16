import { Injectable } from '@angular/core';
import { BaseCrudService } from '../base-crud.service';
import { CpqApiPaths } from './cpq-api-paths';
import { CpqResource, CpqResourceCreate, CpqResourceUpdate } from './cpq-resource.model';

@Injectable({ providedIn: 'root' })
export class CpqPriceBookClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.priceBooks;
}

@Injectable({ providedIn: 'root' })
export class CpqProductClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.products;
}

@Injectable({ providedIn: 'root' })
export class CpqProductBundleClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.productBundles;
}
