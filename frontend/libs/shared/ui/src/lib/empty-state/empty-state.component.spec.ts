import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('title', 'No items');
    fixture.componentRef.setInput('message', 'Nothing to show yet.');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should expose role="status" on the root', () => {
    const root = fixture.nativeElement.querySelector('.empty-state') as HTMLElement;
    expect(root.getAttribute('role')).toBe('status');
  });

  it('should set aria-live="polite" on the root', () => {
    const root = fixture.nativeElement.querySelector('.empty-state') as HTMLElement;
    expect(root.getAttribute('aria-live')).toBe('polite');
  });

  it('should render title and message', () => {
    expect(fixture.nativeElement.querySelector('.empty-state-title')?.textContent).toContain('No items');
    expect(fixture.nativeElement.querySelector('.empty-state-message')?.textContent).toContain(
      'Nothing to show yet.',
    );
  });

  it('should emit action when action button is clicked', () => {
    fixture.componentRef.setInput('actionLabel', 'Create');
    fixture.detectChanges();

    let emitted = false;
    fixture.componentInstance.action.subscribe(() => {
      emitted = true;
    });

    const button = fixture.nativeElement.querySelector('.empty-state-action') as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
    expect(emitted).toBe(true);
  });
});
