🎯 **What:**
The `PercentageTest` class lacked dedicated test coverage for the boolean check methods in the `Percentage` value object, specifically `isNegative`, `isPositive`, and `isZero`. The previous `signDetection` test only verified the `true` (happy path) conditions. This PR adds explicit test methods that thoroughly test both the `true` and `false` return cases for all three boolean checks.

📊 **Coverage:**
* `isPositive`: Now verifies that positive values return `true`, while negative and zero values return `false`.
* `isNegative`: Now verifies that negative values return `true`, while positive and zero values return `false`.
* `isZero`: Now verifies that zero values (both `Percentage.zero()` and `Percentage.of(0)`) return `true`, while non-zero values return `false`.

✨ **Result:**
Improved test coverage for the `Percentage` value object's sign detection methods, ensuring all possible edge cases and valid use cases (like negative percentages representing discounts) are rigorously verified against unexpected regressions.
