$ErrorActionPreference = "Stop"

$postgresRoot = Join-Path $env:LOCALAPPDATA "Programs\PostgreSQL\18\pgsql"
$dataDirectory = Join-Path $env:LOCALAPPDATA "PostgreSQL\18\data"
$pgCtl = Join-Path $postgresRoot "bin\pg_ctl.exe"

if (-not (Test-Path -LiteralPath $pgCtl)) {
    throw "Portable PostgreSQL 18 was not found at $postgresRoot."
}

& $pgCtl -D $dataDirectory status *> $null
if ($LASTEXITCODE -eq 0) {
    & $pgCtl -D $dataDirectory -w stop
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL could not be stopped."
    }
} else {
    Write-Host "PostgreSQL is already stopped."
}
