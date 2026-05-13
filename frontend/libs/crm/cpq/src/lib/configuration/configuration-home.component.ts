import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CpqSectionComponent } from '../cpq-section.component';

@Component({
  selector: 'lib-cpq-configuration-home',
  standalone: true,
  imports: [CpqSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-cpq-section
      title="Configuration"
      description="Guide option selection, compatibility checks, and constraint-driven configuration."
    />
  `
})
export class ConfigurationHomeComponent {}
