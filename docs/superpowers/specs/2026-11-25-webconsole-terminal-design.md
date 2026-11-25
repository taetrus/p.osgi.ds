# WebConsole Terminal Plugin: Design

**Date:** 2026-11-25
**Status:** Draft, revision 2. It waits for approval.

## Intent

An OSGi application shows its output and its `osgi>` prompt only in the terminal that started it.
This design adds a **Felix WebConsole plugin** that shows the same things in the browser, with the
look of a real terminal. The user reads all console output there. The user also types console
commands there, for example `ss` and `scr:list`.

The plugin is one bundle. It is **not specific to this repository**. Each OSGi application that has
a Felix WebConsole can install it. One instance serves one application.

The user asked for a terminal look, find and search, cut, copy and paste, and text formatting.
The user runs few commands. Tab completion is "nice to have" and is not in this version.

Prior art: Apache Karaf has a Gogo plugin for its WebConsole. Apache Felix has no such plugin
(`felix-dev/webconsole-plugins` has only `ds`, `event`, `memoryusage`, `obr` and `packageadmin`).

## Goals

1. A WebConsole page **Terminal** at `/system/console/terminal`.
2. The page shows all output that the application writes to `System.out` and `System.err`, as a
   live stream, in a terminal emulator. This includes log output that a console appender writes,
   direct prints of any bundle, and framework messages.
3. The page has a prompt. The user types a Gogo command and sees its output.
4. Find and search in the scrollback, copy of a selection, and paste of text.
5. Formatting: the page shows the ANSI colors that the application writes. The plugin also colors
   lines that contain `ERROR` or `WARN`.
6. Output that arrives while the user types does not damage the input line.
7. A long scrollback: 50,000 lines by default.
8. The bundle works in a different application with no code change.

## Non-goals (version 1)

- Tab completion.
- `Ctrl+C` to stop a command that runs.
- Commands that ask a question and read an answer. The command gets end-of-input.
- A mirror of the Gogo session of the local terminal. What the user types in the local terminal
  does not show on the page.
- Output that the application wrote before the plugin bundle started.
- WebSocket, JLine, or a server-side terminal emulation.
- Felix WebConsole 5 (`jakarta.servlet`). Version 1 is for WebConsole 4.x (`javax.servlet`).
- A change to the WebConsole login.

## Anti-goals

- A dependency on a logging library, on a product, or on a bundle of this repository.
- A new third-party Java dependency.
- A shell endpoint that answers without the WebConsole login.
- An application thread that waits for a browser.

## Constraints

- Java 8 bytecode. `Bundle-RequiredExecutionEnvironment: JavaSE-1.8`.
- Imports of the bundle: `javax.servlet`, `org.apache.felix.webconsole`, `org.osgi.framework`, and
  `org.apache.felix.service.command` (Gogo, optional). Nothing else.
- Configuration through framework properties (`BundleContext.getProperty`). Thus `config.ini` and
  `-D` flags both work.
- The browser files come from the bundle, not from a CDN. The plugin works offline.
- In this repository: hand-written DS XML, tests in `src_test/`, docs in ASD-STE100.

## Approaches

| | A. Line mode (chosen) | B. Telnet bridge | C. JLine 3 |
|---|---|---|---|
| Who edits the input line | The browser | The server (Equinox telnet console) | The server (JLine) |
| Transport | HTTP POST and Server-Sent Events | WebSocket to a telnet socket | WebSocket |
| Tab completion | No | Yes | Yes |
| New ports | None | One telnet port with no password | None |
| New Java dependencies | None | None | JLine 3, `gogo.jline` |
| Works outside Equinox | Yes | No | Yes |

The user chose **A**. Reasons: it gives all requested functions, it opens no new port, and it
adds no dependency. The only loss is tab completion, and the user runs few commands.
B was not chosen because of the telnet port without a password. C was not chosen because of the
new dependencies and the quantity of code.

## Design

### One shared console

The real console of an application is one thing: one output stream and one prompt. The plugin
keeps this model. It has **one output buffer and one Gogo session for the application**. Each
browser page that opens the plugin is a view of the same console.

