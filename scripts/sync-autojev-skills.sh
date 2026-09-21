#!/usr/bin/env bash
# Refresh the vendored AutoJev agent skills (https://autojev.ai/jev-skills).
# .agents/skills/ is the source of truth; .claude/skills/ is a byte-identical mirror
# so Claude Code discovers the same files. These are third-party agent instructions:
# run this by hand and review the resulting git diff before committing.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="https://autojev.ai/skills"
SKILLS=(
  autojev
  autojev-task-router
  autojev-model-router
  autojev-tool-guard
  autojev-research-guard
  autojev-completion-review
)

for skill in "${SKILLS[@]}"; do
  src="${REPO_ROOT}/.agents/skills/${skill}/SKILL.md"
  mirror="${REPO_ROOT}/.claude/skills/${skill}/SKILL.md"
  mkdir -p "$(dirname "$src")" "$(dirname "$mirror")"

  tmp="$(mktemp)"
  curl -fsSL "${BASE_URL}/${skill}/SKILL.md" -o "$tmp"
  if ! head -n 1 "$tmp" | grep -q '^---'; then
    echo "Refusing ${skill}: response is not a SKILL.md (no frontmatter)" >&2
    rm -f "$tmp"
    exit 1
  fi
  mv "$tmp" "$src"
  cp "$src" "$mirror"
  echo "synced ${skill}"
done
