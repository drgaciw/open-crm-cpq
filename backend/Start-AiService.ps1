<#
.SYNOPSIS
    Builds and boots the Open CRM/CPQ AI Service (GLM-5 / ADR-002).

.DESCRIPTION
    1. Validates prerequisites (Java 25, Maven 3.9+, Docker).
    2. Compiles the ai-service Maven module (skip tests for fast startup).
    3. Ensures the .env file has ZHIPUAI_API_KEY set.
    4. Starts the full Docker Compose stack (postgres + redis + ai-service).
    5. Tails the ai-service logs and waits for the health endpoint to go green.

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
    LLM: GLM-5 via Zhipu AI (ADR-002)
    ⚠️  Data residency: Zhipu AI routes traffic through Chinese infrastructure.
         Complete DPA/compliance review before using with real customer data.
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

$envContent = Get-Content $envFile -Raw
if ($envContent -notmatch 'ZHIPUAI_API_KEY=\S+') {
    Write-Fail @"
ZHIPUAI_API_KEY is not set in $envFile.
  1. Open $envFile
  2. Set: ZHIPUAI_API_KEY=<your-key-from-open.bigmodel.cn>
  3. Re-run this script.
"@
}
Write-OK ".env has ZHIPUAI_API_KEY"

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

$detachFlag = if ($Detach) { "-d" } else { "" }

Write-Host "   Services: $services" -ForegroundColor DarkCyan
try {
    if ($Detach) {
        & docker compose --env-file .env up --build -d $services.Split(" ")
    } else {
        # Build silently first then attach to ai-service logs
        & docker compose --env-file .env up --build -d $services.Split(" ")
        Write-OK "Containers starting. Waiting for ai-service health..."
        Start-Sleep -Seconds 5
        & docker compose logs -f ai-service
    }
} finally {
    Pop-Location
}

# ─── 5. Health check ───────────────────────────────────────────────────────────
if ($Detach) {
    Write-Step "Waiting for ai-service health endpoint (up to 90s)"
    $timeout = 90
    $elapsed = 0
    $healthy = $false
    while ($elapsed -lt $timeout) {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:8086/actuator/health" -TimeoutSec 3 -ErrorAction Stop
            if ($r.StatusCode -eq 200) { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 5
        $elapsed += 5
        Write-Host "   ... $elapsed`s elapsed" -ForegroundColor DarkGray
    }
    if ($healthy) {
        Write-OK "ai-service is UP at http://localhost:8086"
        Write-OK "Agent list: GET http://localhost:8086/api/v1/ai/agents"
        Write-OK "Example invoke: POST http://localhost:8086/api/v1/ai/agents/cpq-agent-004/invoke"
    } else {
        Write-Warn "Health check timed out. Check logs: docker compose logs ai-service"
    }
} else {
    Write-Host "`n   Agents endpoint: GET  http://localhost:8086/api/v1/ai/agents" -ForegroundColor Cyan
    Write-Host "   Example invoke:  POST http://localhost:8086/api/v1/ai/agents/cpq-agent-004/invoke" -ForegroundColor Cyan
}

Write-Host ""
