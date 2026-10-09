param(
    [Parameter(Mandatory)][ValidateSet('validate','start','check-api','stop')][string]$Action,
    [Parameter(Mandatory)][string]$IntegrationRoot
)
$ErrorActionPreference = 'Stop'
$waveRoot = [IO.Path]::GetFullPath('C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-wave-20261009')
$resolvedRoot = (Resolve-Path -LiteralPath $IntegrationRoot).Path
if (!$resolvedRoot.StartsWith($waveRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'IntegrationRoot must be an isolated worktree inside the named ramp wave.'
}
$artifactDir = Join-Path $resolvedRoot '.acceptance-artifacts'
$manifestPath = Join-Path $artifactDir 'processes.json'
$databaseName = 'ramp_wave_20261009'
$serviceSpecs = @(
    @{Name='pricing-service'; Port=18082; Extra=@('--opencrm.pricing.ramp.enabled=true','--opencrm.pricing.metrics.acv-version=term-years','--opencrm.pricebook.base-url=http://127.0.0.1:18087')},
    @{Name='pricebook-service'; Port=18087; Extra=@()},
    @{Name='quote-service'; Port=18084; Extra=@('--opencrm.pricing.ramp.enabled=true','--services.pricing.url=http://127.0.0.1:18082')}
)

if ($Action -eq 'stop' -or $Action -eq 'check-api') {
    if (!(Test-Path -LiteralPath $manifestPath)) { throw 'No wave-owned process manifest exists.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.worktree -ne $resolvedRoot -or $manifest.database -ne $databaseName -or !$manifest.databaseCreatedByWave) {
        throw 'Manifest does not match this isolated acceptance worktree/database.'
    }
    if ($Action -eq 'check-api' -and @($manifest.processes).Count -ne 3) { throw 'API check requires all three owned applications.' }
    foreach ($owned in $manifest.processes) {
        $process = Get-Process -Id $owned.pid -ErrorAction SilentlyContinue
        if (!$process) {
            if ($Action -eq 'check-api') { throw "Owned acceptance process $($owned.name) is no longer live." }
            continue
        }
        $actual = Get-CimInstance Win32_Process -Filter "ProcessId = $($owned.pid)"
        # PowerShell 7 may materialize ISO JSON dates as DateTime; compare exact UTC instants, not string coercions.
        $recordedUtcTicks = if ($owned.startUtc -is [DateTime]) { $owned.startUtc.ToUniversalTime().Ticks }
            else { [DateTimeOffset]::Parse([string]$owned.startUtc, [Globalization.CultureInfo]::InvariantCulture).UtcTicks }
        if ($process.StartTime.ToUniversalTime().Ticks -ne $recordedUtcTicks -or
            $actual.Name -ne 'java.exe' -or !$actual.CommandLine.Contains($owned.jar) -or
            !$actual.CommandLine.Contains("--server.port=$($owned.port)")) {
            throw "PID $($owned.pid) no longer matches the recorded acceptance process; refusing to stop it."
        }
        if ($Action -eq 'check-api') { continue }
        Stop-Process -Id $owned.pid
        if (!$process.WaitForExit(5000)) { throw "Owned acceptance PID $($owned.pid) did not exit." }
        Write-Output "Stopped owned acceptance process $($owned.name), PID $($owned.pid)."
    }
    if ($Action -eq 'check-api') {
        $freeGB = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
        $rssMB = (@($manifest.processes | ForEach-Object { (Get-Process -Id $_.pid).WorkingSet64 }) | Measure-Object -Sum).Sum / 1MB
        @{timestampUtc=[DateTime]::UtcNow.ToString('O'); phase='before-api'; freeGB=$freeGB; ownedRssMB=$rssMB} | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $artifactDir 'memory.jsonl')
        Write-Output "Before API: $([math]::Round($freeGB,2))GB free, $([math]::Round($rssMB))MB owned JVM RSS."
        if ($freeGB -lt 0.8) { throw 'Need at least 0.8GB free RAM for bounded API/PDF acceptance; stop owned processes in finally.' }
    } else { Write-Output 'Dedicated database retained for evidence; no container or development service was stopped.' }
    return
}

