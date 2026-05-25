🎯 **What:** The testing gap addressed
This PR addresses the missing unit tests for the `Money.toFormattedString()` method in the `Money` value object. The method correctly combines the currency code and the plain string representation of the amount, but lacked explicit validation.

📊 **Coverage:** What scenarios are now tested
A new `@Nested` test class named `Formatting` has been added to `MoneyTest.java`. It tests the following scenarios:
*   Positive amounts (e.g., `USD 100.50`)
*   Zero amounts (e.g., `EUR 0.00`)
*   Negative amounts (e.g., `GBP -50.75`)

✨ **Result:** The improvement in test coverage
We now have full test coverage for the string formatting logic of the `Money` value object. This ensures that any future refactoring of `Money` or its formatting behavior will be caught if it alters the expected "Currency Amount" format, increasing the overall reliability of the common library.
