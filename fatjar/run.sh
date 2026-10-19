#!/bin/bash
# Run the fat JAR. Build it first when it is missing.
# The builds use the Maven Wrapper (Maven 3.9.16): Tycho 4.0.13 does not start on Maven 3.10.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$SCRIPT_DIR/target/osgi-fatjar.jar"
MVNW="$REPO_DIR/mvnw"

if [ ! -f "$JAR" ]; then
    # The fat JAR packs the bundles of the built main product, so build that first.
    if ! ls -d "$REPO_DIR"/distribution/target/products/com.kk.pde.ds.product/*/*/*/plugins \
            "$REPO_DIR"/distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/*/Eclipse.app/Contents/Eclipse/plugins \
            >/dev/null 2>&1; then
        echo "Main product not found, building it (tests skipped)..."
        "$MVNW" -q -f "$REPO_DIR/pom.xml" clean package -DskipTests
    fi
    echo "Fat JAR not found, building..."
    "$MVNW" -q -f "$SCRIPT_DIR/pom.xml" clean package
fi

echo "Starting OSGi Fat JAR..."
exec java -jar "$JAR" "$@"
