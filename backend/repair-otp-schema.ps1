param([switch]$Apply, [string]$DatabaseUser = 'root', [string]$MySqlPath = $env:MYSQL_EXE)
$ErrorActionPreference = 'Stop'
$mysql = if ($MySqlPath -and (Test-Path -LiteralPath $MySqlPath)) { $MySqlPath }
         elseif (Get-Command mysql.exe -ErrorAction SilentlyContinue) { (Get-Command mysql.exe).Source }
         elseif (Test-Path 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe') { 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
         else { throw 'MySQL client (mysql.exe) was not found in PATH or standard directories. Specify -MySqlPath or set $env:MYSQL_EXE.' }
$secret = Read-Host 'MySQL password (never saved)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    function Invoke-SchemaSql([string]$Sql) {
        $start = [System.Diagnostics.ProcessStartInfo]::new()
        $start.FileName = $mysql
        $start.Arguments = "--host=127.0.0.1 --port=3306 --user=$DatabaseUser --database=zaylope --batch --skip-column-names"
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardInput = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $start.EnvironmentVariables['MYSQL_PWD'] = $password
        $process = [System.Diagnostics.Process]::Start($start)
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        $result = $process.StandardOutput.ReadToEnd()
        $failure = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw $failure }
        $process.Dispose()
        return $result.Trim()
    }
    if ($DatabaseUser -notmatch '^[a-zA-Z0-9_]+$') { throw 'Use a simple MySQL account name.' }
    if ((Invoke-SchemaSql 'SELECT DATABASE();') -ne 'zaylope') { throw 'Unexpected database. No changes made.' }
    Write-Output 'Confirmed target: 127.0.0.1:3306/zaylope'
    Write-Output (Invoke-SchemaSql "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zaylope' AND TABLE_NAME IN ('applicants','applicant_access_sessions','applicant_verification_codes','otp_abuse_limits') ORDER BY TABLE_NAME,ORDINAL_POSITION;")
    Write-Output (Invoke-SchemaSql "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='zaylope' AND TABLE_NAME IN ('applicants','applicant_access_sessions','applicant_verification_codes','otp_abuse_limits') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;")
    $history = Invoke-SchemaSql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='zaylope' AND TABLE_NAME='flyway_schema_history';"
    if ($history -eq '1') { Write-Output (Invoke-SchemaSql 'SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;') }
    else { Write-Output 'No Flyway history table. Automatic migrations remain disabled.' }
    $parent = Invoke-SchemaSql "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zaylope' AND TABLE_NAME='applicants' AND COLUMN_NAME='applicant_id';"
    if ($parent -ne 'bigint unsigned') { throw 'Applicant primary-key type differs from V7. Review before repair.' }
    $sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/db/manual/missing-otp-tables.sql') -Raw
    foreach ($table in @('applicant_verification_codes', 'otp_abuse_limits')) {
        $exists = Invoke-SchemaSql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='zaylope' AND TABLE_NAME='$table';"
        if ($exists -eq '1') { Write-Output "$table already exists; left unchanged."; continue }
        if (!$Apply) { Write-Output "$table is missing; rerun with -Apply to create it."; continue }
        $statement = [regex]::Match($sql, "(?s)CREATE TABLE $table \(.*?;").Value
        if (!$statement) { throw 'Repair SQL is missing the required CREATE statement.' }
        Invoke-SchemaSql "SET SESSION lock_wait_timeout=5; $statement" | Out-Null
        Write-Output "Created $table. No existing records changed."
    }
    if ($Apply) {
        Invoke-SchemaSql 'SELECT challenge_id,applicant_id,verification_channel,code_hash,created_at,expires_at,resend_available_at,send_window_started_at,send_count,attempt_count,consumed_at,issuance_id,destination_hash,purpose,challenge_status,last_sent_at,row_version FROM applicant_verification_codes LIMIT 0; SELECT bucket_hash,window_started_at,last_sent_at,request_count FROM otp_abuse_limits LIMIT 0;' | Out-Null
        Write-Output 'All current OTP query columns are available. Existing tables were never altered.'
    }
} finally {
    $password = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secret.Dispose()
}
