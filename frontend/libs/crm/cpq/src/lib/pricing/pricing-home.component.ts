import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CpqSectionComponent } from '../cpq-section.component';

@Component({
  selector: 'lib-cpq-pricing-home',
  standalone: true,
  imports: [CpqSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-cpq-section
      title="Pricing"
      description="Review price calculations, discounts, margin context, and approval signals."
    />
  `
})
export class PricingHomeComponent {}
