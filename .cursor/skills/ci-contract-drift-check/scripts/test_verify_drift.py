#!/usr/bin/env python3
"""Unit tests for verify-drift.py and scripts/ci/frontend-uat.sh port parameterization."""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[4]
DRIFT_SCRIPT = REPO_ROOT / ".cursor" / "skills" / "ci-contract-drift-check" / "scripts" / "verify-drift.py"
UAT_SCRIPT = REPO_ROOT / "scripts" / "ci" / "frontend-uat.sh"


class FrontendUatContractTest(unittest.TestCase):
    def test_verify_drift_passes_on_repo(self) -> None:
        proc = subprocess.run(
            ["python3", str(DRIFT_SCRIPT)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(proc.returncode, 0, f"verify-drift.py failed:\n{proc.stdout}\n{proc.stderr}")

    def test_pr_check_excludes_frontend_uat(self) -> None:
        pr_check = (REPO_ROOT / ".github" / "workflows" / "pr-check.yml").read_text(encoding="utf-8")
        self.assertNotIn("frontend-uat", pr_check)

    def test_frontend_uat_script_syntax_and_dry_run_ports(self) -> None:
        self.assertTrue(UAT_SCRIPT.is_file(), f"Missing {UAT_SCRIPT}")
        syntax = subprocess.run(["bash", "-n", str(UAT_SCRIPT)], capture_output=True, text=True)
        self.assertEqual(syntax.returncode, 0, syntax.stderr)

        with tempfile.TemporaryDirectory() as tmpdir:
            env = {
                **os.environ,
                "UAT_UI_PORT": "14400",
                "GATEWAY_PORT": "18180",
                "CRM_PORT": "18181",
                "PG_PORT": "15532",
                "REDIS_PORT": "16479",
                "UAT_ARTIFACT_DIR": tmpdir,
                "UAT_DRY_RUN": "1",
            }
            proc = subprocess.run(
                ["bash", str(UAT_SCRIPT)],
                cwd=REPO_ROOT,
                env=env,
                capture_output=True,
                text=True,
            )
            self.assertEqual(proc.returncode, 0, f"dry-run failed:\n{proc.stdout}\n{proc.stderr}")
            out = proc.stdout
            self.assertIn("UAT_BASE_URL=http://localhost:14400", out)
            self.assertIn("CRM_SERVICE_URI=http://localhost:18181", out)
            self.assertIn("GATEWAY_PORT=18180", out)
            self.assertIn("CORS_ALLOWED_ORIGINS=http://localhost:14400,http://127.0.0.1:14400", out)
            self.assertIn("FRONTEND_URL=http://localhost:14400", out)
            self.assertIn("DB_PORT=15532", out)
            self.assertIn("REDIS_PORT=16479", out)
            self.assertIn("http://localhost:18180", out)
            # Proxy config JSON emitted in dry-run must point /api at the overridden gateway port
            proxy_line = [line for line in out.splitlines() if line.startswith("PROXY_CONFIG_JSON=")]
            self.assertTrue(proxy_line, f"Missing PROXY_CONFIG_JSON in dry-run output:\n{out}")
            proxy_cfg = json.loads(proxy_line[0].split("=", 1)[1])
            self.assertEqual(proxy_cfg["/api"]["target"], "http://localhost:18180")


if __name__ == "__main__":
    unittest.main()
