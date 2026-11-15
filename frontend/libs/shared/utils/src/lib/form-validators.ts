import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export class FormValidators {
  /**
   * Like Validators.required, but whitespace-only text also counts as empty. Uses the same
   * `required` error key, so existing "is required" messages keep working.
   */
  static requiredTrimmed(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (value === null || value === undefined) return { required: true };
    return String(value).trim().length > 0 ? null : { required: true };
  }

  static email(control: AbstractControl): ValidationErrors | null {
    if (!control.value) return null;
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return emailRegex.test(control.value) ? null : { email: 'Invalid email address' };
  }

  static phone(control: AbstractControl): ValidationErrors | null {
    if (!control.value) return null;
    const phoneRegex = /^[+]?[\d\s\-().]{7,20}$/;
    return phoneRegex.test(control.value) ? null : { phone: 'Invalid phone number' };
  }

  static url(control: AbstractControl): ValidationErrors | null {
    if (!control.value) return null;
    try {
      new URL(control.value);
      return null;
    } catch {
      return { url: 'Invalid URL' };
    }
  }

  static minAmount(min: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return control.value >= min ? null : { minAmount: `Amount must be at least ${min}` };
    };
  }

  static maxLength(max: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return control.value.length <= max ? null : { maxLength: `Maximum ${max} characters` };
    };
  }
}
