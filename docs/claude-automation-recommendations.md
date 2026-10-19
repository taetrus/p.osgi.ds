# Claude Code Automation Recommendations

This document recommends Claude Code automations for the **p.osgi.ds** project. The project uses
OSGi Declarative Services, Maven Tycho 4.0.13, Java 8 bytecode and a target platform from the
Eclipse 2025-03 line. The document combines the first top-pick recommendations with more options
in all five categories.

> **Profile refreshed 2026-10-19.** The first version of the recommendations is from 2026-05-29.
> After that date, the codebase got the RAG, ECF and spike bundles. That growth did not make a
> recommendation wrong. It made the arguments stronger. Before you trust this section, compare
> it with `pom.xml`.
>
> **Status:** One automation exists: the project skill `.claude/skills/pde2tycho/SKILL.md`. It is
> not one of the recommendations below. No recommendation below has an implementation yet. There
> is no `.mcp.json`, no `.claude/agents/`, and no `hooks` block in a settings file.

---

## Codebase Profile

- **Type:** Java 8 bytecode, OSGi Declarative Services, many bundles. The reactor has **19
  modules** (`pom.xml` `<modules>`). `fatjar` is not in the reactor. It has a separate build.
- **Build:** Maven Tycho 4.0.13 on a target platform from Maven Central. The target pins
  `org.eclipse.osgi` 3.23.0 (Equinox, 2025-03 line).
- **Bundles**, by cluster:
  - Core demo: `api`, `imp`, `app`, `rest`
  - MCP/LLM: `mcp.api`, `mcp.server`, `mcp.client`, `mcp.llm`, `chatbot`
  - Document Q&A: `rag` (PDFBox parsing, OCR, chunking, embeddings, vector store)
  - Remote services: `ecf.api`, `ecf.host`, `ecf.consumer` (two-JVM topology over ECF Generic)
  - Spike/PoC: `spike.api`, `spike.master`, `spike.detail`
  - Tests: in `src_test/` of `imp`, `mcp.api` and `spike.master`. A plain JVM runs `*Test`.
    Equinox runs `*IT`. See `docs/testing.md`.
  - Packaging: `target`, `feature`, `distribution`
- **Runtime surfaces:** Felix HTTP Jetty (port 8080), Felix WebConsole, MCP JSON-RPC servlet,
  OpenRouter LLM and embeddings bridge, Swing chatbot, ECF TCP transport (port 3288 for the
  greet demo, `ecftcp://localhost:3289/catalog` for the spike), Tesseract OCR subprocess.
- **Already connected:** serena MCP, context7 MCP (global), telegram plugin, chrome-devtools
  plugin, commit-commands plugin, superpowers skills, the `pde2tycho` project skill.

**Four structural facts that affect all recommendations below**

1. **Manifest, component XML and Java class are a coherence triple.** Each DS service needs
   imports in `META-INF/MANIFEST.MF`, an `OSGI-INF/<FQCN>.xml` descriptor and a class with
   `@Component`. When two of the three do not agree, the service does not bind at runtime. The
   descriptors are **hand-written and in Git**. The build does not generate them. The annotations
   are for compile time only (`resolution:=optional`). Thus the build does not fail when the files
   do not agree. Only the runtime shows the problem.
2. **The `IMcpTool` implementations repeat the same scaffolding.** There are **nine** in two bundles.
   Seven are in `com.kk.pde.ds.mcp.server/.../tools/` (`Calculator`, `DateTime`, `Echo`, `Greet`,
   `HttpFetch`, `BundleList`, `SystemInfo`). Two are in `com.kk.pde.ds.rag/.../tool/`
   (`DocumentSearch`, `IngestDocuments`). All have the same shape of approximately 45 lines:
   `@Component(service = IMcpTool.class)` → `getName / getDescription / getInputSchema / execute`.
   Such repeated code is a good target for a template. The use in two bundles shows that the
   pattern is not specific to one bundle.
3. **`./mvnw clean verify` is slow** (Tycho target-platform resolution and p2 packaging take
   minutes). **A build of one module alone does not work here.** Tycho resolves through OSGi
   `Import-Package` against the target platform and the reactor. The bundles declare no
   Maven-level `<dependencies>`. Thus `-pl <module>` alone fails for each module that imports a
   package of a sibling. `-am` does not help. Only leaf bundles such as `com.kk.pde.ds.api` build
   alone. Other modules need their upstream modules in the `-pl` list. Automation must use fast
   static checks (file coherence, manifest validation), because a quick partial build is not
   available.
