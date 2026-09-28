@echo off
rem Runs pde2tycho, building it on first use (needs JDK 17+ and Maven, like any Tycho 4 build).
rem After changing the tool's sources, rebuild with: mvn -q -f tools\pde2tycho package
set "DIR=%~dp0"
set "JAR=%DIR%target\pde2tycho.jar"
if not exist "%JAR%" (
    call mvn -q -f "%DIR%pom.xml" -DskipTests package || exit /b 1
)
java -jar "%JAR%" %*
