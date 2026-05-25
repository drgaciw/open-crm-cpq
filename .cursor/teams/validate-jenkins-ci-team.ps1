# Validates Jenkins CI team manifest and expected project agent/skill files.
#
# Usage:
#   pwsh -File .cursor/teams/validate-jenkins-ci-team.ps1
#   pwsh -File .cursor/teams/validate-jenkins-ci-team.ps1 -RepoRoot "C:\path\to\open-crm-cpq"

param(
  [Parameter(Mandatory = $false)]
  [string]$RepoRoot = ""
)

$ErrorActionPreference = 'Stop'

function Get-NormalizedTextSha256Hex([string]$LiteralPath) {
  $raw = [System.IO.File]::ReadAllText($LiteralPath)
  $norm = $raw -replace "`r`n", "`n" -replace "`r", "`n"
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($norm)
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $h = $sha.ComputeHash($bytes)
    return (-join ($h | ForEach-Object { $_.ToString('x2') }))
  } finally {
    $sha.Dispose()
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
  $RepoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
} else {
  $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
}

$projectManifest = Join-Path $RepoRoot '.cursor' 'teams' 'jenkins-ci-team.yaml'
$userManifest = Join-Path $env:USERPROFILE '.cursor' 'teams' 'jenkins-ci-team.yaml'

if (-not (Test-Path -LiteralPath $projectManifest)) {
  Write-Error "Missing project manifest: $projectManifest"
}

$skills = @(
  'jenkins-quickstart',
  'jenkins-jcasc-multibranch',
  'jenkins-prod-build-agent',
  'ci-contract-drift-check',
  'jenkins-ai-assist-config',
  'jenkins-failure-log-triage',
  'ci-stage-ssot-edit'
)

$agents = @(
  'jenkins-ci-orchestrator.agent.md',
  'jenkins-compose-bootstrap.agent.md',
  'jenkins-multibranch-architect.agent.md',
  'jenkins-build-agent-provisioner.agent.md',
  'ci-drift-guardian.agent.md',
  'ci-ssot-editor.agent.md',
  'jenkins-failure-triage.agent.md',
  'jenkins-ai-gate-curator.agent.md'
)

$miss = New-Object System.Collections.Generic.List[string]
$warn = New-Object System.Collections.Generic.List[string]

foreach ($id in $skills) {
  $p = Join-Path $RepoRoot '.cursor' 'skills' $id 'SKILL.md'
  if (-not (Test-Path -LiteralPath $p)) {
    [void]$miss.Add("skill  $id  -> missing $p")
  }
}

foreach ($f in $agents) {
  $p = Join-Path $RepoRoot '.cursor' 'agents' $f
  if (-not (Test-Path -LiteralPath $p)) {
    [void]$miss.Add("agent  $f  -> missing $p")
  }
}

$driftScript = Join-Path $RepoRoot '.cursor' 'skills' 'ci-contract-drift-check' 'scripts' 'verify-drift.py'
if (-not (Test-Path -LiteralPath $driftScript)) {
  [void]$miss.Add("drift script -> missing $driftScript")
}

if (Test-Path -LiteralPath $userManifest) {
  $hu = Get-NormalizedTextSha256Hex $userManifest
  $hp = Get-NormalizedTextSha256Hex $projectManifest
  if ($hu -ne $hp) {
    [void]$warn.Add("User manifest differs from project copy (sync if intentional):`n  user: $userManifest`n  proj: $projectManifest")
  }
} else {
  [void]$warn.Add("No user manifest at $userManifest (optional)")
}

if ($miss.Count -gt 0) {
  Write-Host 'jenkins-ci-team validation FAILED' -ForegroundColor Red
  foreach ($line in $miss) { Write-Host "  $line" }
  exit 1
}

Write-Host 'jenkins-ci-team validation OK:' -ForegroundColor Green
Write-Host "  project manifest: $projectManifest"
Write-Host ('  skills: ' + $skills.Count)
Write-Host ('  agents: ' + $agents.Count)

foreach ($line in $warn) {
  Write-Host "  WARN: $line" -ForegroundColor Yellow
}

exit 0
