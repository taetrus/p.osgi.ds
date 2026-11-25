# WebConsole Terminal Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A generic Felix WebConsole plugin bundle that shows `System.out` / `System.err` of the application in a browser terminal and runs Gogo commands.

**Architecture:** One bundle, `com.kk.pde.ds.webconsole.terminal`. A bundle activator replaces `System.out` and `System.err` with tee streams that feed one ring buffer (`OutputHub`). A DS component (`TerminalPlugin`, an `AbstractWebConsolePlugin`) serves the page, the static files, a Server-Sent Events stream of the buffer, and a POST endpoint that runs one line in one shared Gogo session. The browser uses vendored xterm.js and a hand-written `terminal.js` line editor.

**Tech Stack:** Java 8 bytecode, Tycho 5.0.4, Felix WebConsole 4.3.16 (`javax.servlet` 3.1), Gogo runtime 1.1.6, Felix SCR, xterm.js 6.0.0, JUnit 5 + Mockito (test only).

**Spec:** `docs/superpowers/specs/2026-11-25-webconsole-terminal-design.md`

## Global Constraints

- Use `./mvnw` for all builds. Never the system `mvn` (Maven 3.10 fails at `com.kk.pde.ds.chatbot`).
- `-pl <module> -am` does not work. List modules explicitly: `-pl com.kk.pde.ds.target,com.kk.pde.ds.webconsole.terminal`.
- `Bundle-RequiredExecutionEnvironment: JavaSE-1.8`. No Java 9+ API (`--release 8` rejects it).
- `Import-Package` of the new bundle: only `javax.servlet`, `javax.servlet.http`, `org.apache.felix.webconsole`, `org.osgi.framework`, `org.apache.felix.service.command` (optional) and `org.osgi.service.component.annotations` (optional). No slf4j, no Logback, no `com.kk.pde.ds.*`.
- No new third-party Java dependency. No change to the target platform.
- The DS component XML in `OSGI-INF/` is hand-written. SCR reads the XML, not the annotations.
- Tests go in `src_test/` of the bundle. `*Test` is tier 1 (plain JVM). `*IT` is tier 2 (live Equinox). Never `src/test/java`.
- The plugin code never writes to `System.out` or `System.err`. It reports its own errors on the original `System.err` that `StdCapture` saved.
- Framework properties (read with `BundleContext.getProperty`): `terminal.scrollback.lines` (default `50000`), `terminal.shell.enabled` (default `true`), `terminal.prompt` (default `osgi> `).
- URLs: page `/system/console/terminal`, files `/system/console/terminal/res/<name>`, stream `/system/console/terminal/stream`, command `/system/console/terminal/exec`. Token header: `X-Terminal-Token`.
- Commits: conventional subject with scope `webconsole`, for example `feat(webconsole): ...`. Branch: `feat/webconsole-terminal`.
- Docs follow ASD-STE100. `tools/doccheck/check-docs.sh` must exit 0. A doc states only facts that a build, a test or a run showed. Mark each other fact as "not tested".

## Review Focus

1. **A multi-byte UTF-8 character split across two `write` calls.** The line must show the character, not two replacement characters. Test in Task 3 (`LineAssemblerTest.decodesCharacterSplitAcrossWrites`).
2. **Output faster than a browser reads.** A thread that prints must never wait, and memory must stay bounded by the buffer size. Test in Task 2 (`OutputHubTest.appendNeverBlocksAndDropsOldest`).
3. **One very long line with no line end** (for example 5 MB of JSON). It must not grow one buffer entry without limit. Lines longer than 8192 characters are split. Test in Task 3 (`LineAssemblerTest.splitsLineLongerThanLimit`).
4. **The browser connects again with a `Last-Event-ID` that is no longer in the buffer.** It must get one "lines are gone" notice and then the oldest line in the buffer. Test in Task 2 (`OutputHubTest.sinceReportsDroppedLines`).
5. **The bundle stops or updates while the application runs.** `System.out` must be the original stream again, and the worker thread must end. Tests in Task 3 (`StdCaptureTest.uninstallRestoresOriginalStreams`) and Task 4 (`ShellTest.closeStopsWorker`).

---

### Task 1: Module skeleton and proof of the six facts

A minimal bundle in the product that proves the facts of the spec section "Facts to verify first". The code of this task is a prototype. Later tasks replace it.

