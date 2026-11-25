# WebConsole Terminal Plugin: Design

**Date:** 2026-11-25
**Status:** Draft. It waits for approval.

## Intent

The product shows its output and its `osgi>` prompt only in the terminal that started it. This
design adds a Felix WebConsole plugin that shows the same things in the browser, with the look of
a real terminal. The user reads the log output there. The user also types console commands there,
for example `ss` and `scr:list`.

The user asked for a terminal look, find and search, cut, copy and paste, and text formatting.
The user runs few commands. Tab completion is "nice to have" and is not in this version.

Prior art: Apache Karaf has a Gogo plugin for its WebConsole. Apache Felix has no such plugin
(`felix-dev/webconsole-plugins` has only `ds`, `event`, `memoryusage`, `obr` and `packageadmin`).

## Goals

1. A WebConsole page **Terminal** at `http://localhost:8080/system/console/terminal`.
2. The page shows the log output of the product as a live stream, in a terminal emulator.
3. The page has a prompt. The user types a Gogo command and sees its output.
4. Find and search in the scrollback, copy of a selection, and paste into the input line.
5. Formatting: ANSI colors. The log level sets the color of a log line.
6. A log line that arrives while the user types does not damage the input line.

## Non-goals (version 1)

- Tab completion.
- `Ctrl+C` to stop a command that runs.
- Commands that ask a question and read an answer. The command gets end-of-input.
- A mirror of the Gogo session of the local terminal. The page has its own session.
- Capture of raw `System.out` and `System.err`. No shipped bundle prints directly (verified with `git grep`).
- WebSocket, JLine, or a server-side terminal emulation.
- The ECF products and the spike products. They have no HTTP service.
- A change to the WebConsole login (`admin` / `admin`).

## Anti-goals

- A new Java dependency. A dependency that needs slf4j 2.x is forbidden in this repository.
- A shell endpoint that answers without the WebConsole login.
- A change to the source of an existing bundle. Only the feature, the products and the docs change.

## Constraints

- Java 8 bytecode. `Bundle-RequiredExecutionEnvironment: JavaSE-1.8`.
- Target platform as it is: Felix WebConsole 4.3.16 (`javax.servlet`), Felix HTTP Jetty 4.2.26
  (Jetty 9.4.54), Gogo runtime 1.1.6, Equinox console 1.4.800, Logback 1.2.11, slf4j 1.7.
- DS component XML in `OSGI-INF/` is hand-written. SCR reads the XML.
- The product must work offline. The browser files come from the bundle, not from a CDN.
- Tests go in `src_test/` (`*Test` for tier 1, `*IT` for tier 2).
- Documentation follows ASD-STE100 and must pass `tools/doccheck/check-docs.sh`.

## Approaches

| | A. Line mode (chosen) | B. Telnet bridge | C. JLine 3 |
|---|---|---|---|
| Who edits the input line | The browser | The server (Equinox telnet console) | The server (JLine) |
| Transport | HTTP POST and Server-Sent Events | WebSocket to a telnet socket | WebSocket |
| Tab completion | No | Yes | Yes |
| New ports | None | One telnet port with no password | None |
| New Java dependencies | None | None | JLine 3, `gogo.jline` |
| Log lines and the input line | The browser keeps the input line intact | Log lines can cut through the input | Depends on the emulation |

The user chose **A**. Reasons: it gives all requested functions, it opens no new port, and it
adds no dependency. The only loss is tab completion, and the user runs few commands.
B was not chosen because of the telnet port without a password. C was not chosen because of the
new dependencies and the quantity of code.

## Design

### Bundle

A new bundle `com.kk.pde.ds.webconsole.terminal`, with the layout of `com.kk.pde.ds.rest`
(`src/`, `src_test/`, `META-INF/MANIFEST.MF`, `OSGI-INF/*.xml`, `build.properties`, `pom.xml`).
The parent pom, `com.kk.pde.ds.feature/feature.xml` and `distribution/p2.product` list it.
The start level is 4, the same as `com.kk.pde.ds.rest`.

The fat JAR supports the terminal. It takes all bundles from the `plugins/` directory of the
product, so it gets the new bundle with its browser files and no other change. Its launcher has
its own start-level table (`BUNDLE_CONFIG` in `fatjar/src/main/java/com/kk/pde/ds/fatjar/Main.java`).
Add `cfg("com.kk.pde.ds.webconsole.terminal", 4, true)` there. Without this line, the launcher
prints a warning for the bundle. The fat JAR gets no separate automatic test. The manual test
procedure includes one start of the fat JAR.

