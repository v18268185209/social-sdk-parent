@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Build sqlite/mysql/postgres Docker images and optionally push them to Alibaba Cloud ACR.
rem
rem Required for push:
rem   set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
rem   set ACR_NAMESPACE=your-namespace
rem Optional:
rem   set IMAGE_NAME=xianyu-manager
rem   set TAG=latest
rem   set ACR_USERNAME=xxx
rem   set ACR_PASSWORD=xxx
rem
rem Usage:
rem   publish-acr.bat --help
rem   publish-acr.bat --build-only
rem   publish-acr.bat --mode sqlite --push

cd /d "%~dp0\..\.." || exit /b 1
set "PROJECT_ROOT=%CD%"
set "SCRIPT_DIR=%PROJECT_ROOT%\scripts\docker"
set "DOCKERFILE=%SCRIPT_DIR%\Dockerfile"

if not defined ACR_REGISTRY set "ACR_REGISTRY="
if not defined ACR_NAMESPACE set "ACR_NAMESPACE="
if not defined IMAGE_NAME set "IMAGE_NAME=xianyu-manager"
if not defined TAG set "TAG=latest"
if not defined PUSH set "PUSH=true"
if not defined BUILD set "BUILD=true"
if not defined PLATFORM set "PLATFORM="
set "MODE=all"

:parse_args
if "%~1"=="" goto after_args
if /I "%~1"=="--help" goto usage
if /I "%~1"=="-h" goto usage
if /I "%~1"=="--mode" (
    set "MODE=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--tag" (
    set "TAG=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--registry" (
    set "ACR_REGISTRY=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--namespace" (
    set "ACR_NAMESPACE=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--image" (
    set "IMAGE_NAME=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--platform" (
    set "PLATFORM=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--build-only" (
    set "BUILD=true"
    set "PUSH=false"
    shift
    goto parse_args
)
if /I "%~1"=="--push-only" (
    set "BUILD=false"
    set "PUSH=true"
    shift
    goto parse_args
)
if /I "%~1"=="--push" (
    set "BUILD=true"
    set "PUSH=true"
    shift
    goto parse_args
)
echo ERROR: Unknown option: %~1
goto usage_error

:after_args
if /I "%MODE%"=="sqlite" goto mode_ok
if /I "%MODE%"=="mysql" goto mode_ok
if /I "%MODE%"=="postgres" goto mode_ok
if /I "%MODE%"=="all" goto mode_ok
echo ERROR: Invalid mode: %MODE%
exit /b 1

:mode_ok
where docker >nul 2>nul
if errorlevel 1 (
    echo ERROR: Docker is not installed or not in PATH.
    exit /b 1
)
if not exist "%DOCKERFILE%" (
    echo ERROR: Dockerfile not found: %DOCKERFILE%
    exit /b 1
)

if /I "%PUSH%"=="true" (
    if "%ACR_REGISTRY%"=="" (
        echo ERROR: ACR_REGISTRY is required when pushing.
        exit /b 1
    )
    if "%ACR_NAMESPACE%"=="" (
        echo ERROR: ACR_NAMESPACE is required when pushing.
        exit /b 1
    )
)

set "REMOTE_PREFIX=%ACR_REGISTRY%/%ACR_NAMESPACE%/%IMAGE_NAME%"
set "LOCAL_PREFIX=%IMAGE_NAME%"

echo ==================================================
echo  social-sdk-parent Docker ACR publisher
echo  Mode  : %MODE%
echo  Tag   : %TAG%
echo  Image : %REMOTE_PREFIX%
echo ==================================================

if /I "%PUSH%"=="true" (
    if defined ACR_USERNAME if defined ACR_PASSWORD (
        echo [STEP] Logging in to ACR: %ACR_REGISTRY%
        echo %ACR_PASSWORD%| docker login --username "%ACR_USERNAME%" --password-stdin "%ACR_REGISTRY%"
        if errorlevel 1 exit /b 1
    ) else (
        echo [WARN] ACR_USERNAME/ACR_PASSWORD not set; assuming docker is already logged in to %ACR_REGISTRY%.
    )
)

if /I "%MODE%"=="all" (
    call :process_mode sqlite || exit /b 1
    call :process_mode mysql || exit /b 1
    call :process_mode postgres || exit /b 1
) else (
    call :process_mode %MODE% || exit /b 1
)

echo [INFO] Done.
exit /b 0

:process_mode
set "DB_MODE=%~1"
set "LOCAL_TAG=%LOCAL_PREFIX%:%DB_MODE%-%TAG%"
set "REMOTE_TAG=%REMOTE_PREFIX%:%DB_MODE%-%TAG%"
if /I "%BUILD%"=="true" call :build_one || exit /b 1
if /I "%PUSH%"=="true" call :push_one || exit /b 1
exit /b 0

:build_one
echo [STEP] Building %DB_MODE% image: %LOCAL_TAG%
set "PLATFORM_ARG="
if not "%PLATFORM%"=="" set "PLATFORM_ARG=--platform %PLATFORM%"
if "%ACR_REGISTRY%"=="" (
    docker build %PLATFORM_ARG% --build-arg DB_MODE=%DB_MODE% -f "%DOCKERFILE%" -t "%LOCAL_TAG%" "%PROJECT_ROOT%"
) else (
    docker build %PLATFORM_ARG% --build-arg DB_MODE=%DB_MODE% -f "%DOCKERFILE%" -t "%LOCAL_TAG%" -t "%REMOTE_TAG%" "%PROJECT_ROOT%"
)
if errorlevel 1 exit /b 1
echo [INFO] Built: %LOCAL_TAG%
if not "%ACR_REGISTRY%"=="" echo [INFO] Tagged: %REMOTE_TAG%
exit /b 0

:push_one
echo [STEP] Pushing %DB_MODE% image: %REMOTE_TAG%
docker push "%REMOTE_TAG%"
if errorlevel 1 exit /b 1
exit /b 0

:usage
echo Usage: publish-acr.bat [options]
echo.
echo Build sqlite/mysql/postgres Docker images and push them to Alibaba Cloud ACR.
echo.
echo Options:
echo   --mode MODE       sqlite ^| mysql ^| postgres ^| all ^(default: all^)
echo   --tag TAG         Image tag ^(default: env TAG or latest^)
echo   --registry URL    ACR registry, e.g. registry.cn-hangzhou.aliyuncs.com
echo   --namespace NS    ACR namespace
echo   --image NAME      Image repository name ^(default: xianyu-manager^)
echo   --build-only      Build images only, do not push
echo   --push-only       Push existing images only, do not build
echo   --push            Build and push ^(default^)
echo   --platform P      docker build --platform value, e.g. linux/amd64
echo   -h, --help        Show help
echo.
echo Environment variables:
echo   ACR_REGISTRY, ACR_NAMESPACE, IMAGE_NAME, TAG, ACR_USERNAME, ACR_PASSWORD, PLATFORM
echo.
echo Example:
echo   set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
echo   set ACR_NAMESPACE=your-namespace
echo   set TAG=v1.0.0
echo   scripts\docker\publish-acr.bat
exit /b 0

:usage_error
call :usage
exit /b 1
