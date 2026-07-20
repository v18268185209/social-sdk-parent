@echo off
REM ============================================================================
REM launch.bat — Electron 启动后端 JAR 的脚本（Windows）
REM ============================================================================
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_DIR=%SCRIPT_DIR%app"

rem ── 查找 JAR 文件 ─────────────────────────────────────────────────────────
set "JAR_FILE="
for %%f in ("%JAR_DIR%\*.jar") do (
    set "JAR_FILE=%%f"
    goto :found_jar
)

echo ERROR: No JAR file found in %JAR_DIR%
exit /b 1

:found_jar
echo [INFO] Found JAR: %JAR_FILE%

rem ── 创建数据目录 ───────────────────────────────────────────────────────────
if not exist "%SCRIPT_DIR%data" mkdir "%SCRIPT_DIR%data"
if not exist "%SCRIPT_DIR%data\openlist" mkdir "%SCRIPT_DIR%data\openlist"
if not exist "%SCRIPT_DIR%chrome-profiles" mkdir "%SCRIPT_DIR%chrome-profiles"
if not exist "%SCRIPT_DIR%logs" mkdir "%SCRIPT_DIR%logs"

rem ── 启动 Spring Boot ────────────────────────────────────────────────────────
java ^
    -Xmx512m ^
    -Xms256m ^
    -Dfile.encoding=UTF-8 ^
    -Duser.dir="%SCRIPT_DIR%" ^
    -jar "%JAR_FILE%" ^
    %*
