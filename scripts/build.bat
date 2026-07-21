@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================================
REM build.bat — 闲鱼管理器 · 统一构建入口（Windows）
REM ============================================================================
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=all"

echo.
echo ╔══════════════════════════════════════════════════╗
echo ║     闲鱼管理器 · 全平台打包                      ║
echo ╚══════════════════════════════════════════════════╝
echo.

if "%ACTION%"=="all" goto :build_all
if "%ACTION%"=="electron" goto :build_electron
if "%ACTION%"=="android" goto :build_android
if "%ACTION%"=="docker" goto :build_docker
if "%ACTION%"=="jar" goto :build_jar
if "%ACTION%"=="frontend" goto :build_frontend
if "%ACTION%"=="clean" goto :do_clean
if "%ACTION%"=="help" goto :show_usage

echo 未知动作: %ACTION%
goto :show_usage

:build_all
call :build_jar
call :build_electron
call :build_android
call :build_docker
goto :eof

:build_jar
echo [STEP] 构建后端 JAR...
cd /d "%PROJECT_ROOT%"
if not exist "social-sdk-xianyu-manager\target\social-sdk-xianyu-manager-0.0.1.jar" (
    call mvn clean package -DskipTests -Dskip.frontend=false
) else (
    echo [INFO] JAR 已存在，跳过
)
goto :eof

:build_electron
echo [STEP] 构建 Electron 桌面应用...
cd /d "%SCRIPT_DIR%\electron"
call build.sh build
goto :eof

:build_android
echo [STEP] 构建 Android 应用...
cd /d "%SCRIPT_DIR%\android"
call build.sh build
goto :eof

:build_docker
echo [STEP] 构建 Docker 镜像...
cd /d "%SCRIPT_DIR%\docker"
call build.sh build
goto :eof

:build_frontend
echo [STEP] 构建前端...
cd /d "%PROJECT_ROOT%\social-sdk-xianyu-manager-web"
if not exist "node_modules" call npm install --registry=https://registry.npmmirror.com
call npm run build
goto :eof

:do_clean
echo [STEP] 清理构建产物...
cd /d "%PROJECT_ROOT%"
call mvn clean -q 2>nul
rd /s /q "%SCRIPT_DIR%\electron\dist_electron" 2>nul
rd /s /q "%SCRIPT_DIR%\docker\build" 2>nul
echo [INFO] 清理完成
goto :eof

:show_usage
echo 用法: build.bat ^<action^>
echo.
echo 动作:
echo   all       打包所有目标（默认）
echo   electron  桌面应用（Electron）
echo   android   Android 应用
echo   docker    Docker 镜像
echo   jar       后端 JAR
echo   frontend  前端
echo   clean     清理
echo   help      帮助
echo.
echo 示例:
echo   build.bat all
echo   build.bat electron
echo   build.bat docker

:end
endlocal
