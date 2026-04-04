#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/osgi-fatjar.jar"

# Build if the fat JAR doesn't exist
if [ ! -f "$JAR" ]; then
    echo "Fat JAR not found, building..."
    cd "$SCRIPT_DIR"
    mvn clean package -q
fi

echo "Starting OSGi Fat JAR..."
exec java -jar "$JAR" "$@"
