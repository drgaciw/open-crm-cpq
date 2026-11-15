import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AssetDto, SpringPageResponse, SubscriptionDto } from '@frontend/shared-models';

/** True when the API refused the read (403), e.g. a user without `InstalledBase.Read` asking for assets. */
export function isAccessDenied(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 403;
}

/**
 * Read-only access to contracts (subscriptions) and their assets (DRG-761).
 * Contract-asset-management lifecycle writes stay held; see docs/forge/42-contracts-ui-hold-exception.md.
 */
@Injectable({ providedIn: 'root' })
export class ContractService {
  private readonly http = inject(HttpClient);

  /** One page of contracts; newest first unless a column sort (`field,dir`) is given. */
  list(page = 0, size = 20, sort?: string): Observable<SpringPageResponse<SubscriptionDto>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort ?? 'createdAt,desc');
    return this.http.get<SpringPageResponse<SubscriptionDto>>('/api/v1/subscriptions', { params });
  }

  get(id: number): Observable<SubscriptionDto> {
    return this.http.get<SubscriptionDto>(`/api/v1/subscriptions/${id}`);
  }

  /** The installed base, or one contract's assets. Requires `InstalledBase.Read`; 403 otherwise. */
  assets(filter?: { subscriptionId?: number }): Observable<AssetDto[]> {
    let params = new HttpParams();
    if (filter?.subscriptionId !== undefined) {
      params = params.set('subscriptionId', filter.subscriptionId);
    }
    return this.http.get<AssetDto[]>('/api/v1/assets', { params });
  }
}
