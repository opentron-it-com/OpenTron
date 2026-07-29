@echo off
REM OpenTron Benchmark Suite - Windows Launcher

setlocal enabledelayedexpansion
cd /d "%~dp0"

set COMMAND=%1
if "%COMMAND%"=="" set COMMAND=start

echo.
echo 🚀 OpenTron Benchmark Suite
echo ============================
echo.

REM Check and setup Java if needed
call :setup_java
if errorlevel 1 (
    echo [ERROR] Java setup failed
    exit /b 1
)

if "%COMMAND%"=="start" (
    echo Pulling latest images...
    docker-compose pull
    
    echo.
    echo Building Docker images...
    docker-compose build
    if errorlevel 1 (
        echo [ERROR] Docker build failed
        exit /b 1
    )
    
    echo.
    echo Starting benchmark services...
    docker-compose up -d
    if errorlevel 1 (
        echo [ERROR] Failed to start services
        exit /b 1
    )
    
    echo.
    echo Waiting for services to become healthy...
    timeout /t 30 /nobreak
    
    echo.
    echo Services are running. Benchmarks will run automatically.
    echo.
    call :show_dashboards
) else if "%COMMAND%"=="stop" (
    echo Stopping services...
    docker-compose down
    echo Services stopped.
) else if "%COMMAND%"=="logs" (
    docker-compose logs -f
) else if "%COMMAND%"=="status" (
    docker-compose ps
) else if "%COMMAND%"=="report" (
    echo Generating benchmark report...
    python analyze_results.py benchmark_results.json
    echo Report generated: BENCHMARK_REPORT.md
) else if "%COMMAND%"=="clean" (
    echo Removing services and volumes...
    docker-compose down -v
    del /q benchmark_results.json 2>nul
    del /q BENCHMARK_REPORT.md 2>nul
    echo Cleanup complete.
) else if "%COMMAND%"=="help" (
    call :show_help
) else (
    echo Unknown command: %COMMAND%
    call :show_help
    exit /b 1
)

endlocal
exit /b 0

:setup_java
REM Check if JAVA_HOME is already set
if not "%JAVA_HOME%"=="" (
    echo [INFO] Using JAVA_HOME: %JAVA_HOME%
    exit /b 0
)

REM Try to find Java 21 in common locations
set JAVA_PATHS=^
    "C:\Program Files\Java\jdk-21"^
    "C:\Program Files\Java\jdk21"^
    "C:\Program Files (x86)\Java\jdk-21"^
    "C:\Users\%USERNAME%\Documents\jdk-21.0.11"^
    "C:\Users\%USERNAME%\Documents\jdk-21"^
    "C:\jdk-21"

for %%P in (%JAVA_PATHS%) do (
    if exist "%%P\bin\java.exe" (
        set "JAVA_HOME=%%P"
        set "PATH=!JAVA_HOME!\bin;!PATH!"
        echo [INFO] Found Java 21 at: !JAVA_HOME!
        exit /b 0
    )
)

REM If not found, ask user
echo.
echo [WARN] Java 21 not found in standard locations
echo Please enter your Java 21 installation path (e.g., C:\jdk-21):
set /p JAVA_HOME="Java Home: "

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java not found at: %JAVA_HOME%
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] Java Home set to: %JAVA_HOME%
exit /b 0

:show_dashboards
echo.
echo 📊 Monitoring Dashboards
echo ========================
echo.
echo Grafana:
echo   URL: http://localhost:3000
echo   User: admin
echo   Password: admin
echo.
echo Prometheus:
echo   URL: http://localhost:9090
echo.
exit /b 0

:show_help
echo.
echo Usage: %0 [COMMAND]
echo.
echo Commands:
echo   start       Start benchmark services (default)
echo   stop        Stop all services
echo   logs        Show service logs
echo   status      Show service status
echo   report      Generate report from results
echo   clean       Stop services and remove volumes
echo   help        Show this help message
echo.
exit /b 0
