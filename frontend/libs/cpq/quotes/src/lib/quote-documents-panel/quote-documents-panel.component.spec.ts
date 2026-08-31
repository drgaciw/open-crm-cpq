import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { QuoteDocumentsPanelComponent } from './quote-documents-panel.component';
import { QuoteService } from '../quote.service';
import { QuoteDocumentDto } from '@frontend/shared-models';

const DOC: QuoteDocumentDto = {
  id: 11,
  uuid: 'doc-11',
  quoteId: 5,
  documentType: 'PROPOSAL',
  fileName: 'quote-5-proposal.pdf',
  contentType: 'application/pdf',
  fileSize: 2048,
  createdAt: '2026-08-30T12:00:00Z',
};

describe('QuoteDocumentsPanelComponent', () => {
  let fixture: ComponentFixture<QuoteDocumentsPanelComponent>;
  let component: QuoteDocumentsPanelComponent;
  let listDocumentsSpy: ReturnType<typeof vi.fn>;
  let generateDocumentsSpy: ReturnType<typeof vi.fn>;
  let downloadDocumentSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    listDocumentsSpy = vi.fn(() => of([DOC]));
    generateDocumentsSpy = vi.fn(() =>
      of({ ...DOC, id: 12, documentType: 'ORDER_FORM' as const }),
    );
    downloadDocumentSpy = vi.fn(() =>
      of(new Blob(['%PDF'], { type: 'application/pdf' })),
    );

    await TestBed.configureTestingModule({
      imports: [QuoteDocumentsPanelComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: QuoteService,
          useValue: {
            listDocuments: listDocumentsSpy,
            generateDocuments: generateDocumentsSpy,
            downloadDocument: downloadDocumentSpy,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteDocumentsPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('quoteId', 5);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists documents for quoteId on init', () => {
    expect(listDocumentsSpy).toHaveBeenCalledWith(5);
    expect(component.documents()).toEqual([DOC]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Proposal');
    expect(el.textContent).toContain('quote-5-proposal.pdf');
  });

  it('defaults generate type to PROPOSAL and calls generateDocuments', () => {
    expect(component.generateForm.controls.type.value).toBe('PROPOSAL');
    listDocumentsSpy.mockClear();
    component.onGenerate();
    expect(generateDocumentsSpy).toHaveBeenCalledWith(5, 'PROPOSAL');
    expect(listDocumentsSpy).toHaveBeenCalledWith(5);
  });

  it('generates with selected document type', () => {
    component.generateForm.controls.type.setValue('SOW');
    component.onGenerate();
    expect(generateDocumentsSpy).toHaveBeenCalledWith(5, 'SOW');
  });

  it('downloads via blob response and uses DTO fileName', () => {
    const createObjectURL = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock');
    const revokeObjectURL = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => undefined);
    const realCreate = document.createElement.bind(document);
    const click = vi.fn();
    vi.spyOn(document, 'createElement').mockImplementation(((tag: string) => {
      const el = realCreate(tag);
      if (tag === 'a') {
        el.click = click;
      }
      return el;
    }) as typeof document.createElement);

    component.onDownload(DOC);

    expect(downloadDocumentSpy).toHaveBeenCalledWith(5, 11);
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock');
    const anchors = (
      document.createElement as unknown as ReturnType<typeof vi.fn>
    ).mock.results
      .map((r: { value: HTMLElement }) => r.value)
      .filter((el: HTMLElement) => el.tagName === 'A') as HTMLAnchorElement[];
    expect(anchors.at(-1)?.download).toBe('quote-5-proposal.pdf');
  });

  it('shows error banner when list fails', () => {
    listDocumentsSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Documents unavailable' } })),
    );
    fixture.componentRef.setInput('quoteId', 6);
    fixture.detectChanges();

    expect(component.error()).toBe('Documents unavailable');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Documents unavailable',
    );
  });

  it('shows empty state when no documents', () => {
    listDocumentsSpy.mockReturnValue(of([]));
    fixture.componentRef.setInput('quoteId', 7);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No documents generated yet',
    );
  });
});
