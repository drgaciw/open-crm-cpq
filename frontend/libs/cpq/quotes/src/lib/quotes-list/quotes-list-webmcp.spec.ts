import { TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { QuoteDto, PaginationMeta } from '@frontend/shared-models';
import { QuoteService } from '../quote.service';
import { MOCK_QUOTES } from '../quotes.mock';
import { QuoteListItem } from '../quotes.model';
import { QUOTES_ROUTES } from '../quotes.routes';
import {
  provideQuotesListWebMcpTools,
  QUOTES_LIST_WEB_MCP_TOOLS,
} from './quotes-list-webmcp';

function toQuoteDto(item: QuoteListItem): QuoteDto {
  return {
    id: item.id,
    uuid: item.uuid,
    quoteNumber: item.quoteNumber,
    name: item.name,
    description: item.description,
    customerExternalId: item.customerExternalId,
    dealExternalId: item.dealExternalId,
    status: item.status,
    dealType: item.dealType,
    currency: item.currency,
    region: item.region,
    durationInMonths: item.durationInMonths,
    totalAmount: item.totalAmount,
    arr: item.arr,
    tcv: item.tcv,
    createdAt: item.createdAt,
    updatedAt: item.createdAt,
  };
}

function parseSnapshot(result: unknown): {
  quotes: Array<{ quoteNumber?: string; name: string }>;
  pagination: PaginationMeta;
  loading: boolean;
  error: string | null;
} {
  const payload = result as { content: Array<{ type: string; text: string }> };
  expect(payload.content[0].type).toBe('text');
  return JSON.parse(payload.content[0].text) as {
    quotes: Array<{ quoteNumber?: string; name: string }>;
    pagination: PaginationMeta;
    loading: boolean;
    error: string | null;
  };
}

/**
 * WebMCP hands each tool untyped JSON input, and `find()` over the mixed-schema
 * tools array yields a union whose `execute` parameter is the intersection of all
 * three inputs, so call it through the shape the runtime actually uses.
 */
function runTool(name: string, input: Record<string, unknown>): unknown {
  const tool = QUOTES_LIST_WEB_MCP_TOOLS.find((t) => t.name === name);
  expect(tool).toBeTruthy();
  const execute = tool!.execute as (args: Record<string, unknown>) => unknown;
  return execute(input);
}

describe('quotes-list WebMCP', () => {
  let listSpy: ReturnType<typeof vi.fn>;
  const quotes = signal<QuoteDto[]>(MOCK_QUOTES.map(toQuoteDto));
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 10,
    totalElements: MOCK_QUOTES.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn();
    quotes.set(MOCK_QUOTES.map(toQuoteDto));
    loading.set(false);
    error.set(null);
    pagination.set({
      page: 0,
      size: 10,
      totalElements: MOCK_QUOTES.length,
      totalPages: 1,
    });

    const serviceMock = {
      quotes,
      loading,
      error,
      pagination,
      isEmpty: computed(() => quotes().length === 0 && !loading()),
      list: listSpy,
    };

    await TestBed.configureTestingModule({
      providers: [
        provideQuotesListWebMcpTools(),
        { provide: QuoteService, useValue: serviceMock },
      ],
    }).compileComponents();
  });

  it('registers provideExperimentalWebMcpTools on the quotes-list route only', () => {
    expect(provideQuotesListWebMcpTools()).toBeTruthy();
    const listRoute = QUOTES_ROUTES.find((route) => route.path === '');
    expect(listRoute?.providers?.length).toBeGreaterThan(0);
    expect(QUOTES_ROUTES.find((route) => route.path === 'new')?.providers).toBeUndefined();
    expect(QUOTES_ROUTES.find((route) => route.path === ':id')?.providers).toBeUndefined();
    expect(QUOTES_LIST_WEB_MCP_TOOLS.map((tool) => tool.name)).toEqual([
      'listQuotes',
      'filterQuotes',
      'pageQuotes',
    ]);
  });

  it('listQuotes execute hits QuoteService.list and quotes/pagination signals', () => {
    const snapshot = TestBed.runInInjectionContext(() =>
      parseSnapshot(runTool('listQuotes', {})),
    );

    expect(listSpy).toHaveBeenCalledWith(0, 10, 'createdAt,desc');
    expect(snapshot.quotes.length).toBe(MOCK_QUOTES.length);
    expect(snapshot.quotes[0].quoteNumber).toBe('Q-2026-0001');
    expect(snapshot.pagination).toEqual(pagination());
    expect(snapshot.loading).toBe(false);
    expect(snapshot.error).toBeNull();
  });

  it('filterQuotes execute hits QuoteService.list with status/dealType and filters quotes()', () => {
    const snapshot = TestBed.runInInjectionContext(() =>
      parseSnapshot(
        runTool('filterQuotes', {
          status: 'DRAFT',
          dealType: 'NEW',
          query: 'Q-2026-0001',
        }),
      ),
    );

    expect(listSpy).toHaveBeenCalledWith(0, 10, 'createdAt,desc', {
      status: 'DRAFT',
      dealType: 'NEW',
    });
    expect(snapshot.quotes.length).toBe(1);
    expect(snapshot.quotes[0].quoteNumber).toBe('Q-2026-0001');
  });

  it('pageQuotes execute hits QuoteService.list with page and size', () => {
    const snapshot = TestBed.runInInjectionContext(() =>
      parseSnapshot(runTool('pageQuotes', { page: 2, size: 5 })),
    );

    expect(listSpy).toHaveBeenCalledWith(2, 5, 'createdAt,desc', {
      status: undefined,
      dealType: undefined,
    });
    expect(snapshot.quotes.length).toBe(MOCK_QUOTES.length);
    expect(snapshot.pagination.size).toBe(10);
  });

  it('does not expose write tools', () => {
    const names = QUOTES_LIST_WEB_MCP_TOOLS.map((tool) => tool.name);
    expect(names.some((name) => /create|update|delete|submit|approve|write/i.test(name))).toBe(
      false,
    );
  });
});
