import { FormControl } from '@angular/forms';
import { FormValidators } from './form-validators';

describe('FormValidators', () => {
  describe('url validation', () => {
    it('should return null if control value is empty', () => {
      const control = new FormControl('');
      expect(FormValidators.url(control)).toBeNull();
    });

    it('should return null if control value is null', () => {
      const control = new FormControl(null);
      expect(FormValidators.url(control)).toBeNull();
    });

    it('should return null if control value is a valid URL', () => {
      const control = new FormControl('https://example.com');
      expect(FormValidators.url(control)).toBeNull();
    });

    it('should return validation error if control value is an invalid URL', () => {
      const control = new FormControl('not-a-valid-url');
      expect(FormValidators.url(control)).toEqual({ url: 'Invalid URL' });
    });
  });
});
