@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
rem Remove trailing backslash
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

rem Detect if we're inside a product directory (plugins\ sits next to us)
rem or in the source tree (distribution\scripts\)
if exist "%SCRIPT_DIR%\plugins\" (
    set "PRODUCT_DIR=%SCRIPT_DIR%"
) else (
    rem Running from source — navigate to the built product for Windows
    for %%A in ("%SCRIPT_DIR%\..") do set "BASE_DIR=%%~fA"
    set "PRODUCT_DIR=!BASE_DIR!\target\products\com.kk.pde.ds.product\win32\win32\x86_64"
)

rem Find the OSGi framework jar (wildcard avoids hardcoding the version)
for %%f in ("!PRODUCT_DIR!\plugins\org.eclipse.osgi_*.jar") do set OSGI_JAR=%%f

if not defined OSGI_JAR (
    echo ERROR: Could not find org.eclipse.osgi jar in: !PRODUCT_DIR!\plugins\
    echo Hint: run "mvn clean verify" from the project root first.
    exit /b 1
)

echo Starting OSGi framework: %OSGI_JAR%
cd /d "!PRODUCT_DIR!"
java -jar "%OSGI_JAR%" -configuration configuration -console -consoleLog %*
