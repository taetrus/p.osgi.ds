# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an OSGi Declarative Services (DS) project built with Maven Tycho 4.0.13. It demonstrates service-oriented architecture with clear separation between API contracts, implementations, and consumers. The build produces a p2 repository and platform-specific product archives.

## Build Commands

```bash
# Full build (recommended for CI/CD)
mvn clean verify

# Quick build without tests
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl com.kk.pde.ds.api

# Resume failed build from distribution
mvn verify -rf :distribution
```

## Build Outputs

After a successful build:
- **p2 Repository:** `distribution/target/repository/`
- **Product Archives:**
  - `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz`
  - `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip`
  - `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz`

## Running the Application

Extract a product archive and run:
```bash
java -jar plugins/org.eclipse.osgi_*.jar -console -consoleLog
```

## Module Structure

```
com.kk.pde.ds.target   → Target platform definition (Eclipse 2024-12)
com.kk.pde.ds.api      → IGreet interface (service contract)
com.kk.pde.ds.imp      → Greet implementation (service provider)
com.kk.pde.ds.app      → App consumer (@Reference injection)
com.kk.pde.ds.feature  → Feature grouping all bundles
distribution           → p2 repository + product builds
```

## Architecture

**Service binding flow:**
1. `Greet` component registers as `IGreet` service
2. `App` component has `@Reference` on `setApi(IGreet)`
3. Felix SCR injects `Greet` into `App`
4. `App.start()` calls `api.greet()` → prints "Hello world!"

## Key Configuration Files

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.target/*.target` | Target platform with p2 repository URLs |
| `distribution/p2.product` | Product definition (bundles, start levels) |
| `distribution/category.xml` | p2 repository category structure |
| `*/META-INF/MANIFEST.MF` | OSGi bundle metadata |
| `*/OSGI-INF/*.xml` | DS component declarations |

## Technology Stack

- **Java 17**
- **Tycho 4.0.13** (Maven OSGi build)
- **Eclipse 2024-12** target platform
- **OSGi Declarative Services** via annotations
- **Felix SCR** runtime
