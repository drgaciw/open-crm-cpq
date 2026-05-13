import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CpqSectionComponent } from '../cpq-section.component';

@Component({
  selector: 'lib-cpq-quote-home',
  standalone: true,
  imports: [CpqSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-cpq-section
      title="Quotes"
      description="Create, review, approve, and hand off customer-ready quote documents."
    />
  `
})
export class QuoteHomeComponent {}
