import { parseLocalDate, toLocalDateString } from './local-date';

/**
 * H-CRM-021 / H-CRM-041: a date-only API value (YYYY-MM-DD) is a calendar day, not an instant.
 * Tests must not depend on the machine's time zone (CI runs in UTC, where the bug is invisible),
 * so they pin local midnight and use a Date whose local and UTC calendar days differ.
 */
/** What a datepicker returns at local midnight on 15 March 2027 in Pacific/Auckland (UTC+13). */
const aucklandMidnight = {
  getFullYear: () => 2027,
  getMonth: () => 2,
  getDate: () => 15,
  toISOString: () => '2027-03-14T11:00:00.000Z',
} as unknown as Date;

describe('local calendar dates', () => {
  it('parses YYYY-MM-DD as local midnight of that day, not UTC midnight', () => {
    expect(parseLocalDate('2027-06-30')?.getTime()).toBe(new Date(2027, 5, 30).getTime());
  });

  it('formats a date by its local calendar day, not its UTC day', () => {
    expect(toLocalDateString(aucklandMidnight)).toBe('2027-03-15');
    expect(toLocalDateString(new Date(2027, 0, 5))).toBe('2027-01-05');
  });

  it('round-trips a calendar day', () => {
    expect(toLocalDateString(parseLocalDate('2027-12-31'))).toBe('2027-12-31');
  });

  it('returns null / undefined for missing or malformed input', () => {
    expect(parseLocalDate(null)).toBeNull();
    expect(parseLocalDate('')).toBeNull();
    expect(parseLocalDate('2027-13-40')).toBeNull();
    expect(toLocalDateString(null)).toBeUndefined();
  });
});
