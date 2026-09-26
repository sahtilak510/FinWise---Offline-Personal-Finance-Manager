@echo off
REM Offline Personal Finance Manager - Start Script for Windows
setlocal

REM Always run from this script's directory. This is required when the path
REM contains spaces or the script is launched from a shortcut/file explorer.
pushd "%~dp0"
if %ERRORLEVEL% NEQ 0 (
    echo Error: Could not access the project directory.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo Starting Offline Personal Finance Manager
echo ==========================================
echo.

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed!
    echo Please install Java 17 or higher.
    pause
    exit /b 1
)

echo Java is installed
java -version
echo.

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Maven is not installed!
    echo Please install Maven 3.6 or higher.
    pause
    exit /b 1
)

echo Maven is installed
echo.

REM Create necessary directories
if not exist data mkdir data
if not exist logs mkdir logs

echo Creating necessary directories...
echo Created: data/ (for database)
echo Created: logs/ (for application logs)
echo.

REM Stop existing instances so Maven can replace target\ and free the SQLite
REM database files. Both the packaged JAR and "mvn spring-boot:run" instances
REM must be stopped: spring-boot:run keeps target\classes and any
REM target\*.db file locked, which makes "mvn clean" fail.
echo Stopping any existing application instance...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$self = $PID; Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { $_.ProcessId -ne $self -and $_.CommandLine -and ($_.CommandLine -match 'com\.finance\.app\.OfflineFinanceApplication' -or $_.CommandLine -match 'offline-finance-manager-1\.0\.0\.jar') } | ForEach-Object { Write-Host ('  Stopping PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 500; Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { $_.CommandLine -and ($_.CommandLine -match 'com\.finance\.app\.OfflineFinanceApplication' -or $_.CommandLine -match 'offline-finance-manager-1\.0\.0\.jar') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
timeout /t 1 /nobreak >nul

REM Build the project
echo Building the project...
call mvn clean install -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Build successful!
echo.

REM Run the application
echo Starting the application...
echo Access the application at: http://localhost:8080
echo.
echo Default credentials:
echo   Username: admin
echo   Password: admin123
echo.
echo Press Ctrl+C to stop the application
echo.

REM Maven may finish writing the executable JAR just after reporting the
REM lifecycle result on slower Windows/OneDrive filesystems.
set "JAR_PATH=%CD%\target\offline-finance-manager-1.0.0.jar"
set /a JAR_WAIT=0
:wait_for_jar
if exist "%JAR_PATH%" goto jar_ready
if %JAR_WAIT% GEQ 30 (
    echo Error: The application JAR was not created by the build.
    popd
    pause
    exit /b 1
)
set /a JAR_WAIT+=1
timeout /t 1 /nobreak >nul
goto wait_for_jar
:jar_ready

REM Start Java in the background, then open the browser only after port 8080
REM accepts connections. This avoids an intermittent connection-refused page.
start "Offline Finance Manager" /b java -jar "%JAR_PATH%" --server.address=0.0.0.0 --server.port=8080

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$deadline = (Get-Date).AddSeconds(30); while ((Get-Date) -lt $deadline) { try { $client = New-Object Net.Sockets.TcpClient('localhost', 8080); $client.Close(); Start-Process 'http://localhost:8080'; exit 0 } catch { Start-Sleep -Milliseconds 500 } }; Write-Host 'Error: The application did not start on port 8080 within 30 seconds.'; exit 1"

if %ERRORLEVEL% NEQ 0 (
    echo Check the console output or logs\finance.log for the startup error.
    popd
    pause
    exit /b 1
)

echo.
echo Application is ready. Leave this window open while using the application.
popd
pause
