import { Pipe, PipeTransform } from '@angular/core';

/**
 * Transforms a date into a relative time string (e.g., "2 hours ago", "3 days ago")
 *
 * @example
 * {{ createdAt | dateAgo }} // "2 hours ago"
 * {{ updatedAt | dateAgo }} // "3 days ago"
 */
@Pipe({
  name: 'dateAgo',
  standalone: true,
})
export class DateAgoPipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    if (!value) {
      return '-';
    }

    const date = typeof value === 'string' ? new Date(value) : value;
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    // Handle future dates
    if (seconds < 0) {
      return 'just now';
    }

    // Define time intervals in seconds
    const intervals = {
      year: 31536000,
      month: 2592000,
      week: 604800,
      day: 86400,
      hour: 3600,
      minute: 60,
      second: 1,
    };

    // Just now (less than 10 seconds)
    if (seconds < 10) {
      return 'just now';
    }

    // Less than a minute
    if (seconds < 60) {
      return 'less than a minute ago';
    }

    // Find the appropriate interval
    for (const [unit, secondsInUnit] of Object.entries(intervals)) {
      const interval = Math.floor(seconds / secondsInUnit);

      if (interval >= 1) {
        return this.formatInterval(interval, unit);
      }
    }

    return 'just now';
  }

  private formatInterval(interval: number, unit: string): string {
    if (interval === 1) {
      // Handle singular forms
      switch (unit) {
        case 'year':
          return 'a year ago';
        case 'month':
          return 'a month ago';
        case 'week':
          return 'a week ago';
        case 'day':
          return 'a day ago';
        case 'hour':
          return 'an hour ago';
        case 'minute':
          return 'a minute ago';
        default:
          return '1 second ago';
      }
    }

    // Plural forms
    return `${interval} ${unit}s ago`;
  }
}