**Files:**
- Create: `com.kk.pde.ds.webconsole.terminal/pom.xml` (copy of `com.kk.pde.ds.imp/pom.xml`; change the `artifactId`; add a second `extraRequirements` entry for `org.apache.felix.gogo.runtime`)
- Create: `com.kk.pde.ds.webconsole.terminal/.classpath`, `.project`, `build.properties`, `.gitignore` (copies from `com.kk.pde.ds.imp`; in `build.properties` add `res/` to `bin.includes`)
- Create: `com.kk.pde.ds.webconsole.terminal/META-INF/MANIFEST.MF`
- Create: `com.kk.pde.ds.webconsole.terminal/OSGI-INF/com.kk.pde.ds.webconsole.terminal.TerminalPlugin.xml`
- Create: `com.kk.pde.ds.webconsole.terminal/src/com/kk/pde/ds/webconsole/terminal/Activator.java`, `TerminalPlugin.java`
- Create: `com.kk.pde.ds.webconsole.terminal/res/terminal.js` (placeholder)
- Modify: `pom.xml:268` (add `<module>com.kk.pde.ds.webconsole.terminal</module>` after `com.kk.pde.ds.rest`)
- Modify: `com.kk.pde.ds.feature/feature.xml` (a `<plugin>` block after `com.kk.pde.ds.rest`)
- Modify: `distribution/p2.product` (a `<plugin id=.../>` line in the plugin list, and `<plugin id="com.kk.pde.ds.webconsole.terminal" autoStart="true" startLevel="1" />` in `<configurations>`)
- Modify: `docs/superpowers/specs/2026-11-25-webconsole-terminal-design.md` (results)

**Interfaces:**
- Produces: the module, its manifest and its product wiring. Later tasks add classes to it.

Manifest (exact):

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: com.kk.pde.ds.webconsole.terminal
Bundle-SymbolicName: com.kk.pde.ds.webconsole.terminal
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.kk.pde.ds.webconsole.terminal.Activator
Import-Package: javax.servlet;version="3.1.0",
 javax.servlet.http;version="3.1.0",
 org.apache.felix.service.command;status=provisional;resolution:=optional,
 org.apache.felix.webconsole;version="[3.3,4)",
 org.osgi.framework;version="1.8.0",
 org.osgi.service.component.annotations;version="1.5.1";resolution:=optional
Service-Component: OSGI-INF/com.kk.pde.ds.webconsole.terminal.TerminalPlugin.xml
Bundle-Vendor: KK
Automatic-Module-Name: com.kk.pde.ds.webconsole.terminal
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
```

No `Bundle-ActivationPolicy: lazy`: the activator must run at bundle start. Gogo exports its package with the mandatory attribute `status=provisional`. Confirm it in the Gogo runtime manifest before you rely on it.

DS XML (exact):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.3.0" name="com.kk.pde.ds.webconsole.terminal.TerminalPlugin" activate="activate" deactivate="deactivate">
   <property name="felix.webconsole.label" value="terminal"/>
   <property name="felix.webconsole.title" value="Terminal"/>
   <reference name="CommandProcessor" interface="org.apache.felix.service.command.CommandProcessor" cardinality="0..1" policy="dynamic" bind="bindProcessor" unbind="unbindProcessor"/>
   <service>
      <provide interface="javax.servlet.Servlet"/>
   </service>
   <implementation class="com.kk.pde.ds.webconsole.terminal.TerminalPlugin"/>
</scr:component>
```

- [ ] **Step 1: Create the module files and the product wiring.** Prototype behavior: `Activator.start` replaces `System.out` and `System.err` with a tee into an in-memory list. `TerminalPlugin` extends `AbstractWebConsolePlugin`: `renderContent` writes a `<pre>` placeholder; `GET .../stream` writes the list as `text/event-stream`, flushes, then writes one event for each new line; `POST .../exec` runs the body in a `CommandSession` whose output streams go into the list; `GET .../res/terminal.js` returns the file.

- [ ] **Step 2: Build.**

Run: `./mvnw clean verify -DskipTests`
Expected: `BUILD SUCCESS`, and `distribution/target/products/com.kk.pde.ds.product/**/plugins/com.kk.pde.ds.webconsole.terminal_*.jar` exists.

- [ ] **Step 3: Start the product and prove each fact.** Start `./distribution/scripts/run.sh` in a terminal that stays open. Use `curl -s -u admin:admin` on `http://localhost:8080/system/console/terminal/...`.

