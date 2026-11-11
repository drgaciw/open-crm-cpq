"""DRG-423: measure actual GetProduct HTTP p95; never starts a service or creates data."""
import argparse
import datetime
import json
import math
import os
import platform
import re
import time
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="Existing numeric GetProduct URL; no credentials/query")
    parser.add_argument("--samples", type=int, default=500)
    parser.add_argument("--warmup", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=5)
    parser.add_argument("--environment", required=True, help="Non-sensitive deployment/dataset description")
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.url)
    if (parsed.scheme not in ("https", "http") or not parsed.hostname
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or not re.fullmatch(r"/api/v1/products/[1-9][0-9]*", parsed.path)):
        parser.error("Use an HTTP(S) /api/v1/products/{numeric-id} URL without credentials/query/fragment")
    if parsed.scheme == "http" and parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
        parser.error("Non-local HTTP is refused; use HTTPS")
    if not 1 <= args.samples <= 10000 or not 0 <= args.warmup <= 1000 or not 0 < args.timeout <= 60:
        parser.error("samples 1..10000, warmup 0..1000, timeout >0..60 required")
    headers = {"Accept": "application/json"}
    token = os.environ.get("CATALOG_BEARER_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    opener = urllib.request.build_opener(NoRedirect())
    durations = []
    for number in range(args.warmup + args.samples):
        request = urllib.request.Request(args.url, headers=headers, method="GET")
        started = time.perf_counter_ns()
        try:
            with opener.open(request, timeout=args.timeout) as response:
                payload = response.read()
                elapsed = (time.perf_counter_ns() - started) / 1_000_000
                if response.status != 200 or not response.headers.get("ETag") or not response.headers.get("X-Catalog-Version"):
                    raise ValueError("Expected 200 with catalog validators")
            data = json.loads(payload)
            if data.get("success") is not True or not isinstance(data.get("data"), dict):
                raise ValueError("Expected successful ProductDto envelope")
        except (urllib.error.URLError, ValueError, TimeoutError):
            # Never echo exceptions: headers, redirect destinations or provider bodies may contain sensitive data.
            raise SystemExit(f"Measurement failed at request {number + 1}; no latency pass is claimed") from None
        if number >= args.warmup:
            durations.append(elapsed)
    ordered = sorted(durations)
    p95 = ordered[math.ceil(len(ordered) * 0.95) - 1]
    result = {
        "measured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "environment": args.environment,
        "python": platform.python_version(),
        "samples": args.samples,
        "warmup": args.warmup,
        "method": "GET 200, full body, sequential requests, nearest-rank p95, no conditional validators sent",
        "p50_ms": ordered[math.ceil(len(ordered) * 0.50) - 1],
        "p95_ms": p95,
        "max_ms": max(ordered),
        "target_ms_exclusive": 100,
        "passed": p95 < 100,
    }
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
