import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
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

  function component(): BulkSimulatorComponent {
    return fixture.componentInstance;
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

  it('renders summary counts and result rows for a partial bulk response (BC-03-10A)', () => {
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

    expect(host().querySelector('[data-testid="data-table"]')).toBeTruthy();
    expect(host().querySelector('[role="alert"]')).toBeNull();

    const rows = component().rows();
    expect(rows).toHaveLength(2);
    expect(rows[0].quoteExternalId).toBe('Q-SIM-001');
    expect(rows[0].status).toBe('Success');
    expect(rows[0].productExternalId).toBe('PROD-001');
    expect(rows[1].quoteExternalId).toBe('Q-SIM-002');
    expect(rows[1].status).toBe('Failed');
    expect(rows[1].errorMessage).toBe('No matching price book');
  });

  it.each([
    {
      input: null,
      label: 'Context 2',
      product: '',
    },
    {
      input: {
        ...MOCK_BULK_CONTEXTS[1],
        lineItems: [null, ...MOCK_BULK_CONTEXTS[1].lineItems],
      },
      label: 'Q-SIM-002',
      product: 'PROD-002',
    },
  ])('keeps the failed row visible for $label with malformed input', ({ input, label, product }) => {
    const contexts = [MOCK_PRICING_CONTEXT, input];
    setJson(JSON.stringify(contexts));

    clickControl('bulk-simulate');

    expect(simulateBulkMock).toHaveBeenCalledExactlyOnceWith({ contexts });
    const rows = component().rows();
    expect(rows).toHaveLength(2);
    expect(rows[0].quoteExternalId).toBe('Q-SIM-001');
    expect(rows[0].status).toBe('Success');
    expect(rows[1].quoteExternalId).toContain(label);
    expect(rows[1].status).toBe('Failed');
    expect(rows[1].errorMessage).toBe('No matching price book');
    if (product) {
      expect(rows[1].productExternalId).toContain(product);
    }
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

  it('runs a wrapped JSON file and downloads the exact partial response', async () => {
    const response = mockBulkPartialResponse();
    simulateBulkMock.mockReturnValue(of(response));
    const upload = host().querySelector('[data-testid="bulk-file-input"]') as HTMLInputElement;
    const content = JSON.stringify({ contexts: MOCK_BULK_CONTEXTS });
    Object.defineProperty(upload, 'files', {
      value: [new File([content], 'contexts.json', { type: 'application/json' })],
    });
    upload.dispatchEvent(new Event('change'));

    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(jsonInput().value).toBe(content);
    });

    clickControl('bulk-simulate');
    expect(simulateBulkMock).toHaveBeenCalledExactlyOnceWith({ contexts: MOCK_BULK_CONTEXTS });

    const createUrl = vi.fn().mockReturnValue('blob:bulk-result');
    const revokeUrl = vi.fn();
    let downloadedName = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloadedName = this.download;
    });
    vi.stubGlobal('URL', { createObjectURL: createUrl, revokeObjectURL: revokeUrl });
    try {
      clickControl('bulk-download-results');
      expect(downloadedName).toBe('bulk-pricing-simulation-results.json');
      expect(revokeUrl).toHaveBeenCalledExactlyOnceWith('blob:bulk-result');
      const blob = createUrl.mock.calls[0]?.[0] as Blob;
      const json = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(blob);
      });
      expect(JSON.parse(json)).toEqual(response);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  describe('CDK enhancements', () => {
    it('applies cdkTextareaAutosize with min/max rows to the JSON input textarea', () => {
      const textarea = jsonInput();
      expect(textarea.hasAttribute('cdktextareaautosize')).toBe(true);
      expect(textarea.getAttribute('cdkautosizeminrows')).toBe('4');
      expect(textarea.getAttribute('cdkautosizemaxrows')).toBe('20');
    });

    it('renders app-copy-button in the summary with the results JSON after a run', () => {
      simulateBulkMock.mockReturnValue(of(mockBulkPartialResponse()));
      setJson(JSON.stringify(MOCK_BULK_CONTEXTS));
      clickControl('bulk-simulate');

      const copyBtn = host().querySelector('[data-testid="bulk-copy-results"]');
      expect(copyBtn).toBeTruthy();
      expect(component().resultsJson()).toBeTruthy();
      expect(JSON.parse(component().resultsJson())).toEqual(mockBulkPartialResponse());
    });

    it('renders results inside a CdkVirtualScrollViewport', () => {
      simulateBulkMock.mockReturnValue(of(mockBulkPartialResponse()));
      setJson(JSON.stringify(MOCK_BULK_CONTEXTS));
      clickControl('bulk-simulate');

      const viewport = host().querySelector('[data-testid="bulk-virtual-viewport"]');
      expect(viewport).toBeTruthy();
      expect(viewport?.tagName.toLowerCase()).toBe('cdk-virtual-scroll-viewport');

      const header = host().querySelector('[data-testid="data-table"] .virtual-header');
      expect(header).toBeTruthy();
      const headerText = header?.textContent ?? '';
      expect(headerText).toContain('Quote');
      expect(headerText).toContain('Status');
      expect(headerText).toContain('Error');
    });

    it('sizes the virtual viewport height to fit rows up to 400px', () => {
      expect(component().viewportHeight()).toBe(0);

      simulateBulkMock.mockReturnValue(of(mockBulkPartialResponse()));
      setJson(JSON.stringify(MOCK_BULK_CONTEXTS));
      clickControl('bulk-simulate');

      expect(component().viewportHeight()).toBe(2 * 40);
    });

    it('clears resultsJson when simulation fails', () => {
      simulateBulkMock.mockReturnValue(of(mockBulkPartialResponse()));
      setJson(JSON.stringify(MOCK_BULK_CONTEXTS));
      clickControl('bulk-simulate');
      expect(component().resultsJson()).toBeTruthy();

      setJson('{bad');
      clickControl('bulk-simulate');
      expect(component().resultsJson()).toBe('');
    });
  });
});
