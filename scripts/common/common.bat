@echo off
REM ============================================================================
REM common.bat — Windows 共享工具函数（由其他 bat 脚本 call 调用）
REM ============================================================================

REM ── 目录推导 ──────────────────────────────────────────────────────────────
REM 假设此脚本从 scripts\common\ 调用，项目根目录是 scripts\ 的上层
for %%i in ("%~dp0..") do set "SCRIPTS_DIR=%%~fi"
for %%i in ("%SCRIPTS_DIR%..") do set "PROJECT_ROOT=%%~fi"

set "PROJECT_VERSION=0.0.1"
set "PROJECT_NAME=social-sdk-xianyu-manager"
set "JAR_NAME=%PROJECT_NAME%-%PROJECT_VERSION%.jar"
set "JAR_PATH=%PROJECT_ROOT%\%PROJECT_NAME%\target\%JAR_NAME%"
set "FRONTEND_DIR=%PROJECT_ROOT%\%PROJECT_NAME%-web"
set "FRONTEND_DIST=%FRONTEND_DIR%\dist"

REM ── 信息输出 ──────────────────────────────────────────────────────────────
echo [INFO] PROJECT_ROOT=%PROJECT_ROOT%
echo [INFO] JAR_PATH=%JAR_PATH%