| Fact | Check | Expected |
|------|-------|----------|
| 1 | `POST exec` with `ss`, then `POST exec` with `scr:list`; read `stream` | The bundle list and the component list are in the stream |
| 2 | `GET /system/console/terminal`, `GET .../res/terminal.js`, `POST .../exec` | 200 with the WebConsole frame; 200 with the file; 202 |
| 3 | `curl -N .../stream`, then `curl http://localhost:8080/api/greet` in a second shell | The new log line shows in the open `curl -N` in less than 1 second |
| 4 | Read `stream` after each of these three: (a) a Logback line: `curl http://localhost:8080/api/greet`. (b) a direct print: one `System.out.println("probe")` in `Activator.start`, after the tee. (c) a framework `!ENTRY` message: cause one framework error, for example start a second product instance while the first has port 8080 (see "Port 8080 is already in use" in `docs/troubleshooting.md`), and read the stream of the second instance on its own port | All three types show in the stream. Record each type that does not show. |
| 5 | Type `ss` in the local terminal | The local prompt and its output work as before |
| 6 | `POST exec` 600 times with `lb` (about 100 lines each), then open the page in a browser | The server answers, and heap use stays stable in `jcmd <pid> GC.heap_info` |

- [ ] **Step 4: Record the results.** In the spec, replace the text "They are **not tested**" with a table: fact, result, date. If fact 4 fails for one output type, write which type and stop for a decision of the user. If fact 1, 2, 3, 5 or 6 fails, stop for a decision of the user.

- [ ] **Step 5: Commit.**

```bash
git add pom.xml com.kk.pde.ds.feature/feature.xml distribution/p2.product com.kk.pde.ds.webconsole.terminal docs/superpowers/specs/2026-11-25-webconsole-terminal-design.md
git commit -m "feat(webconsole): add the terminal plugin skeleton and prove the design facts"
```

---

### Task 2: `OutputHub`, `TextSanitizer`, `SseEncoder`, `Token`

Pure Java. No OSGi and no servlet API.

**Files:**
- Create: `src/com/kk/pde/ds/webconsole/terminal/OutputHub.java`, `TextSanitizer.java`, `SseEncoder.java`, `Token.java` (all below `com.kk.pde.ds.webconsole.terminal/`)
- Test: `src_test/com/kk/pde/ds/webconsole/terminal/OutputHubTest.java`, `TextSanitizerTest.java`, `SseEncoderTest.java`, `TokenTest.java`

**Interfaces:**
- Produces:

```java
public final class OutputHub {
    public static final class Line { public final long seq; public final String type; public final String text; }
    public static final class Batch { public final long dropped; public final List<Line> lines; }
    public OutputHub(int capacity);
    public long append(String type, String text);          // types: "out", "err", "cmd", "done". Returns the sequence number (first is 1). Never blocks on a reader.
    public Batch since(long afterSeq);                     // lines with seq > afterSeq. dropped = number of lines between afterSeq and the oldest line in the buffer.
    public Batch await(long afterSeq, long timeoutMillis) throws InterruptedException; // as since(), but waits for a new line or for the timeout
    public long lastSeq();
}
public final class TextSanitizer { public static String clean(String text); public static String colorByLevel(String cleanLine); }
public final class SseEncoder { public static String event(long id, String type, String data); public static String comment(String text); }
public final class Token { public static String generate(); public static boolean matches(String expected, String given); }
```

- [ ] **Step 1: Write the failing tests.**

`OutputHubTest`:
- `appendGivesIncreasingSequenceFromOne`: three appends return 1, 2, 3; `lastSeq()` is 3.
- `sinceReturnsOnlyNewerLines`: after 5 appends, `since(3).lines` has seq 4 and 5, and `dropped` is 0.
- `ringDropsOldest`: capacity 3, 5 appends; `since(0).lines` has seq 3, 4, 5.
- `sinceReportsDroppedLines`: capacity 3, 10 appends; `since(2)` gives `dropped == 5` and lines 8, 9, 10.
- `awaitReturnsWhenALineArrives`: a second thread appends after 50 ms; `await(0, 2000)` returns one line in less than 1000 ms.
- `awaitTimesOutWithEmptyBatch`: `await(0, 50)` on an empty hub returns no lines.
- `appendNeverBlocksAndDropsOldest`: capacity 100; no reader; 100,000 appends complete in less than 2 seconds; `since(0).lines.size() == 100`.

