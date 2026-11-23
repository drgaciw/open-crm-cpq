import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CopyButtonComponent } from './copy-button.component';

describe('CopyButtonComponent', () => {
  let fixture: ComponentFixture<CopyButtonComponent>;
  let clipboardMock: { copy: ReturnType<typeof vi.fn> };
  let liveAnnouncerMock: { announce: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    vi.useFakeTimers();

    clipboardMock = {
      copy: vi.fn().mockReturnValue(true),
    };
    liveAnnouncerMock = {
      announce: vi.fn().mockResolvedValue(undefined),
    };

    await TestBed.configureTestingModule({
      imports: [CopyButtonComponent, NoopAnimationsModule],
      providers: [
        { provide: Clipboard, useValue: clipboardMock },
        { provide: LiveAnnouncer, useValue: liveAnnouncerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CopyButtonComponent);
    fixture.componentRef.setInput('value', 'QT-2026-0042');
    fixture.componentRef.setInput('label', 'quote number');
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders copy icon and accessible label by default', () => {
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    const icon = fixture.nativeElement.querySelector('mat-icon') as HTMLElement;

    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Copy quote number');
    expect(icon.textContent?.trim()).toBe('content_copy');
  });

  it('copies value, swaps icon for 1500ms, announces politely, and emits true on success', () => {
    const emitted: boolean[] = [];
    fixture.componentInstance.copied.subscribe((result) => emitted.push(result));

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    expect(clipboardMock.copy).toHaveBeenCalledWith('QT-2026-0042');
    expect(liveAnnouncerMock.announce).toHaveBeenCalledWith(
      'Copied quote number to clipboard',
      'polite',
    );
    expect(emitted).toEqual([true]);

    const icon = fixture.nativeElement.querySelector('mat-icon') as HTMLElement;
    expect(icon.textContent?.trim()).toBe('check');
    expect(button.getAttribute('aria-label')).toBe('Copied quote number');

    vi.advanceTimersByTime(1499);
    fixture.detectChanges();
    expect(icon.textContent?.trim()).toBe('check');

    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(icon.textContent?.trim()).toBe('content_copy');
    expect(button.getAttribute('aria-label')).toBe('Copy quote number');
  });

  it('stops click event propagation', () => {
    const parentClick = vi.fn();
    const hostEl = fixture.nativeElement as HTMLElement;
    hostEl.addEventListener('click', parentClick);

    const button = hostEl.querySelector('button') as HTMLButtonElement;
    button.click();

    expect(parentClick).not.toHaveBeenCalled();
  });

  it('does not flip icon or announce and emits false when clipboard copy fails', () => {
    clipboardMock.copy.mockReturnValue(false);
    const emitted: boolean[] = [];
    fixture.componentInstance.copied.subscribe((result) => emitted.push(result));

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('mat-icon') as HTMLElement;
    expect(icon.textContent?.trim()).toBe('content_copy');
    expect(liveAnnouncerMock.announce).not.toHaveBeenCalled();
    expect(emitted).toEqual([false]);
  });

  it('clears pending reset timer cleanly on destroy', () => {
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    fixture.destroy();
    expect(() => vi.advanceTimersByTime(2000)).not.toThrow();
  });
});
