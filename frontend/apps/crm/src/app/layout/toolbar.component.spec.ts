import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '@frontend/shared-data-access';
import { ToolbarComponent } from './toolbar.component';

describe('ToolbarComponent', () => {
  let fixture: ComponentFixture<ToolbarComponent>;
  const session = { signOut: vi.fn() };

  beforeEach(async () => {
    session.signOut.mockReset();
    await TestBed.configureTestingModule({
      imports: [ToolbarComponent],
      providers: [{ provide: SessionService, useValue: session }],
    }).compileComponents();

    fixture = TestBed.createComponent(ToolbarComponent);
    fixture.detectChanges();
  });

  it('exposes menu toggle and does not render inert Notifications/Account', () => {
    const el = fixture.nativeElement as HTMLElement;
    const toggle = el.querySelector(
      'button[aria-label="Toggle menu"]',
    ) as HTMLButtonElement | null;
    expect(toggle).toBeTruthy();
    expect(el.querySelector('button[aria-label="Notifications"]')).toBeNull();
    expect(el.querySelector('button[aria-label="Account"]')).toBeNull();
  });

  it('signs out through the gateway session (ADR-016)', () => {
    const el = fixture.nativeElement as HTMLElement;
    const signOut = el.querySelector(
      'button[aria-label="Sign out"]',
    ) as HTMLButtonElement | null;
    expect(signOut).toBeTruthy();

    signOut?.click();

    expect(session.signOut).toHaveBeenCalledTimes(1);
  });
});
