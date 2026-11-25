@echo off
setlocal
cd /d "%~dp0"
title Sentinel Laptop Guard - installer

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo This installer needs Administrator rights.
    echo Right-click install_startup.bat and choose Run as administrator.
    pause
    exit /b 1
)

where python >nul 2>&1
if %errorlevel% neq 0 (
    echo Python 3 was not found on PATH.
    echo Install it from https://www.python.org/downloads/ and tick Add python.exe to PATH.
    pause
    exit /b 1
)

echo.
echo [1/4] Installing Python packages...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo Package installation failed.
    pause
    exit /b 1
)

echo.
echo [2/4] Configuration...
if not exist config.json (
    python laptop_agent.py --setup
)
if not exist config.json (
    echo config.json was not created. Run: python laptop_agent.py --setup
    pause
    exit /b 1
)

echo.
echo [3/4] Registering hidden auto-start at logon (runs elevated so failed logins can be detected)...
set "VBS=%~dp0start_hidden.vbs"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$q=[char]34; $a=New-ScheduledTaskAction -Execute 'wscript.exe' -Argument ($q+$env:VBS+$q); $t=New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME; $s=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Seconds 0); $p=New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest; Register-ScheduledTask -TaskName 'SentinelLaptopGuard' -Action $a -Trigger $t -Settings $s -Principal $p -Force | Out-Null"
if %errorlevel% neq 0 (
    echo Could not register the scheduled task.
    pause
    exit /b 1
)

echo.
echo [4/4] Starting the agent now...
schtasks /run /tn "SentinelLaptopGuard" >nul 2>&1

echo.
echo Done. The agent runs hidden and starts at every logon.
echo   Log file : %~dp0agent.log
echo   Test     : python laptop_agent.py --test-alert
echo   Remove   : schtasks /delete /tn SentinelLaptopGuard /f
echo.
pause
