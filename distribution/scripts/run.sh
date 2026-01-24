#!/bin/bash

cd "$(dirname "$0")"

# Find the OSGi framework jar
OSGI_JAR=$(ls plugins/org.eclipse.osgi_*.jar 2>/dev/null | head -1)

if [ -z "$OSGI_JAR" ]; then
    echo "ERROR: Could not find org.eclipse.osgi jar in plugins directory"
    exit 1
fi

echo "Starting OSGi framework: $OSGI_JAR"
java -jar "$OSGI_JAR" -configuration configuration -console -consoleLog "$@"
