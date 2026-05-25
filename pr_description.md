🎯 **What:** Removed unused `from __future__ import unicode_literals` import from `cpq_beta/crm/uninstall.py`, `cpq_beta/crm/install.py`, and `cpq_beta/crm/www/crm.py`.

💡 **Why:** This future import is obsolete in modern Python 3, where all strings are unicode by default. Removing it clears out dead code, making the files slightly cleaner and improving general code maintainability.

✅ **Verification:** Verified that tests can successfully import the modified files without errors. Verified that no other references to `unicode_literals` existed in the modified files.

✨ **Result:** A cleaner codebase with obsolete python2 backward compatibility imports removed.
