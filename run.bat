@echo off

:: --- Load API keys from .env ---
for /f "usebackq tokens=1,* delims==" %%A in (".env") do set %%A=%%B

:: --- Free port 8080 if already in use ---
echo Checking port 8080...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING 2^>nul') do (
    echo Killing process %%p on port 8080...
    taskkill /PID %%p /F >nul 2>&1
)

:: --- Build and run with Maven ---
echo Building and starting AI Suite...
mvnw.cmd spring-boot:run

pause
