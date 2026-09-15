import {
  inject,
  makeEnvironmentProviders,
  provideExperimentalWebMcpTools,
} from '@angular/core';
import {
  isQuoteStatus,
  QuoteDealType,
  QuoteDto,
  QuoteStatus,
} from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

/** Matches {@link QuotesListComponent} default page size. */
const DEFAULT_PAGE_SIZE = 10;
const LIST_SORT = 'createdAt,desc';

const QUOTE_DEAL_TYPES: readonly QuoteDealType[] = [
  'NEW',
  'AMENDMENT',
  'RENEWAL',
  'EXPANSION',
];

function parseStatus(value: unknown): QuoteStatus | undefined {
  if (value === undefined || value === '') {
    return undefined;
  }
  if (typeof value !== 'string' || !isQuoteStatus(value)) {
    throw new Error(`Bad status: ${value}`);
  }
  return value;
}

function parseDealType(value: unknown): QuoteDealType | undefined {
  if (value === undefined || value === '') {
    return undefined;
  }
  if (
    typeof value !== 'string' ||
    !(QUOTE_DEAL_TYPES as readonly string[]).includes(value)
  ) {
    throw new Error(`Bad dealType: ${value}`);
  }
  return value as QuoteDealType;
}

function parsePage(value: unknown): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
    throw new Error(`Bad page: ${value}`);
  }
  return value;
}

function parseSize(value: unknown): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1) {
    throw new Error(`Bad size: ${value}`);
  }
  return value;
}

function parseQuery(value: unknown): string {
  if (value === undefined) {
    return '';
  }
  if (typeof value !== 'string') {
    throw new Error(`Bad query: ${value}`);
  }
  return value;
}

function matchesQuoteSearch(quote: QuoteDto, q: string): boolean {
  return (
    quote.name.toLowerCase().includes(q) ||
    (quote.quoteNumber?.toLowerCase().includes(q) ?? false) ||
    quote.customerExternalId.toLowerCase().includes(q) ||
    (quote.description?.toLowerCase().includes(q) ?? false)
  );
}

function pageSize(quoteService: QuoteService, size: number | undefined): number {
  return size ?? (quoteService.pagination().size || DEFAULT_PAGE_SIZE);
}

function quotesListSnapshot(quoteService: QuoteService, quotes: QuoteDto[]) {
  return {
    content: [
      {
        type: 'text' as const,
        text: JSON.stringify({
          quotes: quotes.map((quote) => ({
            id: quote.id,
            quoteNumber: quote.quoteNumber ?? `Q-${quote.id}`,
            name: quote.name,
            customerExternalId: quote.customerExternalId,
            status: quote.status,
            dealType: quote.dealType ?? null,
            totalAmount: quote.totalAmount ?? 0,
          })),
          pagination: quoteService.pagination(),
          loading: quoteService.loading(),
          error: quoteService.error(),
        }),
      },
    ],
  };
}

/**
 * Read-only WebMCP tools for the quotes-list surface.
 * No writes (create/update/delete/submit/approve) — those stay on UI validation.
 *
 * Each tool is its own {@link provideExperimentalWebMcpTools} call: the API is
 * generic over a single `InputSchema`, so mixed schemas cannot share one array.
 */
const LIST_QUOTES_TOOL = {
  name: 'listQuotes',
    description:
      'Lists quotes on the quotes-list page (read-only). Refreshes QuoteService.list and returns quotes, pagination, loading, and error signals.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        page: {
          type: 'number' as const,
          description: 'Zero-based page index. Defaults to the current pagination page or 0.',
        },
        size: {
          type: 'number' as const,
          description: 'Page size. Defaults to the current pagination size or 10.',
        },
      },
      additionalProperties: false as const,
    },
    execute: ({ page, size }: { page?: number; size?: number }) => {
      const quoteService = inject(QuoteService);
      const pageIndex =
        page === undefined ? quoteService.pagination().page : parsePage(page);
      const resolvedSize = pageSize(
        quoteService,
        size === undefined ? undefined : parseSize(size),
      );
      quoteService.list(pageIndex, resolvedSize, LIST_SORT);
      return quotesListSnapshot(quoteService, quoteService.quotes());
    },
};

const FILTER_QUOTES_TOOL = {
  name: 'filterQuotes',
    description:
      'Filters the quotes list by status, deal type, and/or client-side search query. Read-only; does not create or mutate quotes.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        status: {
          type: 'string' as const,
          description:
            'Quote status (DRAFT, IN_REVIEW, PENDING_APPROVAL, APPROVED, SENT, ACCEPTED, EXPIRED, CANCELLED, REJECTED). Omit or empty for all.',
        },
        dealType: {
          type: 'string' as const,
          description:
            'Deal type (NEW, AMENDMENT, RENEWAL, EXPANSION). Omit or empty for all.',
        },
        query: {
          type: 'string' as const,
          description:
            'Client-side search on name, quote number, customer, or description of the current page.',
        },
      },
      additionalProperties: false as const,
    },
    execute: ({
      status,
      dealType,
      query,
    }: {
      status?: string;
      dealType?: string;
      query?: string;
    }) => {
      const quoteService = inject(QuoteService);
      const parsedStatus = parseStatus(status);
      const parsedDealType = parseDealType(dealType);
      const q = parseQuery(query).trim().toLowerCase();
      const size = pageSize(quoteService, undefined);
      if (parsedStatus !== undefined || parsedDealType !== undefined) {
        quoteService.list(0, size, LIST_SORT, {
          status: parsedStatus,
          dealType: parsedDealType,
        });
      }
      const quotes = q
        ? quoteService.quotes().filter((quote) => matchesQuoteSearch(quote, q))
        : quoteService.quotes();
      return quotesListSnapshot(quoteService, quotes);
    },
};

const PAGE_QUOTES_TOOL = {
  name: 'pageQuotes',
    description:
      'Pages the quotes list (read-only). Hits QuoteService.list with the requested page and size.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        page: {
          type: 'number' as const,
          description: 'Zero-based page index.',
        },
        size: {
          type: 'number' as const,
          description: 'Page size. Defaults to the current pagination size or 10.',
        },
        status: {
          type: 'string' as const,
          description: 'Optional status filter to keep while paging.',
        },
        dealType: {
          type: 'string' as const,
          description: 'Optional deal-type filter to keep while paging.',
        },
      },
      required: ['page'] as const,
      additionalProperties: false as const,
    },
    execute: ({
      page,
      size,
      status,
      dealType,
    }: {
      page: number;
      size?: number;
      status?: string;
      dealType?: string;
    }) => {
      const quoteService = inject(QuoteService);
      const pageIndex = parsePage(page);
      const resolvedSize = pageSize(
        quoteService,
        size === undefined ? undefined : parseSize(size),
      );
      quoteService.list(pageIndex, resolvedSize, LIST_SORT, {
        status: parseStatus(status),
        dealType: parseDealType(dealType),
      });
      return quotesListSnapshot(quoteService, quoteService.quotes());
    },
};

export const QUOTES_LIST_WEB_MCP_TOOLS = [
  LIST_QUOTES_TOOL,
  FILTER_QUOTES_TOOL,
  PAGE_QUOTES_TOOL,
];

/** EnvironmentProviders for quotes-list read tools. Route providers only — not app-wide. */
export function provideQuotesListWebMcpTools() {
  return makeEnvironmentProviders([
    provideExperimentalWebMcpTools([LIST_QUOTES_TOOL]),
    provideExperimentalWebMcpTools([FILTER_QUOTES_TOOL]),
    provideExperimentalWebMcpTools([PAGE_QUOTES_TOOL]),
  ]);
}
