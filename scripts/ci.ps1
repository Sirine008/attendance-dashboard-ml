<#
.SYNOPSIS
  CI locale : build + tests des trois modules (backend, ml-service, frontend).
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\ci.ps1
  powershell -ExecutionPolicy Bypass -File scripts\ci.ps1 -Only backend
#>
param(
    [ValidateSet("all", "backend", "ml", "frontend")]
    [string]$Only = "all"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"

function Step($name, [scriptblock]$block) {
    Write-Host "==> $name" -ForegroundColor Cyan
    & $block
    if ($LASTEXITCODE -ne 0) { throw "Échec : $name (code $LASTEXITCODE)" }
}

if ($Only -in "all", "backend") {
    Push-Location "$root\backend"
    try { Step "backend : mvn verify (Testcontainers => Docker requis)" { .\mvnw.cmd -B -q verify } } finally { Pop-Location }
}
if ($Only -in "all", "ml") {
    Push-Location "$root\ml-service"
    try {
        if (-not (Test-Path ".venv")) {
            Step "ml : venv" { if ($PythonExe -eq "py -3.11") { py -3.11 -m venv .venv } else { & $PythonExe -m venv .venv } }
        }
        Step "ml : dépendances" { .\.venv\Scripts\python -m pip install -q -r requirements-dev.txt }
        Step "ml : pytest" { .\.venv\Scripts\python -m pytest }
    } finally { Pop-Location }
}
if ($Only -in "all", "frontend") {
    Push-Location "$root\frontend"
    try {
        if (-not (Test-Path "node_modules")) { Step "frontend : npm ci" { npm ci } }
        Step "frontend : build" { npx ng build }
        Step "frontend : tests" { npx ng test --watch=false }
    } finally { Pop-Location }
}
Write-Host "CI locale OK" -ForegroundColor Green