`TextSanitizerTest`:
- `keepsPlainTextAndTab`: `"a\tb"` is not changed.
- `keepsSgrColor`: `"\u001b[31mred\u001b[0m"` is not changed.
- `removesCursorAndClearSequences`: `"a\u001b[2Jb\u001b[10;5Hc"` gives `"abc"`.
- `removesOscTitle`: `"\u001b]0;evil\u0007x"` gives `"x"`.
- `removesOtherC0`: `"a\u0007\u0008b\u0000"` gives `"ab"`.
- `removesLoneEscape`: `"a\u001bb"` gives `"ab"`.
- `colorByLevelMarksErrorRed`: a line that contains ` ERROR ` starts with `\u001b[31m` and ends with `\u001b[0m`.
- `colorByLevelMarksWarnYellow`: ` WARN ` gives `\u001b[33m`.
- `colorByLevelMarksEquinoxErrorEntry`: a line that starts with `!ENTRY ` and has severity field `4` gives `\u001b[31m`.
- `colorByLevelLeavesColoredLine`: a line that already contains `\u001b[` is not changed.
- `colorByLevelLeavesInfoLine`: a line with ` INFO ` is not changed.

`SseEncoderTest`:
- `encodesIdTypeAndData`: `event(7, "out", "hello")` equals `"id: 7\nevent: out\ndata: hello\n\n"`.
- `splitsEmbeddedLineBreaks`: data `"a\nb"` gives two `data:` lines.
- `commentStartsWithColon`: `comment("ping")` equals `": ping\n\n"`.

`TokenTest`:
- `generatesThirtyTwoHexCharacters`, `twoTokensDiffer`, `matchesEqual`, `rejectsDifferentNullAndEmpty`.

- [ ] **Step 2: Run the tests to see them fail.**

Run: `./mvnw test -pl com.kk.pde.ds.target,com.kk.pde.ds.webconsole.terminal`
Expected: compilation errors for the four missing classes.

- [ ] **Step 3: Implement the four classes.** `OutputHub`: an `ArrayDeque<Line>` with one lock; `append` holds the lock only to add, drop and `notifyAll`. `Token.generate` uses `SecureRandom` (16 bytes as hex). `Token.matches` uses `MessageDigest.isEqual`.

- [ ] **Step 4: Run the tests.**

