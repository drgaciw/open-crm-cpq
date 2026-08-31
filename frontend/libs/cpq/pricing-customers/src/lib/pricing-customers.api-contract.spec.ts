import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Guard: portal + PricingCustomerService must use /api/v1/pricing-customers only —
 * never /api/v1/customers or CRM contact/org paths.
 */
describe('pricing-customers API contract', () => {
  const serviceSource = readFileSync(
    join(
      __dirname,
      '../../../pricing-data-access/src/lib/pricing-customer.service.ts',
    ),
    'utf8',
  );

  const libRoot = join(__dirname, '..');

  it('PricingCustomerService basePath is /api/v1/pricing-customers', () => {
    expect(serviceSource).toContain("basePath = '/api/v1/pricing-customers'");
    expect(serviceSource).not.toContain("basePath = '/api/v1/customers'");
  });

  it('feature lib sources never reference /api/v1/customers or CRM contact APIs', () => {
    const files = [
      'lib/pricing-customer-list/pricing-customer-list.component.ts',
      'lib/pricing-customer-detail/pricing-customer-detail.component.ts',
      'lib/pricing-customer-create-dialog/pricing-customer-create-dialog.component.ts',
      'lib/pricing-customers.routes.ts',
      'index.ts',
    ];
    for (const rel of files) {
      const src = readFileSync(join(libRoot, rel), 'utf8');
      expect(src).not.toContain('/api/v1/customers');
      expect(src).not.toContain('/api/v1/contacts');
      expect(src).not.toContain('/api/v1/organizations');
      expect(src).not.toContain('@frontend/crm-contacts');
      expect(src).not.toContain('@frontend/crm-organizations');
    }
  });

  it('feature lib wires PricingCustomerService (not CRM services)', () => {
    const listSrc = readFileSync(
      join(libRoot, 'lib/pricing-customer-list/pricing-customer-list.component.ts'),
      'utf8',
    );
    const detailSrc = readFileSync(
      join(
        libRoot,
        'lib/pricing-customer-detail/pricing-customer-detail.component.ts',
      ),
      'utf8',
    );
    expect(listSrc).toContain('PricingCustomerService');
    expect(detailSrc).toContain('PricingCustomerService');
    expect(listSrc).toContain('@frontend/cpq-pricing-data-access');
    expect(detailSrc).toContain('@frontend/cpq-pricing-data-access');
  });
});
