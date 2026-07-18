@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM gpg-keygen.bat - One-shot RSA 4096 GPG key generation + upload to public keyservers
REM Used for Maven Central signing (Sonatype OSSRH requires GPG-signed artifacts)
REM Prints fingerprint at the end - paste it into Sonatype console

cd /d "%~dp0"

REM ===== tool check =====
where gpg >nul 2>&1
if errorlevel 1 (
    echo ERROR: gpg not installed
    echo Windows: Git for Windows bundles gpg, or download from https://gnupg.org/download/
    exit /b 1
)

echo ==================================================
echo  GPG key generation (RSA 4096, for Maven Central signing)
echo ==================================================

REM ===== collect params (env vars pre-fill, else interactive) =====
if defined GPG_NAME (set "NAME=%GPG_NAME%") else (
    set /p "NAME=Real name (e.g. rjnetwork): "
)
if "!NAME!"=="" (
    echo ERROR: name cannot be empty
    exit /b 1
)

if defined GPG_EMAIL (set "EMAIL=%GPG_EMAIL%") else (
    set /p "EMAIL=Email (e.g. noreply@rjnetwork.net): "
)
if "!EMAIL!"=="" (
    echo ERROR: email cannot be empty
    exit /b 1
)

if defined GPG_PASSPHRASE (set "PASS=%GPG_PASSPHRASE%") else (
    set /p "PASS=Passphrase (empty = use default): "
)
if "!PASS!"=="" set "PASS=maven-central-sign"

REM ===== write batch param file for non-interactive gpg --gen-key =====
set "KEYFILE=.gpg-keygen-batch.txt"
> "%KEYFILE%" (
    echo Key-Type: RSA
    echo Key-Length: 4096
    echo Key-Usage: sign
    echo Name-Real: !NAME!
    echo Name-Email: !EMAIL!
    echo Expire-Date: 0
    echo Passphrase: !PASS!
)

echo.
echo Generating RSA 4096 key (10-30s, collecting entropy...)...
gpg --batch --gen-key "%KEYFILE%"
if errorlevel 1 (
    echo ERROR: key generation failed
    del "%KEYFILE%" 2>nul
    exit /b 1
)
del "%KEYFILE%" 2>nul

echo.
echo ==================================================
echo  Key generated! Key info below:
echo ==================================================

REM ===== show fingerprint - this is the value to paste into Sonatype =====
echo.
echo [fingerprint] - paste this into Sonatype console:
gpg --list-secret-keys --with-fingerprint --keyid-format LONG "!EMAIL!"

REM ===== extract KEYID (long format, for publish.bat GPG_KEYNAME) =====
set "KEYID="
for /f "tokens=2 delims=/" %%A in ('gpg --list-secret-keys --keyid-format LONG "!EMAIL!" ^| findstr /R "rsa4096"') do (
    set "KEYID=%%A"
)
if "!KEYID!"=="" (
    for /f "tokens=2 delims= " %%A in ('gpg --list-secret-keys --with-fingerprint --keyid-format LONG "!EMAIL!" ^| findstr /R "="') do (
        set "KEYID=%%A"
    )
)

echo.
echo [KEYID] - pass this to publish.bat via GPG_KEYNAME env var:
echo     set GPG_KEYNAME=!KEYID!

REM ===== upload public key to keys.openpgp.org =====
echo.
echo Uploading public key to keys.openpgp.org ...
gpg --keyserver hkps://keys.openpgp.org --send-keys "!KEYID!"
if errorlevel 1 (
    echo WARN: upload to keys.openpgp.org failed (may need email verification; manual upload at https://keys.openpgp.org/upload)
) else (
    echo OK: uploaded to keys.openpgp.org
)

REM ===== upload public key to keyserver.ubuntu.com =====
echo Uploading public key to keyserver.ubuntu.com ...
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "!KEYID!"
if errorlevel 1 (
    echo WARN: upload to keyserver.ubuntu.com failed (can retry manually)
) else (
    echo OK: uploaded to keyserver.ubuntu.com
)

REM ===== backup all info to .gpg-key-info.txt =====
set "INFO=.gpg-key-info.txt"
> "%INFO%" (
    echo GPG key info (generated %DATE% %TIME%^)
    echo.
    echo Name: !NAME!
    echo Email: !EMAIL!
    echo Passphrase: !PASS!
    echo KEYID: !KEYID!
    echo.
    echo Fingerprint:
    gpg --list-secret-keys --with-fingerprint --keyid-format LONG "!EMAIL!"
    echo.
    echo Uploaded to:
    echo   - hkps://keys.openpgp.org
    echo   - hkps://keyserver.ubuntu.com
    echo.
    echo Next steps:
    echo   1. Submit fingerprint at Sonatype console (https://central.sonatype.com^)
    echo   2. Configure serverId=gpg passphrase in settings.xml
    echo   3. Set env GPG_KEYNAME=!KEYID! before running publish.bat
)

echo.
echo ==================================================
echo  Done! Info backed up to %INFO%
echo  Key values:
echo    KEYID       = !KEYID!
echo    Passphrase  = !PASS!
echo.
echo  Next steps:
echo    1. Copy fingerprint above into Sonatype console
echo    2. Add to ~/.m2/settings.xml:
echo       ^<server^>^<id^>gpg^</id^>^<passphrase^>!PASS!^</passphrase^>^</server^>
echo    3. Set env: set GPG_KEYNAME=!KEYID!
echo ==================================================

endlocal
