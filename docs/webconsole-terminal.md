# WebConsole Terminal Plugin

This document describes the bundle `com.kk.pde.ds.webconsole.terminal`. The bundle adds a
**Terminal** page to the Felix WebConsole. The page shows the console output of the application
and runs Gogo commands. The bundle does not depend on this repository. You can use it in a
different application (see [Use the plugin in a different application](#use-the-plugin-in-a-different-application)).

The design is in `docs/superpowers/specs/2026-11-25-webconsole-terminal-design.md`.

## Contents

- [What it is](#what-it-is)
- [Open the page](#open-the-page)
- [Keys and toolbar](#keys-and-toolbar)
- [Properties](#properties)
- [How it works](#how-it-works)
- [Limits](#limits)
- [Security](#security)
- [Use the plugin in a different application](#use-the-plugin-in-a-different-application)
- [Manual test procedure](#manual-test-procedure)
- [Related documents](#related-documents)

## What it is

An OSGi application shows its output and its `osgi>` prompt only in the terminal that started
it. This plugin shows the same output in the browser, in a terminal emulator (xterm.js). The user
can also type Gogo commands, for example `ss` and `scr:list`.

The page shows:

- All text that the application writes to `System.out` and `System.err`. This includes log lines,
  direct prints and framework messages.
- The ANSI colors of the output. The page also colors lines that contain `ERROR` or `WARN`.
- A scrollback of 50,000 lines (default).

The console is shared. The plugin has one output buffer and one Gogo session for the
application. Each open page is a view of the same console. Thus all pages show the same commands
and the same output. A reload loses nothing, because the page gets the buffer again.

## Open the page

1. Start the product. See [running.md](running.md).
2. Open http://localhost:8080/system/console/terminal in a browser.
3. Type the user name `admin` and the password `admin`.

The **Terminal** entry is also in the menu of the WebConsole.

## Keys and toolbar

The input line works in the browser. The page does not use a server-side line editor.

| Key or control | Action |
|----------------|--------|
| Enter | Runs the command line. |
| Left, Right, Home, End | Moves the cursor in the input line. |
| Backspace, Delete | Acts on the character before (Backspace) or after (Delete) the cursor. |
| Up, Down | Shows an earlier or a later command of the history. The history stays in `sessionStorage` of the browser tab. |
| `Ctrl+C` or `Cmd+C` | With a selection: copies the selection. Without a selection: clears the input line. |
| `Ctrl+V` or `Cmd+V` | Pastes text (see below). |
| `Ctrl+L` | Clears the screen. |
| Search field, `<`, `>` | Searches the scrollback. Enter in the field finds the next match. Shift+Enter finds the previous match. |
| **Clear** | Clears the screen. |
| **Follow** | On: the page scrolls to the last line when output arrives. Off: the page stays where it is. |

Paste:

- Text with no line break goes into the input line. It does not run.
- Text with line breaks is a list of commands. The page shows the number of lines and asks one
  time. Enter runs each line as its own request, in order. Esc cancels.

The page shows the command echo `osgi> <command>` when the command runs. All open pages show it.
Then the output follows, then the end of the command.

A line without a line end (for example a prompt) shows after 200 to 400 ms. The capture uses a
quiet-interval rule for this.

A Gogo error (for example `CommandNotFoundException`) shows in red.

## Properties

The plugin reads framework properties (`BundleContext.getProperty`). Thus `config.ini` and `-D`
flags both work.

| Property | Default | Meaning |
|----------|---------|---------|
| `terminal.scrollback.lines` | `50000` | Lines in the server buffer and in the browser scrollback. |
| `terminal.shell.enabled` | `true` | `false` makes the page read-only. `exec` answers with status 403. The output stream stays on. |
| `terminal.prompt` | `osgi> ` | The prompt text on the page. |

Example:

```bash
./distribution/scripts/run.sh -Dterminal.shell.enabled=false
```

## How it works

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
| `Activator` | Starts `StdCapture` when the bundle starts. It does not wait for SCR or for the WebConsole. |
| `StdCapture` | Replaces `System.out` and `System.err` with streams that write to the original stream and to the hub. At bundle stop, it puts the original streams back, but only if `System.out` is still its own stream. |
| `OutputHub` | A ring buffer of the last `terminal.scrollback.lines` lines. Each line has a sequence number. |
| `Shell` | One Gogo `CommandSession`. One worker thread runs the commands one at a time, in the order of arrival. |
| `TextSanitizer` | Strips dangerous control characters from the output. |
| `TerminalPlugin` | The WebConsole plugin. Label `terminal`, title `Terminal`. It renders the page, serves the static files, and handles `stream` and `exec`. |
| `terminal.js` | The browser part: line editor, history, stream reader, toolbar. |

URLs and answers:

| URL | Answer |
|-----|--------|
| `GET /system/console/terminal` | The page. It contains the token. |
| `GET /system/console/terminal/stream` | Server-Sent Events. Each event has an `id`. After a break, the browser sends `Last-Event-ID` and gets no line two times. The server sends a comment line each 15 seconds. |
| `POST /system/console/terminal/exec` | One command line. The request needs the header `X-Terminal-Token`. |

Status codes of `exec`:

| Status | Meaning |
|--------|---------|
| 202 | The command is queued. |
| 204 | The line is empty. Nothing runs. |
| 400 | The line contains a control character: a character below `0x20` (also line break) other than tab, `0x7F`, or `0x80` to `0x9F`. Nothing runs. A line break would run a second statement. A tab (`0x09`) is accepted, because it is whitespace for Gogo. |
| 403 | The token is missing or wrong, or `terminal.shell.enabled` is `false`. |
| 413 | The line is longer than 4096 characters. |
| 503 | There is no Gogo `CommandProcessor`, or the queue does not accept the line. Tier-1 tests (`TerminalPluginTest`, `NoGogoTest`) cover the status. A product run on 2026-11-25 gave 503 for `ss` after a stop of the Gogo runtime. |

The server checks in this sequence: token (403), `terminal.shell.enabled` (403), Gogo
available (503), length (413), empty line (204), control characters (400). A request that passes
all checks gets 202. The server strips one trailing line break (`\n` or `\r\n`) before the
checks. On 2026-11-25 a product run gave 400 for `ss` + line break + `lb`, 400 for a line
with `ESC [2J`, and 202 for `lb` + tab + `terminal`. The page changes each tab to a space, in
typed text and in each line of a paste with more than one line (not tested in a browser).

Vendored browser files (MIT license, in `res/`): xterm.js 6.0.0, addon-search 0.16.0 and
addon-fit 0.11.0. `res/VERSIONS.txt` lists the source and the SHA-256 of each file. The plugin
works offline, because it uses no CDN.

### Start order

The bundle must start before the Gogo runtime. A tier-2 test showed this. The Gogo runtime puts
its own stream into `System.out` before each command. If this bundle starts later, printed
output stops showing after the first command.

A stop and a start of this bundle while Gogo runs has the same effect. An update or a refresh of
the bundle does this. A product run on 2026-11-25 showed it:

1. `update 13` (this bundle) ran from the page. The bundle was ACTIVE again, and the page had a
   new token.
2. The warning below was line 1 on the page. It was not in the console log of the product.
3. `ss` ran, and its output showed on the page.
4. Two `GET /api/greet called` log lines, one before `ss` and one after `ss`, were in the console
   log. They were not in the stream.

Thus restart the application after an update of this bundle.

Start level 1 is the earliest level for a bundle. A bundle cannot set its own start level. The
product sets the level. In this repository the bundle has level 1 (`distribution/p2.product`)
and the Gogo bundles have level 3.

If the bundle starts after the Gogo runtime, it writes this warning on the original
`System.err`. It also shows the warning as a red line on the page:

```text
Terminal: this bundle started after the Gogo runtime, or it was restarted while Gogo runs. Printed output stops showing after the first command. Give com.kk.pde.ds.webconsole.terminal a lower start level than org.apache.felix.gogo.runtime, for example 1, and restart the application after an update of this bundle.
```

An `update` of this bundle from the page itself works. Before a fix on 2026-11-25, the close of
the Gogo session interrupted the `update` command, and the bundle stayed INSTALLED. Now the shell
closes its session after the command ends. `ShellIT` and the product run above showed this.

The wait for the command has a limit of 30 seconds (`Shell.DEFERRED_CLOSE_MILLIS`). After that
limit, the shell closes the session and stops its worker. This interrupts the command. Thus a
line such as `stop <this bundle>` followed by a long command does not keep the old worker, the old
session and the old class loader alive. `ShellIT` showed this with a limit of 500 ms. A run
on the product is not tested.

### Stop of the Gogo runtime

A stop of the Gogo runtime makes SCR call the unbind method of the plugin, and the shell loses
the `CommandProcessor`. When the stop comes from a page command, the shell closes its session
only after the command, the same as for this bundle. `ShellIT` showed this. But Gogo 1.1.6 itself closes all its sessions when it stops.
Thus `stop <Gogo runtime>` still ends with a red `java.lang.InterruptedException` line.

After a `stop <Gogo runtime>` from a Gogo command, the next start of the Gogo runtime fails with
`IllegalStateException: Thread Print Stream already set`. The bundle stays RESOLVED. The local
console gives the same result (`gogo: InterruptedException: null`). Thus this is Gogo behavior,
not behavior of this plugin. A stop and a start from the WebConsole Bundles page (not from a Gogo
command) work. A product run on 2026-11-25 showed these facts:

1. A stop from the Bundles page: the bundle was RESOLVED, a reload of the terminal page gave
   `data-shell="false"`, and `exec` gave 503.
2. A start from the Bundles page: the bundle was ACTIVE, a reload gave `data-shell="true"`, and
   `ss` gave 202 and its output on the stream.
3. `stop 21` from the page: the bundle was RESOLVED. A later start failed as described above.

Thus do not stop, update or refresh the Gogo runtime from a Gogo command. Use the Bundles page, or
restart the application. An `update` or a `refresh` of the Gogo runtime is not tested.

## Limits

- **No output from before the bundle start.** The capture starts with the bundle. Earlier output
  is not captured. A writer that kept a reference to the original stream before the start is also
  not captured.
- **No tab completion.**
- **No `Ctrl+C` to stop a running command.** The key clears the input line.
- **No commands that read input.** The session input is empty. The command gets end-of-input.
- **One shared console.** The page does not mirror the Gogo session of the local terminal. What
  you type in the local terminal does not show on the page.
- `exit`, `close` and `shutdown` stop the application, the same as in the local terminal.
- A different component that replaces `System.out` after this bundle is not captured after that.
  The plugin does not replace the stream again.
- WebConsole 4.x only (`javax.servlet`).
- If the application has no Gogo `CommandProcessor` service when the page loads, the page is
  read-only and shows no prompt (`data-shell="false"`). A page that loaded before Gogo was
  available stays read-only until a reload. If Gogo stops after the page loads, `exec` answers
  503 and the page becomes read-only (see [Use the plugin in a different application](#use-the-plugin-in-a-different-application)).
- A stop of the Gogo runtime from a Gogo command prevents a later start of the Gogo runtime (see
  [Stop of the Gogo runtime](#stop-of-the-gogo-runtime)).
- After a product restart, an open page says that the connection is closed and becomes
  read-only. Reload the page. This was tested (row 13 of the manual test).

## Security

- All URLs are below `/system/console/`. The WebConsole login protects them. The default login
  of this repository is `admin` / `admin`. See [`SECURITY.md`](../SECURITY.md).
- A user who can log in to the WebConsole can already install and start a bundle. The shell gives
  no new type of access. It makes that access easier.
- **Token.** The plugin makes a random token of 128 bits when it starts. The page contains the
  token. `exec` needs the token in the header `X-Terminal-Token`. A page from a different origin
  cannot read the token (not tested). After an application restart the token is new, and an old page gets 403.
- **Escape sequences.** The server keeps the color and style sequences (`ESC [ ... m`). It strips
  all other escape sequences and the other C0 control characters (but not tab). The command echo
  `osgi> <command>` goes through the same filter. `exec` refuses a line with a control character
  other than tab (status 400). Tier-1 tests (`TextSanitizerTest`, `ShellTest`, `TerminalPluginTest`) show that the
  output and the echo keep no other escape sequence. Thus output cannot move the cursor, clear the
  screen or change the title of the page. The effect in a browser is not tested.
- **The output can contain secrets** that the application prints, for example keys in log lines.
  Each user who can log in to the WebConsole can read all console output.
- **Switch.** `terminal.shell.enabled=false` makes the page read-only. Use it when you do not want
  commands from the browser. Change the WebConsole password (`felix.webconsole.password`) before
  you use the page outside localhost.

## Use the plugin in a different application

The bundle needs these imports and nothing else:

| Import | Version | Note |
|--------|---------|------|
| `javax.servlet`, `javax.servlet.http` | 3.1.0 | WebConsole 4.x |
| `org.apache.felix.webconsole` | `[3.3,4)` | The WebConsole API |
| `org.osgi.framework` | 1.8.0 | |
| `org.apache.felix.service.command` | `[1.0,2)` | Gogo. Optional. |

Steps:

1. Build the bundle (`./mvnw clean verify`) and take the jar from the p2 repository or from the
   product. Install it in the application.
2. Give the bundle a lower start level than the Gogo runtime. Use level 1, or the lowest level
   that your application permits.
3. Open `/system/console/terminal` of the application.
4. Set the properties of the [Properties](#properties) table if needed.

Gogo is optional. The tier-1 test `NoGogoTest` loads the plugin through a class loader that
refuses the Gogo package. The test showed these facts:

- `getDeclaredMethods()` of the plugin class works. SCR and `HttpServlet.doOptions` call it.
- The public constructor and `activate` work.
- The page renders. It is read-only (`data-shell="false"`).
- `exec` answers 503. On 503, `terminal.js` shows `[shell not available]` and stops the input (not tested in a browser without Gogo).

An application with no Gogo bundle is not tested. WebConsole 5 (`jakarta.servlet`) is not supported.

## Manual test procedure

The repository has no JavaScript test tooling. Use this procedure in a browser. The test ran
in headless Google Chrome 158, driven over the DevTools protocol, on 2026-11-25. 14 rows passed.
After two fixes, rows 1, 5 and 10 ran again and passed.

Start the product with `./distribution/scripts/run.sh`. Row 14 needs
`-Dterminal.shell.enabled=false`.

| # | Action | Expected result |
|---|--------|-----------------|
| 1 | Open `/system/console/terminal`. | The page shows the start-up output. The last line is the prompt `osgi> `. The terminal fills the window height. |
| 2 | Run `curl http://localhost:8080/api/greet`. | The log line `GET /api/greet called` shows on the page in less than 1 second. |
| 3 | Type `ss`. Press Enter. | The page shows `osgi> ss`, the bundle list, and a new prompt. |
| 4 | Type `scr:list`. Press Enter. | The page shows the component list and a new prompt. |
| 5 | Type `scr:li` and do not press Enter. Run the `curl` of row 2. | The log lines show above the input line. The input line `osgi> scr:li` stays intact. |
| 6 | Press Up. | The input line shows `scr:list`. |
| 7 | Type `xyz`. Press Enter. | A red `CommandNotFoundException` line shows. The prompt comes back. |
| 8 | Type `ACTIVE` in the search field. Press Enter two times. | The first and then the second match are selected. |
| 9 | Select text. Press `Cmd+C` (or `Ctrl+C`). Paste a single line. | The clipboard holds the selection. The line goes into the input line. It does not run. |
| 10 | Paste two lines (`ss` and `lb`). Press Enter. | The page asks once. After Enter, `ss` runs, then `lb`. Each echo shows before its own output. |
| 11 | Reload the page. | The same lines show again. |
| 12 | Open a second tab. Run `lb` in it. | Both tabs show the same echo and the same output. |
| 13 | Stop the product, start it again. Run `ss` in the old page. | The old page says that the connection is closed and gets 403. After a reload, `ss` works. |
| 14 | Start with `-Dterminal.shell.enabled=false`. Open the page. | The page shows output and no prompt. Typing runs nothing. `exec` answers 403. |

The tests and the browser run used JDK 21. A run on Java 8 used `curl` only (2026-11-25, Temurin 1.8.0_512,
macOS x86_64, the product of commit `fc7ef9c`):

1. The product started. The terminal bundle was `ACTIVE`.
2. The page gave a token. All six `res/` files gave 200.
3. `exec` gave 403 with no token, 202 for `ss` and 400 for a line with `ESC`.
4. The stream gave `osgi> ss`, the bundle list and the end of the command.
5. The log line of a later `GET /api/greet` came on the stream.
6. The log had no start-order warning and no `UnsupportedClassVersionError`.

Not tested: a browser on Java 8, a headed (visible) browser, Firefox, Safari, Windows, and a runtime with no Gogo.

## Related documents

- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the WebConsole.
- [testing.md](testing.md): the tests of the bundle.
- [troubleshooting.md](troubleshooting.md#the-terminal-page-shows-no-output): the Terminal page shows no output.
- [`SECURITY.md`](../SECURITY.md): known exposures.
