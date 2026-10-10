param(
    [Parameter(Mandatory)][ValidateSet('prepare','start-broker','start','stop','stop-broker')][string]$Action,
    [Parameter(Mandatory)][string]$IntegrationRoot,
    [string]$ArtifactSource,
    [string]$GatewayArtifactSource,
    [string]$Services,
    [switch]$RampDisabled,
    [switch]$IncludeContract
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $IntegrationRoot).Path
$wave = [IO.Path]::GetFullPath('C:/Users/dgorn/.codex-worktrees/open-crm-cpq/ramp-next-20261010')
if ($root -ne (Join-Path $wave 'integration')) { throw 'Only the named isolated integration worktree is supported.' }
if ((& git -C $root rev-parse --show-toplevel).Replace('/', '\') -ne $root) { throw 'Unexpected Git worktree root.' }
$artifacts = Join-Path $root '.acceptance-artifacts/lifecycle'
$manifestPath = Join-Path $artifacts 'processes.json'
$specs = @{
    'gateway-service' = @{port=19080; heap=192; schema=$null; extra=@()}
    'product-service' = @{port=19083; heap=256; schema='product'; extra=@()}
    'pricing-service' = @{port=19082; heap=384; schema='pricing'; extra=@('--opencrm.pricing.metrics.acv-version=term-years','--opencrm.pricebook.base-url=http://127.0.0.1:19087')}
    'pricebook-service' = @{port=19087; heap=256; schema='pricing'; extra=@()}
    'quote-service' = @{port=19084; heap=320; schema='quote'; extra=@('--services.pricing.url=http://127.0.0.1:19082','--services.configuration-engine.url=http://127.0.0.1:19088','--services.approval-routing.url=http://127.0.0.1:19085')}
    'approval-routing' = @{port=19085; heap=256; schema='approval'; extra=@()}
    'configuration-engine' = @{port=19088; heap=192; schema='configuration'; extra=@('--services.product.url=http://127.0.0.1:19083','--services.product.rules-cache-ttl=0s')}
}
if ($IncludeContract) {
    $specs['contract-asset-management'] = @{port=19089; heap=256; schema='contract'; extra=@(
        '--opencrm.erp-sync.enabled=false', '--opencrm.subscription-ramp.enabled=true',
        '--opencrm.subscription-ramp.poll-interval=PT1S')}
}
function Save-LifecycleManifest {
    $script:manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath
}
function Assert-LifecycleMemory([double]$MinimumGB) {
    $free = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
    @{timestampUtc=[DateTime]::UtcNow.ToString('O'); action=$Action; freeGB=$free} | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $artifacts 'memory.jsonl')
    if ($free -lt $MinimumGB) { throw "Need $MinimumGB GB free for $Action; measured $([math]::Round($free,2))GB. No existing service will be stopped." }
}
function Assert-OwnedLifecycleProcess($Owned) {
    $live = Get-Process -Id $Owned.pid -ErrorAction SilentlyContinue
    if (!$live) { return $null }
    $actual = Get-CimInstance Win32_Process -Filter "ProcessId = $($Owned.pid)"
    $recorded = if ($Owned.startUtc -is [DateTime]) { $Owned.startUtc.ToUniversalTime().Ticks }
        else { [DateTimeOffset]::Parse([string]$Owned.startUtc, [Globalization.CultureInfo]::InvariantCulture).UtcTicks }
    if ($live.StartTime.ToUniversalTime().Ticks -ne $recorded -or $actual.Name -ne 'java.exe' -or
        !$actual.CommandLine.Contains($Owned.jar) -or !$actual.CommandLine.Contains("--server.port=$($Owned.port)")) {
        throw "PID $($Owned.pid) no longer matches the owned process; refusing to use or stop it."
    }
    return $live
}
function Start-LifecycleService([string]$Name) {
    $spec = $specs[$Name]
    if (!$spec) { throw "Unknown service: $Name" }
    if (@($manifest.processes | Where-Object name -eq $Name).Count) { throw "Service $Name already recorded; stop it before replacement." }
    Assert-LifecycleMemory 1.5
    if (Get-NetTCPConnection -State Listen -LocalPort $spec.port -ErrorAction SilentlyContinue) { throw "Port $($spec.port) occupied; refusing to reuse it." }
    $jar = Join-Path $artifacts "jars/$Name.jar"
    $expected = $manifest.artifacts | Where-Object name -eq $Name
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $expected.sha256) { throw "Artifact hash changed for $Name." }
    if (!$env:DB_PASSWORD -or !$env:OPENCRM_SECURITY_JWT_ISSUERURI -or !$env:OPENCRM_SECURITY_CLIENT_CLIENTSECRET) {
        throw 'DB_PASSWORD and the in-memory synthetic identity fixture environment are required.'
    }
    $env:DB_NAME = $manifest.database
    $env:OPENCRM_SECURITY_CLIENT_CLIENTID = "ramp-acceptance-$Name"
    $env:QUOTE_SERVICE_URI='http://127.0.0.1:19084'
    $env:PRICING_SERVICE_URI='http://127.0.0.1:19082'
    $env:PRICEBOOK_SERVICE_URI='http://127.0.0.1:19087'
    $env:PRODUCT_SERVICE_URI='http://127.0.0.1:19083'
    $env:APPROVAL_ROUTING_URI='http://127.0.0.1:19085'
    $env:CONFIGURATION_ENGINE_URI='http://127.0.0.1:19088'
    if ($IncludeContract) { $env:CONTRACT_ASSET_MANAGEMENT_URI='http://127.0.0.1:19089' }
    $env:KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19392'
    $arguments = @("-Xmx$($spec.heap)m",'-XX:MaxMetaspaceSize=192m','-XX:MaxDirectMemorySize=48m','-XX:ReservedCodeCacheSize=48m',
        '-jar',('"'+$jar+'"'),'--server.address=127.0.0.1',"--server.port=$($spec.port)",
        '--spring.profiles.active=acceptance','--opencrm.security.mode=jwt','--spring.cache.type=none',
        '--opencrm.jev.enabled=false','--opencrm.jev.transport=noop','--opencrm.jev.quote-tier-suggestion.enabled=false',
        '--opencrm.jev.discount-tier-shadow.enabled=false','--approvals.legacy-local-read-enabled=true',
        '--spring.data.redis.host=127.0.0.1','--spring.data.redis.port=19379','--management.endpoint.health.probes.enabled=true',
        '--logging.level.com.opencrm=INFO','--logging.level.org.hibernate.SQL=WARN','--spring.jpa.show-sql=false') + $spec.extra
    if ($IncludeContract -and $Name -eq 'quote-service') {
        $arguments += '--services.contract-asset-management.url=http://127.0.0.1:19089'
    }
    if ($spec.schema) {
        $arguments += @("--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/$($manifest.database)?currentSchema=$($spec.schema)",
            '--spring.datasource.username=postgres','--spring.datasource.hikari.maximum-pool-size=3','--spring.datasource.hikari.minimum-idle=1',
            "--spring.jpa.properties.hibernate.default_schema=$($spec.schema)","--spring.flyway.default-schema=$($spec.schema)","--spring.flyway.schemas=$($spec.schema)")
    }
    $ramp = if ($RampDisabled) { 'false' } else { 'true' }
    if ($Name -in @('quote-service','pricing-service')) { $arguments += "--opencrm.pricing.ramp.enabled=$ramp" }
    # Only a fresh, manifest-owned database is replayed to this isolated broker; no shared backlog is enabled.
    if ($Name -eq 'quote-service' -or ($IncludeContract -and $Name -eq 'contract-asset-management')) { $arguments += @('--opencrm.outbox.relay.enabled=true','--opencrm.outbox.relay.poll-interval=PT1S') }
    else { $arguments += '--opencrm.outbox.relay.enabled=false' }
    if ($Name -ne 'approval-routing' -and !($IncludeContract -and $Name -eq 'contract-asset-management')) { $arguments += '--spring.kafka.listener.auto-startup=false' }
    $attempt = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')
    $process = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $artifacts "$Name-$attempt.stdout.log") -RedirectStandardError (Join-Path $artifacts "$Name-$attempt.stderr.log")
    $manifest.processes += @{name=$Name; pid=$process.Id; startUtc=$process.StartTime.ToUniversalTime().ToString('O'); jar=$jar; port=$spec.port; rampEnabled=(!$RampDisabled)}
    Save-LifecycleManifest
    $ready = $false
    for ($count=0; $count -lt 45; $count++) {
        if ($process.HasExited) { throw "$Name exited; inspect its owned logs." }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$($spec.port)/actuator/health/readiness" -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready=$true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw "$Name did not reach application readiness. This is not aggregate dependency health." }
    Assert-LifecycleMemory 0.8
    Write-Output "Owned $Name ready on $($spec.port), PID $($process.Id)."
}
if ($Action -eq 'prepare') {
    if (Test-Path -LiteralPath $manifestPath) { throw 'Existing lifecycle manifest: inspect/resume explicitly, do not overwrite ownership.' }
    $source = (Resolve-Path -LiteralPath $ArtifactSource).Path
    & git -C $source diff --quiet HEAD -- backend
    if ($LASTEXITCODE -ne 0) { throw 'Artifact source has uncommitted backend changes.' }
    $gatewaySource = if ($GatewayArtifactSource) { (Resolve-Path -LiteralPath $GatewayArtifactSource).Path } else { $source }
    & git -C $gatewaySource diff --quiet HEAD -- backend/services/gateway-service
    if ($LASTEXITCODE -ne 0) { throw 'Gateway artifact source has uncommitted gateway changes; commit its verified patch first.' }
    $changed = & git -C $root diff --name-only HEAD (& git -C $source rev-parse HEAD) -- backend
    if ($LASTEXITCODE -ne 0) { throw 'Artifact source comparison failed.' }
    foreach ($file in $changed) {
        if (!$GatewayArtifactSource -or !$file.StartsWith('backend/services/gateway-service/')) { throw "Unverified artifact source difference: $file" }
    }
    $files = & git -C $root ls-files backend
    $sources = foreach ($file in $files) {
        $targetHash = (Get-FileHash -LiteralPath (Join-Path $root $file) -Algorithm SHA256).Hash
        $fileSource = if ($file.StartsWith('backend/services/gateway-service/')) { $gatewaySource } else { $source }
        $sourceHash = (Get-FileHash -LiteralPath (Join-Path $fileSource $file) -Algorithm SHA256).Hash
        if ($targetHash -ne $sourceHash) { throw "Backend source hash mismatch: $file" }
        @{path=$file; sha256=$targetHash; sourceWorktree=$fileSource}
    }
    New-Item -ItemType Directory -Path (Join-Path $artifacts 'jars') -Force | Out-Null
    $jars = foreach ($name in $specs.Keys) {
        $jarSource = if ($name -eq 'gateway-service') { $gatewaySource } else { $source }
        $sourceJar = Join-Path $jarSource "backend/services/$name/target/$name-1.0.0-SNAPSHOT.jar"
        $targetJar = Join-Path $artifacts "jars/$name.jar"
        Copy-Item -LiteralPath $sourceJar -Destination $targetJar
        @{name=$name; sha256=(Get-FileHash -LiteralPath $targetJar -Algorithm SHA256).Hash; sourceWorktree=$jarSource; sourceHead=(& git -C $jarSource rev-parse HEAD)}
    }
    @{sourceWorktree=$source; sourceHead=(& git -C $source rev-parse HEAD); gatewaySourceWorktree=$gatewaySource; gatewaySourceHead=(& git -C $gatewaySource rev-parse HEAD); targetHead=(& git -C $root rev-parse HEAD); sources=$sources; jars=$jars} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $artifacts 'artifact-provenance.json')
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
    $manifest = @{worktree=$root; database="ramp_next_acceptance_$stamp"; databaseCreatedByWave=$false; broker=$null; redis=$null; processes=@(); artifacts=$jars; includeContract=[bool]$IncludeContract}
    Save-LifecycleManifest
    Write-Output "Verified $($sources.Count) backend file hashes and $($jars.Count) reusable clean-build jars. Runtime has not started."
    return
}
if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Run prepare first.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -AsHashtable
if ($manifest.worktree -ne $root -or $manifest.database -notmatch '^ramp_next_acceptance_[0-9]{14}$') { throw 'Ownership manifest mismatch.' }
if ($Action -in @('start-broker','start') -and [bool]$manifest.includeContract -ne [bool]$IncludeContract) {
    throw 'Contract acceptance mode differs from the prepared artifact manifest; refusing startup.'
}
if ($Action -eq 'start-broker') {
    Assert-LifecycleMemory 2.5
    if ($manifest.broker -or $manifest.redis) { throw 'An owned container is already recorded.' }
    if (Get-NetTCPConnection -State Listen -LocalPort 19392 -ErrorAction SilentlyContinue) { throw 'Broker port occupied.' }
    if (Get-NetTCPConnection -State Listen -LocalPort 19379 -ErrorAction SilentlyContinue) { throw 'Redis acceptance port occupied.' }
    $exists = & docker exec postgres psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$($manifest.database)'"
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL inspection failed.' }
    if (($exists -join '').Trim() -eq '1') { throw 'Database exists without creation ownership; refusing reuse.' }
    & docker exec postgres psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $($manifest.database)"
    if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }
    $manifest.databaseCreatedByWave=$true
    Save-LifecycleManifest
    $name = "ramp-next-$($manifest.database.Replace('ramp_next_acceptance_',''))"
    # Redis Pub/Sub is not scoped by database number. A separate instance prevents synthetic
    # QuoteAccepted messages from reaching any unrelated development CRM subscriber.
    $redisId = & docker run -d --pull=never --name "$name-redis" --memory=96m --cpus=1 `
        --label "opencrm.acceptance.worktree=$root" --label "opencrm.acceptance.database=$($manifest.database)" `
        -p 127.0.0.1:19379:6379 redis:8 redis-server --maxmemory 64mb --maxmemory-policy allkeys-lru --save '' --appendonly no
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Redis launch failed.' }
    $manifest.redis=@{id=($redisId -join '').Trim(); name="$name-redis"}
    Save-LifecycleManifest
    $id = & docker run -d --pull=never --name $name --memory=512m --cpus=1 --label "opencrm.acceptance.worktree=$root" --label "opencrm.acceptance.database=$($manifest.database)" `
        -p 127.0.0.1:19392:9092 -e 'KAFKA_HEAP_OPTS=-Xmx256m -Xms128m' -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller `
        -e KAFKA_LISTENERS=INTERNAL://:29092,EXTERNAL://:9092,CONTROLLER://:9093 -e KAFKA_ADVERTISED_LISTENERS=INTERNAL://localhost:29092,EXTERNAL://127.0.0.1:19392 `
        -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL `
        -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 `
        -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 `
        -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true apache/kafka:4.3.1
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Kafka launch failed.' }
    $manifest.broker=@{id=($id -join '').Trim(); name=$name}
    Save-LifecycleManifest
    for ($count=0; $count -lt 30; $count++) {
        & docker exec -e 'KAFKA_HEAP_OPTS=-Xmx96m -Xms32m' $manifest.broker.id /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --list 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Output 'Owned Kafka ready; only the new synthetic database is eligible for relay.'; return }
        Start-Sleep -Seconds 2
    }
    throw 'Owned Kafka not ready; stop-broker after inspecting logs.'
}
if ($Action -eq 'start') {
    if (!$manifest.databaseCreatedByWave -or !$manifest.broker) { throw 'Owned database and broker are required.' }
    foreach ($name in $Services.Split(',', [StringSplitOptions]::RemoveEmptyEntries)) { Start-LifecycleService $name }
    return
}
if ($Action -eq 'stop') {
    $selected = if ($Services) { @($Services.Split(',')) } else { @($manifest.processes | ForEach-Object name) }
    foreach ($owned in @($manifest.processes)) {
        if ($owned.name -notin $selected) { continue }
        $live = Assert-OwnedLifecycleProcess $owned
        if ($live) {
            Stop-Process -Id $owned.pid
            if (!$live.WaitForExit(10000)) { throw 'Owned process did not exit.' }
        }
        $manifest.processes=@($manifest.processes | Where-Object pid -ne $owned.pid)
        Save-LifecycleManifest
        Write-Output "Stopped owned $($owned.name)."
    }
    return
}
if ($Action -eq 'stop-broker') {
    if (@($manifest.processes).Count) { throw 'Stop owned JVMs before broker cleanup.' }
    foreach ($containerType in @('broker','redis')) {
        $ownedContainer=$manifest[$containerType]
        if (!$ownedContainer) { continue }
        $inspection = & docker inspect $ownedContainer.id | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $inspection.Config.Labels.'opencrm.acceptance.worktree' -ne $root -or
            $inspection.Config.Labels.'opencrm.acceptance.database' -ne $manifest.database) { throw 'Container ownership mismatch; refusing cleanup.' }
        & docker logs $ownedContainer.id 2>&1 | Set-Content -LiteralPath (Join-Path $artifacts "$containerType.log")
        & docker stop $ownedContainer.id | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Owned container did not stop.' }
        & docker rm $ownedContainer.id | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Owned container cleanup failed.' }
        $manifest[$containerType]=$null
        Save-LifecycleManifest
    }
    Write-Output 'Owned broker/Redis removed; synthetic evidence database retained. Existing services were preserved.'
}
