#Requires -Version 5.1
<#
.SYNOPSIS
  Quick gateway smoke against CRM routes (staging or local).

.DESCRIPTION
  Windows twin of staging-gateway-smoke.sh. Exercises CRM GET paths via the gateway
  (frontend/libs/crm → :8080), not crm-service direct :8081.

.PARAMETER GatewayBase
  Base URL for the API gateway (default http://localhost:8080).

.PARAMETER Token
  Optional Bearer token for secured staging gateways.

.PARAMETER CheckErp
  If set, assert GET /api/v1/erp/costs/bulk returns HTTP 501 (stub), not 404.

.EXAMPLE
  ./staging-gateway-smoke.ps1 -GatewayBase 'https://your-staging-gateway.azurewebsites.net'

.EXAMPLE
  ./staging-gateway-smoke.ps1 -GatewayBase 'http://localhost:8080' -CheckErp
#>
param(
    [string] $GatewayBase = 'http://localhost:8080',
    [string] $Token = '',
    [switch] $CheckErp
)

$ErrorActionPreference = 'Stop'
$base = $GatewayBase.TrimEnd('/')
$script:FailCount = 0
$script:PassCount = 0

function Get-SmokeHeaders {
    $h = @{ Accept = 'application/json' }
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    return $h
}

function Invoke-JsonGet([string]$Path) {
    $uri = "$base$Path"
    Write-Host "GET $uri"
    try {
        Invoke-RestMethod -Uri $uri -Method Get -Headers (Get-SmokeHeaders) | Out-Null
        Write-Host "  OK"
        $script:PassCount++
    }
    catch {
        Write-Host "FAIL  $Path — $($_.Exception.Message)" -ForegroundColor Red
        $script:FailCount++
    }
}

function Assert-Status([string]$Path, [int]$Want) {
    $uri = "$base$Path"
    Write-Host "GET $uri (expect $Want)"
    try {
        $resp = Invoke-WebRequest -Uri $uri -Method Get -Headers (Get-SmokeHeaders) -SkipHttpErrorCheck
        if ($resp.StatusCode -eq $Want) {
            Write-Host "  OK  HTTP $($resp.StatusCode)"
            $script:PassCount++
        }
        else {
            Write-Host "FAIL  HTTP $($resp.StatusCode) (wanted $Want) for $Path" -ForegroundColor Red
            $script:FailCount++
        }
    }
    catch {
        # Windows PowerShell 5.1 may not support -SkipHttpErrorCheck
        $status = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($null -ne $status -and $status -eq $Want) {
            Write-Host "  OK  HTTP $status"
            $script:PassCount++
        }
        else {
            Write-Host "FAIL  $Path — $($_.Exception.Message)" -ForegroundColor Red
            $script:FailCount++
        }
    }
}

Write-Host "=== CRM gateway smoke ($base) ==="
Invoke-JsonGet '/api/v1/deals/pipeline'
Invoke-JsonGet '/api/v1/deals?page=0&size=5'
Invoke-JsonGet '/api/v1/leads?page=0&size=5'
Invoke-JsonGet '/api/v1/contacts?page=0&size=5'
Invoke-JsonGet '/api/v1/organizations?page=0&size=5'

# API-only routes (optional): uncomment to verify server exposes them
# Invoke-JsonGet '/api/v1/leads/stats'
# Invoke-JsonGet '/api/v1/organizations/1/contacts?page=0&size=5'

if ($CheckErp) {
    Write-Host '--- optional ERP stub ---'
    Assert-Status '/api/v1/erp/costs/bulk' 501
}

Write-Host ''
if ($script:FailCount -gt 0) {
    Write-Host "FAILED — $($script:FailCount) check(s) failed, $($script:PassCount) passed." -ForegroundColor Red
    Write-Host 'Quote-accept → deal WON + duplicate idempotency: docs/scripts/staging-quote-accept-idempotency.md'
    exit 1
}

Write-Host "OK — basic CRM reads succeeded ($($script:PassCount) checks)."
Write-Host 'Next: quote accept → deal WON + duplicate publish (docs/scripts/staging-quote-accept-idempotency.md).'
exit 0
