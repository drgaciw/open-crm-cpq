import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CpqSectionComponent } from '../cpq-section.component';

@Component({
  selector: 'lib-cpq-catalog-home',
  standalone: true,
  imports: [CpqSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-cpq-section
      title="Catalog"
      description="Browse product, bundle, and attribute data before configuration begins."
    />
  `
})
export class CatalogHomeComponent {}
