import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AgentPanelComponent } from './agent-panel.component';

describe('AgentPanelComponent', () => {
  let fixture: ComponentFixture<AgentPanelComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentPanelComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AgentPanelComponent);
    element = fixture.nativeElement as HTMLElement;
  });

  it('renders advisory chrome and empty state by default', () => {
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="agent-panel"]')).toBeTruthy();
    expect(element.querySelector('.agent-panel__chip')?.textContent).toContain(
      'Advisory — not a decision',
    );
    expect(element.querySelector('.agent-panel__state')?.textContent).toContain(
      'No advisory available yet.',
    );
  });

  it('shows loading live region', () => {
    fixture.componentRef.setInput('state', 'loading');
    fixture.detectChanges();
    const state = element.querySelector('.agent-panel__state') as HTMLElement;
    expect(state.getAttribute('role')).toBe('status');
    expect(state.getAttribute('aria-live')).toBe('polite');
    expect(element.querySelector('mat-spinner')).toBeTruthy();
  });

  it('shows disabled copy without projecting ready content', () => {
    fixture.componentRef.setInput('state', 'disabled');
    fixture.componentRef.setInput(
      'disabledMessage',
      'Jev runtime flags stay disabled (ADR-015).',
    );
    fixture.detectChanges();
    expect(element.textContent).toContain('Jev runtime flags stay disabled');
  });

  it('shows evidence and actions when ready', () => {
    fixture.componentRef.setInput('state', 'ready');
    fixture.componentRef.setInput('evidence', ['Model typesafe/jev', 'Rubric lead-score-v1']);
    fixture.componentRef.setInput('showActions', true);
    fixture.detectChanges();

    expect(element.querySelector('.agent-panel__evidence')?.textContent).toContain('lead-score-v1');
    const actionButtons = Array.from(
      element.querySelectorAll('.agent-panel__actions button'),
    ) as HTMLButtonElement[];
    expect(actionButtons.map((b) => b.textContent?.trim())).toEqual([
      'Dismiss',
      'Acknowledge',
    ]);
  });

  it('collapses and expands the body', () => {
    fixture.componentRef.setInput('state', 'empty');
    fixture.detectChanges();
    expect(element.querySelector('.agent-panel__body')).toBeTruthy();

    const toggle = element.querySelector('.agent-panel__toggle') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    expect(element.querySelector('.agent-panel__body')).toBeNull();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(element.querySelector('.agent-panel__body')).toBeTruthy();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
  });

  it('surfaces error alerts', () => {
    fixture.componentRef.setInput('state', 'error');
    fixture.componentRef.setInput('errorMessage', 'Backend advisory down');
    fixture.detectChanges();
    const alert = element.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Backend advisory down');
  });
});
