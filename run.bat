@echo off
title HR-System CLI
echo Starting PostgreSQL container...
docker-compose up -d
echo Starting HR-System application...
java -XX:+UseSerialGC -Xmx384m -jar target\hr-system-1.0.0-SNAPSHOT.jar
pause
