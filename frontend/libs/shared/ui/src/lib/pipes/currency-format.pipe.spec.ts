import { CurrencyFormatPipe } from './currency-format.pipe';

describe('CurrencyFormatPipe', () => {
  let pipe: CurrencyFormatPipe;

  beforeEach(() => {
    pipe = new CurrencyFormatPipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should format USD currency with default parameters', () => {
    const result = pipe.transform(1234.56);
    expect(result).toContain('1,234.56');
    expect(result).toContain('$');
  });

  it('should format large amounts with proper thousand separators', () => {
    const result = pipe.transform(1234567.89);
    expect(result).toContain('1,234,567.89');
  });

  it('should format EUR currency when specified', () => {
    const result = pipe.transform(1234.56, 'EUR');
    expect(result).toContain('1,234.56');
    expect(result).toContain('€');
  });

  it('should return "-" for null values', () => {
    const result = pipe.transform(null);
    expect(result).toBe('-');
  });

  it('should return "-" for undefined values', () => {
    const result = pipe.transform(undefined);
    expect(result).toBe('-');
  });

  it('should return "-" for NaN values', () => {
    const result = pipe.transform(NaN);
    expect(result).toBe('-');
  });

  it('should format zero correctly', () => {
    const result = pipe.transform(0);
    expect(result).toContain('0.00');
  });

  it('should format negative amounts', () => {
    const result = pipe.transform(-1234.56);
    expect(result).toContain('1,234.56');
    expect(result).toMatch(/[-()]/); // Should contain either minus sign or parentheses
  });

  it('should round to two decimal places', () => {
    const result = pipe.transform(1234.567);
    expect(result).toContain('1,234.57');
  });

  it('should handle very small decimal values', () => {
    const result = pipe.transform(0.01);
    expect(result).toContain('0.01');
  });

  it('should format different currency codes correctly', () => {
    const gbpResult = pipe.transform(1234.56, 'GBP');
    expect(gbpResult).toContain('1,234.56');

    const jpyResult = pipe.transform(1234, 'JPY');
    expect(jpyResult).toBeTruthy();
  });

  it('should use symbol display by default', () => {
    const result = pipe.transform(1000, 'USD', 'symbol');
    expect(result).toContain('$');
    expect(result).not.toContain('USD');
  });

  it('should handle different locales', () => {
    const usResult = pipe.transform(1234.56, 'USD', 'symbol', 'en-US');
    expect(usResult).toBeTruthy();

    const deResult = pipe.transform(1234.56, 'EUR', 'symbol', 'de-DE');
    expect(deResult).toBeTruthy();
  });

  it('should fallback to basic formatting on error', () => {
    // Test with an invalid currency code that might cause Intl to fail
    const result = pipe.transform(1234.56, 'INVALID' as any);
    expect(result).toBe('1,234.56');
  });

  it('should fallback to basic formatting with large numbers on error', () => {
    // Test with an invalid currency code that might cause Intl to fail
    const result = pipe.transform(1234567.89, 'INVALID' as any);
    expect(result).toBe('1,234,567.89');
  });
});
