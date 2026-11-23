import {
  Component,
  ChangeDetectionStrategy,
  ElementRef,
  effect,
  input,
  output,
  signal,
  viewChildren,
} from '@angular/core';
import { CdkStepper } from '@angular/cdk/stepper';
import { MatIconModule } from '@angular/material/icon';

export type StagePathState = 'completed' | 'active' | 'upcoming' | 'danger';

export interface StagePathItem {
  id: string;
  label: string;
  state: StagePathState;
}

/**
 * SLDS2-inspired horizontal stage path extending `CdkStepper`.
 * Displays chevron/pill stage progress with keyboard Left/Right navigation
 * and emits `stageSelect` when a stage is activated.
 */
@Component({
  selector: 'app-stage-path',
  standalone: true,
  imports: [MatIconModule],
  providers: [{ provide: CdkStepper, useExisting: StagePathComponent }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav
      class="stage-path"
      data-testid="stage-path"
      [attr.aria-label]="ariaLabel()"
    >
      <ol class="stage-path__list" role="list">
        @for (stage of stages(); track stage.id; let i = $index) {
          <li class="stage-path__item">
            @if (interactive()) {
              <button
                #stageButton
                type="button"
                class="stage-path__step"
                [class.stage-path__step--completed]="stage.state === 'completed'"
                [class.stage-path__step--active]="stage.state === 'active'"
                [class.stage-path__step--upcoming]="stage.state === 'upcoming'"
                [class.stage-path__step--danger]="stage.state === 'danger'"
                [attr.aria-current]="stage.state === 'active' ? 'step' : null"
                [attr.tabindex]="focusedIndex() === i ? 0 : -1"
                [attr.data-stage-id]="stage.id"
                (click)="selectStage(stage, i)"
                (focus)="focusedIndex.set(i)"
                (keydown)="onStepKeydown($event, i)"
              >
                @if (stage.state === 'completed') {
                  <mat-icon class="stage-path__icon" aria-hidden="true">check</mat-icon>
                } @else if (stage.state === 'danger') {
                  <mat-icon class="stage-path__icon" aria-hidden="true">close</mat-icon>
                }
                <span class="stage-path__label">{{ stage.label }}</span>
              </button>
            } @else {
              <span
                class="stage-path__step stage-path__step--static"
                [class.stage-path__step--completed]="stage.state === 'completed'"
                [class.stage-path__step--active]="stage.state === 'active'"
                [class.stage-path__step--upcoming]="stage.state === 'upcoming'"
                [class.stage-path__step--danger]="stage.state === 'danger'"
                [attr.aria-current]="stage.state === 'active' ? 'step' : null"
                [attr.data-stage-id]="stage.id"
              >
                @if (stage.state === 'completed') {
                  <mat-icon class="stage-path__icon" aria-hidden="true">check</mat-icon>
                } @else if (stage.state === 'danger') {
                  <mat-icon class="stage-path__icon" aria-hidden="true">close</mat-icon>
                }
                <span class="stage-path__label">{{ stage.label }}</span>
              </span>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styles: [`
    :host {
      display: block;
    }

    .stage-path {
      width: 100%;
      overflow-x: auto;
    }

    .stage-path__list {
      display: flex;
      align-items: stretch;
      gap: var(--crm-space-xs, 4px);
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .stage-path__item {
      flex: 1 1 0;
      min-width: 0;
      display: flex;
    }

    .stage-path__step {
      position: relative;
      flex: 1 1 auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      min-height: 36px;
      padding: 6px 14px;
      border: 1px solid var(--crm-color-line);
      border-radius: 999px;
      background: var(--crm-color-track);
      color: var(--crm-color-muted);
      font: inherit;
      font-size: 0.8125rem;
      font-weight: 500;
      white-space: nowrap;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
    }

    .stage-path__step:hover {
      border-color: var(--crm-color-brand);
      color: var(--crm-color-ink);
    }

    .stage-path__step--static {
      cursor: default;
    }

    .stage-path__step--static:hover {
      border-color: var(--crm-color-line);
      color: var(--crm-color-muted);
    }

    .stage-path__step--static.stage-path__step--completed:hover {
      border-color: var(--crm-color-success);
      color: var(--crm-color-success-strong);
    }

    .stage-path__step--static.stage-path__step--active:hover {
      border-color: var(--crm-color-brand-strong);
      color: var(--crm-color-on-brand);
    }

    .stage-path__step--static.stage-path__step--danger:hover {
      border-color: var(--crm-color-danger);
      color: var(--crm-color-danger);
    }

    .stage-path__step:focus-visible {
      outline: 3px solid var(--crm-focus-ring);
      outline-offset: 2px;
    }

    .stage-path__step--completed {
      background: var(--crm-color-success-container);
      border-color: var(--crm-color-success);
      color: var(--crm-color-success-strong);
    }

    .stage-path__step--active {
      background: var(--crm-color-brand);
      border-color: var(--crm-color-brand-strong);
      color: var(--crm-color-on-brand);
      font-weight: 600;
    }

    .stage-path__step--active:hover {
      color: var(--crm-color-on-brand);
    }

    .stage-path__step--danger {
      background: var(--crm-color-danger-container);
      border-color: var(--crm-color-danger);
      color: var(--crm-color-danger);
      font-weight: 600;
    }

    .stage-path__icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      line-height: 16px;
      flex-shrink: 0;
    }

    .stage-path__label {
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `],
})
export class StagePathComponent extends CdkStepper {
  stages = input.required<StagePathItem[]>();
  ariaLabel = input<string>('Stage progress');
  interactive = input<boolean>(true);

  stageSelect = output<string>();

  protected readonly focusedIndex = signal(0);
  private readonly stageButtons = viewChildren<ElementRef<HTMLButtonElement>>('stageButton');

  constructor() {
    super();

    effect(() => {
      const items = this.stages();
      const activeIdx = items.findIndex((stage) => stage.state === 'active');
      const targetIndex = activeIdx >= 0 ? activeIdx : 0;
      this.focusedIndex.set(targetIndex);
    });
  }

  selectStage(stage: StagePathItem, index: number): void {
    this.focusedIndex.set(index);
    this.stageSelect.emit(stage.id);
  }

  onStepKeydown(event: KeyboardEvent, currentIndex: number): void {
    const items = this.stages();
    if (items.length === 0) {
      return;
    }

    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % items.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + items.length) % items.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = items.length - 1;
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.selectStage(items[currentIndex], currentIndex);
      return;
    }

    if (nextIndex !== null) {
      event.preventDefault();
      this.focusedIndex.set(nextIndex);
      this.stageButtons()[nextIndex]?.nativeElement.focus();
    }
  }
}
