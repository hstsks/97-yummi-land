@echo off
chcp 65001 >nul
title [유미의 97랜드] 마인크래프트 자율 운영 서버
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\start-server.ps1"
pause
