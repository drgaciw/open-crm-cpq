import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ConfigRuleCreateRequest,
  ConfigurationRuleDto,
} from './configuration-rule.model';

/**
 * Product configuration-rule HTTP helpers (sibling of {@link ProductService}).
 * Base path: `/api/v1/configuration/rules` (product-service via gateway).
 * Never calls pricing `/api/v1/rules` or `POST /api/v1/configuration/validate`.
 */
@Injectable({ providedIn: 'root' })
export class ConfigurationRuleService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/configuration/rules';

  listByProduct(productId: number): Observable<ConfigurationRuleDto[]> {
    return this.http.get<ConfigurationRuleDto[]>(
      `${this.basePath}/product/${productId}`,
    );
  }

  create(request: ConfigRuleCreateRequest): Observable<ConfigurationRuleDto> {
    return this.http.post<ConfigurationRuleDto>(this.basePath, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }
}
