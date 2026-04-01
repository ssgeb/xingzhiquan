param()

$ErrorActionPreference = "Stop"

$KafkaRoot = "D:\\ruanjian\\Kafka"
$KafkaHome = Join-Path $KafkaRoot "kafka_2.13-4.2.0"
$PidFile = Join-Path $KafkaRoot "kafka.pid"

if (Test-Path $PidFile) {
    $pidText = Get-Content -Path $PidFile -Raw
    $pid = 0
    if ([int]::TryParse($pidText.Trim(), [ref]$pid) -and $pid -gt 0) {
        try {
            Stop-Process -Id $pid -Force
            Write-Host "Stopped Kafka process $pid"
        } catch {
            Write-Host "Kafka process $pid is already stopped."
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    return
}

$listener = Get-NetTCPConnection -LocalPort 9092 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    Stop-Process -Id $listener.OwningProcess -Force
    Write-Host "Stopped Kafka process $($listener.OwningProcess)"
    return
}

if (Test-Path (Join-Path $KafkaHome "bin\windows\kafka-server-stop.bat")) {
    & (Join-Path $KafkaHome "bin\windows\kafka-server-stop.bat") | Out-Host
}

Write-Host "Kafka is not running."
