@echo off
setlocal

cd /d "%~dp0"

rem Find the OSGi framework jar
for %%f in (plugins\org.eclipse.osgi_*.jar) do set OSGI_JAR=%%f

if not defined OSGI_JAR (
    echo ERROR: Could not find org.eclipse.osgi jar in plugins directory
    exit /b 1
)

echo Starting OSGi framework: %OSGI_JAR%
java -jar "%OSGI_JAR%" -configuration configuration -console -consoleLog %*
