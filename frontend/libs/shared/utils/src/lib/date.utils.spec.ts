import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { timeAgo, formatDate, formatDateTime } from './date.utils';

describe('date.utils', () => {
  describe('timeAgo', () => {
    beforeEach(() => {
      // Set system time to a fixed date to ensure deterministic tests
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2024-03-15T12:00:00.000Z'));
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should return "just now" for dates less than 60 seconds ago', () => {
      const date = new Date('2024-03-15T11:59:15.000Z'); // 45 seconds ago
      expect(timeAgo(date.toISOString())).toBe('just now');
    });

    it('should return "1 minute ago" for exactly 60 seconds ago', () => {
      const date = new Date('2024-03-15T11:59:00.000Z'); // 60 seconds ago
      expect(timeAgo(date.toISOString())).toBe('1 minute ago');
    });

    it('should return "X minutes ago" for multiple minutes', () => {
      const date = new Date('2024-03-15T11:55:00.000Z'); // 5 minutes ago
      expect(timeAgo(date.toISOString())).toBe('5 minutes ago');
    });

    it('should return "1 hour ago" for exactly 1 hour ago', () => {
      const date = new Date('2024-03-15T11:00:00.000Z'); // 1 hour ago
      expect(timeAgo(date.toISOString())).toBe('1 hour ago');
    });

    it('should return "X hours ago" for multiple hours', () => {
      const date = new Date('2024-03-15T09:00:00.000Z'); // 3 hours ago
      expect(timeAgo(date.toISOString())).toBe('3 hours ago');
    });

    it('should return "1 day ago" for exactly 24 hours ago', () => {
      const date = new Date('2024-03-14T12:00:00.000Z'); // 1 day ago
      expect(timeAgo(date.toISOString())).toBe('1 day ago');
    });

    it('should return "X days ago" for multiple days', () => {
      const date = new Date('2024-03-10T12:00:00.000Z'); // 5 days ago
      expect(timeAgo(date.toISOString())).toBe('5 days ago');
    });

    it('should return "1 month ago" for exactly 30 days ago', () => {
      const date = new Date('2024-02-14T12:00:00.000Z'); // 30 days ago
      expect(timeAgo(date.toISOString())).toBe('1 month ago');
    });

    it('should return "X months ago" for multiple months', () => {
      const date = new Date('2023-12-15T12:00:00.000Z'); // 3 months ago
      expect(timeAgo(date.toISOString())).toBe('3 months ago');
    });

    it('should return "1 year ago" for exactly 365 days ago', () => {
      const date = new Date('2023-03-16T12:00:00.000Z'); // ~1 year ago
      expect(timeAgo(date.toISOString())).toBe('1 year ago');
    });

    it('should return "X years ago" for multiple years', () => {
      const date = new Date('2020-03-15T12:00:00.000Z'); // 4 years ago
      expect(timeAgo(date.toISOString())).toBe('4 years ago');
    });

    it('should return "just now" for future dates', () => {
      const date = new Date('2024-03-16T12:00:00.000Z'); // 1 day in the future
      expect(timeAgo(date.toISOString())).toBe('just now');
    });

    it('should return "Invalid Date" for invalid dates', () => {
      expect(timeAgo('invalid-date')).toBe('Invalid Date');
    });

    it('should handle leap February month length (29 days)', () => {
      // 2024 is a leap year. From Mar 15, Feb 15 is exactly 29 days ago.
      // 29 days = 2505600 seconds, which is less than 2592000 (30 days),
      // so the logic uses the days block.
      const date = new Date('2024-02-15T12:00:00.000Z'); // 29 days ago
      expect(timeAgo(date.toISOString())).toBe('29 days ago');
    });

    it('should handle long month length (31 days)', () => {
      // From Mar 15, Jan 14 is 61 days ago (Jan has 31 days).
      // 61 days = 5270400 seconds, which is >= 2592000 (30 days),
      // so the logic uses the months block: Math.floor(5270400 / 2592000) = 2.
      const date = new Date('2024-01-14T12:00:00.000Z'); // 61 days ago
      expect(timeAgo(date.toISOString())).toBe('2 months ago');
    });

    it('should return "1 year ago" for a leap year duration (366 days)', () => {
      // 2024 is a leap year, so from Mar 15 2024 to Mar 15 2023 is 366 days.
      // 366 days = 31622400 seconds, which is >= 31536000 (365 days),
      // so it falls to the years block.
      const date = new Date('2023-03-15T12:00:00.000Z'); // 366 days ago
      expect(timeAgo(date.toISOString())).toBe('1 year ago');
    });
  });

  describe('formatDate', () => {
    it('should format date string to localized short date', () => {
      const dateStr = '2024-03-15T14:30:00.000Z';
      // The output depends on the local timezone of the environment
      // so we use the same Date conversion to check
      const expected = new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
      expect(formatDate(dateStr)).toBe(expected);
    });

    it('should handle invalid date string by returning "Invalid Date"', () => {
      const dateStr = 'invalid-date';
      expect(formatDate(dateStr)).toBe('Invalid Date');
    });
  });

  describe('formatDateTime', () => {
    it('should format date string to localized short date and time', () => {
      const dateStr = '2024-03-15T14:30:00.000Z';
      const expected = new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
      expect(formatDateTime(dateStr)).toBe(expected);
    });

    it('should handle invalid date string by returning "Invalid Date"', () => {
      const dateStr = 'invalid-date';
      expect(formatDateTime(dateStr)).toBe('Invalid Date');
    });
  });
});