### Parts

```
 Browser (xterm.js + terminal.js)
   |  GET  /system/console/terminal                 page
   |  GET  /system/console/terminal/res/...         static files
   |  POST /system/console/terminal/session         make a session (ID and token)
   |  GET  /system/console/terminal/stream?s=ID     Server-Sent Events (output)
   |  POST /system/console/terminal/exec            one command line (input)
   v
 TerminalPlugin (AbstractWebConsolePlugin, DS service javax.servlet.Servlet)
   |-- ShellSessions --> Gogo CommandProcessor.createSession(...) / session.execute(line)
   |-- OutputHub     <-- LogbackCapture      (Logback appender on the root logger)
                     <-- FrameworkLogCapture (OSGi LogReaderService listener)
```

| Part | Responsibility |
|------|----------------|
| `TerminalPlugin` | The WebConsole plugin. DS properties `felix.webconsole.label=terminal` and `felix.webconsole.title=Terminal`. It renders the page, serves the static files, and sends the `stream` and `exec` requests to the other parts. |
| `OutputHub` | A ring buffer of the last 2000 log lines (`-Dterminal.buffer.lines`). The hub gives each new log line to all sessions. A new session starts with a copy of this buffer. |
| `LogbackCapture` | Adds an appender to the Logback root logger at activation and removes it at deactivation. It formats each event with the pattern of the console appender and adds a color for the level. |
| `FrameworkLogCapture` | Listens to the OSGi `LogReaderService`. It gives the framework messages that `-consoleLog` prints as `!ENTRY` lines, for example a bundle that does not resolve. |
| `ShellSessions` | One session for each browser tab. A session has an ID, a token, a Gogo `CommandSession`, a worker thread that runs its commands one at a time, and its own line buffer with its own sequence numbers. |
| `TextSanitizer` | Removes the control characters from text that goes to the browser (see Security). |
| `terminal.js` | The browser part: the line editor, the history, the stream reader and the toolbar. Hand-written, no framework. |

### Sessions

- The page GET makes no session. At load, `terminal.js` sends `POST session`. The answer is a
  JSON object with the session ID and the token.
- The page keeps the ID and the token in `sessionStorage`. After a refresh, the page uses the
  same session again. If the server does not know the ID (status 410), the page makes a new session.
- Limit: 8 sessions. A `POST session` above the limit gets status 503.
- A session with no open stream for 30 minutes closes.

### Output stream

- The browser opens `stream` with `EventSource`. The server answers with `text/event-stream`.
- Each session has one line buffer (2000 lines) and one sequence. All lines for the session go
  into it in the order of arrival: the log lines (event `log`) and the output of the commands of
  this session (event `out`). Thus one number is sufficient for the position of the browser.
- A new session starts with a copy of the log lines in the `OutputHub`.
- The output of a command goes only to the session that ran it. It is in no other buffer.
- Each event has an `id` (the sequence number of the session). When the connection breaks,
  `EventSource` connects again and sends `Last-Event-ID`. The server continues after that number.
  Thus the browser gets no line two times.
- When the buffer of a session is full, the oldest lines go. If the browser asks for a number
  that is gone, the server sends one line that says how many lines it dropped.
- The server sends a comment line each 15 seconds. This finds a closed connection.
- A slow browser must not block the product. The threads that log never wait for a browser.
- The **Logs** switch works in the page only. When it is off, the page ignores the `log` events.
  When the user sets it to on again, the page shows new log lines only.

### Commands

- The browser sends `POST exec` with the session ID, the token and one command line.
- The server answers immediately with status 202. The command runs on the worker thread of the
  session. Its output goes to the stream. An event `done` tells the browser to show the prompt again.
- The session input is empty. A command that reads input gets end-of-input.
- `exit`, `close` and `shutdown` stop the product, the same as in the local terminal.
- A Gogo error (for example `gogo: CommandNotFoundException`) shows in red as output.

### Browser

- Vendored files in the bundle: `@xterm/xterm` 6.0.0 (`lib/xterm.js`, `css/xterm.css`),
  `@xterm/addon-search` and `@xterm/addon-fit`. License: MIT. The bundle includes the license
  text. The exact file names and add-on versions are not tested; the builder confirms them.
- The line editor is in `terminal.js`: insert, Backspace, Delete, Left, Right, Home, End, and the
  history with Up and Down. The history stays in `sessionStorage` for the tab.
