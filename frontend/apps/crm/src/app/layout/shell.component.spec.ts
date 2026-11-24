import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { filter, firstValueFrom, of } from 'rxjs';
import { LoadingService } from '@frontend/shared-data-access';
import { CommandPaletteItem, CommandPaletteService } from '@frontend/shared-ui';
import { ShellComponent } from './shell.component';

@Component({ template: '' })
class RoutedPageComponent {}

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let component: ShellComponent;
  let observeSpy: ReturnType<typeof vi.fn>;
  let loadingService: LoadingService;
  let paletteOpenSignal: ReturnType<typeof signal<boolean>>;
  let commandPaletteMock: {
    open: ReturnType<typeof vi.fn>;
    close: ReturnType<typeof vi.fn>;
    isOpen: ReturnType<ReturnType<typeof signal<boolean>>['asReadonly']>;
    shortcutLabel: ReturnType<typeof vi.fn>;
  };

  async function setup(matchesMobile: boolean): Promise<void> {
    observeSpy = vi.fn(() => of({ matches: matchesMobile, breakpoints: {} }));
    paletteOpenSignal = signal(false);
    commandPaletteMock = {
      open: vi.fn(() => {
        paletteOpenSignal.set(true);
      }),
      close: vi.fn(() => {
        paletteOpenSignal.set(false);
      }),
      isOpen: paletteOpenSignal.asReadonly(),
      shortcutLabel: vi.fn(() => 'Ctrl+K'),
    };

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([
          { path: 'organizations', component: RoutedPageComponent },
          { path: 'quotes', component: RoutedPageComponent },
        ]),
        provideNoopAnimations(),
        { provide: BreakpointObserver, useValue: { observe: observeSpy } },
        { provide: CommandPaletteService, useValue: commandPaletteMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    loadingService = TestBed.inject(LoadingService);
    loadingService.reset();
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('uses side mode and opens sidenav on desktop', async () => {
    await setup(false);
    expect(observeSpy).toHaveBeenCalledWith('(max-width: 767.98px)');
    expect(component.sidenavMode()).toBe('side');
    expect(component.sidenavOpened()).toBe(true);
  });

  it('uses over mode and starts closed on mobile', async () => {
    await setup(true);
    expect(component.sidenavMode()).toBe('over');
    expect(component.sidenavOpened()).toBe(false);
  });

  it('renders skip-link targeting #main-content', async () => {
    await setup(false);
    const el = fixture.nativeElement as HTMLElement;
    const skip = el.querySelector('a.skip-link') as HTMLAnchorElement | null;
    const main = el.querySelector('main#main-content');
    expect(skip).toBeTruthy();
    expect(skip?.getAttribute('href')).toBe('#main-content');
    expect(skip?.textContent?.trim()).toBe('Skip to main content');
    expect(main).toBeTruthy();
  });

  it('moves focus to the main landmark without navigating on skip-link activation', async () => {
    await setup(false);
    const root = fixture.nativeElement as HTMLElement;
    const link = root.querySelector<HTMLAnchorElement>('a.skip-link')!;
    const main = root.querySelector<HTMLElement>('main#main-content')!;
    const click = new MouseEvent('click', { bubbles: true, cancelable: true });

    link.focus();
    link.dispatchEvent(click);

    expect(click.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(main);
  });

  it('exposes aria-label on sidenav navigation', async () => {
    await setup(false);
    const el = fixture.nativeElement as HTMLElement;
    const sidenav = el.querySelector('mat-sidenav');
    expect(sidenav?.getAttribute('aria-label')).toBe('Main navigation');
    expect(sidenav?.getAttribute('role')).toBe('navigation');
  });

  it('hamburger toggle opens mobile sidenav', async () => {
    await setup(true);
    expect(component.sidenavOpened()).toBe(false);
    component.onToggleSidenav();
    expect(component.sidenavOpened()).toBe(true);
    component.onToggleSidenav();
    expect(component.sidenavOpened()).toBe(false);
  });

  it('exposes the drawer relationship and tracks its expanded state', async () => {
    await setup(true);
    const root = fixture.nativeElement as HTMLElement;
    const drawer = root.querySelector('mat-sidenav')!;
    const toggle = root.querySelector<HTMLButtonElement>('button[aria-label="Toggle menu"]')!;

    expect(drawer.id).not.toBe('');
    expect(toggle.getAttribute('aria-controls')).toBe(drawer.id);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('closes the temporary drawer when selecting a sidebar route or the already active link', async () => {
    await setup(true);
    const root = fixture.nativeElement as HTMLElement;
    const sidebarLink = root.querySelector<HTMLAnchorElement>('app-sidebar a[href="/organizations"]')!;
    const toggle = root.querySelector<HTMLButtonElement>('button[aria-label="Toggle menu"]')!;

    const open = async () => {
      const drawerOpened = firstValueFrom(component.sidenav.openedChange.pipe(filter(Boolean)));
      toggle.click();
      fixture.detectChanges();
      await drawerOpened;
    };

    await open();
    expect(component.sidenavOpened()).toBe(true);
    sidebarLink.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/organizations');
    expect(component.sidenavOpened()).toBe(false);

    await open();
    sidebarLink.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/organizations');
    expect(component.sidenavOpened()).toBe(false);
  });

  it('keeps the desktop drawer open and marks only the active sidebar destination as current', async () => {
    await setup(false);
    const root = fixture.nativeElement as HTMLElement;
    const accounts = root.querySelector<HTMLAnchorElement>('app-sidebar a[href="/organizations"]')!;
    const quotes = root.querySelector<HTMLAnchorElement>('app-sidebar a[href="/quotes"]')!;

    accounts.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.sidenavOpened()).toBe(true);
    expect(accounts.getAttribute('aria-current')).toBe('page');
    expect(quotes.hasAttribute('aria-current')).toBe(false);

    quotes.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.sidenavOpened()).toBe(true);
    expect(accounts.hasAttribute('aria-current')).toBe(false);
    expect(quotes.getAttribute('aria-current')).toBe('page');
  });

  it('shows a global progress bar while LoadingService reports loading', async () => {
    await setup(false);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-progress-bar.global-loading')).toBeNull();

    loadingService.startRequest();
    fixture.detectChanges();
    const bar = el.querySelector('mat-progress-bar.global-loading');
    expect(bar).toBeTruthy();
    expect(bar?.getAttribute('aria-label')).toBe('Loading');

    loadingService.endRequest();
    fixture.detectChanges();
    expect(el.querySelector('mat-progress-bar.global-loading')).toBeNull();
  });

  it('opens CommandPaletteService on toolbar trigger click with workspace commands', async () => {
    await setup(false);
    const el = fixture.nativeElement as HTMLElement;
    const trigger = el.querySelector(
      'button[data-testid="command-palette-trigger"]',
    ) as HTMLButtonElement | null;
    expect(trigger).toBeTruthy();

    trigger?.click();

    expect(commandPaletteMock.open).toHaveBeenCalledTimes(1);
    const items = commandPaletteMock.open.mock.calls[0][0] as CommandPaletteItem[];
    const routes = items.map((item) => item.route?.join('/'));
    expect(routes).toEqual(
      expect.arrayContaining([
        '/dashboard',
        '/organizations',
        '/contacts',
        '/leads',
        '/deals',
        '/deals/kanban',
        '/quotes',
        '/catalog',
        '/price-books',
        '/pricing-customers',
        '/pricing/simulator',
        '/approvals',
        '/product-bundles',
        '/rule-sets',
        '/leads/new',
        '/deals/new',
        '/quotes/new',
      ]),
    );
  });

  it('toggles CommandPaletteService on Ctrl+K and prevents default', async () => {
    await setup(false);

    const openEvent = new KeyboardEvent('keydown', {
      key: 'k',
      ctrlKey: true,
      cancelable: true,
    });
    window.dispatchEvent(openEvent);

    expect(openEvent.defaultPrevented).toBe(true);
    expect(commandPaletteMock.open).toHaveBeenCalledTimes(1);
    expect(commandPaletteMock.close).not.toHaveBeenCalled();

    const closeEvent = new KeyboardEvent('keydown', {
      key: 'K',
      ctrlKey: true,
      cancelable: true,
    });
    window.dispatchEvent(closeEvent);

    expect(closeEvent.defaultPrevented).toBe(true);
    expect(commandPaletteMock.close).toHaveBeenCalledTimes(1);
  });

  it('toggles CommandPaletteService on Meta+K and ignores plain k', async () => {
    await setup(false);

    const plainKeyEvent = new KeyboardEvent('keydown', {
      key: 'k',
      cancelable: true,
    });
    window.dispatchEvent(plainKeyEvent);

    expect(plainKeyEvent.defaultPrevented).toBe(false);
    expect(commandPaletteMock.open).not.toHaveBeenCalled();

    const metaOpenEvent = new KeyboardEvent('keydown', {
      key: 'k',
      metaKey: true,
      cancelable: true,
    });
    window.dispatchEvent(metaOpenEvent);

    expect(metaOpenEvent.defaultPrevented).toBe(true);
    expect(commandPaletteMock.open).toHaveBeenCalledTimes(1);

    const metaCloseEvent = new KeyboardEvent('keydown', {
      key: 'k',
      metaKey: true,
      cancelable: true,
    });
    window.dispatchEvent(metaCloseEvent);

    expect(metaCloseEvent.defaultPrevented).toBe(true);
    expect(commandPaletteMock.close).toHaveBeenCalledTimes(1);
  });
});
