@echo off
REM install.bat — Wrapper cho install.ps1 trên Windows CMD.
REM
REM Usage:
REM   install.bat                                    (prompt nhập target)
REM   install.bat C:\path\to\android\project        (silent, dùng path truyền vào)
REM   install.bat "C:\path with space\project"      (path có khoảng trắng)
REM
REM Mọi việc thật sự do install.ps1 làm — script này chỉ:
REM   1. Detect PowerShell có sẵn không
REM   2. Set code page UTF-8 (để hiển thị tiếng Việt + ký tự đặc biệt)
REM   3. Gọi PowerShell với -ExecutionPolicy Bypass (không cần admin)

setlocal enabledelayedexpansion

REM Save original code page + switch to UTF-8 for Vietnamese output
for /f "tokens=2 delims=:" %%a in ('chcp') do set "ORIGINAL_CP=%%a"
set "ORIGINAL_CP=!ORIGINAL_CP: =!"
chcp 65001 >nul 2>nul

set "SCRIPT_DIR=%~dp0"
REM Remove trailing backslash
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "PS_SCRIPT=%SCRIPT_DIR%\install.ps1"

REM Verify PowerShell available
where powershell >nul 2>nul
if errorlevel 1 (
    echo.
    echo [ERROR] PowerShell khong co trong PATH.
    echo.
    echo Windows 7+ co san powershell.exe. Co the bi disable boi group policy.
    echo Cach khac:
    echo   1. Cai PowerShell tu Microsoft Store
    echo   2. Hoac chay install.sh tu Git Bash / WSL
    echo.
    chcp !ORIGINAL_CP! >nul 2>nul
    exit /b 1
)

REM Verify .ps1 script exists
if not exist "%PS_SCRIPT%" (
    echo.
    echo [ERROR] Khong tim thay install.ps1 tai:
    echo   %PS_SCRIPT%
    echo.
    echo Script nay phai chay tu thu muc da unzip analytics-automation\
    echo.
    chcp !ORIGINAL_CP! >nul 2>nul
    exit /b 1
)

REM Delegate to PowerShell
if "%~1"=="" (
    powershell -ExecutionPolicy Bypass -NoProfile -File "%PS_SCRIPT%"
) else (
    powershell -ExecutionPolicy Bypass -NoProfile -File "%PS_SCRIPT%" -Target "%~1"
)

set "PS_EXIT=%errorlevel%"

REM Restore original code page
chcp !ORIGINAL_CP! >nul 2>nul

exit /b %PS_EXIT%
