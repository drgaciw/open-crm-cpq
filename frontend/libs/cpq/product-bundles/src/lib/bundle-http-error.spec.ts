import { bundleHttpErrorMessage } from './bundle-http-error';

describe('bundleHttpErrorMessage', () => {
  it('appends BUNDLE_MAX_ITEMS_EXCEEDED from ApiError-shaped object', () => {
    expect(
      bundleHttpErrorMessage(
        {
          message: 'Bundle has reached maximum items (5)',
          errorCode: 'BUNDLE_MAX_ITEMS_EXCEEDED',
        },
        'fallback',
      ),
    ).toBe(
      'Bundle has reached maximum items (5) [BUNDLE_MAX_ITEMS_EXCEEDED]',
    );
  });

  it('appends BUNDLE_MIN_ITEMS_REQUIRED from nested error body', () => {
    expect(
      bundleHttpErrorMessage(
        {
          error: {
            message: 'Bundle requires minimum 2 items',
            errorCode: 'BUNDLE_MIN_ITEMS_REQUIRED',
          },
        },
        'fallback',
      ),
    ).toBe('Bundle requires minimum 2 items [BUNDLE_MIN_ITEMS_REQUIRED]');
  });

  it('returns message without code for other errors', () => {
    expect(
      bundleHttpErrorMessage(
        { message: 'Not found', errorCode: 'RESOURCE_NOT_FOUND' },
        'fallback',
      ),
    ).toBe('Not found');
  });

  it('uses fallback when err is empty', () => {
    expect(bundleHttpErrorMessage(null, 'Failed')).toBe('Failed');
  });
});
