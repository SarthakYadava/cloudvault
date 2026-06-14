$ErrorActionPreference = "Stop"

$postgresRoot = Join-Path $env:LOCALAPPDATA "Programs\PostgreSQL\18\pgsql"
$dataDirectory = Join-Path $env:LOCALAPPDATA "PostgreSQL\18\data"
$logFile = Join-Path $env:LOCALAPPDATA "PostgreSQL\18\postgresql.log"
$pgCtl = Join-Path $postgresRoot "bin\pg_ctl.exe"
$pgIsReady = Join-Path $postgresRoot "bin\pg_isready.exe"

if (-not (Test-Path -LiteralPath $pgCtl)) {
    throw "Portable PostgreSQL 18 was not found at $postgresRoot."
}

& $pgCtl -D $dataDirectory status *> $null
if ($LASTEXITCODE -ne 0) {
    & $pgCtl -D $dataDirectory -l $logFile -w start
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL could not be started. Check $logFile."
    }
}

& $pgIsReady -h localhost -p 5432
