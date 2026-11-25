#!/bin/sh
# Runs pde2tycho, building it on first use (needs JDK 17+ and Maven; a generated Tycho 5 build needs JDK 21+).
# After changing the tool's sources, rebuild with: mvn -q -f tools/pde2tycho package
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/pde2tycho.jar"
if [ ! -f "$JAR" ]; then
    mvn -q -f "$DIR/pom.xml" -DskipTests package || exit 1
fi
exec java -jar "$JAR" "$@"
