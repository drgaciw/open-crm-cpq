import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Guard: portal + ProductBundleService must use /api/v1/product-bundles only —
 * never /api/v1/cpq/product-bundles, /api/v1/configuration/**, or /api/v1/subscriptions.
 */
describe('product-bundles API contract', () => {
  const serviceSource = readFileSync(
    join(__dirname, 'product-bundle.service.ts'),
    'utf8',
  );

  const libRoot = join(__dirname, '..');

  it('ProductBundleService basePath is /api/v1/product-bundles', () => {
    expect(serviceSource).toContain("basePath = '/api/v1/product-bundles'");
    expect(serviceSource).not.toContain('/api/v1/cpq/product-bundles');
    expect(serviceSource).not.toContain('/api/v1/configuration/');
    expect(serviceSource).not.toContain('/api/v1/subscriptions');
  });

  it('service exposes list/get/create/delete and item add/remove paths', () => {
    expect(serviceSource).toContain('list(page');
    expect(serviceSource).toContain('getById(id: number)');
    expect(serviceSource).toContain('create(request: ProductBundleCreateRequest)');
    expect(serviceSource).toContain('delete(id: number)');
    expect(serviceSource).toContain('addItem(');
    expect(serviceSource).toContain('removeItem(');
    expect(serviceSource).toContain('`${this.basePath}/${bundleId}/items`');
    expect(serviceSource).toContain(
      '`${this.basePath}/${bundleId}/items/${itemId}`',
    );
  });

  it('feature lib sources never reference forbidden APIs', () => {
    const files = [
      'lib/product-bundle-list/product-bundle-list.component.ts',
      'lib/product-bundle-detail/product-bundle-detail.component.ts',
      'lib/product-bundle-create-dialog/product-bundle-create-dialog.component.ts',
      'lib/bundle-item-dialog/bundle-item-dialog.component.ts',
      'lib/product-bundle.service.ts',
      'lib/product-bundles.routes.ts',
      'index.ts',
    ];
    for (const rel of files) {
      const src = readFileSync(join(libRoot, rel), 'utf8');
      expect(src).not.toContain('/api/v1/cpq/product-bundles');
      expect(src).not.toContain('/api/v1/configuration/');
      expect(src).not.toContain('/api/v1/subscriptions');
      expect(src).not.toContain('@ngrx');
    }
  });
});
