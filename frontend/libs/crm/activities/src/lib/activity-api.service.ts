import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SpringPageResponse } from '@frontend/shared-models';
import { ActivityCreateRequest, ActivityDto, ActivityTimelineQuery } from './activity.model';

/** Uses the application's ApiResponse interceptor, matching the other CRM clients. */
@Injectable({ providedIn: 'root' })
export class ActivityApiService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/activities';

  create(request: ActivityCreateRequest): Observable<ActivityDto> {
    return this.http.post<ActivityDto>(this.basePath, request);
  }

  list(query: ActivityTimelineQuery): Observable<SpringPageResponse<ActivityDto>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.organizationId !== undefined) params = params.set('organizationId', query.organizationId);
    if (query.contactId !== undefined) params = params.set('contactId', query.contactId);
    if (query.dealId !== undefined) params = params.set('dealId', query.dealId);
    return this.http.get<SpringPageResponse<ActivityDto>>(this.basePath, { params });
  }

  getById(id: number): Observable<ActivityDto> {
    return this.http.get<ActivityDto>(`${this.basePath}/${id}`);
  }

  complete(id: number): Observable<ActivityDto> {
    return this.http.post<ActivityDto>(`${this.basePath}/${id}/complete`, {});
  }
}
