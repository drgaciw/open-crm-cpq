import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { ActivityApiService } from './activity-api.service';
import { ActivityCreateRequest, ActivityDto, ActivityType } from './activity.model';

describe('ActivityApiService', () => {
  let service: ActivityApiService;
  let http: HttpTestingController;
  const occurredAt = '2026-11-05T19:00:00Z';
  const activity: ActivityDto = {
    id: 7,
    uuid: 'f3f5d9d0-4615-4594-b296-b094f8e81e01',
    type: 'NOTE',
    title: 'Discovery call',
    note: 'Follow up next week',
    organizationId: 12,
    occurredAt,
    authorId: 'verified-user',
    source: 'MANUAL',
    createdAt: occurredAt,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiResponseInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ActivityApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it.each<ActivityType>(['NOTE', 'TASK', 'EMAIL', 'MEETING'])('creates a %s and unwraps its server identity', async (type) => {
    const request: ActivityCreateRequest = {
      type,
      title: 'Discovery call',
      organizationId: 12,
      occurredAt,
      ...(type === 'NOTE' ? { note: 'Follow up next week' } : {}),
      ...(type === 'TASK' ? { dueAt: '2026-11-12T19:00:00Z' } : {}),
      ...(type === 'EMAIL' || type === 'MEETING' ? { reference: 'opaque-provider-reference' } : {}),
    };
    const result = firstValueFrom(service.create(request));
    const pending = http.expectOne('/api/v1/activities');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);
    expect(pending.request.body).not.toHaveProperty('authorId');
    expect(pending.request.body).not.toHaveProperty('source');
    expect(pending.request.body).not.toHaveProperty('completedAt');
    const response = { ...activity, type };
    pending.flush({ success: true, timestamp: occurredAt, data: response });
    expect(await result).toEqual(response);
  });

  it('passes subject filters and pagination, and receives the actual List plus PageInfo response', async () => {
    const result = firstValueFrom(service.list({ organizationId: 12, contactId: 23, dealId: 34, page: 2, size: 5 }));
    const pending = http.expectOne((request) => request.url === '/api/v1/activities');
    expect(pending.request.method).toBe('GET');
    expect(pending.request.params.keys().sort()).toEqual(['contactId', 'dealId', 'organizationId', 'page', 'size']);
    expect(pending.request.params.get('organizationId')).toBe('12');
    expect(pending.request.params.get('contactId')).toBe('23');
    expect(pending.request.params.get('dealId')).toBe('34');
    expect(pending.request.params.get('page')).toBe('2');
    expect(pending.request.params.get('size')).toBe('5');
    pending.flush({
      success: true,
      timestamp: occurredAt,
      data: [activity],
      pagination: { page: 2, size: 5, totalElements: 11, totalPages: 3, hasNext: false, hasPrevious: true },
    });
    expect(await result).toEqual({ content: [activity], number: 2, size: 5, totalElements: 11, totalPages: 3 });
  });

  it('omits absent filters and retains empty-page metadata', async () => {
    const result = firstValueFrom(service.list({ contactId: 23 }));
    const pending = http.expectOne((request) => request.url === '/api/v1/activities');
    expect(pending.request.params.keys().sort()).toEqual(['contactId', 'page', 'size']);
    expect(pending.request.params.get('page')).toBe('0');
    expect(pending.request.params.get('size')).toBe('20');
    pending.flush({
      success: true,
      timestamp: occurredAt,
      data: [],
      pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
    });
    expect(await result).toEqual({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('loads one activity through the gateway-relative API', async () => {
    const result = firstValueFrom(service.getById(7));
    const pending = http.expectOne('/api/v1/activities/7');
    expect(pending.request.method).toBe('GET');
    pending.flush({ success: true, timestamp: occurredAt, data: activity });
    expect(await result).toEqual(activity);
  });

  it('completes a task without sending a client-chosen completion identity or time', async () => {
    const result = firstValueFrom(service.complete(7));
    const pending = http.expectOne('/api/v1/activities/7/complete');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual({});
    const completed = { ...activity, type: 'TASK', completedAt: '2026-11-06T08:00:00Z' };
    pending.flush({ success: true, timestamp: occurredAt, data: completed });
    expect(await result).toEqual(completed);
  });

  it('preserves a server refusal instead of returning a fabricated empty timeline', async () => {
    const result = firstValueFrom(service.list({ dealId: 34 }));
    const rejected = expect(result).rejects.toMatchObject({ status: 403 });
    http.expectOne((request) => request.url === '/api/v1/activities').flush(
      { message: 'Access denied', errorCode: 'ACTIVITY_ACCESS_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    await rejected;
  });

  it('preserves business error details from the shared response interceptor', async () => {
    const result = firstValueFrom(service.complete(7));
    const rejected = expect(result).rejects.toMatchObject({
      message: 'Only tasks can be completed',
      errorCode: 'ACTIVITY_NOT_TASK',
    });
    http.expectOne('/api/v1/activities/7/complete').flush({
      success: false,
      timestamp: occurredAt,
      message: 'Only tasks can be completed',
      errorCode: 'ACTIVITY_NOT_TASK',
    });
    await rejected;
  });
});
