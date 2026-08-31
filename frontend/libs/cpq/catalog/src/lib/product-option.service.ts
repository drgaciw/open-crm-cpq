import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  OptionCreateRequest,
  OptionGroupCreateRequest,
  ProductOptionGroupDto,
} from '@frontend/shared-models';

/**
 * Product option-group HTTP helpers (sibling of {@link ProductService}).
 * Base path: `/api/v1/product-options` (product-service via gateway).
 * Does not call CRM `/api/v1/customers`.
 */
@Injectable({ providedIn: 'root' })
export class ProductOptionService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/product-options';

  listGroupsByProduct(productId: number): Observable<ProductOptionGroupDto[]> {
    return this.http.get<ProductOptionGroupDto[]>(
      `${this.basePath}/groups/product/${productId}`,
    );
  }

  createGroup(
    request: OptionGroupCreateRequest,
  ): Observable<ProductOptionGroupDto> {
    return this.http.post<ProductOptionGroupDto>(
      `${this.basePath}/groups`,
      request,
    );
  }

  addOption(
    groupId: number,
    request: OptionCreateRequest,
  ): Observable<ProductOptionGroupDto> {
    return this.http.post<ProductOptionGroupDto>(
      `${this.basePath}/groups/${groupId}/options`,
      request,
    );
  }

  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/groups/${id}`);
  }
}