Run: `./mvnw test -pl com.kk.pde.ds.target,com.kk.pde.ds.webconsole.terminal`
Expected: `Tests run: 25, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit.** `git commit -m "feat(webconsole): add the output buffer, the sanitizer and the stream encoder"`

---

### Task 3: `StdCapture` and the activator

**Files:**
- Create: `src/.../terminal/LineAssembler.java`, `StdCapture.java`, `TerminalRuntime.java`
- Modify: `src/.../terminal/Activator.java` (replace the prototype)
- Test: `src_test/.../terminal/LineAssemblerTest.java`, `StdCaptureTest.java`

**Interfaces:**
- Consumes: `OutputHub.append(String, String)`, `TextSanitizer.clean`, `TextSanitizer.colorByLevel`.
- Produces:

```java
final class LineAssembler {                                 // bytes in, complete lines out
    static final int MAX_LINE = 8192;                       // characters
    interface Sink { void line(String text); }
    LineAssembler(Charset charset, Sink sink);
    synchronized void write(byte[] b, int off, int len);   // splits on \n, removes one \r before it
    synchronized void flushPartial();                      // gives the text with no line end, if there is one
}
public final class StdCapture {
    public StdCapture(OutputHub hub, long partialFlushMillis);
    public void install();                                  // replaces System.out and System.err
    public void uninstall();                                // puts the originals back only if System.out / System.err are still its own streams
    public PrintStream originalErr();
}
public final class TerminalRuntime {                        // what the activator owns and the DS component reads
    public static TerminalRuntime get();                    // null before start and after stop
    public OutputHub hub();
    public String property(String key, String defaultValue);
    public PrintStream originalErr();
}
```

Each line goes to the hub as `hub.append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(text)))`, with type `out` or `err`.

- [ ] **Step 1: Write the failing tests.**

`LineAssemblerTest`:
- `emitsOneLinePerLineFeed`: `"a\nb\n"` gives `a`, `b`.
- `removesCarriageReturnBeforeLineFeed`: `"a\r\n"` gives `a`.
- `keepsPartialLineUntilLineFeed`: `"ab"` gives nothing; then `"c\n"` gives `abc`.
- `flushPartialEmitsTextWithoutLineEnd`: `"osgi> "` then `flushPartial()` gives `osgi> `; a second `flushPartial()` gives nothing.
- `decodesCharacterSplitAcrossWrites`: the UTF-8 bytes of `"ğ\n"` in two writes (first byte, then the other bytes) give `ğ`.
- `splitsLineLongerThanLimit`: 20,000 characters `x` and one `\n` give three lines of 8192, 8192 and 3616 characters.

`StdCaptureTest` (each test saves `System.out` / `System.err` and puts them back in `@AfterEach`):
- `installSendsOutputToOriginalAndToHub`: set `System.out` to a `ByteArrayOutputStream` stream, install, `System.out.println("hello")`; the byte stream contains `hello`, and `hub.since(0)` has one line with type `out` and text `hello`.
- `errGoesToHubWithTypeErr`.
- `partialLineShowsAfterFlushInterval`: `partialFlushMillis = 50`; `System.out.print("osgi> ")`; in less than 1000 ms the hub has the line `osgi> `.
- `uninstallRestoresOriginalStreams`: after `uninstall()`, `System.out` and `System.err` are the same objects as before `install()`.
- `uninstallLeavesAForeignStream`: after `install()`, set `System.out` to a third stream; `uninstall()` does not change `System.out`.
- `controlSequencesAreCleaned`: print `"a\u001b[2Jb"`; the hub line is `ab`.

- [ ] **Step 2: Run the tests to see them fail.** Same command as Task 2. Expected: compilation errors.

- [ ] **Step 3: Implement.** `StdCapture` uses one daemon thread (`terminal-flush`) that calls `flushPartial()` of both assemblers each `partialFlushMillis`; `uninstall()` stops it. The tee `PrintStream` wraps an `OutputStream` that writes to the original stream first and then to the assembler. `Activator.start`: make the `TerminalRuntime` (hub capacity from `terminal.scrollback.lines`), then `new StdCapture(hub, 200).install()`. `Activator.stop`: `uninstall()`, then clear the `TerminalRuntime`.

- [ ] **Step 4: Run the tests.** Expected: `Tests run: 37, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit.** `git commit -m "feat(webconsole): capture System.out and System.err into the output buffer"`

---

### Task 4: `Shell`

**Files:**
- Create: `src/.../terminal/Shell.java`
- Test: `src_test/.../terminal/ShellTest.java` (tier 1, Mockito), `src_test/.../terminal/ShellIT.java` (tier 2)

**Interfaces:**
- Consumes: `OutputHub.append`, `LineAssembler`, `TextSanitizer`.
- Produces:

```java
public final class Shell {
    public Shell(OutputHub hub, String prompt);
    public synchronized void bind(CommandProcessor processor);    // creates the one CommandSession: empty InputStream, out and err into the hub
    public synchronized void unbind(CommandProcessor processor);  // closes the session
    public boolean available();
    public boolean submit(String line);                            // false if not available. Appends type "cmd" with prompt + line, then queues the line.
    public void close();                                           // closes the session and stops the worker thread
}
```

The worker is one daemon thread (`terminal-shell`). For each line: `session.execute(line)`; if the result is not null, append `session.format(result, Converter.INSPECT)` as `out` lines; if it throws, append `\u001b[31m` + exception class and message + `\u001b[0m` as an `err` line; then append one `done` line with empty text.

- [ ] **Step 1: Write the failing tests.**

`ShellTest` (mock `CommandProcessor` and `CommandSession`):
- `notAvailableBeforeBind`: `submit("ss")` returns `false`, and the hub is empty.
- `submitEchoesThePromptAndTheCommand`: after `bind`, `submit("ss")`; the first hub line has type `cmd` and text `osgi> ss`.
- `runsCommandsInOrder`: submit `a`, `b`, `c`; Mockito `inOrder` verifies `execute("a")`, `execute("b")`, `execute("c")`.
- `everyCommandEndsWithDone`: after `submit("a")`, the hub gets a `done` line (wait up to 2 s).
- `exceptionBecomesRedErrLineAndSessionStaysOpen`: `execute("bad")` throws `IllegalArgumentException("nope")`; the hub has an `err` line that contains `nope` and `\u001b[31m`; a later `submit("a")` still runs.
- `unbindMakesShellNotAvailable`.
- `closeStopsWorker`: after `close()`, no live thread has the name `terminal-shell` (wait up to 2 s).

