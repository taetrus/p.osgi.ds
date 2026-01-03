# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an OSGi Declarative Services (DS) demonstration project built with Maven Tycho. It showcases the service-oriented architecture pattern with a clear separation between API contracts, implementations, and consumers.

## Build Commands

```bash
# Build all modules
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build a specific module
mvn clean package -pl com.kk.pde.ds.api

# Run tests (requires enabling test module first - see below)
mvn clean test
```

**Note:** The maven-clean-plugin is disabled (`<skip>true</skip>`), so `mvn clean` alone won't delete target folders. Use `mvn clean package` or delete target directories manually if needed.

## Running Tests

The test module `com.kk.pde.ds.imp.tests` is currently commented out in the root `pom.xml`. To enable:

1. Uncomment `<module>com.kk.pde.ds.imp.tests</module>` in root `pom.xml` (line 98)
2. Run `mvn clean test`

Tests use JUnit Jupiter 5.10.2 and run via tycho-surefire-plugin.

## Running in Eclipse

Use the Equinox launch configuration at `target/New_configuration.launch`. This starts an OSGi console with:
- All three bundles (api, imp, app)
- Felix SCR for declarative services
- Felix Gogo shell for the OSGi console

Required VM arguments: `-Declipse.ignoreApp=true -Dosgi.noShutdown=true`

## Architecture

```
com.kk.pde.ds.api     → Defines IGreet interface (service contract)
        ↑
com.kk.pde.ds.imp     → Implements IGreet via Greet class (service provider)
        ↑
com.kk.pde.ds.app     → Consumes IGreet via @Reference injection (service consumer)

distribution          → Aggregates all JARs into distribution/target/plugins/
```

**Service binding flow:**
1. `Greet` component activates and registers as `IGreet` service
2. `App` component has `@Reference` on `setApi(IGreet)`
3. SCR injects the `Greet` instance into `App`
4. `App.start()` calls `api.greet()` which prints "Hello world!"

## Key Files

- **Service declarations:** `*/OSGI-INF/*.xml` - Generated from DS annotations
- **Bundle manifests:** `*/META-INF/MANIFEST.MF` - OSGi metadata (Export-Package, Import-Package)
- **Build properties:** `*/build.properties` - PDE build configuration

## Technology Stack

- **Java 17** (bundles target 1.8 bytecode)
- **Tycho 4.0.13** for Maven-based OSGi builds
- **OSGi Declarative Services** via annotations (`org.osgi.service.component.annotations`)
- **JUnit 5** for testing
