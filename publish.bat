@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem publish.bat - Publish social-sdk-parent to Maven Central / Sonatype OSSRH.
rem Usage:
rem   publish.bat          Run real release deploy.
rem   publish.bat --check  Check script syntax and Maven release profile only; no version change, no deploy.

cd /d "%~dp0" || exit /b 1

set "CHECK_ONLY=0"
if /I "%~1"=="--check" set "CHECK_ONLY=1"

set "MAVEN_SETTINGS=D:\Program Files\apache-maven-3.9.12\conf\settings.xml"
if not exist "%MAVEN_SETTINGS%" (
    echo ERROR: Maven settings.xml not found: %MAVEN_SETTINGS%
    exit /b 1
)
set "MVN=mvn -s "%MAVEN_SETTINGS%""

set "_U="
if defined OSSRH_USERNAME set "_U=-Dossrh.username=%OSSRH_USERNAME%"

set "_P="
if defined OSSRH_PASSWORD set "_P=-Dossrh.password=%OSSRH_PASSWORD%"

set "_K="
if defined GPG_KEYNAME set "_K=-Dgpg.keyname=%GPG_KEYNAME%"

where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: mvn is not in PATH.
    exit /b 1
)

where gpg >nul 2>nul
if errorlevel 1 (
    echo WARN: gpg is not in PATH. Real release signing may fail.
)

set "VERSION_FILE=.publish-version"
set "LAST="

rem Prefer the current root POM version as the last version anchor.
rem Example: current POM 0.0.1 -> next release 0.0.2; current POM 0.0.2-SNAPSHOT -> next release 0.0.3.
set "POM_VERSION_FILE=target\publish-pom-version.txt"
if not exist "target" mkdir "target" >nul 2>nul
call %MVN% -q help:evaluate -Dexpression=project.version -DforceStdout -N > "%POM_VERSION_FILE%" 2>nul
if not errorlevel 1 set /p "LAST=" < "%POM_VERSION_FILE%"
set "LAST=%LAST:-SNAPSHOT=%"

if not defined LAST if exist "%VERSION_FILE%" set /p "LAST=" < "%VERSION_FILE%"
if not defined LAST set "LAST=0.0.0"

for /f "tokens=1,2,3 delims=." %%A in ("%LAST%") do (
    set "MAJOR=%%A"
    set "MINOR=%%B"
    set "PATCH=%%C"
)

if not defined MAJOR set "MAJOR=0"
if not defined MINOR set "MINOR=0"
if not defined PATCH set "PATCH=0"
set /a PATCH=PATCH+1
set "NEW_VERSION=%MAJOR%.%MINOR%.%PATCH%"

echo ==================================================
echo  social-sdk-parent Maven Central publish
echo  Last version: %LAST%
echo  New version : %NEW_VERSION%
echo ==================================================

if "%CHECK_ONLY%"=="1" (
    echo CHECK ONLY: validating Maven release profile. No deploy will be executed.
    call %MVN% -q -P release -DskipTests -Dgpg.skip=true -Dskip.frontend=true validate
    if errorlevel 1 (
        echo ERROR: Maven release profile validation failed.
        exit /b 1
    )
    echo CHECK OK.
    exit /b 0
)

echo Setting release version: %NEW_VERSION%
call %MVN% -q versions:set -DnewVersion=%NEW_VERSION% -DprocessAllModules=true -DgenerateBackupPoms=false
if errorlevel 1 (
    echo ERROR: Failed to set release version.
    exit /b 1
)

echo Building and deploying to Central Portal...
call %MVN% clean deploy -P release -DskipTests -Dgpg.passphraseServerId=gpg %_U% %_P% %_K%
if errorlevel 1 (
    echo ERROR: Build or deploy failed. POM version is still %NEW_VERSION%; fix the error before retrying or rollback manually.
    exit /b 1
)

echo %NEW_VERSION%> "%VERSION_FILE%"

echo ==================================================
echo  Publish completed. Version: %NEW_VERSION%
echo  Maven Central sync usually takes 30 minutes to 2 hours.
echo ==================================================

echo Rolling POM version back to development snapshot: %NEW_VERSION%-SNAPSHOT
call %MVN% -q versions:set -DnewVersion=%NEW_VERSION%-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
if errorlevel 1 (
    echo WARN: Failed to roll POM version back to snapshot. Please run versions:set manually.
    exit /b 1
)

echo POM version rolled back to %NEW_VERSION%-SNAPSHOT.
endlocal