`ShellIT`:
- `runsARealGogoCommand`: register a service `Object` with the properties `osgi.command.scope=termtest` and `osgi.command.function=[hello]` and a method `public String hello() { return "hi-from-gogo"; }`. Get the `CommandProcessor` service, make an `OutputHub(100)` and a `Shell`, `bind`, `submit("termtest:hello")`. In less than 5 s the hub contains a line with `hi-from-gogo` and then a `done` line.

- [ ] **Step 2: Run the tests to see them fail.**

Run: `./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.webconsole.terminal`
Expected: compilation errors for `Shell`.

- [ ] **Step 3: Implement `Shell`.**

- [ ] **Step 4: Run the tests.** Expected: tier 1 `Tests run: 44`, tier 2 `Tests run: 1`, no failures.

- [ ] **Step 5: Commit.** `git commit -m "feat(webconsole): run Gogo commands in one shared session"`

---

### Task 5: `TerminalPlugin` (the servlet)

**Files:**
- Modify: `src/.../terminal/TerminalPlugin.java` (replace the prototype)
- Test: `src_test/.../terminal/TerminalPluginTest.java` (tier 1, Mockito request and response), `src_test/.../terminal/TerminalPluginIT.java` (tier 2)

**Interfaces:**
- Consumes: `TerminalRuntime.get()`, `OutputHub.await`, `SseEncoder`, `Token`, `Shell`.
- Produces the HTTP contract that `terminal.js` uses:

| Request | Answer |
|---------|--------|
| `GET /system/console/terminal` | The page inside the WebConsole frame. It contains `<div id="terminal" data-token="<token>" data-shell="true|false" data-prompt="<prompt>" data-scrollback="<n>">`, the toolbar markup, and the `<link>` / `<script>` tags for the files in `res/`. |
| `GET .../res/<name>` | The file from the bundle folder `res/`. Only the names `xterm.js`, `xterm.css`, `addon-search.js`, `addon-fit.js`, `terminal.js`, `terminal.css`. Other names: 404. |
| `GET .../stream` | `Content-Type: text/event-stream; charset=UTF-8`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`. Starts after the request header `Last-Event-ID` (or 0). For `dropped > 0`, first one event of type `gone` with the number as data. Then one event for each line (`id` = seq, `event` = line type, `data` = text). `flush` after each batch. `SseEncoder.comment("ping")` after 15 s with no line. Ends when a write fails. |
| `POST .../exec` | Header `X-Terminal-Token` must match, else 403. `terminal.shell.enabled=false`: 403. Shell not available: 503. Body = one command line in UTF-8, at most 4096 characters, else 413. An empty line: 204. Else `Shell.submit` and 202. |

- [ ] **Step 1: Write the failing tests.**

`TerminalPluginTest`:
- `execWithoutTokenIs403`, `execWithWrongTokenIs403`.
- `execWithTokenSubmitsTheLineAndAnswers202` (mock `Shell` through a package-visible setter or constructor).
- `execWhenShellDisabledIs403`: property `terminal.shell.enabled=false`.
- `execWhenShellNotAvailableIs503`.
- `execWithLineLongerThanLimitIs413`.
- `execWithEmptyLineIs204`.
- `resServesOnlyKnownNames`: `res/terminal.js` gives 200 with `application/javascript`; `res/../META-INF/MANIFEST.MF` and `res/unknown.js` give 404.
- `streamStartsAfterLastEventId`: hub with 3 lines, header `Last-Event-ID: 2`; the output contains `id: 3` and not `id: 2` (end the loop with a response writer that throws on the second flush).
- `streamReportsGoneLines`: hub with capacity 2 and 5 lines, no header; the first event is `event: gone` with `data: 3`.
- `pageContainsTokenAndFlags`: `renderContent` output contains `data-token="` + the token and `data-shell="true"`.

`TerminalPluginIT`:
- `pluginIsRegisteredWithLabel`: a `javax.servlet.Servlet` service exists with `felix.webconsole.label=terminal`.
- `printFromTheTestReachesTheHub`: `System.out.println("probe-" + nonce)`; `TerminalRuntime.get().hub()` contains the line in less than 2 s.

- [ ] **Step 2: Run the tests to see them fail.** Same command as Task 4.

- [ ] **Step 3: Implement.** `activate` makes the token and the `Shell` (prompt from `terminal.prompt`). `deactivate` closes the `Shell`. `bindProcessor` / `unbindProcessor` call `Shell.bind` / `unbind`. `doGet` checks `getPathInfo()` for `/terminal/stream` and `/terminal/res/` before it calls `super.doGet`. The stream uses a blocking loop on `OutputHub.await(last, 15000)`.

