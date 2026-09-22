import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { JevSuggestion } from '@frontend/shared-models';
import { AdvisorySuggestionComponent } from './advisory-suggestion.component';

const SUGGESTION: JevSuggestion = {
  value: 'DIRECTOR',
  confidence: 0.8,
  probabilities: { MANAGER: 0.1, DIRECTOR: 0.8, VP_SALES: 0.1 },
  rubricVersion: 'tier-v1-thresholds',
  model: 'typesafe/jev-1.13',
  advisory: true,
  computedAt: '2026-09-20T10:00:00Z',
};

describe('AdvisorySuggestionComponent', () => {
  let fixture: ComponentFixture<AdvisorySuggestionComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdvisorySuggestionComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AdvisorySuggestionComponent);
    element = fixture.nativeElement as HTMLElement;
  });

  it('should render nothing when there is no suggestion', () => {
    fixture.detectChanges();
    expect(element.querySelector('.advisory-suggestion')).toBeNull();

    fixture.componentRef.setInput('suggestion', undefined);
    fixture.detectChanges();
    expect(element.querySelector('.advisory-suggestion')).toBeNull();
  });

  it('should always show the advisory label', () => {
    fixture.componentRef.setInput('suggestion', SUGGESTION);
    fixture.detectChanges();

    expect(element.querySelector('.advisory-chip')?.textContent).toContain('Advisory — not a decision');
    expect(element.querySelector('.advisory-suggestion')?.getAttribute('role')).toBe('note');
  });

  it('should show the value, confidence and evaluator pins', () => {
    fixture.componentRef.setInput('suggestion', SUGGESTION);
    fixture.componentRef.setInput('heading', 'Suggested tier');
    fixture.detectChanges();

    expect(element.querySelector('.advisory-heading')?.textContent).toContain('Suggested tier');
    expect(element.querySelector('.advisory-value strong')?.textContent).toContain('DIRECTOR');
    expect(element.querySelector('.advisory-confidence')?.textContent).toContain('80% confidence');
    expect(element.querySelector('.advisory-meta')?.textContent).toContain('tier-v1-thresholds');
  });

  it('should render one accessible meter per probability and mark the suggested one', () => {
    fixture.componentRef.setInput('suggestion', SUGGESTION);
    fixture.detectChanges();

    const meters = element.querySelectorAll('[role="meter"]');
    expect(meters.length).toBe(3);
    expect(meters[1].getAttribute('aria-valuenow')).toBe('80');

    const suggested = element.querySelectorAll('.advisory-bar.suggested');
    expect(suggested.length).toBe(1);
    expect(suggested[0].textContent).toContain('DIRECTOR');
  });

  it('should use display labels when provided', () => {
    fixture.componentRef.setInput('suggestion', SUGGESTION);
    fixture.componentRef.setInput('labels', { DIRECTOR: 'Director', MANAGER: 'Manager' });
    fixture.detectChanges();

    expect(element.querySelector('.advisory-value strong')?.textContent).toContain('Director');
    expect(element.querySelector('.advisory-bars')?.textContent).toContain('VP_SALES');
  });
});