4. **Dependency minimalism is a standing policy, not a preference.** The project rejected Apache
   Tika, because it needs slf4j 2.0. slf4j 2.0 breaks the `org.slf4j.helpers [1.7,2.0)` range of
   Felix Health Check. The parser reads OOXML zips directly, with no Apache POI. OCR runs the
   Tesseract CLI as a subprocess and does not use Tess4J/JNA. An automation that proposes a new
   library must show this constraint before the build finds it. See `docs/rag-reference.md` and
   `docs/rag-design.md` for the record of the rejection.

---

## 🔌 MCP Servers

### 1. context7: project-scoped registration *(top pick)*
**Why:** The project uses Felix SCR 2.2.12, OSGi SCR annotations 1.5.1, Tycho 4.0.13 and Felix
HTTP Whiteboard. These APIs change between minor versions. The global CLAUDE.md already prefers
context7. A project-local `.mcp.json` gives context7 to *each* clone of this repository. Claude
then uses it for Felix, Tycho and OSGi questions with no prompt.
**Install:** put this file at the repository root as `.mcp.json`:
```json
{ "mcpServers": { "context7": { "command": "npx", "args": ["-y", "@upstash/context7-mcp"] } } }
```

### 2. GitHub MCP
**Why:** The commit history shows real GitHub work (`feat(chatbot)`, `fix(chatbot)`,
conventional-commits style). With a GitHub MCP, Claude can query issues, PRs and CI status
directly. It does not need `gh` and shell parsing. Example: "summarize the open PRs that change
the MCP server bundle".
**Install:** `claude mcp add github` (or through the plugin marketplace).

### 3. Custom: OSGi WebConsole MCP *(custom, high value)*
**Why:** Felix WebConsole gives JSON at `/system/console/components.json`,
`/system/console/bundles.json` and `/system/console/services.json`. A thin MCP server around
these endpoints lets Claude *inspect a running OSGi runtime* during diagnosis. Example questions:
"which `@Reference` is unsatisfied?", "is `IMcpToolRegistry` registered?". This is a small Python
or Node MCP, possibly 100 lines. It replaces a manual `curl ... | jq` loop with tool calls.
**Effort:** 1–2 hours for the first version. It gives value at the first service-binding bug.

### 4. Do not use these
| MCP | Why not |
|-----|---------|
| Playwright / chrome-devtools | The UI is **Swing**, not web |
| Database MCPs (Postgres, Supabase, Prisma) | There is no database *now*. But `com.kk.pde.ds.rag` keeps vectors in `InMemoryVectorStore`, behind a `VectorStore` interface that is replaceable by design, for a pgvector backend. Examine this again if that change occurs. |
| Filesystem MCP | Read and Bash already do this |
| Memory MCP | The serena memory bank already does this |
| Sentry MCP | The repository has no error-tracker integration |

---

## 🎯 Skills (the category with the most value for this repository)

### 1. `ds-component` *(top pick, project skill)*
**Why:** Each new service needs three related edits:
- a Java class with `@Component`
- an `OSGI-INF/<FQCN>.xml` descriptor
- a matching `Service-Component:` entry in `META-INF/MANIFEST.MF`

Some bundles use the wildcard `OSGI-INF/*.xml` (`chatbot`, `ecf.host`, `imp`, `mcp.llm`,
`mcp.server`, `rag`). Other bundles list each XML file explicitly (`app`, `rest`, `mcp.client`,
`ecf.consumer`, `spike.master`, `spike.detail`). In those bundles, a new XML file has no effect
until you add it to the list. New contributors (and Claude) frequently forget one of the edits.
**Path:** `.claude/skills/ds-component/SKILL.md`
**Invocation:** user only (`disable-model-invocation: true`). It is deliberate scaffolding, not background context.
**Skeleton:**
```yaml
---
name: ds-component
description: Scaffold a new OSGi Declarative Services component (Java + MANIFEST.MF + OSGI-INF XML) in a chosen bundle.
disable-model-invocation: true
---
# Args: <bundle-dir> <FQCN> <ServiceInterface>
# Steps:
# 1. Create src/.../<Class>.java with @Component(service = <Service>.class)
# 2. Read Service-Component in META-INF/MANIFEST.MF. If it lists explicit files (no OSGI-INF/*.xml wildcard), add the new XML to the list
# 3. Generate OSGI-INF/<FQCN>.xml from template (scr v1.1.0 namespace).
#    If the @Activate/@Deactivate methods are not named activate/deactivate, set activate="..."/deactivate="..."
# 4. Verify Import-Package includes the service interface's package
# 5. Verify Import-Package has org.osgi.service.component.annotations;resolution:=optional
```

