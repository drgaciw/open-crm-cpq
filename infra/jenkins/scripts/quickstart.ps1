# OpenCRM CPQ — local Jenkins quickstart (dev stack).
# Usage:
#   pwsh -File infra/jenkins/scripts/quickstart.ps1
#   pwsh -File infra/jenkins/scripts/quickstart.ps1 -UpOnly
#   pwsh -File infra/jenkins/scripts/quickstart.ps1 -HealthOnly
#   pwsh -File infra/jenkins/scripts/quickstart.ps1 -SmokeOnly
#   pwsh -File infra/jenkins/scripts/quickstart.ps1 -Down

[CmdletBinding()]
param(
  [switch]$UpOnly,
  [switch]$HealthOnly,
  [switch]$SmokeOnly,
  [switch]$Down
)

$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$JenkinsDir = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$RepoRoot = (Resolve-Path (Join-Path $JenkinsDir '..\..')).Path
$ComposeFile = Join-Path $JenkinsDir 'docker-compose.jenkins.yml'
$EnvExample = Join-Path $JenkinsDir 'env\jenkins.env.example'
$EnvFile = Join-Path $JenkinsDir 'env\jenkins.env'

function Require-Command([string]$Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $Name"
  }
}

function Get-EnvMap {
  $map = @{}
  if (-not (Test-Path -LiteralPath $EnvFile)) { return $map }
  Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^\s*#' -or $line -eq '') { return }
    if ($line -match '^([^=]+)=(.*)$') {
      $map[$Matches[1].Trim()] = $Matches[2].Trim()
    }
  }
  return $map
}

function Ensure-EnvFile {
  if (Test-Path -LiteralPath $EnvFile) {
    Write-Host "Using env file: $EnvFile"
    return
  }
  if (-not (Test-Path -LiteralPath $EnvExample)) {
    throw "Missing $EnvExample"
  }
  Copy-Item -LiteralPath $EnvExample -Destination $EnvFile
  Write-Host "Created $EnvFile from example."
  Write-Host "Edit JENKINS_ADMIN_PASSWORD before shared environments."
}

function Warn-DefaultPassword {
  if (-not (Test-Path -LiteralPath $EnvFile)) { return }
  $content = Get-Content -LiteralPath $EnvFile -Raw
  if ($content -match '(?m)^JENKINS_ADMIN_PASSWORD=changeme\s*$') {
    Write-Warning "JENKINS_ADMIN_PASSWORD is still 'changeme' — change it in $EnvFile"
  }
}

function Invoke-Compose {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  $base = @('compose', '-f', $ComposeFile, '--env-file', $EnvFile) + $Args
  & docker @base
  if ($LASTEXITCODE -ne 0) { throw "docker compose failed (exit $LASTEXITCODE)" }
}

function Wait-ForHealth {
  $envMap = Get-EnvMap
  $port = if ($envMap['JENKINS_HTTP_PORT']) { $envMap['JENKINS_HTTP_PORT'] } else { '8080' }
  $baseUrl = if ($envMap['JENKINS_URL']) { $envMap['JENKINS_URL'].TrimEnd('/') } else { "http://localhost:$port" }
  $url = "$baseUrl/login"
  $attempts = if ($env:QUICKSTART_HEALTH_ATTEMPTS) { [int]$env:QUICKSTART_HEALTH_ATTEMPTS } else { 60 }
  $delaySec = if ($env:QUICKSTART_HEALTH_DELAY_SEC) { [int]$env:QUICKSTART_HEALTH_DELAY_SEC } else { 5 }

  Write-Host "Waiting for Jenkins at $url (up to $($attempts * $delaySec)s)..."
  for ($i = 1; $i -le $attempts; $i++) {
    try {
      $null = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
      Write-Host "Jenkins is up ($url)"
      return
    } catch {
      Start-Sleep -Seconds $delaySec
    }
  }
  Write-Host '--- last 30 lines: docker logs opencrm-jenkins ---' -ForegroundColor Yellow
  docker logs opencrm-jenkins --tail 30 2>&1 | ForEach-Object { Write-Host $_ }
  throw "Jenkins did not become healthy in time. Check: docker compose -f $ComposeFile --env-file $EnvFile logs jenkins"
}

function Invoke-ToolchainSmoke {
  Write-Host 'Running host toolchain smoke: scripts/ci/run-core.sh toolchain'
  $bash = Get-Command bash -ErrorAction SilentlyContinue
  if (-not $bash) {
    throw 'bash not found. Install Git Bash/WSL or run toolchain manually from repo root.'
  }
  Push-Location $RepoRoot
  try {
    & bash scripts/ci/run-core.sh toolchain
    if ($LASTEXITCODE -ne 0) { throw "toolchain smoke failed (exit $LASTEXITCODE)" }
  } finally {
    Pop-Location
  }
}

if ($Down) {
  Require-Command docker
  if (Test-Path -LiteralPath $EnvFile) {
    Invoke-Compose @('down')
  } else {
    & docker compose -f $ComposeFile down
    if ($LASTEXITCODE -ne 0) { throw "docker compose down failed" }
  }
  Write-Host 'Jenkins stack stopped.'
  exit 0
}

if ($SmokeOnly) {
  Invoke-ToolchainSmoke
  exit 0
}

if ($HealthOnly) {
  if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Missing $EnvFile" }
  Wait-ForHealth
  exit 0
}

Require-Command docker

Ensure-EnvFile
Warn-DefaultPassword

Write-Host 'Starting Jenkins (compose build may take several minutes)...'
Invoke-Compose @('up', '-d', '--build')

Wait-ForHealth

$envMap = Get-EnvMap
$jenkinsUrl = if ($envMap['JENKINS_URL']) { $envMap['JENKINS_URL'] } else { 'http://localhost:8080/' }
Write-Host ''
Write-Host 'Next steps (first green build):'
Write-Host "  1. Open $jenkinsUrl (user from env: JENKINS_ADMIN_USER)"
Write-Host "  2. Use JCasC job 'opencrm-cpq-ci' or Pipeline from SCM → Jenkinsfile"
Write-Host '  3. Keep AI_CI_ENABLED=false for the first run'
Write-Host '  4. Trigger a build; expect Security Audit may be UNSTABLE'
Write-Host ''

if (-not $UpOnly) {
  Invoke-ToolchainSmoke
}

Write-Host 'Quickstart complete.'
