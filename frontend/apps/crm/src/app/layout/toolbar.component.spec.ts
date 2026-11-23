import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '@frontend/shared-data-access';
import { CommandPaletteService } from '@frontend/shared-ui';
import { ToolbarComponent } from './toolbar.component';

describe('ToolbarComponent', () => {
  let fixture: ComponentFixture<ToolbarComponent>;
  const session = { signOut: vi.fn() };
  const commandPalette = {
    shortcutLabel: vi.fn(() => 'Ctrl+K'),
  };

  beforeEach(async () => {
    session.signOut.mockReset();
    commandPalette.shortcutLabel.mockReset();
    commandPalette.shortcutLabel.mockReturnValue('Ctrl+K');

    await TestBed.configureTestingModule({
      imports: [ToolbarComponent],
      providers: [
        { provide: SessionService, useValue: session },
        { provide: CommandPaletteService, useValue: commandPalette },
      ],
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

  it('renders command palette trigger with platform shortcut label and emits openCommandPalette on click', () => {
    const el = fixture.nativeElement as HTMLElement;
    const trigger = el.querySelector(
      'button[data-testid="command-palette-trigger"]',
    ) as HTMLButtonElement | null;
    expect(trigger).toBeTruthy();
    expect(trigger?.getAttribute('aria-label')).toBe('Open command palette');
    expect(trigger?.textContent).toContain('Ctrl+K');

    let emitCount = 0;
    fixture.componentInstance.openCommandPalette.subscribe(() => {
      emitCount += 1;
    });

    trigger?.click();
    expect(emitCount).toBe(1);
  });

  it('displays Apple shortcut label when CommandPaletteService returns ⌘K', async () => {
    TestBed.resetTestingModule();
    commandPalette.shortcutLabel.mockReturnValue('⌘K');

    await TestBed.configureTestingModule({
      imports: [ToolbarComponent],
      providers: [
        { provide: SessionService, useValue: session },
        { provide: CommandPaletteService, useValue: commandPalette },
      ],
    }).compileComponents();

    const macFixture = TestBed.createComponent(ToolbarComponent);
    macFixture.detectChanges();

    const trigger = (macFixture.nativeElement as HTMLElement).querySelector(
      'button[data-testid="command-palette-trigger"]',
    ) as HTMLButtonElement | null;
    expect(trigger?.textContent).toContain('⌘K');
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