### 2. `mcp-tool` *(top pick, project skill)*
**Why:** Examine `com.kk.pde.ds.mcp.server/src/.../tools/`. `CalculatorTool`, `DateTimeTool`,
`EchoTool`, `GreetTool`, `HttpFetchTool`, `BundleListTool` and `SystemInfoTool` are almost the
same file with different bodies. `com.kk.pde.ds.rag/.../tool/` repeats the shape two more times in
a different bundle. A skill takes a tool name, a description and a JSON Schema. It writes the
class and the DS descriptor. This replaces 5 minutes of careful copy and paste with one command.
The skill must take the **target bundle** as an argument, because new tools are no longer only in
`mcp.server`. `com.kk.pde.ds.mcp.client/McpClient` cannot test a new tool. It calls only `echo`,
`greet` and `system_info`. Use `curl` against `/mcp` (see `docs/mcp-server-testing.md`).
**Path:** `.claude/skills/mcp-tool/SKILL.md`
**Invocation:** both. The user types `/mcp-tool weather …`. Claude uses it when you say "add a tool that …".

### 3. `rest-endpoint`
**Why:** `com.kk.pde.ds.rest.GreetServlet` is the reference HTTP Whiteboard pattern in the
repository (`@Component`, `HTTP_WHITEBOARD_SERVLET_PATTERN`, `Servlet.class`). Each new endpoint
repeats the same wiring. A skill contains the pattern *and* the MANIFEST `Import-Package` entries
for `javax.servlet` and `org.osgi.service.http.whiteboard`.
**Path:** `.claude/skills/rest-endpoint/SKILL.md`
**Invocation:** both.

### 4. `run-and-probe`
**Why:** The end-to-end check here is: build → start the product → wait for Jetty → `curl
/api/greet` → `curl` the MCP servlet → stop the process. This loop is tedious, so people skip it,
and regressions go into `main`. A skill that the user can call (`/run-and-probe`) records the loop one time.
**Path:** `.claude/skills/run-and-probe/SKILL.md`
**Invocation:** user only (side effect: it starts a process).
**Outline:**
```yaml
---
name: run-and-probe
description: Start the OSGi product, wait for Jetty, smoke-test REST + MCP endpoints, then shut down.
disable-model-invocation: true
---
# 1. ./distribution/scripts/run.sh & (capture PID)
# 2. Poll http://localhost:8080/system/console/bundles.json until 200 (max 30s)
# 3. curl /api/greet, /api/greet/World, MCP /mcp endpoint
# 4. Print summary; kill PID
```

### 5. `ste-docs`
**Why:** The project rule is: all documentation goes in `docs/` and follows ASD-STE100 Simplified
Technical English. Only `README.md`, `CLAUDE.md` and `SECURITY.md` stay in the root. The
narrative documents (`docs/ecf-remote-services.md`, `docs/rag-design.md`) must change when the
code changes. The `anthropic-skills:asd-ste100` skill and its `ste-lint.py` linter already exist.
A thin project skill can join them: select the applicable file in `docs/`, write the text, run the
linter, and fix each hard violation.
**Path:** `.claude/skills/ste-docs/SKILL.md`
**Invocation:** both. Claude can also use it automatically after a large feature.

### 6. `bundle-bump`
**Why:** The bundle version is in `META-INF/MANIFEST.MF` (`Bundle-Version: 1.0.0.qualifier`). The
bundle poms declare no version of their own. They inherit `1.0.0-SNAPSHOT` from the parent pom.
Tycho requires that the two versions agree. Thus a version change for one bundle must add a
`<version>` to that bundle pom, or change the parent and all manifests together. A skill takes
`<bundle-dir> <new-version>`, makes the edits and shows a warning about the `Import-Package`
version ranges of dependent bundles.
**Invocation:** user only.

---

## ⚡ Hooks

Command hooks get the event as JSON on stdin. Read the file path from `.tool_input.file_path` and
the Bash command from `.tool_input.command`. Claude sees the stderr of a hook only when the hook
exits with code 2. On exit 0, stderr goes only to the debug log. A `PreToolUse` hook that exits
with code 2 blocks the tool call.