- All pages show the same output, including the commands that a different page ran.
- A refresh of the page loses nothing. The page gets the buffer again.
- There is no session object for each page, and thus no limit on the number of pages.

### Parts

```
 Browser (xterm.js + terminal.js)
   |  GET  /system/console/terminal                 page (contains the token)
   |  GET  /system/console/terminal/res/...         static files
   |  GET  /system/console/terminal/stream          Server-Sent Events (output)
   |  POST /system/console/terminal/exec            one command line (input)
   v
 TerminalPlugin (AbstractWebConsolePlugin, service javax.servlet.Servlet)
   |-- Shell      --> Gogo CommandProcessor.createSession(...) / session.execute(line)
   |-- OutputHub  <-- StdCapture (System.out and System.err)
                  <-- Shell (echo of each command, and its output)
```

| Part | Responsibility |
|------|----------------|
| `StdCapture` | At bundle start, it replaces `System.out` and `System.err` with streams that write to the original stream and to the `OutputHub`. At bundle stop, it puts the original streams back. |
| `OutputHub` | A ring buffer of the last 50,000 lines (`terminal.scrollback.lines`). Each line has a sequence number. The hub gives each new line to all open streams. |
| `Shell` | One Gogo `CommandSession`. One worker thread runs the commands one at a time, in the order of arrival. |
| `TextSanitizer` | Removes dangerous control characters from the output (see Security). |
| `TerminalPlugin` | The WebConsole plugin, with the properties `felix.webconsole.label=terminal` and `felix.webconsole.title=Terminal`. It renders the page, serves the static files, and handles `stream` and `exec`. |
| `terminal.js` | The browser part: the line editor, the history, the stream reader and the toolbar. Hand-written, no framework. |

### Capture of `System.out` and `System.err`

- `StdCapture` starts in the bundle activator, not in a DS component. Thus the capture starts as
  early as the start level of the bundle permits, and it does not wait for SCR or for the WebConsole.
- Each write goes to the original stream first. The local terminal shows the same output as before.
- The capture makes lines from the bytes. If a line has no end after 200 ms (for example a
  prompt), the hub shows the partial line.
- The capture never calls code that writes to `System.out`. Thus it cannot call itself.
- The plugin has no logging dependency. It reports its own errors on the original `System.err`.
- At bundle stop, `StdCapture` puts the original streams back only if `System.out` is still its
  own stream. If a different component replaced the stream after it, `StdCapture` changes nothing.
- A writer that kept a reference to the original stream before the bundle started is not
  captured. See "Facts to verify first".

### Output stream

- The browser opens `stream` with `EventSource`. The server answers with `text/event-stream`.
- At connection, the server sends the lines in the buffer. Then it sends each new line.
- Each event has an `id` (the sequence number). When the connection breaks, `EventSource`
  connects again and sends `Last-Event-ID`. The server continues after that number. Thus the
  browser gets no line two times.
- If the browser asks for a number that is no longer in the buffer, the server sends one line
  that says how many lines are gone.
- The server sends a comment line each 15 seconds. This finds a closed connection.
- The threads of the application never wait for a browser. A slow browser gets its lines from
  the buffer later, or loses the oldest ones.
- The page sets the scrollback of xterm.js to the same number of lines as the buffer.

### Commands

- The browser sends `POST exec` with the token and one command line.
- The server answers immediately with status 202. The hub gets the line `osgi> <command>`, so all
  pages and the buffer show what ran. Then the command runs on the worker thread. Its output goes
  to the hub.
- An event `done` tells the pages that the command is complete.
- The session input is empty. A command that reads input gets end-of-input.
- `exit`, `close` and `shutdown` stop the application, the same as in the local terminal.
- A Gogo error (for example `gogo: CommandNotFoundException`) shows in red.
- If the application has no Gogo `CommandProcessor` service, the page is read-only.

### Browser

