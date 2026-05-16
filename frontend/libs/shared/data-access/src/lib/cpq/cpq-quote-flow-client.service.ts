import { Injectable } from '@angular/core';
import { BaseCrudService } from '../base-crud.service';
import { CpqApiPaths } from './cpq-api-paths';
import { CpqResource, CpqResourceCreate, CpqResourceUpdate } from './cpq-resource.model';

@Injectable({ providedIn: 'root' })
export class CpqQuoteClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.quotes;
}

@Injectable({ providedIn: 'root' })
export class CpqConfigurationSessionClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.configurations;
}

@Injectable({ providedIn: 'root' })
export class CpqApprovalClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.approvals;
}

@Injectable({ providedIn: 'root' })
export class CpqDocumentClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.documents;
}

@Injectable({ providedIn: 'root' })
export class CpqSubscriptionClient extends BaseCrudService<
  CpqResource,
  CpqResourceCreate,
  CpqResourceUpdate
> {
  protected override basePath = CpqApiPaths.subscriptions;
}