### 1. DS coherence guard (PostToolUse) *(top pick)*
**Why:** Most OSGi runtime failures here come from an edit to one file of the triple without an
edit to the other files. The check must do these steps:
- Make sure that each Java class with `@Component` has a matching `OSGI-INF/<FQCN>.xml`.
- Make sure that the `<implementation class="…">` of each XML points to an existing `.java`.
- Make sure that the `Service-Component` header includes each XML. Some bundles use the wildcard
  `OSGI-INF/*.xml`. Others list explicit files.
- Make sure that each `@Reference` injects a type whose package is in `Import-Package` of the bundle.
- Make sure that the lifecycle methods agree. SCR reads only the XML. With no `activate` or
  `deactivate` attribute, it calls methods named `activate` and `deactivate`. If the `@Activate`
  or `@Deactivate` method has a different name, the XML must name it.

**Evidence:** The last check found a real bug. `App.java` has `@Deactivate` on `stop()`, but
`App.xml` had no `deactivate="stop"`. SCR never called `stop()`. When the app bundle stopped, the
Clock and Chat windows stayed open and the clock timer continued. Commit 7470d2c fixed the XML.
The build and the tests did not find this bug. A static check finds it at the edit.

A fast Python or bash script runs after each edit of `*/META-INF/MANIFEST.MF`,
`*/OSGI-INF/*.xml` or `**/src/**/*.java`. It shows warnings with no build.

**Where:** `.claude/settings.json`
```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Edit|Write",
      "hooks": [{
        "type": "command",
        "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/scripts/check-bundle-coherence.sh"
      }]
    }]
  }
}
```
The script reads the file path with `jq -r '.tool_input.file_path'`. For a path outside a bundle,
it exits with code 0 and no output. When it finds a mismatch, it writes the warning to stderr and
exits with code 2, so that Claude sees the warning.

### 2. Scoped-build guard (PreToolUse on Bash)
**Why:** The usual fix for a slow Tycho build is `-pl <module> -am`. On this project, that is a
**trap**, not a speedup. Tycho resolves through OSGi `Import-Package`, but the bundles declare no
Maven `<dependencies>`. Thus `-am` adds only the parent. A bare `-pl com.kk.pde.ds.rag` and
`-pl … -am` both fail with `Missing requirement: ... requires 'java.package; com.kk.pde.ds.mcp.api'`.
This resolution error looks like a real dependency bug, and it takes some minutes to recognize.
Only leaf bundles such as `com.kk.pde.ds.api` build alone. All other modules need their upstream
modules in the `-pl` list (`-pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp`, see
CLAUDE.md → Testing).

Thus the hook blocks `-am`, and it tells Claude to list the upstream modules. Use it with `-o`
(offline) and `-DskipTests`, which *are* real speedups here.
```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash",
      "hooks": [{
        "type": "command",
        "command": "case \"$(jq -r '.tool_input.command')\" in *mvn*-am*) echo 'BLOCKED: -am does not work on this Tycho reactor (no Maven-level deps between bundles). List the upstream modules in -pl (see CLAUDE.md, Testing).' >&2; exit 2 ;; esac; exit 0"
      }]
    }]
  }
}
```
For reference, the full reactor with `-o clean package -DskipTests` completes in much less than a
minute. The expensive step is `verify` (p2 packaging and product materialization), not compilation.

### 3. Target-platform edit guard (PreToolUse on Edit/Write)
**Why:** An edit of `com.kk.pde.ds.target/*.target` makes the resolved Tycho cache out of date.
The next build needs `./mvnw -U` or `./mvnw clean` to use the change. People forget this. The
build then does not "see" the new dependency, and the cause is not clear. A hook that matches
`*.target` edits and gives the text "run `./mvnw clean` at the next build" prevents this.

### 4. Permissions cleanup (manual, not a hook)
**Why:** `.claude/settings.local.json` has many old Windows-path permissions (`D:\Dev\…`,
`cmd /c`, `taskkill`) from earlier machines. They add noise to each permission decision. Run
`/fewer-permission-prompts` one time to make the list again from the current activity. This is
not a hook, but it is worth the time.

### 5. Stop hook for uncommitted work
**Why:** Long sessions on this repository change many bundles. A Stop hook runs
`git status --porcelain | head` and prints a one-line summary when the tree has changes. This
prevents the end of a session with hours of unstaged changes.

---

## 🤖 Subagents

