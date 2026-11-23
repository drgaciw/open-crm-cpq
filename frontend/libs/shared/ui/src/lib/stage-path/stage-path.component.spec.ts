import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CdkStepper } from '@angular/cdk/stepper';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { StagePathComponent, StagePathItem } from './stage-path.component';

describe('StagePathComponent', () => {
  let fixture: ComponentFixture<StagePathComponent>;

  const sampleStages: StagePathItem[] = [
    { id: 'QUALIFICATION', label: 'Qualification', state: 'completed' },
    { id: 'PROPOSAL', label: 'Proposal', state: 'active' },
    { id: 'NEGOTIATION', label: 'Negotiation', state: 'upcoming' },
    { id: 'WON', label: 'Closed Won', state: 'upcoming' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StagePathComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(StagePathComponent);
    fixture.componentRef.setInput('stages', sampleStages);
    fixture.detectChanges();
  });

  it('extends CdkStepper and renders data-testid="stage-path" with aria-label', () => {
    expect(fixture.componentInstance).toBeInstanceOf(CdkStepper);

    const nav = fixture.nativeElement.querySelector('[data-testid="stage-path"]') as HTMLElement;
    expect(nav).toBeTruthy();
    expect(nav.getAttribute('aria-label')).toBe('Stage progress');

    fixture.componentRef.setInput('ariaLabel', 'Opportunity stage');
    fixture.detectChanges();
    expect(nav.getAttribute('aria-label')).toBe('Opportunity stage');
  });

  it('renders stage states and marks only the active stage with aria-current="step"', () => {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button.stage-path__step'),
    ) as HTMLButtonElement[];

    expect(buttons.length).toBe(4);
    expect(buttons[0].classList.contains('stage-path__step--completed')).toBe(true);
    expect(buttons[0].getAttribute('aria-current')).toBeNull();

    expect(buttons[1].classList.contains('stage-path__step--active')).toBe(true);
    expect(buttons[1].getAttribute('aria-current')).toBe('step');

    expect(buttons[2].classList.contains('stage-path__step--upcoming')).toBe(true);
    expect(buttons[2].getAttribute('aria-current')).toBeNull();
  });

  it('renders danger state styling when a stage is marked danger', () => {
    fixture.componentRef.setInput('stages', [
      { id: 'QUALIFICATION', label: 'Qualification', state: 'completed' },
      { id: 'LOST', label: 'Closed Lost', state: 'danger' },
    ]);
    fixture.detectChanges();

    const lostButton = fixture.nativeElement.querySelector(
      'button[data-stage-id="LOST"]',
    ) as HTMLButtonElement;
    expect(lostButton.classList.contains('stage-path__step--danger')).toBe(true);
    expect(lostButton.textContent).toContain('Closed Lost');
  });

  it('emits stageSelect when a stage button is clicked', () => {
    const selected: string[] = [];
    fixture.componentInstance.stageSelect.subscribe((id) => selected.push(id));

    const negotiationBtn = fixture.nativeElement.querySelector(
      'button[data-stage-id="NEGOTIATION"]',
    ) as HTMLButtonElement;
    negotiationBtn.click();

    expect(selected).toEqual(['NEGOTIATION']);
  });

  it('supports keyboard ArrowRight, ArrowLeft, Home, End, and Enter navigation', () => {
    const selected: string[] = [];
    fixture.componentInstance.stageSelect.subscribe((id) => selected.push(id));

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button.stage-path__step'),
    ) as HTMLButtonElement[];
    const focusSpies = buttons.map((btn) => vi.spyOn(btn, 'focus'));

    // Starts focused on active stage (index 1: PROPOSAL)
    expect(buttons[1].getAttribute('tabindex')).toBe('0');

    // ArrowRight -> index 2 (NEGOTIATION)
    buttons[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(focusSpies[2]).toHaveBeenCalledTimes(1);
    expect(buttons[2].getAttribute('tabindex')).toBe('0');

    // ArrowLeft -> index 1 (PROPOSAL)
    buttons[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(focusSpies[1]).toHaveBeenCalledTimes(1);

    // End -> index 3 (WON)
    buttons[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(focusSpies[3]).toHaveBeenCalledTimes(1);

    // Home -> index 0 (QUALIFICATION)
    buttons[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(focusSpies[0]).toHaveBeenCalledTimes(1);

    // Enter on index 0 emits stageSelect('QUALIFICATION')
    buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(selected).toEqual(['QUALIFICATION']);
  });

  describe('non-interactive mode (interactive=false)', () => {
    it('defaults interactive to true', () => {
      expect(fixture.componentInstance.interactive()).toBe(true);
    });

    it('renders non-focusable, non-button steps in a list with aria-current="step" and visual states', () => {
      fixture.componentRef.setInput('interactive', false);
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelectorAll('button.stage-path__step').length).toBe(0);

      const steps = Array.from(el.querySelectorAll('.stage-path__step')) as HTMLElement[];
      expect(steps.length).toBe(4);
      for (const step of steps) {
        expect(step.tagName).not.toBe('BUTTON');
        expect(step.getAttribute('tabindex')).toBeNull();
      }

      expect(steps[0].classList.contains('stage-path__step--completed')).toBe(true);
      expect(steps[0].getAttribute('aria-current')).toBeNull();
      expect(steps[1].classList.contains('stage-path__step--active')).toBe(true);
      expect(steps[1].getAttribute('aria-current')).toBe('step');
      expect(steps[2].classList.contains('stage-path__step--upcoming')).toBe(true);
      expect(steps[2].getAttribute('aria-current')).toBeNull();
    });

    it('does not emit stageSelect when a non-interactive step is clicked', () => {
      fixture.componentRef.setInput('interactive', false);
      fixture.detectChanges();

      const selected: string[] = [];
      fixture.componentInstance.stageSelect.subscribe((id) => selected.push(id));

      const step = fixture.nativeElement.querySelector(
        '[data-stage-id="NEGOTIATION"]',
      ) as HTMLElement;
      step.click();

      expect(selected).toEqual([]);
    });
  });
});
