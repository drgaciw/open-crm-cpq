import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats deal amounts as currency with proper locale formatting
 *
 * @example
 * {{ amount | currencyFormat }} // $1,234.56
 * {{ amount | currencyFormat:'EUR' }} // €1,234.56
 * {{ amount | currencyFormat:'USD':'symbol-narrow' }} // $1,234.56
 */
@Pipe({
  name: 'currencyFormat',
  standalone: true,
})
export class CurrencyFormatPipe implements PipeTransform {
  transform(
    value: number | null | undefined,
    currencyCode = 'USD',
    display: 'code' | 'symbol' | 'symbol-narrow' = 'symbol',
    locale = 'en-US'
  ): string {
    if (value === null || value === undefined || isNaN(value)) {
      return '-';
    }

    try {
      const currencyDisplay: Intl.NumberFormatOptions['currencyDisplay'] =
        display === 'symbol-narrow' ? 'narrowSymbol' : display;
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: currencyCode,
        currencyDisplay,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    } catch (error) {
      // Fallback to basic formatting if Intl fails
      return `${value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
    }
  }
}
