/**
 * Date-only API values (`YYYY-MM-DD`) are calendar days, not instants. `new Date('2027-06-30')`
 * is UTC midnight, which is the 29th west of UTC (H-CRM-041); `toISOString()` of a local-midnight
 * Date is the previous day east of UTC (H-CRM-021). These helpers keep the calendar day.
 */
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Local midnight of a `YYYY-MM-DD` day, or null when missing or not a real day. */
export function parseLocalDate(value: string | null | undefined): Date | null {
  const match = DATE_ONLY.exec(value ?? '');
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return date;
}

/** `YYYY-MM-DD` of a Date's local calendar day, or undefined when there is no date. */
export function toLocalDateString(date: Date | null | undefined): string | undefined {
  if (!date) return undefined;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