$freeGB = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
# Prior same-artifact bounded run measured 1.15GB aggregate RSS for all three ready JVMs.
# Staging still refuses each next JVM below 1.5GB and retains the 0.8GB API/OS reserve.
if ($freeGB -lt 2.5) { throw "Need at least 2.5GB free RAM before staged isolated acceptance launch; available $([math]::Round($freeGB,2))GB." }
foreach ($spec in $serviceSpecs) {
    $spec.Jar = Join-Path $resolvedRoot "backend/services/$($spec.Name)/target/$($spec.Name)-1.0.0-SNAPSHOT.jar"
    if (!(Test-Path -LiteralPath $spec.Jar)) { throw "Missing assembled artifact for $($spec.Name)." }
    if (Get-NetTCPConnection -State Listen -LocalPort $spec.Port -ErrorAction SilentlyContinue) {
        throw "Acceptance port $($spec.Port) already has a listener; refusing to reuse or stop it."
    }
}
$javaPath = (Get-Command java.exe).Source
Write-Output "Validated isolated artifact paths, free ports and $([math]::Round($freeGB,2))GB free RAM."
if ($Action -eq 'validate') { return }
if (!$env:DB_PASSWORD) { throw 'Supply DB_PASSWORD through the secure process environment; the script never prints it.' }
$ownedDatabase = $false
if (Test-Path -LiteralPath $manifestPath) {
    $priorManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($priorManifest.database -ne $databaseName -or !$priorManifest.databaseCreatedByWave -or $priorManifest.worktree -ne $resolvedRoot) {
        throw 'Existing manifest does not prove this wave owns the dedicated database.'
    }
    foreach ($owned in $priorManifest.processes) {
        if (Get-Process -Id $owned.pid -ErrorAction SilentlyContinue) {
            throw "Recorded PID $($owned.pid) is still live; stop/inspect owned processes before resume."
        }
    }
    $ownedDatabase = $true
}
$databaseExists = & docker exec postgres psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'ramp_wave_20261009'"
if ($LASTEXITCODE -ne 0) { throw 'Database existence check failed.' }
if (($databaseExists -join '').Trim() -eq '1') {
    if (!$ownedDatabase) { throw 'Dedicated database already exists without wave ownership; refusing to reuse it.' }
    Write-Output 'Resuming only the dedicated database proven wave-created by the manifest; all recorded PIDs are gone.'
} else {
    & docker exec postgres psql -U postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE ramp_wave_20261009'
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated database creation failed.' }
}
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
$attemptId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')
if ($ownedDatabase) { Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $artifactDir "processes-before-$attemptId.json") }
$manifest = @{database=$databaseName; databaseCreatedByWave=$true; worktree=$resolvedRoot; processes=@()}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath
$priorDatabase = $env:DB_NAME
$env:DB_NAME = $databaseName
try {
    foreach ($spec in $serviceSpecs) {
        $freeGB = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
        if (@($manifest.processes).Count -gt 0 -and $freeGB -lt 1.5) {
            throw "Need at least 1.5GB free before the next owned JVM; available $([math]::Round($freeGB,2))GB. Stop owned processes in finally."
        }
        $schema = if ($spec.Name -eq 'quote-service') { 'quote' } else { 'pricing' }
        $arguments = @('-Xmx384m','-XX:MaxMetaspaceSize=192m','-XX:MaxDirectMemorySize=64m','-XX:ReservedCodeCacheSize=64m','-jar',('"' + $spec.Jar + '"'),
            '--server.address=127.0.0.1',"--server.port=$($spec.Port)",'--spring.profiles.active=dev',
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/$($databaseName)?currentSchema=$schema",
            '--spring.datasource.username=postgres','--spring.data.redis.host=127.0.0.1','--spring.data.redis.port=6379',
            '--opencrm.security.mode=dev','--spring.datasource.hikari.maximum-pool-size=3',
            '--management.endpoint.health.probes.enabled=true',
            '--spring.datasource.hikari.minimum-idle=1','--opencrm.outbox.relay.enabled=false',
            '--spring.kafka.listener.auto-startup=false') + $spec.Extra
        $process = Start-Process -FilePath $javaPath -ArgumentList $arguments -WorkingDirectory $resolvedRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $artifactDir "$($spec.Name)-$attemptId.stdout.log") `
            -RedirectStandardError (Join-Path $artifactDir "$($spec.Name)-$attemptId.stderr.log")
        $manifest.processes += @{name=$spec.Name; pid=$process.Id; startUtc=$process.StartTime.ToUniversalTime().ToString('O'); jar=$spec.Jar; port=$spec.Port}
        $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath
        Write-Output "Started owned $($spec.Name), PID $($process.Id), loopback port $($spec.Port)."
        $ready = $false
        for ($attempt=0; $attempt -lt 30; $attempt++) {
            if ($process.HasExited) { throw "$($spec.Name) exited; inspect owned logs and use stop to shut down other owned PIDs." }
            try {
                # Boot readiness checks the started application's lifecycle; absent Kafka does not falsify it.
                # Aggregate /health is intentionally not claimed: this check covers calculate only.
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$($spec.Port)/actuator/health/readiness" -TimeoutSec 2
                if ($health.status -eq 'UP') { $ready=$true; break }
            } catch { }
            Start-Sleep -Seconds 2
        }
        if (!$ready) { throw "$($spec.Name) not healthy; stop owned PIDs before retrying." }
        $freeGB = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
        $rssMB = (@($manifest.processes | ForEach-Object { (Get-Process -Id $_.pid).WorkingSet64 }) | Measure-Object -Sum).Sum / 1MB
        @{timestampUtc=[DateTime]::UtcNow.ToString('O'); phase="$($spec.Name)-ready"; freeGB=$freeGB; ownedRssMB=$rssMB} | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $artifactDir 'memory.jsonl')
        Write-Output "Measured $([math]::Round($freeGB,2))GB free, $([math]::Round($rssMB))MB owned JVM RSS."
        if ($freeGB -lt 0.8) { throw 'Free RAM fell below 0.8GB reserve after readiness; stop owned processes in finally.' }
        Write-Output "$($spec.Name) application readiness is UP."
    }
    Write-Output 'All isolated acceptance applications ready. Aggregate dependency health is not asserted. Set RAMP_ACCEPTANCE_DATABASE=ramp_wave_20261009 and run the synthetic acceptance script.'
} finally {
    $env:DB_NAME = $priorDatabase
}
