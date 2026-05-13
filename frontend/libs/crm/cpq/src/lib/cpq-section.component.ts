import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'lib-cpq-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cpq-section">
      <p class="eyebrow">CPQ</p>
      <h1>{{ title() }}</h1>
      <p>{{ description() }}</p>
    </section>
  `,
  styles: [`
    .cpq-section {
      display: grid;
      gap: 8px;
      max-width: 760px;
    }
    .eyebrow {
      margin: 0;
      color: #1976d2;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: 1.75rem;
      font-weight: 600;
    }
    p {
      margin: 0;
      color: #555;
      line-height: 1.5;
    }
  `]
})
export class CpqSectionComponent {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
