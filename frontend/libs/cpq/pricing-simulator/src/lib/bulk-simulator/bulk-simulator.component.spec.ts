import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { CopyButtonComponent } from '@frontend/shared-ui';
import { of } from 'rxjs';
import { PricingExecutionService } from '@frontend/cpq-pricing-data-access';
import { PricingContext } from '@frontend/shared-models';
import { BulkSimulatorComponent } from './bulk-simulator.component';
import {
  MOCK_BULK_CONTEXTS,
  MOCK_PRICING_CONTEXT,
  mockBulkPartialResponse,
} from '../pricing-simulator.mock';

/**
 * BC-03-10A BulkSimulatorComponent:
 * - invalid JSON → alert matching /invalid json/i, simulateBulk not called
 * - fewer than 2 contexts → alert matching /at least 2 pricing contexts/i
 * - summary test id `bulk-summary` with
 *   "Total requests: N", "Successful requests: N", "Failed requests: N"
 * - result rows in `[data-testid="data-table"]` with quote ids, status
 *   words Success / Failed, the success product id, and the failure message
 */

const INVALID_JSON = /invalid json/i;
const TOO_FEW_CONTEXTS = /at least 2 pricing contexts/i;

describe('BulkSimulatorComponent', () => {
  let fixture: ComponentFixture<BulkSimulatorComponent>;
  let simulateBulkMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    simulateBulkMock = vi.fn().mockReturnValue(of(mockBulkPartialResponse()));

    await TestBed.configureTestingModule({
      imports: [BulkSimulatorComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: PricingExecutionService,
          useValue: {
            simulateBulk: simulateBulkMock,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BulkSimulatorComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function host(): HTMLElement {
    const root = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="bulk-simulator"]',
    );
    expect(root).toBeTruthy();
    return root as HTMLElement;
  }

  function jsonInput(): HTMLTextAreaElement {
    const textarea = host().querySelector(
      '[data-testid="bulk-json-input"]',
    );
    expect(textarea).toBeInstanceOf(HTMLTextAreaElement);
    return textarea as HTMLTextAreaElement;
  }

  function setJson(raw: string): void {
    const textarea = jsonInput();
    textarea.value = raw;
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  function clickControl(testId: string): void {
    const button = host().querySelector(`[data-testid="${testId}"]`);
    expect(button).toBeInstanceOf(HTMLButtonElement);
    (button as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function alertText(): string {
    return host().querySelector('[role="alert"]')?.textContent ?? '';
  }

  function readContexts(raw: string): PricingContext[] {
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed as PricingContext[];
    }
    const wrapped = parsed as { contexts?: PricingContext[] };
    return wrapped.contexts ?? [];
  }

  it('exposes the bulk simulator root and hides the result table before a run (BC-03-10A)', () => {
    // Arrange
    const root = host();

    // Act — initial detectChanges already ran

    // Assert
    expect(root.querySelector('[data-testid="bulk-json-input"]')).toBeTruthy();
    expect(root.querySelector('[data-testid="bulk-simulate"]')).toBeTruthy();
    expect(root.querySelector('[data-testid="bulk-load-sample"]')).toBeTruthy();
    expect(root.querySelector('[data-testid="data-table"]')).toBeNull();
    expect(root.querySelector('[data-testid="bulk-summary"]')).toBeNull();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(simulateBulkMock).not.toHaveBeenCalled();
  });

  it.each(['{not json', '', '   '])(
    'rejects invalid JSON %j and does not call simulateBulk (BC-03-10A)',
    (raw) => {
      // Arrange
      setJson(raw);

      // Act
      clickControl('bulk-simulate');

      // Assert
      expect(alertText()).toMatch(INVALID_JSON);
      expect(host().querySelector('[data-testid="data-table"]')).toBeNull();
      expect(simulateBulkMock).not.toHaveBeenCalled();
    },
  );

  it.each([
    {
      label: 'an empty array',
      json: '[]',
    },
    {
      label: 'a one-context array',
      json: JSON.stringify([MOCK_PRICING_CONTEXT]),
    },
    {
      label: 'a contexts wrapper with one entry',
      json: JSON.stringify({ contexts: [MOCK_PRICING_CONTEXT] }),
    },
    {
      label: 'a single context object',
      json: JSON.stringify(MOCK_PRICING_CONTEXT),
    },
  ])(
    'rejects $label and does not call simulateBulk (BC-03-10A)',
    ({ json }) => {
      // Arrange
      setJson(json);

      // Act
      clickControl('bulk-simulate');

      // Assert
      expect(alertText()).toMatch(TOO_FEW_CONTEXTS);
      expect(alertText()).not.toMatch(INVALID_JSON);
      expect(host().querySelector('[data-testid="data-table"]')).toBeNull();
      expect(simulateBulkMock).not.toHaveBeenCalled();
    },
  );

  it('calls simulateBulk once with contexts parsed from a JSON array (BC-03-10A)', () => {
    // Arrange
    setJson(JSON.stringify(MOCK_BULK_CONTEXTS));

    // Act
    clickControl('bulk-simulate');

    // Assert
    expect(simulateBulkMock).toHaveBeenCalledTimes(1);
    expect(simulateBulkMock).toHaveBeenCalledWith({
      contexts: MOCK_BULK_CONTEXTS,
    });
    expect(alertText()).toBe('');
  });

  it('calls simulateBulk once with contexts parsed from a contexts wrapper (BC-03-10A)', () => {
    // Arrange
    setJson(JSON.stringify({ contexts: MOCK_BULK_CONTEXTS }));

    // Act
    clickControl('bulk-simulate');

    // Assert
    expect(simulateBulkMock).toHaveBeenCalledTimes(1);
    expect(simulateBulkMock).toHaveBeenCalledWith({
      contexts: MOCK_BULK_CONTEXTS,
    });
    expect(alertText()).toBe('');
  });

  it('renders summary counts and per-row success and failure for a partial bulk response (BC-03-10A)', () => {
    // Arrange
    const response = mockBulkPartialResponse();
    simulateBulkMock.mockReturnValue(of(response));
    setJson(JSON.stringify(MOCK_BULK_CONTEXTS));

    // Act
    clickControl('bulk-simulate');

    // Assert
    expect(simulateBulkMock).toHaveBeenCalledTimes(1);

    const summary = host().querySelector('[data-testid="bulk-summary"]');
    expect(summary).toBeTruthy();
    const summaryText = summary?.textContent ?? '';
    expect(summaryText).toMatch(/Total requests:\s*2/);
    expect(summaryText).toMatch(/Successful requests:\s*1/);
    expect(summaryText).toMatch(/Failed requests:\s*1/);

    const table = host().querySelector('[data-testid="data-table"]');
    expect(table).toBeTruthy();
    const tableText = table?.textContent ?? '';
    expect(tableText).toContain('Q-SIM-001');
    expect(tableText).toContain('Q-SIM-002');
    expect(tableText).toMatch(/\bSuccess\b/);
    expect(tableText).toMatch(/\bFailed\b/);
    expect(tableText).toContain('PROD-001');
    expect(tableText).toContain('No matching price book');
    expect(host().querySelector('[role="alert"]')).toBeNull();
  });

  it('loads a sample of at least two contexts into the JSON input (BC-03-10A)', () => {
    // Arrange
    expect(jsonInput().value.trim()).toBe('');

    // Act
    clickControl('bulk-load-sample');

    // Assert
    const contexts = readContexts(jsonInput().value);
    expect(contexts.length).toBeGreaterThanOrEqual(2);
    expect(contexts).toEqual(MOCK_BULK_CONTEXTS);
    expect(simulateBulkMock).not.toHaveBeenCalled();
    expect(host().querySelector('[data-testid="data-table"]')).toBeNull();
  });

  it('applies cdkTextareaAutosize with min/max rows to the JSON input textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(8);
    expect(autosize.maxRows).toBe(24);
  });

  describe('CdkVirtualScrollViewport for large result sets (Phase 5.4)', () => {
    function buildBulkContexts(count: number): PricingContext[] {
      return Array.from({ length: count }, (_, idx) => ({
        ...MOCK_PRICING_CONTEXT,
        quoteExternalId: `Q-BULK-${idx + 1}`,
        customerExternalId: `CUST-${idx + 1}`,
      }));
    }

    it('auto-switches to cdk-virtual-scroll-viewport when rows exceed 25', () => {
      const contexts = buildBulkContexts(30);
      simulateBulkMock.mockReturnValue(
        of({
          totalRequests: 30,
          successfulRequests: 30,
          failedRequests: 0,
          totalExecutionTimeMs: 42,
          results: contexts.map((ctx) => ({
            success: true,
            lineResults: [{ productExternalId: 'PROD-001' }],
            waterfall: { netPrice: 100 },
            financialMetrics: { arr: 1200 },
            approvalState: 'AUTO_APPROVED',
          })),
        }),
      );
      setJson(JSON.stringify(contexts));

      clickControl('bulk-simulate');

      const viewportDe = fixture.debugElement.query(
        By.directive(CdkVirtualScrollViewport),
      );
      expect(viewportDe).toBeTruthy();
      expect(host().querySelector('[data-testid="bulk-virtual-viewport"]')).toBeTruthy();
      expect(host().querySelector('[data-testid="data-table"]')).toBeNull();
    });

    it('allows toggling between table and virtualized views via bulk-toggle-virtual-scroll', () => {
      const response = mockBulkPartialResponse();
      simulateBulkMock.mockReturnValue(of(response));
      setJson(JSON.stringify(MOCK_BULK_CONTEXTS));

      clickControl('bulk-simulate');
      expect(host().querySelector('[data-testid="data-table"]')).toBeTruthy();
      expect(host().querySelector('[data-testid="bulk-virtual-viewport"]')).toBeNull();

      clickControl('bulk-toggle-virtual-scroll');
      expect(host().querySelector('[data-testid="bulk-virtual-viewport"]')).toBeTruthy();
      expect(host().querySelector('[data-testid="data-table"]')).toBeNull();

      clickControl('bulk-toggle-virtual-scroll');
      expect(host().querySelector('[data-testid="data-table"]')).toBeTruthy();
      expect(host().querySelector('[data-testid="bulk-virtual-viewport"]')).toBeNull();
    });
  });

  it('wires CopyButtonComponent for JSON payload and simulation results (DoD P1.2)', () => {
    const response = mockBulkPartialResponse();
    simulateBulkMock.mockReturnValue(of(response));
    const payload = JSON.stringify(MOCK_BULK_CONTEXTS);
    setJson(payload);

    clickControl('bulk-simulate');

    const copyButtons = fixture.debugElement
      .queryAll(By.directive(CopyButtonComponent))
      .map((de) => de.componentInstance as CopyButtonComponent);

    expect(copyButtons.some((b) => b.label() === 'JSON payload' && b.value() === payload)).toBe(true);
    expect(copyButtons.some((b) => b.label() === 'results JSON' && b.value().includes('"totalRequests": 2'))).toBe(true);
  });
});