- [ ] **Step 4: Run the tests.** Expected: tier 1 `Tests run: 55`, tier 2 `Tests run: 3`, no failures.

- [ ] **Step 5: Commit.** `git commit -m "feat(webconsole): serve the terminal page, the output stream and the command endpoint"`

---

### Task 6: The browser part

**Files:**
- Create: `com.kk.pde.ds.webconsole.terminal/res/xterm.js`, `xterm.css`, `addon-search.js`, `addon-fit.js`, `LICENSE-xterm.txt` (vendored), `terminal.js` (replace the placeholder), `terminal.css`
- Create: `com.kk.pde.ds.webconsole.terminal/res/VERSIONS.txt` (package name, version and source URL of each vendored file)

**Interfaces:**
- Consumes: the HTTP contract of Task 5.

- [ ] **Step 1: Vendor xterm.js.** Download the npm tarballs of `@xterm/xterm@6.0.0`, and of the newest `@xterm/addon-search` and `@xterm/addon-fit` that name xterm 6 as a peer, from `https://registry.npmjs.org/`. Copy `lib/xterm.js`, `css/xterm.css`, `lib/addon-search.js`, `lib/addon-fit.js` and the `LICENSE` file. Write the exact versions and URLs to `VERSIONS.txt`. If a file name is different in the tarball, use the UMD build and record the real name.

- [ ] **Step 2: Write `terminal.js`.** No framework, no build step. Behavior:

| Function | Behavior |
|----------|----------|
| Setup | Read `data-token`, `data-shell`, `data-prompt`, `data-scrollback` from `#terminal`. `new Terminal({ scrollback, convertEol: true, cursorBlink: true })`. Load the fit add-on and the search add-on. Fit on window resize. |
| Stream | `new EventSource("terminal/stream")` relative to the page URL. For `out`, `err`, `cmd`: erase the input line (`\r\x1b[2K`), write the text and `\r\n`, draw the input line again. For `gone`: write a dim line `[N earlier lines are gone]`. For `done`: nothing more. On error: show a dim line `[connection lost, connecting again]` one time. |
| Input line | A string buffer and a cursor index. Printable keys insert. Backspace, Delete, Left, Right, Home, End edit. Up and Down move in the history (`sessionStorage` key `terminal.history`, at most 200 entries). Enter sends the line with `fetch("terminal/exec", { method: "POST", headers: { "X-Terminal-Token": token }, body: line })` and clears the buffer. Status 403: show `[the page is old, load it again]`. |
| No shell | With `data-shell="false"`, or after status 503: show no prompt and ignore the keys. |
| Copy | `Ctrl+C` / `Cmd+C` with a selection: `navigator.clipboard.writeText(term.getSelection())`. `Ctrl+C` with no selection: clear the input buffer. |
| Paste | Text with no line break: insert at the cursor. Text with line breaks: split into lines, drop empty lines, show `[paste: N lines. Enter = run all, Esc = cancel]`; Enter sends the lines in sequence (wait for each `fetch`), Esc drops them. |
| Toolbar | Search field: Enter = `findNext`, Shift+Enter = `findPrevious`, with buttons for the two. **Clear** and `Ctrl+L`: `term.clear()`. **Follow**: when off, do not call `scrollToBottom()` on new output. |

- [ ] **Step 3: Build and run.**

Run: `./mvnw clean verify`
Expected: `BUILD SUCCESS`. Then start `./distribution/scripts/run.sh`.

- [ ] **Step 4: Test in a real browser.** Open `http://localhost:8080/system/console/terminal` (login `admin` / `admin`). Run each row and record PASS or FAIL. All rows must pass.

