import { TestBed } from '@angular/core/testing';
import { OverlayContainer } from '@angular/cdk/overlay';
import { Platform } from '@angular/cdk/platform';
import { Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CommandPaletteComponent, CommandPaletteItem } from './command-palette.component';
import { CommandPaletteService } from './command-palette.service';

describe('CommandPaletteComponent & CommandPaletteService', () => {
  const sampleItems: CommandPaletteItem[] = [
    {
      id: 'nav-accounts',
      label: 'Go to Accounts',
      category: 'Navigation',
      icon: 'business',
      keywords: ['organizations', 'customers'],
      route: ['/organizations'],
    },
    {
      id: 'nav-quotes',
      label: 'Go to Quotes',
      category: 'CPQ',
      icon: 'request_quote',
      keywords: ['proposals'],
      route: ['/quotes'],
    },
    {
      id: 'new-lead',
      label: 'Create Lead',
      category: 'Actions',
      icon: 'person_add',
      action: vi.fn(),
    },
  ];

  describe('CommandPaletteComponent (standalone)', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [CommandPaletteComponent, NoopAnimationsModule],
      }).compileComponents();
    });

    it('renders combobox, listbox, and filters items by label, category, and keywords', () => {
      const fixture = TestBed.createComponent(CommandPaletteComponent);
      fixture.componentRef.setInput('items', sampleItems);
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="command-palette"]')).toBeTruthy();

      const input = el.querySelector('input[role="combobox"]') as HTMLInputElement;
      expect(input).toBeTruthy();
      expect(input.getAttribute('aria-expanded')).toBe('true');
      expect(el.querySelector('[role="listbox"]')).toBeTruthy();
      expect(el.querySelectorAll('[role="option"]').length).toBe(3);

      // Filter by keyword
      input.value = 'organizations';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(el.querySelectorAll('[role="option"]').length).toBe(1);
      expect(el.querySelector('[role="option"]')?.textContent).toContain('Go to Accounts');

      // Filter with no matches
      input.value = 'nonexistent-xyz';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(el.querySelectorAll('[role="option"]').length).toBe(0);
      expect(el.querySelector('.command-palette__empty')?.textContent).toContain(
        'No matching commands',
      );
    });

    it('navigates with ArrowDown/ArrowUp, updates aria-activedescendant, and selects on Enter', () => {
      const fixture = TestBed.createComponent(CommandPaletteComponent);
      fixture.componentRef.setInput('items', sampleItems);
      fixture.detectChanges();

      const selected: CommandPaletteItem[] = [];
      fixture.componentInstance.itemSelected.subscribe((item) => selected.push(item));

      const input = fixture.nativeElement.querySelector(
        'input[role="combobox"]',
      ) as HTMLInputElement;
      const firstId = input.getAttribute('aria-activedescendant');
      expect(firstId).toContain('nav-accounts');

      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      fixture.detectChanges();
      expect(input.getAttribute('aria-activedescendant')).toContain('nav-quotes');

      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
      fixture.detectChanges();
      expect(input.getAttribute('aria-activedescendant')).toContain('nav-accounts');

      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      fixture.detectChanges();
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      fixture.detectChanges();

      expect(selected).toEqual([sampleItems[1]]);
    });

    it('emits closed on Escape', () => {
      const fixture = TestBed.createComponent(CommandPaletteComponent);
      fixture.componentRef.setInput('items', sampleItems);
      fixture.detectChanges();

      let closedCount = 0;
      fixture.componentInstance.closed.subscribe(() => {
        closedCount += 1;
      });

      const input = fixture.nativeElement.querySelector(
        'input[role="combobox"]',
      ) as HTMLInputElement;
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      expect(closedCount).toBe(1);
    });
  });

  describe('CommandPaletteService', () => {
    let service: CommandPaletteService;
    let overlayContainer: OverlayContainer;
    let routerMock: { navigate: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
      routerMock = { navigate: vi.fn().mockResolvedValue(true) };

      await TestBed.configureTestingModule({
        imports: [NoopAnimationsModule],
        providers: [{ provide: Router, useValue: routerMock }],
      }).compileComponents();

      service = TestBed.inject(CommandPaletteService);
      overlayContainer = TestBed.inject(OverlayContainer);
    });

    afterEach(() => {
      service.close();
      overlayContainer.ngOnDestroy();
    });

    it('opens overlay, tracks isOpen signal, and closes on backdrop click', () => {
      expect(service.isOpen()).toBe(false);

      const closedResults: Array<CommandPaletteItem | null> = [];
      const ref = service.open(sampleItems);
      ref.afterClosed().subscribe((res) => closedResults.push(res));

      expect(service.isOpen()).toBe(true);
      const containerEl = overlayContainer.getContainerElement();
      expect(containerEl.querySelector('[data-testid="command-palette"]')).toBeTruthy();

      const backdrop = containerEl.querySelector('.cdk-overlay-backdrop') as HTMLElement;
      expect(backdrop).toBeTruthy();
      backdrop.click();

      expect(service.isOpen()).toBe(false);
      expect(closedResults).toEqual([null]);
    });

    it('closes on Escape key and executes action or route on item selection', () => {
      const actionSpy = vi.fn();
      const items: CommandPaletteItem[] = [
        {
          id: 'act-1',
          label: 'Run Action',
          category: 'Actions',
          action: actionSpy,
        },
        {
          id: 'route-1',
          label: 'Open Quotes',
          category: 'Navigation',
          route: ['/quotes'],
        },
      ];

      // Close on Escape
      service.open(items);
      expect(service.isOpen()).toBe(true);
      document.body.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
      );
      expect(service.isOpen()).toBe(false);

      // Select action item
      const results: Array<CommandPaletteItem | null> = [];
      const ref = service.open(items);
      ref.afterClosed().subscribe((r) => results.push(r));

      const containerEl = overlayContainer.getContainerElement();
      const firstOption = containerEl.querySelector('[role="option"]') as HTMLButtonElement;
      firstOption.click();

      expect(service.isOpen()).toBe(false);
      expect(actionSpy).toHaveBeenCalledTimes(1);
      expect(results).toEqual([items[0]]);

      // Select route item
      service.open(items);
      const options = containerEl.querySelectorAll('[role="option"]');
      (options[1] as HTMLButtonElement).click();

      expect(service.isOpen()).toBe(false);
      expect(routerMock.navigate).toHaveBeenCalledWith(['/quotes']);
    });

    it('returns ⌘K on Apple platforms and Ctrl+K otherwise', async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        providers: [
          {
            provide: Platform,
            useValue: { isBrowser: true, IOS: true },
          },
        ],
      }).compileComponents();
      const iosService = TestBed.inject(CommandPaletteService);
      expect(iosService.shortcutLabel()).toBe('⌘K');

      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        providers: [
          {
            provide: Platform,
            useValue: { isBrowser: false, IOS: false },
          },
        ],
      }).compileComponents();
      const nonAppleService = TestBed.inject(CommandPaletteService);
      expect(nonAppleService.shortcutLabel()).toBe('Ctrl+K');
    });
  });
});
