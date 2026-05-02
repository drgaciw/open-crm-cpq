#Requires -Version 5.1
<#
.SYNOPSIS
  Quick gateway smoke against CRM routes (staging or local).

.PARAMETER GatewayBase
  Base URL for the API gateway (default http://localhost:8080).

.EXAMPLE
  ./staging-gateway-smoke.ps1 -GatewayBase 'https://your-staging-gateway.azurewebsites.net'
#>
param(
    [string] $GatewayBase = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$base = $GatewayBase.TrimEnd('/')

function Invoke-JsonGet([string]$Path) {
    $uri = "$base$Path"
    Write-Host "GET $uri"
    return Invoke-RestMethod -Uri $uri -Method Get -Headers @{ Accept = 'application/json' }
}

# Adjust if your gateway requires Bearer tokens in staging.
Write-Host "=== CRM gateway smoke ($base) ==="
Invoke-JsonGet '/api/v1/deals/pipeline' | Out-Null
Invoke-JsonGet '/api/v1/deals?page=0&size=5' | Out-Null
Invoke-JsonGet '/api/v1/leads?page=0&size=5' | Out-Null
Invoke-JsonGet '/api/v1/contacts?page=0&size=5' | Out-Null
Invoke-JsonGet '/api/v1/organizations?page=0&size=5' | Out-Null

# API-only routes (optional): uncomment to verify server exposes them
# Invoke-JsonGet '/api/v1/leads/stats' | ConvertTo-Json -Depth 5
# Invoke-JsonGet '/api/v1/organizations/1/contacts?page=0&size=5'

Write-Host "OK — basic CRM reads succeeded."
Write-Host "Next: manual quote accept -> deal WON with dealExternalId (see CRM_CUTOVER_CHECKLIST.md)."