| # | Action | Expected |
|---|--------|----------|
| 1 | Load the page | The earlier output of the product shows, then the prompt |
| 2 | `curl http://localhost:8080/api/greet` in a shell | The log line shows on the page in less than 1 second |
| 3 | Type `ss`, Enter | The bundle list shows, then a new prompt |
| 4 | Type `scr:list`, Enter | The component list shows |
| 5 | Type `scr:li`, then run the `curl` of row 2 | The log line shows above; `osgi> scr:li` is intact below it |
| 6 | Up arrow | The line `scr:list` comes back |
| 7 | Type `xyz`, Enter | A red error line shows, and the prompt comes back |
| 8 | Search for `ACTIVE`, Enter two times | The first and then the second match are selected |
| 9 | Select a line, `Cmd+C`, paste into the input line | The text is in the input line and did not run |
| 10 | Paste two lines (`ss` and `lb`) | The question shows; Enter runs both in sequence |
| 11 | Load the page again | The same scrollback shows again |
| 12 | Open a second tab, type `lb` there | The first tab also shows `osgi> lb` and the output |
| 13 | Stop and start the product, type `ss` in the old page | The page says that it is old; after a reload it works |
| 14 | Start with `-Dterminal.shell.enabled=false` | The output shows and there is no prompt |

- [ ] **Step 5: Commit.** `git commit -m "feat(webconsole): add the xterm.js terminal page"`

---

### Task 7: Fat JAR entry, documentation and full verification

**Files:**
- Modify: `fatjar/src/main/java/com/kk/pde/ds/fatjar/Main.java:55` (add `cfg("com.kk.pde.ds.webconsole.terminal", 1, true);` in the block "Start level 1")
- Create: `docs/webconsole-terminal.md`
- Modify: `README.md`, `CLAUDE.md`, `SECURITY.md`, `docs/architecture.md`, `docs/webconsole-and-health-checks.md`, `docs/build.md`, `docs/testing.md`, `docs/running.md`, `docs/fat-jar.md`, `docs/troubleshooting.md`

- [ ] **Step 1: Add the fat JAR start-level entry.** Build it: `./mvnw -f fatjar clean package`. Start the fat JAR as `docs/fat-jar.md` shows. Expected: no warning `Add it to BUNDLE_CONFIG` for the new bundle, and the page works.

- [ ] **Step 2: Write `docs/webconsole-terminal.md`** with the `anthropic-skills:asd-ste100` skill. Sections: what it is; how to open it; keys and toolbar (table); the three properties (table); how it works (the parts diagram of the spec); limits (no output from before the bundle start, no tab completion, no `Ctrl+C`, no commands that read input, shared console); security (login, token, the output can contain secrets, `terminal.shell.enabled`); "Use the plugin in a different application" (the 4 imports, WebConsole 4.x, start level advice, Gogo optional: mark "not tested" for a runtime with no Gogo); the manual test procedure of Task 6.

- [ ] **Step 3: Update the other documents.** Search first: `git grep -n -E "54 tier-1|6 tier-2|19 modules|system/console|webconsole" -- '*.md' pom.xml`.
- `CLAUDE.md`: Documentation Map row; Module Structure line; the test counts in "Testing" and the test class list; a short "Terminal" paragraph below "Felix WebConsole".
- `README.md`: feature list and doc index.
- `SECURITY.md`: a row in "Known exposures" (`/system/console/terminal`: command execution and console output behind the WebConsole login `admin` / `admin`), and a checklist item (change the WebConsole password or set `terminal.shell.enabled=false`).
- `docs/build.md`: the module count and the project structure tree.
- `docs/testing.md`: the test counts and the new test classes.
- `docs/running.md`: the three properties.
- `docs/architecture.md`, `docs/webconsole-and-health-checks.md`, `docs/fat-jar.md` (start-level table), `docs/troubleshooting.md` (entry: "The Terminal page shows no output": causes are a component that replaced `System.out` later, and output from before the bundle start).

- [ ] **Step 4: Full verification.**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify`
Expected: `BUILD SUCCESS`; tier 1 total = 54 + 55 = 109; tier 2 total = 6 + 3 = 9.

Run: `./mvnw -f tools/pde2tycho verify` and `tools/doccheck/check-docs.sh`
Expected: `BUILD SUCCESS`; exit 0.

Run: `unzip -l distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz 2>/dev/null; tar tzf distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz | grep -E "webconsole.terminal|junit|mockito"`
Expected: one line with `com.kk.pde.ds.webconsole.terminal_`, and no line with `junit` or `mockito`.

Run: `unzip -l <the bundle jar> | grep -E "res/|src_test|Test.class"`
Expected: the files of `res/`; no test class.

- [ ] **Step 5: Commit.** `git commit -m "docs(webconsole): document the terminal plugin and wire the fat JAR"`

- [ ] **Step 6: Open the pull request** to `main` from `feat/webconsole-terminal`. The description lists the updated documents and the results of the browser test table. The 4 CI jobs must pass.
