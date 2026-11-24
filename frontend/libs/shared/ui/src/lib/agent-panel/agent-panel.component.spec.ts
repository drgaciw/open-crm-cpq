import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CdkAccordionItem } from '@angular/cdk/accordion';
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

  it('names the progress indicator with its advisory loading message', () => {
    fixture.componentRef.setInput('state', 'loading');
    fixture.componentRef.setInput('loadingMessage', 'Evaluating advisory tier…');
    fixture.detectChanges();
    const state = element.querySelector('.agent-panel__state') as HTMLElement;
    expect(state.getAttribute('role')).toBe('status');
    expect(state.getAttribute('aria-live')).toBe('polite');
    expect(element.querySelector('[role="progressbar"]')?.getAttribute('aria-label'))
      .toBe('Evaluating advisory tier…');
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

  it('uses CdkAccordionItem for expand/collapse while preserving aria-controls and initiallyCollapsed', () => {
    fixture.componentRef.setInput('initiallyCollapsed', true);
    fixture.detectChanges();

    const accordionDebugEl = fixture.debugElement.query(By.directive(CdkAccordionItem));
    expect(accordionDebugEl).toBeTruthy();

    const accordionItem = accordionDebugEl.injector.get(CdkAccordionItem);
    const toggle = element.querySelector('.agent-panel__toggle') as HTMLButtonElement;

    expect(accordionItem.expanded).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe(fixture.componentInstance.bodyId);
    expect(element.querySelector('.agent-panel__body')).toBeNull();

    accordionItem.open();
    fixture.detectChanges();

    expect(accordionItem.expanded).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    const body = element.querySelector('.agent-panel__body') as HTMLElement;
    expect(body).toBeTruthy();
    expect(body.id).toBe(fixture.componentInstance.bodyId);
  });
});
