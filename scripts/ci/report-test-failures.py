#!/usr/bin/env python3
"""Print failing JUnit test cases as GitHub Actions error annotations.

Run with `if: failure()` after a Maven stage. Annotations are readable in the run summary and
through the check-runs API, so a failing test can be named without downloading the job log or the
report artifact (agents behind an egress proxy often cannot reach either).

GitHub keeps at most 10 error annotations per step, so failures are packed several to an annotation.
Always exits 0: it reports, it never decides.
"""
from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET

PATTERNS = (
    "backend/**/target/surefire-reports/*.xml",
    "backend/**/target/failsafe-reports/*.xml",
)
PER_ANNOTATION = 6
MAX_ANNOTATIONS = 9
MAX_CHARS = 6000


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def failures() -> list[str]:
    found: list[str] = []
    for pattern in PATTERNS:
        for path in sorted(glob.glob(pattern, recursive=True)):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            for case in root.iter("testcase"):
                for kind in ("failure", "error"):
                    node = case.find(kind)
                    if node is None:
                        continue
                    causes = [
                        line.strip()
                        for line in (node.text or "").splitlines()
                        if line.strip().startswith("Caused by")
                    ][-2:]
                    message = (node.get("message") or "").strip()[:300]
                    entry = f"{case.get('classname')}#{case.get('name')} [{kind}] {message}"
                    if causes:
                        entry += " | " + " | ".join(causes)[:500]
                    found.append(entry)
    return found


def main() -> int:
    items = failures()
    print(f"::notice title=Failing tests::{len(items)} failing test case(s) in JUnit reports")
    for start in range(0, min(len(items), PER_ANNOTATION * MAX_ANNOTATIONS), PER_ANNOTATION):
        chunk = "\n".join(items[start : start + PER_ANNOTATION])[:MAX_CHARS]
        print(f"::error title=Failing tests {start + 1}-{start + PER_ANNOTATION}::{escape(chunk)}")
    if len(items) > PER_ANNOTATION * MAX_ANNOTATIONS:
        print(f"::error title=Failing tests::{len(items) - PER_ANNOTATION * MAX_ANNOTATIONS} more not shown")
    return 0


if __name__ == "__main__":
    sys.exit(main())
