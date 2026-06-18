@echo off
echo ===================================================
echo === IMS for Sven: Applying Patch via ADB ===
echo ===================================================

where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: adb is not found in your PATH.
    pause
    exit /b 1
)

echo Checking for connected ADB devices...
adb devices
echo Syncing ADB keys to phone...

adb push "%USERPROFILE%\.android\adbkey" /data/local/tmp/adbkey_temp >nul 2>nul
adb push "%USERPROFILE%\.android\adbkey.pub" /data/local/tmp/adbkey_temp.pub >nul 2>nul

adb shell run-as com.clean.pixelvolte cp /data/local/tmp/adbkey_temp /data/data/com.clean.pixelvolte/files/adbkey >nul 2>nul
adb shell run-as com.clean.pixelvolte cp /data/local/tmp/adbkey_temp.pub /data/data/com.clean.pixelvolte/files/adbkey.pub >nul 2>nul
adb shell run-as com.clean.pixelvolte chmod 600 /data/data/com.clean.pixelvolte/files/adbkey /data/data/com.clean.pixelvolte/files/adbkey.pub >nul 2>nul

adb shell rm /data/local/tmp/adbkey_temp /data/local/tmp/adbkey_temp.pub >nul 2>nul

echo Executing Patch...
adb shell am instrument -w com.clean.pixelvolte/com.clean.pixelvolte.BrokerInstrumentation

echo Waiting 8 seconds for network reconnection...
timeout /t 8 >nul

echo Querying final IMS status...
adb shell am instrument -e query_only true -w com.clean.pixelvolte/com.clean.pixelvolte.BrokerInstrumentation

echo.
echo Verifying status...
adb shell run-as com.clean.pixelvolte cat /data/data/com.clean.pixelvolte/files/ims_status_0.txt > temp_status0.txt 2>nul
set /p STATUS0=<temp_status0.txt
del /q temp_status0.txt 2>nul

adb shell run-as com.clean.pixelvolte cat /data/data/com.clean.pixelvolte/files/ims_status_1.txt > temp_status1.txt 2>nul
set /p STATUS1=<temp_status1.txt
del /q temp_status1.txt 2>nul

if "%STATUS0%"=="true" (
    echo [SUCCESS] SIM 1 (Slot 1): IMS is REGISTERED.
) else (
    if not "%STATUS0%"=="" (
        echo [WARNING] SIM 1 (Slot 1): IMS is UNREGISTERED.
    )
)

if "%STATUS1%"=="true" (
    echo [SUCCESS] SIM 2 (Slot 2): IMS is REGISTERED.
) else (
    if not "%STATUS1%"=="" (
        echo [WARNING] SIM 2 (Slot 2): IMS is UNREGISTERED.
    )
)

echo ===================================================
pause
