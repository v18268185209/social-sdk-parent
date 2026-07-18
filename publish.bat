@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM publish.bat — 一键发布 social-sdk 到 Maven 中央仓库（Sonatype OSSRH）
REM 版本号从 0.0.1 依次递增，发布后写入 .publish-version 锚定下次递增

cd /d "%~dp0"

REM ===== OSSRH 账号（用环境变量或从 settings.xml 读 serverId=ossrh）=====
if defined OSSRH_USERNAME (set "_U=-Dossrh.username=%OSSRH_USERNAME%") else (set "_U=")
if defined OSSRH_PASSWORD (set "_P=-Dossrh.password=%OSSRH_PASSWORD%") else (set "_P=")
if defined GPG_KEYNAME (set "_K=-Dgpg.keyname=%GPG_KEYNAME%") else (set "_K=")

REM ===== 工具检测 =====
where mvn >nul 2>&1 || (echo ERROR: mvn 不在 PATH & exit /b 1)
where gpg >nul 2>&1 || (echo WARN: gpg 未安装，GPG 签名会失败)

REM ===== 版本号管理：从 .publish-version 读上次版本，+1 末段得到本次版本 =====
set "VERSION_FILE=.publish-version"
if exist "%VERSION_FILE%" (
    set /p LAST=<"%VERSION_FILE%"
) else (
    set "LAST=0.0.0"
)
for /f "tokens=1,2,3 delims=." %%A in ("%LAST%") do (
    set "MAJOR=%%A"
    set "MINOR=%%B"
    set "PATCH=%%C"
)
set /a PATCH+=1
set "NEW_VERSION=%MAJOR%.%MINOR%.%PATCH%"

echo ==================================================
echo  social-sdk 发布到 Maven 中央仓库
echo  上次版本: %LAST%
echo  本次版本: %NEW_VERSION%
echo ==================================================

REM ===== 设置本次发布版本号（versions:set 改所有模块版本）=====
call mvn -q versions:set -DnewVersion=%NEW_VERSION% -DprocessAllModules=true -DgenerateBackupPoms=false
if errorlevel 1 (echo ERROR: 设置版本失败 & exit /b 1)
echo 已设置本次发布版本: %NEW_VERSION%

REM ===== 构建 + 发布到 Sonatype Staging（含 source/javadoc/gpg 签名）=====
echo 正在构建并发布到 Sonatype Staging...
call mvn clean verify source:jar javadoc:jar deploy -P release -DskipTests -Dgpg.passphraseServerId=gpg %_U% %_P% %_K%
if errorlevel 1 (echo ERROR: 构建或发布失败 & exit /b 1)

REM Staging autoReleaseAfterClose=true 已配，deploy 会自动 close+Release
echo Sonatype Staging 已自动 Close+Release

REM ===== 写入本次版本号锚点，下次脚本执行时递增 =====
echo %NEW_VERSION%> "%VERSION_FILE%"
echo.
echo ==================================================
echo  发布完成！版本: %NEW_VERSION%
echo  中央仓库同步通常需要 30 分钟~2 小时
echo  下次执行本脚本将自动递增到 %MAJOR%.%MINOR%.%PATCH%
echo ==================================================

REM ===== 回滚 pom 版本为 -SNAPSHOT（保持开发态）=====
call mvn -q versions:set -DnewVersion=%NEW_VERSION%-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
echo pom 已回滚到 %NEW_VERSION%-SNAPSHOT 开发态

endlocal
