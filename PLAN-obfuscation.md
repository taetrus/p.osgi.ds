# Plan: Obfuscate OSGi DS Application Bundles to Prevent Decompilation

## Context

The application bundles ship as standard JARs with unobfuscated Java 8 bytecode. Anyone with JD-GUI or CFR can read all business logic, algorithms, and implementation details. The goal is to make decompiled code effectively unreadable while keeping the OSGi runtime functional.

**Scope:** Obfuscation only (no jpackage/native packaging). Local builds only (no CI).

---

## Approach: ProGuard Post-Build Obfuscation

Run ProGuard on the 8 application bundle JARs **after** Tycho builds the product, activated via a Maven profile so the dev workflow stays untouched.

**What gets obfuscated (your business logic):**
- Private/package-private method names → `a`, `b`, `c`
- Local variable names → removed entirely
- Method bodies → optimized and restructured
- Internal helper classes → renamed and repackaged into `a/`
- String operations → inlined and optimized

**What must stay readable (OSGi contract):**
- Component class names (referenced in OSGI-INF XML)
- Service interface names and methods (cross-bundle wiring)
- DS bind/activate method names (called by Felix SCR via XML)
- Servlet lifecycle methods (called by container)

---

## Implementation Steps

### Step 1: Create ProGuard configuration
**New file:** `proguard-base.pro` (project root)

Keep rules for all 12 DS component classes, 3 service interfaces, and ~10 bind/activate methods. Everything else gets obfuscated with aggressive settings:
- `-optimizationpasses 5`
- `-allowaccessmodification`
- `-overloadaggressively` (reuses short names like `a()` across unrelated methods)
- `-repackageclasses 'a'` (moves private classes into flat `a/` package)

### Step 2: Add obfuscation script to distribution build
**Modified file:** `distribution/pom.xml`

Add an `obfuscate` Maven profile that uses `maven-antrun-plugin` (already used in this POM) to:

1. Download ProGuard JAR to `target/proguard/` (via `maven-dependency-plugin`)
2. For each of the 8 application bundle JARs in `target/products/.../plugins/`:
   - Run `java -jar proguard.jar @proguard-base.pro -injars <bundle.jar> -outjars <bundle-obf.jar> -libraryjars <all-other-plugin-jars>`
   - Replace original JAR with obfuscated JAR
3. The `MANIFEST.MF` and `OSGI-INF/*.xml` inside each JAR are preserved by ProGuard (they're not `.class` files)

### Step 3: Add ProGuard dependency management to parent POM
**Modified file:** `pom.xml` (root)

Add `com.guardsquare:proguard-base:7.6.1` as a dependency in the `obfuscate` profile so it's available for the antrun script.

---

## Application Bundle JARs to Obfuscate

These 8 JARs in `plugins/` are your code (everything else is third-party/framework):

| JAR | Key logic to protect |
|-----|---------------------|
| `com.kk.pde.ds.api_*.jar` | Interface only — minimal benefit, but obfuscate internals |
| `com.kk.pde.ds.imp_*.jar` | `Greet` implementation, health check logic |
| `com.kk.pde.ds.app_*.jar` | `App` startup, Swing clock UI, MCP settings loading |
| `com.kk.pde.ds.rest_*.jar` | REST servlet request handling |
| `com.kk.pde.ds.mcp.api_*.jar` | MCP interfaces — minimal benefit |
| `com.kk.pde.ds.mcp.server_*.jar` | MCP servlet, tool registry, JSON parsing (198 lines), tool implementations |
| `com.kk.pde.ds.mcp.client_*.jar` | MCP client HTTP communication |
| `com.kk.pde.ds.mcp.llm_*.jar` | OpenRouter agent loop, LLM chat servlet, JSON utilities |

### Complete Keep Rules Inventory

**Service interfaces (all public methods kept):**
- `com.kk.pde.ds.api.IGreet`
- `com.kk.pde.ds.mcp.api.IMcpTool`
- `com.kk.pde.ds.mcp.api.IMcpToolRegistry`

**Component classes + their DS-referenced methods:**
- `com.kk.pde.ds.imp.Greet` → keep `start()`
- `com.kk.pde.ds.imp.GreetHealthCheck` → keep field `greetService`
- `com.kk.pde.ds.app.App` → keep `start()`, `setApi(IGreet)`
- `com.kk.pde.ds.rest.GreetServlet` → keep `setGreetService(IGreet)`
- `com.kk.pde.ds.mcp.server.McpServlet` → keep `setRegistry(IMcpToolRegistry)`
- `com.kk.pde.ds.mcp.server.McpToolRegistry` → keep `addTool(IMcpTool)`, `removeTool(IMcpTool)`
- `com.kk.pde.ds.mcp.server.tools.EchoTool` → keep all (implements IMcpTool)
- `com.kk.pde.ds.mcp.server.tools.GreetTool` → keep `setGreetService(IGreet)`
- `com.kk.pde.ds.mcp.server.tools.SystemInfoTool` → keep all (implements IMcpTool)
- `com.kk.pde.ds.mcp.client.McpClient` → keep `start()`
- `com.kk.pde.ds.mcp.llm.LlmChatServlet` → keep `setAgent(OpenRouterAgent)`
- `com.kk.pde.ds.mcp.llm.OpenRouterAgent` → keep `setRegistry(IMcpToolRegistry)`

**Servlet methods (called by Jetty container):**
- `doGet(HttpServletRequest, HttpServletResponse)`
- `doPost(HttpServletRequest, HttpServletResponse)`

---

## Build Commands

```bash
# Normal dev build (unchanged)
mvn clean verify

# Build with obfuscation
mvn clean verify -Pobfuscate
```

---

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `proguard-base.pro` | **Create** | ProGuard keep rules for OSGi compatibility |
| `pom.xml` (root) | **Modify** | Add `obfuscate` profile with ProGuard dependency |
| `distribution/pom.xml` | **Modify** | Add antrun execution for post-build obfuscation loop |

---

## Limitations (honest assessment)

- **OSGI-INF XML** still reveals component class names and service wiring (architecture visible)
- **MANIFEST.MF** still reveals package names and imports
- **Service interface signatures** (`IGreet.greet()`) remain readable
- **Method bodies** — the actual business logic — become effectively unreadable

This protects the "how" (algorithms, logic) while the "what" (component structure) stays visible. This is the maximum protection possible without abandoning OSGi.

---

## Verification

1. `mvn clean verify -Pobfuscate` completes without errors
2. Open an obfuscated JAR in JD-GUI → method bodies should show meaningless variable names and restructured control flow
3. Run the product with `./distribution/scripts/run.sh` → all DS components activate, REST endpoints respond at `http://localhost:8080/api/greet`
4. Felix WebConsole at `http://localhost:8080/system/console` shows all components as "Active"
