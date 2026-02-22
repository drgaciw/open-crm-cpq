import { DateAgoPipe } from './date-ago.pipe';

describe('DateAgoPipe', () => {
  let pipe: DateAgoPipe;

  beforeEach(() => {
    pipe = new DateAgoPipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should return "-" for null values', () => {
    const result = pipe.transform(null);
    expect(result).toBe('-');
  });

  it('should return "-" for undefined values', () => {
    const result = pipe.transform(undefined);
    expect(result).toBe('-');
  });

  it('should return "just now" for dates less than 10 seconds ago', () => {
    const now = new Date();
    const fiveSecondsAgo = new Date(now.getTime() - 5000);
    const result = pipe.transform(fiveSecondsAgo);
    expect(result).toBe('just now');
  });

  it('should return "less than a minute ago" for dates less than 60 seconds ago', () => {
    const now = new Date();
    const thirtySecondsAgo = new Date(now.getTime() - 30000);
    const result = pipe.transform(thirtySecondsAgo);
    expect(result).toBe('less than a minute ago');
  });

  it('should return "a minute ago" for dates about 1 minute ago', () => {
    const now = new Date();
    const oneMinuteAgo = new Date(now.getTime() - 60000);
    const result = pipe.transform(oneMinuteAgo);
    expect(result).toBe('a minute ago');
  });

  it('should return correct minutes for dates 2-59 minutes ago', () => {
    const now = new Date();
    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60000);
    const result = pipe.transform(fiveMinutesAgo);
    expect(result).toBe('5 minutes ago');
  });

  it('should return "an hour ago" for dates about 1 hour ago', () => {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 3600000);
    const result = pipe.transform(oneHourAgo);
    expect(result).toBe('an hour ago');
  });

  it('should return correct hours for dates 2-23 hours ago', () => {
    const now = new Date();
    const threeHoursAgo = new Date(now.getTime() - 3 * 3600000);
    const result = pipe.transform(threeHoursAgo);
    expect(result).toBe('3 hours ago');
  });

  it('should return "a day ago" for dates about 1 day ago', () => {
    const now = new Date();
    const oneDayAgo = new Date(now.getTime() - 86400000);
    const result = pipe.transform(oneDayAgo);
    expect(result).toBe('a day ago');
  });

  it('should return correct days for dates 2-6 days ago', () => {
    const now = new Date();
    const threeDaysAgo = new Date(now.getTime() - 3 * 86400000);
    const result = pipe.transform(threeDaysAgo);
    expect(result).toBe('3 days ago');
  });

  it('should return "a week ago" for dates about 1 week ago', () => {
    const now = new Date();
    const oneWeekAgo = new Date(now.getTime() - 7 * 86400000);
    const result = pipe.transform(oneWeekAgo);
    expect(result).toBe('a week ago');
  });

  it('should return correct weeks for dates 2-3 weeks ago', () => {
    const now = new Date();
    const twoWeeksAgo = new Date(now.getTime() - 14 * 86400000);
    const result = pipe.transform(twoWeeksAgo);
    expect(result).toBe('2 weeks ago');
  });

  it('should return "a month ago" for dates about 1 month ago', () => {
    const now = new Date();
    const oneMonthAgo = new Date(now.getTime() - 30 * 86400000);
    const result = pipe.transform(oneMonthAgo);
    expect(result).toBe('a month ago');
  });

  it('should return correct months for dates 2-11 months ago', () => {
    const now = new Date();
    const threeMonthsAgo = new Date(now.getTime() - 90 * 86400000);
    const result = pipe.transform(threeMonthsAgo);
    expect(result).toBe('3 months ago');
  });

  it('should return "a year ago" for dates about 1 year ago', () => {
    const now = new Date();
    const oneYearAgo = new Date(now.getTime() - 365 * 86400000);
    const result = pipe.transform(oneYearAgo);
    expect(result).toBe('a year ago');
  });

  it('should return correct years for dates multiple years ago', () => {
    const now = new Date();
    const twoYearsAgo = new Date(now.getTime() - 730 * 86400000);
    const result = pipe.transform(twoYearsAgo);
    expect(result).toBe('2 years ago');
  });

  it('should handle ISO string dates', () => {
    const now = new Date();
    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60000);
    const result = pipe.transform(fiveMinutesAgo.toISOString());
    expect(result).toBe('5 minutes ago');
  });

  it('should handle Date objects', () => {
    const now = new Date();
    const twoHoursAgo = new Date(now.getTime() - 2 * 3600000);
    const result = pipe.transform(twoHoursAgo);
    expect(result).toBe('2 hours ago');
  });

  it('should return "just now" for future dates', () => {
    const now = new Date();
    const future = new Date(now.getTime() + 60000);
    const result = pipe.transform(future);
    expect(result).toBe('just now');
  });

  it('should handle edge case of exactly 60 seconds', () => {
    const now = new Date();
    const exactlyOneMinute = new Date(now.getTime() - 60000);
    const result = pipe.transform(exactlyOneMinute);
    expect(result).toBe('a minute ago');
  });
});
