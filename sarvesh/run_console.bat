@echo off
title EcoDrop Console Application
cd /d "%~dp0"
echo =========================================================
echo   Starting EcoDrop Console App (Pure Java OOP + DBMS)
echo =========================================================

java -cp "out;lib/*" ecodrop.ConsoleApp
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Notice] Compiling Java classes with your local Java version...
    javac -d out src\ecodrop\*.java
    java -cp "out;lib/*" ecodrop.ConsoleApp
)
pause
