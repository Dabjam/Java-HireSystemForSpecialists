$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Запуск HR System (Java + Spring Data JPA + PostgreSQL)    " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$javaExe = "C:\Program Files\Java\jdk-21.0.12.1\bin\java.exe"
if (!(Test-Path $javaExe)) {
    $javaExe = "java"
}

Write-Host "`n[1/2] Проверка базы данных PostgreSQL (Docker порт 5434)..." -ForegroundColor Yellow
docker-compose up -d

Write-Host "`n[2/2] Запуск приложения HR System..." -ForegroundColor Green
& $javaExe -XX:+UseSerialGC -Xmx384m -jar target\hr-system-1.0.0-SNAPSHOT.jar
