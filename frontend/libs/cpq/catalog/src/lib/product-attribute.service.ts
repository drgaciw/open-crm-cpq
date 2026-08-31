import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AttributeValueDto,
  AttributeValueSetRequest,
  ProductAttributeDto,
  SpringPageResponse,
} from '@frontend/shared-models';

/**
 * Product attribute HTTP helpers (sibling of {@link ProductService}).
 * Base path: `/api/v1/product-attributes` (product-service via gateway).
 * Does not call CRM `/api/v1/customers`.
 */
@Injectable({ providedIn: 'root' })
export class ProductAttributeService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/product-attributes';

  /**
   * Paged attribute definitions for the set-value picker.
   * After ApiResponse unwrap, body is Spring page-shaped (`content` + totals).
   */
  listDefinitions(
    page = 0,
    size = 50,
    sort = 'name,asc',
  ): Observable<SpringPageResponse<ProductAttributeDto>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<SpringPageResponse<ProductAttributeDto>>(
      this.basePath,
      { params },
    );
  }

  listValues(productId: number): Observable<AttributeValueDto[]> {
    return this.http.get<AttributeValueDto[]>(
      `${this.basePath}/products/${productId}/values`,
    );
  }

  setValue(
    productId: number,
    request: AttributeValueSetRequest,
  ): Observable<AttributeValueDto> {
    return this.http.post<AttributeValueDto>(
      `${this.basePath}/products/${productId}/values`,
      request,
    );
  }
}
