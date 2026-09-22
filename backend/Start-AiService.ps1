<#
.SYNOPSIS
    Builds and boots the Open CRM/CPQ AI Service (Azure baseline / ADR-014).

.DESCRIPTION
    1. Validates prerequisites (Java 25, Maven 3.9+, Docker).
    2. Compiles the ai-service Maven module (skip tests for fast startup).
    3. Ensures a local .env configuration exists.
    4. Starts the full Docker Compose stack (postgres + redis + ai-service).
    5. Waits for health, then optionally tails the ai-service logs.

.PARAMETER SkipBuild
    Skip the Maven build step (use if JAR is already current).

.PARAMETER FullStack
    Start all services (crm, pricing, product, quote, gateway) in addition to ai-service.
    Default: starts only postgres, redis, and ai-service.

.PARAMETER Detach
    Run docker compose in detached mode (no log tailing).

.EXAMPLE
    # First run — build and boot just the AI service with its dependencies
    .\Start-AiService.ps1

    # Rebuild and start full stack
    .\Start-AiService.ps1 -FullStack

    # Start without rebuilding (already have a current JAR)
    .\Start-AiService.ps1 -SkipBuild

.NOTES
    Requires: Java 25, Maven 3.9+, Docker Desktop (or Docker Engine)
    LLM: Azure baseline; Jev/OpenRouter selection stays disabled pending rollout gates.
#>
param(
    [switch]$SkipBuild,
    [switch]$FullStack,
    [switch]$Detach
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot   # backend/

# ─── Colors ────────────────────────────────────────────────────────────────────
function Write-Step  { param($msg) Write-Host "`n▶  $msg" -ForegroundColor Cyan }
function Write-OK    { param($msg) Write-Host "   ✓  $msg" -ForegroundColor Green }
function Write-Warn  { param($msg) Write-Host "   ⚠  $msg" -ForegroundColor Yellow }
function Write-Fail  { param($msg) Write-Host "   ✗  $msg" -ForegroundColor Red; exit 1 }

# ─── 1. Prerequisites ──────────────────────────────────────────────────────────
Write-Step "Checking prerequisites"

# Java 25 (CI/Docker already enforce 25; do not use 21 or 27)
$javaVer = & java -version 2>&1 | Select-String 'version "(\d+)' | ForEach-Object {
    [int]($_.Matches[0].Groups[1].Value)
}
if (-not $javaVer) { Write-Fail "java not found. Install JDK 25 and add to PATH (JAVA_HOME=…/temurin-25)." }
if ($javaVer -lt 25) { Write-Fail "Java $javaVer found but Java 25 is required." }
Write-OK "Java $javaVer"

# Maven 3.9+
$mvnCmd = if (Test-Path "$ProjectRoot\mvnw.cmd") { "$ProjectRoot\mvnw.cmd" }
          elseif (Get-Command mvn -ErrorAction SilentlyContinue) { "mvn" }
          else { $null }
if (-not $mvnCmd) { Write-Fail "Maven (mvn) not found. Install Maven 3.9+ or ensure mvnw.cmd exists." }
Write-OK "Maven: $mvnCmd"

# Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Fail "Docker not found. Install Docker Desktop and ensure it is running."
}
$dockerRunning = docker info 2>&1 | Select-String "Server Version"
if (-not $dockerRunning) { Write-Fail "Docker is installed but not running. Start Docker Desktop." }
Write-OK "Docker running"

# ─── 2. .env file ──────────────────────────────────────────────────────────────
Write-Step "Checking .env configuration"

$envFile = Join-Path $ProjectRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Warn ".env not found — copying from .env.example"
    Copy-Item "$ProjectRoot\.env.example" $envFile
}

# Provider credentials are configured in .env only when their provider is enabled.
Write-OK ".env configuration present"

# ─── 3. Maven build ────────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Step "Building ai-service (mvn package -DskipTests)"
    Push-Location $ProjectRoot
    try {
        & $mvnCmd package -pl services/ai-service -am -DskipTests -q
        if ($LASTEXITCODE -ne 0) { Write-Fail "Maven build failed. Run with full output: $mvnCmd package -pl services/ai-service -am -DskipTests" }
        Write-OK "Build succeeded — JAR: services/ai-service/target/ai-service-*.jar"
    } finally {
        Pop-Location
    }
} else {
    Write-Warn "Skipping Maven build (-SkipBuild). Using existing JAR."
    $jar = Get-ChildItem "$ProjectRoot\services\ai-service\target\*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jar) { Write-Fail "No JAR found at services/ai-service/target/. Remove -SkipBuild and rebuild." }
    Write-OK "Using: $($jar.Name)"
}

# ─── 4. Docker Compose up ──────────────────────────────────────────────────────
Write-Step "Starting Docker Compose stack"
Push-Location $ProjectRoot

$services = if ($FullStack) {
    "postgres redis crm-service pricing-service product-service quote-service gateway-service ai-service"
} else {
    "postgres redis ai-service"
}

Write-Host "   Services: $services" -ForegroundColor DarkCyan
try {
    & docker compose --env-file .env up --build -d $services.Split(" ")
    if ($LASTEXITCODE -ne 0) { Write-Fail "Docker Compose startup failed." }
} finally {
    Pop-Location
}

# Health must pass before reporting success, including when following logs.
Write-Step "Waiting for ai-service health endpoint (up to 90s)"
$deadline = [DateTime]::UtcNow.AddSeconds(90)
$healthy = $false
while ([DateTime]::UtcNow -lt $deadline) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8090/actuator/health" -TimeoutSec 3 -ErrorAction Stop
        if ($r.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 5
}
if (-not $healthy) {
    Write-Fail "Health check timed out. Check logs: docker compose logs ai-service"
}
Write-OK "ai-service is UP at http://localhost:8090"
Write-OK "Agent list: GET http://localhost:8090/api/v1/ai/agents"
Write-OK "Example invoke: POST http://localhost:8090/api/v1/ai/agents/cpq-agent-004/invoke"
if (-not $Detach) {
    Push-Location $ProjectRoot
    try {
        & docker compose logs -f ai-service
    } finally {
        Pop-Location
    }
}
