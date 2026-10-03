import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { QuoteService } from './quote.service';
import {
  QuoteDto,
  QuoteLineItemDto,
  QuoteUpdateRequest,
  SpringPageResponse,
} from '@frontend/shared-models';

function quote(
  partial: Partial<QuoteDto> & Pick<QuoteDto, 'id' | 'name' | 'customerExternalId'>,
): QuoteDto {
  return {
    uuid: `u-${partial.id}`,
    status: 'DRAFT',
    durationInMonths: 12,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

function lineItem(
  partial: Partial<QuoteLineItemDto> & Pick<QuoteLineItemDto, 'id' | 'productExternalId'>,
): QuoteLineItemDto {
  return {
    uuid: `li-${partial.id}`,
    quantity: 1,
    sortOrder: 0,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('QuoteService', () => {
  let service: QuoteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [QuoteService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(QuoteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list GETs /api/v1/quotes with filters', () => {
    const page: SpringPageResponse<QuoteDto> = {
      content: [quote({ id: 1, name: 'Q1', customerExternalId: 'cust-1' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    service.list(0, 20, 'createdAt,desc', {
      status: 'DRAFT',
      customerExternalId: 'cust-1',
    });
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/quotes' &&
        r.params.get('status') === 'DRAFT' &&
        r.params.get('customerExternalId') === 'cust-1',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
    expect(service.quotes()).toEqual(page.content);
  });

  it('list error.set is message [CODE] when errorCode present', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/quotes');
    req.flush(
      { message: 'List failed', errorCode: 'QUOTE_LIST_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(service.error()).toBe('List failed [QUOTE_LIST_DENIED]');
  });

  it('getById error.set is message [CODE] when errorCode present', () => {
    service.getById(42);
    const req = httpMock.expectOne('/api/v1/quotes/42');
    req.flush(
      { message: 'Quote missing', errorCode: 'QUOTE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Quote missing [QUOTE_NOT_FOUND]');
    expect(service.selectedQuote()).toBeNull();
  });

  it('getTierSuggestion GETs the advisory endpoint and returns the envelope', () => {
    let received: unknown;
    service.getTierSuggestion(42).subscribe((envelope) => (received = envelope));

    const req = httpMock.expectOne('/api/v1/quotes/42/approvals/tier-suggestion');
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'PENDING', suggestion: null });

    expect(received).toEqual({ status: 'PENDING', suggestion: null });
  });

  it('delete DELETEs /api/v1/quotes/:id (soft-delete)', async () => {
    const del = firstValueFrom(service.delete(42));
    const req = httpMock.expectOne('/api/v1/quotes/42');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toBeNull();
    req.flush(null);
    await expect(del).resolves.toBeNull();
  });

  it('update PUTs /api/v1/quotes/:id with QuoteUpdateRequest body', async () => {
    const body: QuoteUpdateRequest = {
      name: 'Renamed quote',
      description: 'Updated description',
      dealExternalId: 'deal-9',
      subscriptionType: 'RENEWAL',
      currency: 'USD',
      region: 'EMEA',
      durationInMonths: 24,
      startDate: '2026-02-01',
      endDate: '2028-01-31',
      notes: 'Header notes',
    };
    const update = firstValueFrom(service.update(42, body));
    const req = httpMock.expectOne('/api/v1/quotes/42');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    expect(req.request.body).not.toHaveProperty('customerExternalId');
    expect(req.request.body).not.toHaveProperty('status');
    expect(req.request.body).not.toHaveProperty('totalAmount');
    req.flush(
      quote({
        id: 42,
        name: 'Renamed quote',
        customerExternalId: 'c',
        region: 'EMEA',
        durationInMonths: 24,
      }),
    );
    await expect(update).resolves.toMatchObject({
      id: 42,
      name: 'Renamed quote',
      region: 'EMEA',
    });
  });

  it('lifecycle POSTs hit quote action paths', async () => {
    const submit = firstValueFrom(service.submit(5));
    httpMock.expectOne('/api/v1/quotes/5/submit').flush(
      quote({ id: 5, name: 'Q', customerExternalId: 'c', status: 'IN_REVIEW' }),
    );
    await expect(submit).resolves.toMatchObject({ status: 'IN_REVIEW' });

    const approve = firstValueFrom(service.approve(5));
    httpMock.expectOne('/api/v1/quotes/5/approve').flush(
      quote({ id: 5, name: 'Q', customerExternalId: 'c', status: 'APPROVED' }),
    );
    await expect(approve).resolves.toMatchObject({ status: 'APPROVED' });

    const reject = firstValueFrom(service.reject(5, 'No budget'));
    const rejectReq = httpMock.expectOne(
      (r) => r.url === '/api/v1/quotes/5/reject' && r.params.get('reason') === 'No budget',
    );
    expect(rejectReq.request.method).toBe('POST');
    rejectReq.flush(
      quote({ id: 5, name: 'Q', customerExternalId: 'c', status: 'REJECTED' }),
    );
    await expect(reject).resolves.toMatchObject({ status: 'REJECTED' });
  });

  it('send / accept / calculate / clone hit expected paths', async () => {
    const send = firstValueFrom(service.send(2));
    httpMock.expectOne('/api/v1/quotes/2/send').flush(
      quote({ id: 2, name: 'Q', customerExternalId: 'c', status: 'SENT' }),
    );
    await expect(send).resolves.toMatchObject({ status: 'SENT' });

    const accept = firstValueFrom(service.accept(2));
    httpMock.expectOne('/api/v1/quotes/2/accept').flush(
      quote({ id: 2, name: 'Q', customerExternalId: 'c', status: 'ACCEPTED' }),
    );
    await expect(accept).resolves.toMatchObject({ status: 'ACCEPTED' });

    const calculate = firstValueFrom(service.calculate(2));
    httpMock.expectOne('/api/v1/quotes/2/calculate').flush(
      quote({ id: 2, name: 'Q', customerExternalId: 'c', totalAmount: 100 }),
    );
    await expect(calculate).resolves.toMatchObject({ totalAmount: 100 });

    const clone = firstValueFrom(service.cloneQuote(2));
    const cloneReq = httpMock.expectOne('/api/v1/quotes/2/clone');
    expect(cloneReq.request.method).toBe('POST');
    cloneReq.flush(quote({ id: 99, name: 'Q copy', customerExternalId: 'c' }));
    await expect(clone).resolves.toMatchObject({ id: 99 });
  });

  it('expire / cancel POSTs hit expected paths', async () => {
    const expire = firstValueFrom(service.expire(3));
    httpMock.expectOne('/api/v1/quotes/3/expire').flush(
      quote({ id: 3, name: 'Q', customerExternalId: 'c', status: 'EXPIRED' }),
    );
    await expect(expire).resolves.toMatchObject({ status: 'EXPIRED' });

    const cancel = firstValueFrom(service.cancel(3));
    httpMock.expectOne('/api/v1/quotes/3/cancel').flush(
      quote({ id: 3, name: 'Q', customerExternalId: 'c', status: 'CANCELLED' }),
    );
    await expect(cancel).resolves.toMatchObject({ status: 'CANCELLED' });
  });

  it('document generate/list/download hit /documents paths; download uses blob', async () => {
    const generate = firstValueFrom(service.generateDocuments(8, 'ORDER_FORM'));
    const genReq = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/quotes/8/documents/generate' &&
        r.params.get('type') === 'ORDER_FORM',
    );
    expect(genReq.request.method).toBe('POST');
    genReq.flush({
      id: 11,
      uuid: 'doc-11',
      quoteId: 8,
      documentType: 'ORDER_FORM',
      fileName: 'quote-8-order.pdf',
      contentType: 'application/pdf',
      fileSize: 1024,
      createdAt: '2026-08-30T12:00:00Z',
    });
    await expect(generate).resolves.toMatchObject({
      id: 11,
      documentType: 'ORDER_FORM',
    });

    const list = firstValueFrom(service.listDocuments(8));
    const listReq = httpMock.expectOne('/api/v1/quotes/8/documents');
    expect(listReq.request.method).toBe('GET');
    listReq.flush([
      {
        id: 11,
        uuid: 'doc-11',
        quoteId: 8,
        documentType: 'ORDER_FORM',
        fileName: 'quote-8-order.pdf',
        createdAt: '2026-08-30T12:00:00Z',
      },
    ]);
    await expect(list).resolves.toHaveLength(1);

    const download = firstValueFrom(service.downloadDocument(8, 11));
    const dlReq = httpMock.expectOne('/api/v1/quotes/8/documents/11/download');
    expect(dlReq.request.method).toBe('GET');
    expect(dlReq.request.responseType).toBe('blob');
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' });
    dlReq.flush(pdf);
    const blob = await download;
    expect(blob).toBeInstanceOf(Blob);
  });

  it('generateDocuments defaults type to PROPOSAL', async () => {
    const generate = firstValueFrom(service.generateDocuments(9));
    const genReq = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/quotes/9/documents/generate' &&
        r.params.get('type') === 'PROPOSAL',
    );
    expect(genReq.request.method).toBe('POST');
    genReq.flush({
      id: 12,
      uuid: 'doc-12',
      quoteId: 9,
      documentType: 'PROPOSAL',
      createdAt: '2026-08-30T12:00:00Z',
    });
    await expect(generate).resolves.toMatchObject({ documentType: 'PROPOSAL' });
  });

  it('line-item CRUD hits /api/v1/quotes/:id/line-items', async () => {
    service.listLineItems(7);
    const listReq = httpMock.expectOne('/api/v1/quotes/7/line-items');
    expect(listReq.request.method).toBe('GET');
    listReq.flush([lineItem({ id: 1, productExternalId: 'prod-1' })]);
    expect(service.lineItems()).toHaveLength(1);

    const add = firstValueFrom(
      service.addLineItem(7, { productExternalId: 'prod-2', listPrice: 50 }),
    );
    const addReq = httpMock.expectOne('/api/v1/quotes/7/line-items');
    expect(addReq.request.method).toBe('POST');
    addReq.flush(lineItem({ id: 2, productExternalId: 'prod-2', listPrice: 50 }));
    await expect(add).resolves.toMatchObject({ id: 2 });

    const update = firstValueFrom(
      service.updateLineItem(7, 2, { productExternalId: 'prod-2', listPrice: 60 }),
    );
    const updateReq = httpMock.expectOne('/api/v1/quotes/7/line-items/2');
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush(lineItem({ id: 2, productExternalId: 'prod-2', listPrice: 60 }));
    await expect(update).resolves.toMatchObject({ listPrice: 60 });

    const remove = firstValueFrom(service.removeLineItem(7, 2));
    const removeReq = httpMock.expectOne('/api/v1/quotes/7/line-items/2');
    expect(removeReq.request.method).toBe('DELETE');
    removeReq.flush(null);
    await expect(remove).resolves.toBeNull();
  });

  it('updateLineItem sends schedule steps and escalator on the line write', async () => {
    const schedule = {
      steps: [
        {
          sequence: 1,
          startMonth: 1,
          months: 12,
          quantity: 50,
          priceBasis: 'ABSOLUTE' as const,
          unitPrice: 40,
        },
      ],
      escalator: {
        startPrice: 40,
        mode: 'PERCENT' as const,
        amount: 5,
        everyMonths: 12,
      },
    };
    const update = firstValueFrom(
      service.updateLineItem(7, 2, {
        productExternalId: 'prod-2',
        listPrice: 40,
        schedule,
      }),
    );
    const req = httpMock.expectOne('/api/v1/quotes/7/line-items/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      productExternalId: 'prod-2',
      listPrice: 40,
      schedule,
    });
    req.flush(lineItem({ id: 2, productExternalId: 'prod-2', listPrice: 40 }));
    await expect(update).resolves.toMatchObject({ id: 2 });
  });

  it('expandRampSchedule POSTs the escalator and does not change quote signals', async () => {
    const beforeQuotes = service.quotes();
    const beforeSelected = service.selectedQuote();
    const beforeLines = service.lineItems();
    const body = {
      durationInMonths: 36,
      escalator: {
        startPrice: 40,
        mode: 'PERCENT' as const,
        amount: 5,
        everyMonths: 12,
      },
    };
    const steps = [
      {
        sequence: 1,
        startMonth: 1,
        months: 12,
        quantity: 50,
        priceBasis: 'ABSOLUTE' as const,
        unitPrice: 40,
      },
      {
        sequence: 2,
        startMonth: 13,
        months: 12,
        quantity: 50,
        priceBasis: 'ABSOLUTE' as const,
        unitPrice: 42,
      },
      {
        sequence: 3,
        startMonth: 25,
        months: 12,
        quantity: 50,
        priceBasis: 'ABSOLUTE' as const,
        unitPrice: 44.1,
      },
    ];
    const pending = firstValueFrom(service.expandRampSchedule(7, body));
    const req = httpMock.expectOne('/api/v1/quotes/7/ramp-schedule/expand');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(steps);
    await expect(pending).resolves.toEqual(steps);
    expect(service.quotes()).toBe(beforeQuotes);
    expect(service.selectedQuote()).toBe(beforeSelected);
    expect(service.lineItems()).toBe(beforeLines);
    httpMock.expectNone('/api/v1/quotes/7/line-items');
  });
});
