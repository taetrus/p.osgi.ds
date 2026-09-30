#!/bin/sh
# Runs pde2tycho, building it on first use (needs JDK 17+ and Maven, like any Tycho 4 build).
# After changing the tool's sources, rebuild with: mvn -q -f tools/pde2tycho package
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/pde2tycho.jar"
if [ ! -f "$JAR" ]; then
    mvn -q -f "$DIR/pom.xml" -DskipTests package || exit 1
fi
exec java -jar "$JAR" "$@"
