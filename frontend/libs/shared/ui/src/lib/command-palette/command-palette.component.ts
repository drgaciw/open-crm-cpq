import {
  Component,
  ChangeDetectionStrategy,
  DestroyRef,
  Directive,
  ElementRef,
  InjectionToken,
  Injector,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import {
  ActiveDescendantKeyManager,
  CdkTrapFocus,
  Highlightable,
} from '@angular/cdk/a11y';
import { MatIconModule } from '@angular/material/icon';

export interface CommandPaletteItem {
  id: string;
  label: string;
  category: string;
  icon?: string;
  keywords?: string[];
  route?: string[];
  action?: () => void;
}

export interface CommandPaletteData {
  items: CommandPaletteItem[];
  onSelect: (item: CommandPaletteItem) => void;
  onClose: () => void;
}

export const COMMAND_PALETTE_DATA = new InjectionToken<CommandPaletteData>(
  'COMMAND_PALETTE_DATA',
);

/**
 * Highlightable option directive for `ActiveDescendantKeyManager` inside `CommandPaletteComponent`.
 */
@Directive({
  selector: '[appCommandPaletteOption]',
  standalone: true,
  host: {
    '[class.palette-option--active]': 'isActive()',
    '[attr.aria-selected]': 'isActive()',
  },
})
export class CommandPaletteOptionDirective implements Highlightable {
  readonly appCommandPaletteOption = input.required<CommandPaletteItem>();
  readonly optionId = input.required<string>();
  disabled = false;

  readonly isActive = signal(false);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  setActiveStyles(): void {
    this.isActive.set(true);
    this.elementRef.nativeElement.scrollIntoView?.({ block: 'nearest' });
  }

  setInactiveStyles(): void {
    this.isActive.set(false);
  }

  getLabel(): string {
    return this.appCommandPaletteOption().label;
  }
}

/**
 * Command palette overlay dialog with keyboard navigation via `ActiveDescendantKeyManager`
 * and focus containment via `CdkTrapFocus`.
 */
@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [CdkTrapFocus, MatIconModule, CommandPaletteOptionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="command-palette"
      data-testid="command-palette"
      cdkTrapFocus
      [cdkTrapFocusAutoCapture]="true"
      role="dialog"
      aria-modal="true"
      aria-label="Command palette"
    >
      <div class="command-palette__search">
        <mat-icon class="command-palette__search-icon" aria-hidden="true">search</mat-icon>
        <input
          #searchInput
          type="text"
          class="command-palette__input"
          role="combobox"
          aria-expanded="true"
          aria-autocomplete="list"
          [attr.aria-controls]="listboxId"
          [attr.aria-activedescendant]="activeOptionId()"
          [value]="query()"
          placeholder="Type a command or search…"
          cdkFocusInitial
          (input)="onQueryInput($event)"
          (keydown)="onKeydown($event)"
        />
        <kbd class="command-palette__esc-hint">Esc</kbd>
      </div>

      <div
        class="command-palette__results"
        [id]="listboxId"
        role="listbox"
        aria-label="Commands"
      >
        @if (filteredItems().length === 0) {
          <div class="command-palette__empty" role="status">
            No matching commands
          </div>
        } @else {
          @for (item of filteredItems(); track item.id; let i = $index) {
            <button
              type="button"
              role="option"
              class="palette-option"
              [id]="optionDomId(item.id)"
              [attr.aria-selected]="activeOptionId() === optionDomId(item.id)"
              [appCommandPaletteOption]="item"
              [optionId]="optionDomId(item.id)"
              (mouseenter)="setActiveIndex(i)"
              (click)="selectItem(item)"
            >
              @if (item.icon) {
                <mat-icon class="palette-option__icon" aria-hidden="true">{{ item.icon }}</mat-icon>
              }
              <span class="palette-option__label">{{ item.label }}</span>
              <span class="palette-option__category">{{ item.category }}</span>
            </button>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .command-palette {
      width: min(560px, calc(100vw - 32px));
      background: var(--crm-color-surface);
      color: var(--crm-color-ink);
      border: 1px solid var(--crm-color-line);
      border-radius: var(--crm-radius-md);
      box-shadow: 0 16px 40px color-mix(in srgb, var(--crm-color-brand-strong) 22%, transparent);
      overflow: hidden;
    }

    .command-palette__search {
      display: flex;
      align-items: center;
      gap: var(--crm-space-sm);
      padding: 12px var(--crm-space-md);
      border-bottom: 1px solid var(--crm-color-line);
      background: var(--crm-color-surface);
    }

    .command-palette__search-icon {
      color: var(--crm-color-muted);
      flex-shrink: 0;
    }

    .command-palette__input {
      flex: 1;
      min-width: 0;
      border: none;
      outline: none;
      background: transparent;
      color: var(--crm-color-ink);
      font-size: 0.95rem;
      line-height: 1.4;
    }

    .command-palette__input::placeholder {
      color: var(--crm-color-muted);
    }

    .command-palette__esc-hint {
      padding: 2px 6px;
      border-radius: var(--crm-radius-sm);
      border: 1px solid var(--crm-color-line);
      background: var(--crm-color-canvas);
      color: var(--crm-color-muted);
      font-size: 0.75rem;
      font-family: inherit;
    }

    .command-palette__results {
      max-height: 360px;
      overflow-y: auto;
      padding: var(--crm-space-xs);
    }

    .command-palette__empty {
      padding: var(--crm-space-lg) var(--crm-space-md);
      text-align: center;
      color: var(--crm-color-muted);
      font-size: 0.875rem;
    }

    .palette-option {
      width: 100%;
      display: flex;
      align-items: center;
      gap: var(--crm-space-sm);
      padding: 10px 12px;
      border: none;
      border-radius: var(--crm-radius-sm);
      background: transparent;
      color: var(--crm-color-ink);
      text-align: left;
      cursor: pointer;
      font: inherit;
      font-size: 0.875rem;
    }

    .palette-option:hover,
    .palette-option--active {
      background: var(--crm-color-info-container);
      color: var(--crm-color-brand-strong);
    }

    .palette-option:focus-visible {
      outline: 3px solid var(--crm-focus-ring);
      outline-offset: -2px;
    }

    .palette-option__icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      color: var(--crm-color-brand);
      flex-shrink: 0;
    }

    .palette-option__label {
      flex: 1;
      min-width: 0;
      font-weight: 500;
    }

    .palette-option__category {
      font-size: 0.75rem;
      color: var(--crm-color-muted);
      padding: 2px 8px;
      border-radius: var(--crm-radius-sm);
      background: var(--crm-color-track);
      flex-shrink: 0;
    }
  `],
})
export class CommandPaletteComponent {
  private readonly injectedData = inject(COMMAND_PALETTE_DATA, { optional: true });
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  readonly listboxId = `command-palette-listbox-${Math.random().toString(36).slice(2, 9)}`;

  /** Optional direct inputs so the component can also be tested or mounted standalone. */
  items = input<CommandPaletteItem[]>([]);
  itemSelected = output<CommandPaletteItem>();
  closed = output<void>();

  readonly query = signal('');
  private readonly activeOptionIdState = signal<string | null>(null);
  readonly activeOptionId = this.activeOptionIdState.asReadonly();

  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  private readonly options = viewChildren(CommandPaletteOptionDirective);

  private readonly keyManager: ActiveDescendantKeyManager<CommandPaletteOptionDirective>;

  readonly resolvedItems = computed<CommandPaletteItem[]>(() => {
    const direct = this.items();
    if (direct.length > 0) {
      return direct;
    }
    return this.injectedData?.items ?? [];
  });

  readonly filteredItems = computed<CommandPaletteItem[]>(() => {
    const needle = this.query().trim().toLowerCase();
    const all = this.resolvedItems();
    if (!needle) {
      return all;
    }
    return all.filter((item) => {
      if (item.label.toLowerCase().includes(needle)) {
        return true;
      }
      if (item.category.toLowerCase().includes(needle)) {
        return true;
      }
      return item.keywords?.some((kw) => kw.toLowerCase().includes(needle)) ?? false;
    });
  });

  constructor() {
    this.keyManager = new ActiveDescendantKeyManager(this.options, this.injector).withWrap();

    const changeSub = this.keyManager.change.subscribe(() => {
      this.syncActiveDescendant();
    });

    this.destroyRef.onDestroy(() => {
      changeSub.unsubscribe();
      this.keyManager.destroy();
    });

    effect(() => {
      const currentOptions = this.options();
      if (currentOptions.length > 0) {
        const currentActive = this.keyManager.activeItem;
        const activeStillPresent =
          currentActive !== null && currentOptions.includes(currentActive);
        if (!activeStillPresent) {
          this.keyManager.setActiveItem(currentOptions[0]);
        }
      } else {
        this.keyManager.updateActiveItem(-1);
      }
      this.syncActiveDescendant();
    });
  }

  focusInput(): void {
    this.searchInput()?.nativeElement.focus();
  }

  optionDomId(itemId: string): string {
    return `${this.listboxId}-opt-${itemId}`;
  }

  onQueryInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.query.set(target?.value ?? '');
    this.keyManager.updateActiveItem(-1);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }

    if (event.key === 'Enter') {
      event.preventDefault();
      const active = this.keyManager.activeItem;
      if (active) {
        this.selectItem(active.appCommandPaletteOption());
      }
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.keyManager.setNextItemActive();
      this.syncActiveDescendant();
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.keyManager.setPreviousItemActive();
      this.syncActiveDescendant();
      return;
    }

    this.keyManager.onKeydown(event);
    this.syncActiveDescendant();
  }

  setActiveIndex(index: number): void {
    this.keyManager.setActiveItem(index);
    this.syncActiveDescendant();
  }

  selectItem(item: CommandPaletteItem): void {
    this.itemSelected.emit(item);
    this.injectedData?.onSelect(item);
  }

  close(): void {
    this.closed.emit();
    this.injectedData?.onClose();
  }

  private syncActiveDescendant(): void {
    this.activeOptionIdState.set(this.keyManager.activeItem?.optionId() ?? null);
  }
}
