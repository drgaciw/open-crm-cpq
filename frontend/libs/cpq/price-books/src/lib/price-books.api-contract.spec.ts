import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Guard: portal + PriceBookService must use /api/v1/price-books only —
 * never the wrong gateway prefix /api/v1/pricing/price-books.
 */
describe('price-books API contract', () => {
  const serviceSource = readFileSync(
    join(
      __dirname,
      '../../../pricing-data-access/src/lib/price-book.service.ts',
    ),
    'utf8',
  );

  const libRoot = join(__dirname, '..');

  it('PriceBookService basePath is /api/v1/price-books', () => {
    expect(serviceSource).toContain("basePath = '/api/v1/price-books'");
    expect(serviceSource).not.toContain('/api/v1/pricing/price-books');
  });

  it('feature lib sources never reference /api/v1/pricing/price-books', () => {
    const files = [
      'lib/price-book-list/price-book-list.component.ts',
      'lib/price-book-detail/price-book-detail.component.ts',
      'lib/price-book-create-dialog/price-book-create-dialog.component.ts',
      'lib/price-books.routes.ts',
      'index.ts',
    ];
    for (const rel of files) {
      const src = readFileSync(join(libRoot, rel), 'utf8');
      expect(src).not.toContain('/api/v1/pricing/price-books');
    }
  });
});
