param(
    [string]$KafkaHome = "D:\\ruanjian\\Kafka\\kafka_2.13-4.2.0",
    [string]$KafkaRoot = "D:\\ruanjian\\Kafka"
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DataDir = Join-Path $KafkaRoot "data"
$LogsDir = Join-Path $KafkaRoot "logs"
$PidFile = Join-Path $KafkaRoot "kafka.pid"
$OutLog = Join-Path $KafkaRoot "kafka.out.log"
$ErrLog = Join-Path $KafkaRoot "kafka.err.log"

if (-not (Test-Path $KafkaHome)) {
    throw "Kafka home not found: $KafkaHome"
}

$StorageTool = Join-Path $KafkaHome "bin\windows\kafka-storage.bat"
$ServerTool = Join-Path $KafkaHome "bin\windows\kafka-server-start.bat"
$TopicsTool = Join-Path $KafkaHome "bin\windows\kafka-topics.bat"
$ConfigFile = Join-Path $KafkaHome "config\server-draft.properties"

New-Item -ItemType Directory -Force -Path $KafkaRoot, $DataDir, $LogsDir | Out-Null

$metaProperties = Join-Path $DataDir "meta.properties"
if (-not (Test-Path $metaProperties)) {
    $clusterId = (& $StorageTool random-uuid).Trim()
    Write-Host "Formatting Kafka storage with cluster id $clusterId"
    & $StorageTool format --standalone -t $clusterId -c $ConfigFile --ignore-formatted | Out-Host
}

if (Get-NetTCPConnection -LocalPort 9092 -ErrorAction SilentlyContinue) {
    Write-Host "Kafka is already listening on 9092."
} else {
    if (Test-Path $OutLog) { Remove-Item $OutLog -Force }
    if (Test-Path $ErrLog) { Remove-Item $ErrLog -Force }
    if (Test-Path $PidFile) { Remove-Item $PidFile -Force }

    Write-Host "Starting Kafka broker from $KafkaHome..."
    $process = Start-Process -FilePath $ServerTool -ArgumentList @($ConfigFile) -WorkingDirectory $KafkaHome -RedirectStandardOutput $OutLog -RedirectStandardError $ErrLog -PassThru

    $ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        $listener = Get-NetTCPConnection -LocalPort 9092 -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($listener) {
            Set-Content -Path $PidFile -Value $listener.OwningProcess -Encoding ASCII
            $ready = $true
            break
        }
        if ($process.HasExited) {
            break
        }
    }

    if (-not $ready) {
        Write-Host "Kafka did not become ready. Check $OutLog and $ErrLog."
        exit 1
    }
}

$waitReady = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        & $TopicsTool --bootstrap-server localhost:9092 --list | Out-Null
        $waitReady = $true
        break
    } catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $waitReady) {
    throw "Kafka broker is listening, but topic admin commands are not ready yet."
}

$topics = @(
    "xingzhiquan-counter-events",
    "xingzhiquan-canal-outbox"
)

foreach ($topic in $topics) {
    & $TopicsTool --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 3 --replication-factor 1 | Out-Host
}

Write-Host "Kafka is ready on localhost:9092"
Write-Host "Logs: $OutLog"
Write-Host "Stop with: powershell -ExecutionPolicy Bypass -File `"$PSScriptRoot\stop-kafka.ps1`""