### 1. `osgi-wiring-doctor` *(top pick)*
**Why:** Bugs where a service does not bind are the slowest to diagnose here. The diagnosis must
compare MANIFEST `Import-Package`, OSGI-INF `<reference>` cardinality, target-platform exports and
the state in Felix WebConsole `/system/console/components`. A subagent that knows the procedure
keeps the main context clean.
**Path:** `.claude/agents/osgi-wiring-doctor.md`
```yaml
---
name: osgi-wiring-doctor
description: Diagnose why an OSGi @Reference is not resolving. Reads bundle manifests, DS component XML, target platform, and (if running) Felix WebConsole DS state.
tools: Read, Grep, Bash, WebFetch
---
You are an OSGi Declarative Services diagnostic specialist…
Playbook:
1. Read the failing component's @Reference declarations
2. For each referenced interface, locate the @Component that provides it
3. Verify the providing bundle's MANIFEST exports the interface package
4. Verify the consuming bundle's MANIFEST imports the interface package
5. Verify the OSGI-INF XML names match Java FQCNs, and Service-Component includes the XML
6. Verify the XML activate/deactivate names match the @Activate/@Deactivate methods
7. If the product is running, curl /system/console/components.json and report unsatisfied references
```

### 2. `manifest-import-fixer`
**Why:** This is a narrow subagent for one failure mode: a missing `Import-Package`. It gets a
Java compile error such as "package X is not visible". It finds the bundle that exports `X`,
reads the MANIFEST of the consumer, and proposes the exact `Import-Package` entry. A narrow scope
gives a high success rate.

### 3. `docs-writer`
**Why:** The narrative documents in `docs/` (`ecf-remote-services.md`, `rag-design.md`) are long
prose in ASD-STE100. Good text needs context window space and a different approach than code
work. A subagent keeps that work separate. Give it a diff summary and the `ste-docs` skill (Skills 5).
**Tools:** Read, Grep, Bash (for `git log` and `ste-lint.py`).

### 4. `mcp-tool-tester`
**Why:** After the `mcp-tool` skill makes a new `IMcpTool`, you must call the tool. The existing
`com.kk.pde.ds.mcp.client/McpClient` has no CLI. It is a one-shot daemon thread that calls only
`echo`, `greet` and `system_info` at startup. Thus the subagent sends JSON-RPC `tools/call`
requests to `/mcp` with `curl` (see `docs/mcp-server-testing.md`). It reports the shape of the
response. This completes the loop after the `mcp-tool` skill.

### 5. `product-launcher`
**Why:** This is a larger version of the `run-and-probe` skill. It starts the product, reads the
Logback output, finds DS failures and bundle-resolution errors, and gives a summary of the startup
health. Example use: "I changed `feature.xml`. Does the product still start with no errors?"

---

## 📦 Plugins

### Already installed or in use
- **`commit-commands`**: `/commit-commands:commit` matches the conventional-commits style of this
  repository (`feat(chatbot):`, `fix(chatbot):`).
- **`superpowers`**: `/superpowers:writing-plans` and `/superpowers:test-driven-development` are
  good for large bundle work where the manifest/XML/Java triple needs coordination.
- **`chrome-devtools-mcp`**: not in use now. It is useful only if the project gets a web frontend.

### Use, but do not install
- **`document-skills:mcp-builder`**: The project builds MCP tools in OSGi. The official
  mcp-builder skill contains the tool-design guidance of Anthropic (naming, error handling,
  response shape). The guidance applies here, although the transport is custom. Use it as a
  *reviewer*: "Use mcp-builder to review `HttpFetchTool.java`".
- **`/code-review ultra`**: Before you merge large work to `main`, the multi-agent cloud review
  finds issues across bundles that a single pass does not find. The user starts it, and it costs money.

### Do not use
- **`frontend-design`**: The UI is Swing only. No fit.
- **`anthropic-agent-skills` (docx/xlsx/pptx)**: These formats are not deliverables of this project.

---

## Recommended Implementation Order

If you implement only three items, do them in this order. Each item increases the value of the next:

1. **`/fewer-permission-prompts`**: Clean `settings.local.json` first. Each later automation
   configures permissions, so start from a clean baseline.
2. **`.claude/skills/ds-component/`** and **`.claude/skills/mcp-tool/`**: These give value at the
   next component that you add.
3. **DS coherence hook**: a continuous check of the coherence triple and of the lifecycle-method
   names. It catches what the skills miss.

Then add the subagents (`osgi-wiring-doctor` first), and then the custom MCP server (the Felix
WebConsole wrapper), when you find the problem that each one removes.

---

## More

Ask for more detail on one category. Examples: "show five more hook patterns" or "design the OSGi
WebConsole MCP". To build one of these items (the `ds-component` skill, the coherence hook, the
`osgi-wiring-doctor` subagent), name the item.