- Vendored files in the bundle: `@xterm/xterm` 6.0.0 (`lib/xterm.js`, `css/xterm.css`),
  `@xterm/addon-search` and `@xterm/addon-fit`. License: MIT. The bundle includes the license
  text. The exact file names and add-on versions are not tested. The builder confirms them.
- The line editor is in `terminal.js`: insert, Backspace, Delete, Left, Right, Home, End, and the
  history with Up and Down. The history stays in `sessionStorage` for the tab.
- When output arrives, the page erases the prompt line, writes the output, and writes the prompt
  and the typed text again.
- Copy: select with the mouse, then `Ctrl+C` or `Cmd+C`. Without a selection, `Ctrl+C` clears the
  input line.
- Paste: `Ctrl+V` or `Cmd+V` pastes the full text.
  - Text with no line break goes into the input line at the cursor.
  - Text with line breaks is a list of commands. The page shows the number of lines and asks one
    time: Enter runs all lines in sequence, Esc cancels. Windows Terminal does the same.
- Cut applies to the input line only. The scrollback is read-only.
- Toolbar: a search field (next, previous), **Clear**, and a **Follow** switch that stops or
  starts the automatic scroll to the last line. `Ctrl+L` clears the screen.

What the user sees:

```
 [ Search: ________ ] [<] [>]   [Clear]   [Follow: on]
 ----------------------------------------------------------------
 14:02:11.345 [main] INFO  c.k.p.d.app.App - Hello world!
 osgi> ss
 id  State     Bundle
 0   ACTIVE    org.eclipse.osgi_3.23.0.v20250228-0640
 14:02:15.120 [qtp-21] WARN  c.k.p.d.rest.GreetServlet - ...        (yellow)
 !ENTRY org.apache.felix.http.jetty 4 0 2026-11-25 14:02:16.001     (red)
 osgi> scr:li_
```

### Security

- All URLs are below `/system/console/`. The WebConsole login protects them.
- A user who can log in to the WebConsole can already install and start a bundle on the Bundles
  page. Thus the shell gives no new type of access. It makes that access easier.
- **Cross-site requests:** the plugin makes a random token of 128 bits at activation. The page
  contains the token. `exec` needs the token in the header `X-Terminal-Token`. A page from a
  different origin cannot read the token and cannot send that header.
- **Escape sequences in output:** the output can contain terminal control sequences. The server
  keeps the color and style sequences (`ESC [ ... m`). It removes all other escape sequences and
  the other C0 control characters (but not tab). Thus the application colors show, but output
  cannot move the cursor, clear the screen or change the title of the page.
- **Switch:** `terminal.shell.enabled=false` makes the page read-only. `exec` then answers with
  status 403, and the page shows no prompt. The default is `true`. The output stream stays on.
  Each user who can log in to the WebConsole can read the console output. This is accepted.
- The output can contain secrets that the application prints. The docs must say this.

### Error handling

| Condition | Behavior |
|-----------|----------|
| No `CommandProcessor` service | The page shows "Shell not available" and stays read-only. |
| Token missing or wrong (for example after a restart of the application) | Status 403. The page tells the user to load it again. |
| The command throws an exception | The message shows in red. The session stays open. |
| A different component replaces `System.out` after the plugin | The plugin does not capture the output after that. It does not replace the stream again. |

### Configuration

| Property | Default | Meaning |
|----------|---------|---------|
| `terminal.scrollback.lines` | `50000` | Lines in the server buffer and in the browser scrollback |
| `terminal.shell.enabled` | `true` | `false` makes the page read-only |
| `terminal.prompt` | `osgi> ` | The prompt text on the page |

### Tests

- **Tier 1 (`*Test`):** `OutputHub` (ring buffer, sequence numbers, continue after a number, lines
  that are gone), `StdCapture` (write to both targets, line assembly, partial line, put back),
  `TextSanitizer`, the event-stream encoder, the token check.
- **Tier 2 (`*IT`, live Equinox):** a real Gogo command runs and its output arrives in the hub. A
  `System.out.println` from a different bundle arrives in the hub. The plugin registers as a
  `javax.servlet.Servlet` service with the label `terminal`.
