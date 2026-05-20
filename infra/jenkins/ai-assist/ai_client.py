#!/usr/bin/env python3
"""
LLM client for Jenkins CI assist stages (ADR-003).

Calls Azure OpenAI or an OpenAI-compatible corporate gateway — NOT ai-service:8086
(CPQ AgentRouter exposes /api/v1/ai/agents, not chat completions).
"""

from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from typing import Any


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def sanitize_text(text: str) -> str:
    """Redact common secret patterns before sending content to an LLM."""
    patterns = [
        (r"(?i)(api[_-]?key|password|secret|token)\s*[=:]\s*\S+", r"\1=***REDACTED***"),
        (r"Bearer\s+[A-Za-z0-9._-]+", "Bearer ***REDACTED***"),
        (r"-----BEGIN [A-Z ]+-----[\s\S]*?-----END [A-Z ]+-----", "***REDACTED PEM***"),
    ]
    for pattern, repl in patterns:
        text = re.sub(pattern, repl, text)
    return text


def _llm_config() -> dict[str, str]:
    """Unified LLM_* env with legacy AI_* aliases (see scripts/ci/common.sh)."""
    return {
        "api_key": _env("LLM_API_KEY") or _env("AI_API_KEY"),
        "endpoint": (_env("LLM_ENDPOINT") or _env("AI_API_ENDPOINT")).rstrip("/"),
        "model": _env("LLM_MODEL") or _env("AI_MODEL", "gpt-4o"),
        "timeout_ms": _env("LLM_TIMEOUT_MS") or _env("AI_TIMEOUT_MS", "120000"),
        "api_version": _env("LLM_API_VERSION") or _env("AI_API_VERSION", "2024-02-15-preview"),
        "provider": _env("LLM_PROVIDER") or _env("AI_PROVIDER", "azure"),
    }


def is_ai_enabled() -> bool:
    enabled = _env("AI_CI_ENABLED", "false").lower() in ("1", "true", "yes")
    cfg = _llm_config()
    has_key = bool(cfg["api_key"]) and cfg["api_key"] != "mock-key"
    has_endpoint = bool(cfg["endpoint"])
    # Reject misconfigured sidecar URL (ai-service is not a chat API)
    if has_endpoint and cfg["endpoint"].rstrip("/").endswith(":8086"):
        if "ai-service" in cfg["endpoint"] or cfg["endpoint"].startswith("http://ai-service"):
            return False
    return enabled and has_key and has_endpoint


def chat_completion(system: str, user: str, max_tokens: int = 4096) -> str:
    """Call Azure OpenAI or OpenAI-compatible chat completions API."""
    cfg = _llm_config()
    api_key = cfg["api_key"]
    endpoint = cfg["endpoint"]
    model = cfg["model"]
    timeout_s = int(cfg["timeout_ms"]) / 1000.0
    api_version = cfg["api_version"]

    if not endpoint or not api_key:
        raise RuntimeError("LLM_ENDPOINT and LLM_API_KEY (or AI_* aliases) are required")

    if "openai.azure.com" in endpoint or cfg["provider"] == "azure":
        url = (
            f"{endpoint}/openai/deployments/{model}/chat/completions"
            f"?api-version={api_version}"
        )
        headers = {"api-key": api_key, "Content-Type": "application/json"}
    else:
        url = f"{endpoint}/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }

    body: dict[str, Any] = {
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "max_tokens": max_tokens,
        "temperature": 0.2,
    }
    if "openai.azure.com" not in endpoint:
        body["model"] = model

    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API HTTP {exc.code}: {detail[:2000]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"LLM API connection failed: {exc}") from exc

    try:
        return payload["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError(f"Unexpected LLM response shape: {payload!r}") from exc


def write_report(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print(f"Wrote report: {path}")


def skip_report(path: str, reason: str) -> int:
    write_report(
        path,
        f"# AI stage skipped\n\nReason: {reason}\n\n"
        "Set `AI_CI_ENABLED=true` and configure `LLM_API_KEY` + `LLM_ENDPOINT` "
        "(Azure OpenAI or OpenAI-compatible gateway). Do not point at ai-service:8086.\n",
    )
    return 0