- When a log line arrives, the page erases the prompt line, writes the log line, and writes the
  prompt and the typed text again.
- Copy: select with the mouse, then `Ctrl+C` or `Cmd+C`. Without a selection, `Ctrl+C` clears the
  input line.
- Paste: `Ctrl+V` or `Cmd+V` inserts the text at the cursor. A paste never runs a command. If
  the pasted text has a line break, the page inserts the first line only and shows a note with
  the number of lines that it ignored. The user must push Enter to run the line.
- Cut applies to the input line only. The scrollback is read-only.
- Toolbar: a search field (next, previous), **Clear**, and a **Logs** switch that hides or shows
  the log lines. `Ctrl+L` clears the screen.

What the user sees:

```
 [ Search: ________ ] [<] [>]   [Clear]   [Logs: on]
 ----------------------------------------------------------------
 14:02:11.345 [main] INFO  c.k.p.d.app.App - Hello world!
 osgi> ss
 id  State     Bundle
 0   ACTIVE    org.eclipse.osgi_3.23.0.v20250228-0640
 14:02:15.120 [qtp-21] WARN  c.k.p.d.rest.GreetServlet - ...        (yellow)
 osgi> scr:li_
```

### Security

- All four URLs are below `/system/console/`. The WebConsole login protects them.
- A user who can log in to the WebConsole can already install and start a bundle on the Bundles
  page. Thus the shell gives no new type of access. It makes that access easier.
- **Cross-site requests:** each session has a random token of 128 bits. `exec` needs the token in
  the header `X-Terminal-Token`. `session` needs the header `X-Terminal-Request: 1`. A page from a
  different origin cannot send these headers and cannot read the token.
- **Escape sequences in output:** a log message can contain terminal control characters. The
  server removes `ESC` and the other C0 control characters (but not tab) from each message and
  each command output. Then it adds its own color codes.
- **Switch:** `-Dterminal.shell.enabled=false` makes the page read-only. `exec` then answers with
  status 403, and the page shows no prompt. The default is `true`. The log stream stays on. Each
  user who can log in to the WebConsole can read the log output of the product. This is accepted.
- `SECURITY.md` gets a new entry in "Known exposures" and a new item in the checklist.

### Error handling

| Condition | Behavior |
|-----------|----------|
| The slf4j binding is not Logback | `LogbackCapture` logs one warning and stays off. The shell works. |
| No `CommandProcessor` service | The page shows "Shell not available" and stays read-only. |
| Token missing or wrong | Status 403. |
| Session ID not known (for example after a restart of the product) | Status 410. The page makes a new session and tells the user. |
| The command throws an exception | The message shows in red. The session stays open. |

### Tests

- **Tier 1 (`*Test`):** `OutputHub` (ring buffer, sequence numbers, continue after a number, drop
  on a full queue), `TextSanitizer`, the event-stream encoder, the token check, the log line format.
- **Tier 2 (`*IT`, live Equinox):** a session runs a real Gogo command and its output arrives in
  the hub. The plugin registers as a `javax.servlet.Servlet` service with the label `terminal`.
- **Browser:** the repository has no JavaScript test tooling, and this design adds none. A manual
  test procedure goes into the new doc. The builder runs it in a real browser before completion.

### Documentation

New: `docs/webconsole-terminal.md`. Update: `README.md`, `CLAUDE.md` (module structure, doc map,
test counts), `SECURITY.md`, `docs/architecture.md`, `docs/webconsole-and-health-checks.md`,
`docs/build.md` (module count), `docs/testing.md` (test counts), `docs/running.md` (the new properties).

## Facts to verify first

These points come from the API signatures and from memory. They are **not tested**. The first
task of the plan must prove them with a small running prototype, because the design depends on them.

1. A session from `CommandProcessor.createSession` can run `ss` and `scr:list`.
2. `AbstractWebConsolePlugin` gets the GET and POST requests for the paths below its label, and
   serves the static files.
3. Jetty and the WebConsole send each event of the stream immediately (no buffering).
4. The bundle can cast the slf4j `ILoggerFactory` to the Logback `LoggerContext` and add an appender.
5. The Equinox `LogReaderService` gives the events that `-consoleLog` prints.

If point 5 fails, version 1 ships without the framework messages. If one of the other points
fails, stop and change the design with the user.

## Left to the builder

- The class layout inside the bundle and the names of the private classes.
- Blocking or asynchronous servlet for the stream.
- The exact ANSI colors for each level.
- The text of the messages on the page.
