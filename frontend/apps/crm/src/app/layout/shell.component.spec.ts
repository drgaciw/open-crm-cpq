import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BreakpointObserver } from '@angular/cdk/layout';
import { of } from 'rxjs';
import { LoadingService } from '@frontend/shared-data-access';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let component: ShellComponent;
  let observeSpy: ReturnType<typeof vi.fn>;
  let loadingService: LoadingService;

  async function setup(matchesMobile: boolean): Promise<void> {
    observeSpy = vi.fn(() => of({ matches: matchesMobile, breakpoints: {} }));

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: BreakpointObserver, useValue: { observe: observeSpy } },
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
});