- **Browser:** the repository has no JavaScript test tooling, and this design adds none. A manual
  test procedure goes into the new doc. The builder runs it in a real browser before completion.

## Integration in this repository

The plugin is generic. This repository is its first user. The integration is small and separate:

- A new module `com.kk.pde.ds.webconsole.terminal`, with the layout of `com.kk.pde.ds.rest`.
- The parent pom and `com.kk.pde.ds.feature/feature.xml` list it.
- `distribution/p2.product` lists it. This is the only product with a WebConsole. A product that
  gets a WebConsole later needs only this one line.
- Start level: as low as the product permits, so that the capture starts early. The builder
  selects the value. The fat JAR launcher has its own start-level table, which needs the same entry.
- Documentation: new `docs/webconsole-terminal.md`, with a section "Use the plugin in a different
  application". Update `README.md`, `CLAUDE.md`, `SECURITY.md`, `docs/architecture.md`,
  `docs/webconsole-and-health-checks.md`, `docs/build.md`, `docs/testing.md` and `docs/running.md`.

## Facts to verify first

These points came from the API signatures and from memory. A prototype of the plugin bundle
(Task 1 of the plan) tested them on the running product (Equinox 3.23, Felix WebConsole 4.3.16,
Gogo runtime 1.1.6, Felix HTTP Jetty 4.2.26, JDK 21, macOS):

| Fact | Result | Date |
|------|--------|------|
| 1 | Pass. `ss` and `scr:list` ran in a session from `createSession`. The bundle list and the component list were in the stream. | 2026-11-25 |
| 2 | Pass. The page gave 200 in the WebConsole frame. `res/terminal.js` gave 200 (`text/javascript`). `POST exec` gave 202. | 2026-11-25 |
| 3 | Pass. A Logback line from `GET /api/greet` arrived in an open `curl -N` stream 17 ms after the request. | 2026-11-25 |
| 4 | Pass for all three types: Logback console lines, a direct `System.out.println` from the activator, and `!ENTRY` from `-consoleLog`. A second instance on port 8081 with HTTPS on the busy port 8080 caused the `!ENTRY`. | 2026-11-25 |
| 5 | Pass. After the replacement, `ss` and `lb` in the local terminal gave their output and the local prompt. | 2026-11-25 |
| 6 | Pass on the server. With 1,800 `lb` commands, the buffer stayed at 50,000 lines (4.4 MB of events). The page, the stream and `/api/greet` answered in less than 2 ms. The heap after a full GC was 14.6 MB before, then 19.8 MB and 18.4 MB with a full buffer. The browser part is **not tested**. | 2026-11-25 |

Notes from the prototype:

- The plugin bundle must start before the Gogo runtime (start level 1 here, Gogo at 3). The Gogo
  runtime replaces `System.out` with its own stream for each thread, and that stream must wrap
  the capture. The prototype did not test the reverse order.
- The `lb` command writes about 45 lines, not 100.
- The `ss` command of the session writes only to the session streams. It does not write to the
  local terminal.

1. A session from `CommandProcessor.createSession` can run `ss` and `scr:list`.
2. `AbstractWebConsolePlugin` gets the GET and POST requests for the paths below its label, and
   serves the static files.
3. Jetty and the WebConsole send each event of the stream immediately (no buffering).
4. After the replacement of `System.out`, the page gets these three types of output: the Logback
   console appender, a direct `System.out.println`, and the `!ENTRY` messages of `-consoleLog`.
5. The local terminal and its `osgi>` prompt work as before after the replacement.
6. The browser and the server stay responsive with a full buffer of 50,000 lines.

If one type of output in point 4 is not captured, find a second source for that type and tell the
user. If one of the other points fails, stop and change the design with the user.

## Left to the builder

- The class layout inside the bundle and the names of the private classes.
- Blocking or asynchronous servlet for the stream.
- The exact ANSI colors, and the rule that finds `ERROR` and `WARN` lines.
- The text of the messages on the page.
- How a lone carriage return in the output shows.
