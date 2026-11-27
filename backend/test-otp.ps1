param(
    [switch]$Browser,
    [string]$TestClasses,
    [string]$MySqlBin = $(
        if ($env:MYSQL_BIN -and (Test-Path $env:MYSQL_BIN)) { $env:MYSQL_BIN }
        elseif (Get-Command mysqld.exe -ErrorAction SilentlyContinue) { Split-Path (Get-Command mysqld.exe).Source }
        elseif (Test-Path 'C:\Program Files\MySQL\MySQL Server 8.0\bin') { 'C:\Program Files\MySQL\MySQL Server 8.0\bin' }
        else { $null }
    )
)
$ErrorActionPreference = 'Stop'
if (!$MySqlBin -or !(Test-Path -LiteralPath $MySqlBin)) { throw 'Provide -MySqlBin pointing to an installed MySQL bin directory, or add MySQL to PATH / $env:MYSQL_BIN.' }
$backendRoot = $PSScriptRoot
$testRoot = Join-Path $backendRoot 'target'
# Mutable harness-owned state must survive Maven clean; never publish this directory.
$dataRoot = Join-Path $backendRoot '.local-test-data/otp-mysql-data'
$marker = Join-Path $dataRoot '.zaylope-otp-test-owned'
$initFile = Join-Path $testRoot 'otp-mysql-init.sql'
$mysqlServer = Join-Path $MySqlBin 'mysqld.exe'
$mysqlClient = Join-Path $MySqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $MySqlBin 'mysqladmin.exe'
if (!(Test-Path -LiteralPath $mysqlServer)) { throw 'Provide -MySqlBin pointing to an installed MySQL 8 bin directory.' }
if (Get-NetTCPConnection -State Listen -LocalPort 3308 -ErrorAction SilentlyContinue) { throw 'Port 3308 is in use. This script will not touch an existing server.' }
if ($Browser -and !(Get-NetTCPConnection -State Listen -LocalPort 5180 -ErrorAction SilentlyContinue)) { throw 'Start the frontend at http://localhost:5180 before using -Browser.' }
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
if (Test-Path -LiteralPath $dataRoot) {
    if (!(Test-Path -LiteralPath $marker)) { throw 'Existing data directory is not marked as owned by this isolated OTP test harness.' }
} else {
    New-Item -ItemType Directory -Path $dataRoot | Out-Null
    & $mysqlServer --no-defaults --initialize-insecure "--datadir=$dataRoot" *> (Join-Path $testRoot 'otp-mysql-initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL initialization failed; see target/otp-mysql-initialize.log.' }
    'Isolated OTP test database only. No production/shared data.' | Set-Content -LiteralPath $marker
}
$randomSource = [Security.Cryptography.RandomNumberGenerator]::Create()
$randomBytes = New-Object byte[] 32
try {
    $randomSource.GetBytes($randomBytes)
    $password = [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
} finally {
    $randomSource.Dispose()
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
"ALTER USER 'root'@'localhost' IDENTIFIED BY '$password';`nCREATE DATABASE IF NOT EXISTS zaylope_otp_test;" | Set-Content -LiteralPath $initFile
$saved = @{}
foreach ($name in 'MYSQL_PWD','OTP_MYSQL_TEST','OTP_BROWSER_TEST','OTP_TEST_DB_PASSWORD') { $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$server = $null
try {
    $serverArguments = @('--no-defaults', "--datadir=`"$dataRoot`"", '--port=3308', '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-log-bin', "--init-file=`"$initFile`"", "--log-error=`"$(Join-Path $testRoot 'otp-mysql-error.log')`"")
    $server = Start-Process -FilePath $mysqlServer -ArgumentList $serverArguments -WindowStyle Hidden -PassThru
    $env:MYSQL_PWD = $password
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 500
        $portReady = Test-NetConnection -ComputerName 127.0.0.1 -Port 3308 -InformationLevel Quiet -WarningAction SilentlyContinue
        if ($portReady) { $ready = $true; break }
        if ($server.HasExited) { break }
    }
    if (!$ready) { throw 'Isolated MySQL did not start; inspect target/otp-mysql-error.log.' }
    & $mysqlClient --no-defaults --host=127.0.0.1 --port=3308 --user=root --execute='SELECT 1' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL did not accept the generated test credentials.' }
    # MySQL has consumed this one-time startup file; it is no longer needed.
    Remove-Item -LiteralPath $initFile
    $env:OTP_MYSQL_TEST = 'true'
    $env:OTP_BROWSER_TEST = if ($Browser) { 'true' } else { 'false' }
    $env:OTP_TEST_DB_PASSWORD = $password
    Push-Location $backendRoot
    try {
        if ($TestClasses) { & .\mvnw.cmd "-Dtest=$TestClasses" test }
        else { & .\mvnw.cmd verify }
        $testExit = $LASTEXITCODE
    } finally { Pop-Location }
    if ($testExit -ne 0) { throw 'OTP verification tests failed; inspect target/surefire-reports.' }
} finally {
    if ($server -and !$server.HasExited) {
        & $mysqlAdmin --no-defaults --host=127.0.0.1 --port=3308 --user=root shutdown *> $null
        if (!$server.WaitForExit(10000)) { Stop-Process -Id $server.Id }
    }
    if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    $password = $null
}

