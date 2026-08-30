import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApprovalService } from './approval.service';
import { ApprovalRequestDto } from '@frontend/shared-models';
import { MOCK_PENDING_APPROVALS } from './approvals.mock';

function approval(
  partial: Partial<ApprovalRequestDto> &
    Pick<ApprovalRequestDto, 'id' | 'quoteId' | 'tier'>,
): ApprovalRequestDto {
  return {
    status: 'PENDING',
    ...partial,
  };
}

describe('ApprovalService', () => {
  let service: ApprovalService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApprovalService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApprovalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listPending GETs quote BFF /api/v1/quotes/approvals/pending', () => {
    service.listPending();
    const req = httpMock.expectOne('/api/v1/quotes/approvals/pending');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_PENDING_APPROVALS);
    expect(service.pending()).toEqual(MOCK_PENDING_APPROVALS);
    expect(service.loading()).toBe(false);
  });

  it('does not call approval-routing /api/v1/approvals paths', () => {
    service.listPending();
    const req = httpMock.expectOne('/api/v1/quotes/approvals/pending');
    expect(req.request.url).not.toContain('/api/v1/approvals/pending');
    expect(req.request.url.startsWith('/api/v1/quotes/approvals')).toBe(true);
    req.flush([]);
  });

  it('approve POSTs /api/v1/quotes/approvals/{id}/approve and drops from pending', async () => {
    service.pending.set([...MOCK_PENDING_APPROVALS]);
    const result = firstValueFrom(
      service.approve(10, { comments: 'Looks good' }),
    );
    const req = httpMock.expectOne('/api/v1/quotes/approvals/10/approve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comments: 'Looks good' });
    const body = approval({
      id: 10,
      quoteId: 7,
      tier: 'DEAL_DESK',
      status: 'APPROVED',
    });
    req.flush(body);
    await expect(result).resolves.toMatchObject({ status: 'APPROVED' });
    expect(service.pending().map((a) => a.id)).toEqual([11]);
  });

  it('reject POSTs /api/v1/quotes/approvals/{id}/reject and drops from pending', async () => {
    service.pending.set([...MOCK_PENDING_APPROVALS]);
    const result = firstValueFrom(
      service.reject(11, { comments: 'Discount too high' }),
    );
    const req = httpMock.expectOne('/api/v1/quotes/approvals/11/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comments: 'Discount too high' });
    req.flush(
      approval({
        id: 11,
        quoteId: 8,
        tier: 'CFO',
        status: 'REJECTED',
      }),
    );
    await expect(result).resolves.toMatchObject({ status: 'REJECTED' });
    expect(service.pending().map((a) => a.id)).toEqual([10]);
  });

  it('listPending records HTTP error message', () => {
    service.listPending();
    const req = httpMock.expectOne('/api/v1/quotes/approvals/pending');
    req.flush(
      { message: 'BFF unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBeTruthy();
    expect(service.loading()).toBe(false);
  });
});
