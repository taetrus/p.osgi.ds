# WebConsole Terminal Plugin

This document describes the bundle `com.kk.pde.ds.webconsole.terminal`. The bundle adds a
**Terminal** page to the Felix WebConsole. The page shows the console output of the application
and runs Gogo commands. The bundle does not depend on this repository. You can use it in a
different application (see [Use the plugin in a different application](#use-the-plugin-in-a-different-application)).

The design is in `docs/superpowers/specs/2026-11-25-webconsole-terminal-design.md`. The design of
the export, the themes and the font settings is in
`docs/superpowers/specs/2026-11-27-terminal-export-theme-font-design.md`.

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
- [Check the terminal on Windows](#check-the-terminal-on-windows)
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
| **Export** | Downloads the captured output as the text file `terminal-<yyyyMMdd-HHmmss>.log` (see [Export](#export)). |
| **Theme** | Sets the colors: Dark (default), Light, High contrast or Solarized Dark. |
| **Font** | Sets the font family: Default, Menlo, Consolas, DejaVu Sans Mono or Courier New. |
| `-`, `+` | Makes the font smaller or larger, from 10 px to 24 px. The default is 15 px, the xterm.js default. |

Theme, font family and font size stay in `localStorage` of the browser, in the key
`terminal.settings`. Thus they apply to all tabs of the same browser, after a reload too. They
do not apply to a different browser or a different computer. A stored value that is not valid
gives the default value for that setting.

A font family shows only when the computer of the viewer has the font. Otherwise the browser uses
its `monospace` font. Each theme other than Dark sets all 16 ANSI colors, so a red error line stays
readable on its background.

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
   |  GET  /system/console/terminal/export          the output as a text file
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
| `TextSanitizer` | Strips dangerous control characters from the output. `plain` also strips the color sequences, for the export. |
| `TerminalPlugin` | The WebConsole plugin. Label `terminal`, title `Terminal`. It renders the page, serves the static files, and handles `stream`, `export` and `exec`. |
| `terminal.js` | The browser part: line editor, history, stream reader, toolbar, theme and font settings. |

URLs and answers:

| URL | Answer |
|-----|--------|
| `GET /system/console/terminal` | The page. It contains the token. |
| `GET /system/console/terminal/stream` | Server-Sent Events. Each event has an `id`. After a break, the browser sends `Last-Event-ID` and gets no line two times. The server sends a comment line each 15 seconds. |
| `GET /system/console/terminal/export` | The output as a text file (see [Export](#export)). No token. |
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

### Export

`GET /system/console/terminal/export` gives all lines of the server buffer as one file:

- Status 200, `Content-Type: text/plain; charset=UTF-8`, `Cache-Control: no-store`.
- `Content-Disposition: attachment; filename="terminal-<yyyyMMdd-HHmmss>.log"`. The time is the
  time of the server.
- One line for each output line and for each command echo (`osgi> ss`), oldest first. Each line
  ends with `\n`, on Windows too.
- No color sequences and no other escape sequences.
- When the buffer already dropped old lines, the first line is `[<n> earlier lines are gone]`.
- The export adds no time stamp. The log lines of the application have their own time stamps.

The export reads the server buffer, not the screen. Thus **Clear** clears the screen only. The
export still contains each line. The export also works when the page is read-only (`terminal.shell.enabled=false`, or no Gogo).

Get the file with `curl`:

```bash
curl -s -u admin:admin -o terminal.log http://localhost:8080/system/console/terminal/export
```

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

The close of the session changed again later on 2026-11-25 (the limit below). A product run on
that final code showed these facts after `update 13` (this bundle) from the page:

1. `exec` gave 202. Five seconds later the bundle was ACTIVE.
2. A reload gave a new token and `data-shell="true"`.
3. `ss` gave 202. Its output was on the stream, with `13 ACTIVE` for this bundle.
4. About 39 seconds after the update, `jstack` showed one `terminal-shell` thread, the new one.
   The thread of the old bundle revision was gone. No `terminal-shell-close` thread was left.

The wait for the command has a limit of 30 seconds (`Shell.DEFERRED_CLOSE_MILLIS`). After that
limit, the shell closes the session and stops its worker. This interrupts the command. Thus a
line such as `stop <this bundle>` followed by a long command does not keep the old worker, the old
session and the old class loader alive. `ShellIT` showed this with a limit of 500 ms. A run
on the product is not tested.

### Stop of the Gogo runtime

A stop of the Gogo runtime makes SCR call the unbind method of the plugin, and the shell loses
the `CommandProcessor`. When the stop comes from a page command, the shell closes its session
only after the command, the same as for this bundle. `ShellIT` showed this. But on the product,
`stop <Gogo runtime>` still ends with a red `java.lang.InterruptedException` line. A probable
cause, from the Gogo 1.1.6 bytecode and not tested: Gogo closes all its sessions when it stops.

After a `stop <Gogo runtime>` from a Gogo command, the next start of the Gogo runtime fails with
`IllegalStateException: Thread Print Stream already set`. The bundle stays RESOLVED. A stop and
a start from the WebConsole Bundles page (not from a Gogo command) work. Product runs on
2026-11-25 showed these facts:

1. A stop from the Bundles page: the bundle was RESOLVED, a reload of the terminal page gave
   `data-shell="false"`, and `exec` gave 503.
2. A start from the Bundles page: the bundle was ACTIVE, a reload gave `data-shell="true"`, and
   `ss` gave 202 and its output on the stream.
3. `stop 21` from the page: the bundle was RESOLVED. A later start failed as described above.
4. `stop 21` from the local console, with this bundle ACTIVE: the console wrote
   `gogo: InterruptedException: null`, and a later start failed the same way.
5. The same result with this bundle stopped from the Bundles page before the `stop 21`. The
   error was in the Equinox log file in `configuration/`, not in the console log.
6. The same result in a copy of the product where this bundle never started (no `start` flag
   in `config.ini`).

Thus the failure does not come from this plugin. A probable cause, from the Gogo 1.1.6 bytecode
and not tested: after its stop, Gogo puts its own stream back into `System.out` when the command
ends.

Thus do not stop, update or refresh the Gogo runtime from a Gogo command. Use the Bundles page, or
restart the application. An `update` or a `refresh` of the Gogo runtime is not tested.

## Limits

- **The export holds only the last `terminal.scrollback.lines` lines** of the server buffer.
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
- After a product restart, an open page cannot run commands. Reload the page. Row 13 of the
  manual test shows the two messages that the page can write.

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
- **Export.** `export` needs no token, because it only reads. `stream` gives the same lines with
  no token. Without the login, `export` answers 401 and gives no line. A downloaded file stays
  on the computer of the user after the session.
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

Rows 15 to 24 ran on 2026-11-27 in Google Chrome 158 on macOS, driven over the DevTools
protocol, and with `curl`. The rows ran two times: one time headless, and one time in a Chrome
window (1200 x 800). All rows passed in the two runs. The checks read the state of xterm.js, the
computed colors at the edges of the terminal, and screenshots. Firefox and Safari are not tested.
A run on Windows is not tested.

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
| 13 | Stop the product, start it again. Run `ss` in the old page. | One of two results (see below the table). After a reload, `ss` works. |
| 14 | Start with `-Dterminal.shell.enabled=false`. Open the page. | The page shows output and no prompt. Typing runs nothing. `exec` answers 403. |
| 15 | Run `xyz`. Select each theme. | Each theme changes the background and the text at once. The red `CommandNotFoundException` line is readable in each theme. The edges of the terminal have the background of the theme. |
| 16 | Press `+` until 24. Press `-` until 10. | The size label follows. It stops at 24 and at 10. The terminal fills its box at each size. |
| 17 | Type a line longer than the width. Change the font size. | The input line stays intact. The cursor stays at its end. |
| 18 | Select each font family. | The font changes, or the browser uses its `monospace` font. The `ss` columns stay aligned. |
| 19 | Reload. Open a second tab. | Theme, font family and font size stay the same. The two lists show them. |
| 20 | In the browser console, set `localStorage` key `terminal.settings` to `{bad`. Reload. Then set it to `{"theme":"x","fontSize":999,"fontFamily":"constructor"}`. Reload. | Each time: Dark, 15 px, Default. The terminal works. The console shows no error. |
| 21 | Click **Export**. | The file `terminal-<yyyyMMdd-HHmmss>.log` downloads. It has no escape character. It contains the line `osgi> xyz`. |
| 22 | Click **Clear**. Click **Export**. | The file still contains the earlier lines. |
| 23 | Run `curl -s -u admin:admin -D - http://localhost:8080/system/console/terminal/export`. | Status 200. The headers `Content-Type: text/plain;charset=utf-8`, `Content-Disposition` and `Cache-Control: no-store`. Then the log lines. |
| 24 | Run the `curl` of row 23 without `-u admin:admin`. | Status 401. No log line. |

Row 13 has two results. The timing of the restart selects one of them:

- The stream connects again after the restart. `ss` gets 403, and the page writes
  `[the page is old, load it again]`. The macOS run showed this (screenshot and network log).
  Windows runs also showed it.
- The browser stops the stream connection. A probable cause (not tested) is a non-200 answer during the
  restart. The page writes `[the connection is closed, load the page again]` and becomes
  read-only. `ss` sends no request. One Windows run showed this.

While the product is down, the page writes `[connection lost, connecting again]` first.

The tests and the browser run used JDK 21. A run on Java 8 used `curl` only (2026-11-25, Temurin 1.8.0_512,
macOS x86_64, the product of commit `fc7ef9c`):

1. The product started. The terminal bundle was `ACTIVE`.
2. The page gave a token. All six `res/` files gave 200.
3. `exec` gave 403 with no token, 202 for `ss` and 400 for a line with `ESC`.
4. The stream gave `osgi> ss`, the bundle list and the end of the command.
5. The log line of a later `GET /api/greet` came on the stream.
6. The log had no start-order warning and no `UnsupportedClassVersionError`.

A run on Windows used the same 14 rows (2026-11-25, GitHub Actions `windows-latest`, image
`win25-vs2026 20261116.315.1`):

- Microsoft Windows Server 2025 Datacenter 10.0.26100, Temurin 21.0.13, Node v22.23.5.
- Headless Google Chrome 157.0.8103.79, driven over the DevTools protocol.
- The product started with `distribution\scripts\run.bat` and stopped with `Stop-Process`.
- Result on the final code: 14 rows passed. Row 1 also ran 8 more times, each time in a new tab.
  The 8 runs passed.

Before a fix, row 1 failed in 2 of 4 runs. The terminal did not fill the window height. The
runs recorded this sequence for `#terminal`:

1. At about 220 ms the top was at 255 px. `terminal.js` set the height to 534 px (805 - 255 - 16).
2. About 30 ms later, after the `load` event, the WebConsole page moved the top to 132 px. The
   window height did not change.
3. The page got no `resize` event, so the height stayed 534 px. The expected height was 657 px.
   An empty band of 123 px stayed below the terminal.

The fix: `terminal.js` observes `document.body` with a `ResizeObserver`. Content above the
terminal that changes also changes the body size, so `fit()` runs again. `fit()` changes the
height only when the value differs. Thus the observer does not loop. The `resize` listener
stays. Without `ResizeObserver`, the page fits one time more in an animation frame (not tested).
On the final code, each run recorded the same move (top 255 px, then 132 px), and the height
became 657 px. In three workflow runs, all 27 runs of row 1 passed (3 in the full test, 24 in new tabs).

A macOS run on the final code repeated rows 1 and 5 in headless Google Chrome 158.0.8127.38
(2026-11-25). Row 1 passed in 5 of 5 new tabs (top 132 px, height 665 px, window height 813
px). Row 5 passed.

Not tested: a browser on Java 8, a headed (visible) browser, Firefox, Safari, a browser on
Windows other than headless Chrome, and a runtime with no Gogo.

## Check the terminal on Windows

Use these commands to check the terminal on Windows without a browser. The commands work in
Windows PowerShell 5.1 and in PowerShell 7. The `windows-docs` CI job runs them in both shells.

1. Start the product with `distribution\scripts\run.bat`. See [running.md](running.md).
2. Open a second PowerShell window.
3. Paste this block. It gets the token, sends `exec` two times and reads the stream for 15 seconds.

   ```powershell
   $auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin')) }
   $page = Invoke-WebRequest -UseBasicParsing -Headers $auth -Uri http://localhost:8080/system/console/terminal
   $token = [regex]::Match($page.Content, 'data-token="([0-9a-f]+)"').Groups[1].Value
   $exec = 'http://localhost:8080/system/console/terminal/exec'
   try { Invoke-WebRequest -UseBasicParsing -Method Post -Uri $exec -Headers $auth -ContentType 'text/plain; charset=utf-8' -Body 'ss' } catch { "no token: $([int]$_.Exception.Response.StatusCode)" }
   $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $exec -Headers ($auth + @{ 'X-Terminal-Token' = $token }) -ContentType 'text/plain; charset=utf-8' -Body 'ss'
   "with token: $($r.StatusCode)"
   curl.exe -s -N --max-time 15 -u admin:admin http://localhost:8080/system/console/terminal/stream
   ```

4. Make sure that the output contains these lines:
   - `no token: 403`. Without the token, `exec` refuses the command.
   - `with token: 202`. With the token, `exec` queues the command.
   - `data: osgi> ss`, then the bundle list, then `event: done`.
5. In the same window, paste this block. It runs `echo äöü` and looks for `äöü` on the stream.

   ```powershell
   $umlauts = [string][char]0xE4 + [char]0xF6 + [char]0xFC
   $u = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $exec -Headers ($auth + @{ 'X-Terminal-Token' = $token }) -ContentType 'text/plain; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes("echo $umlauts"))
   curl.exe -s -N --max-time 15 -u admin:admin -o "$env:TEMP\terminal-stream.txt" http://localhost:8080/system/console/terminal/stream
   "echo status: $($u.StatusCode), umlauts on the stream: $((Get-Content -Encoding UTF8 "$env:TEMP\terminal-stream.txt") -ccontains "data: $umlauts")"
   ```

6. Make sure that the output is `echo status: 202, umlauts on the stream: True`.

Notes:

- `curl.exe` stops after 15 seconds with exit code 28. This is expected, because the stream does not end. The 15 seconds give the command time to complete on a slow computer.
- Use `curl.exe`, not `curl`. In Windows PowerShell 5.1, `curl` is an alias of `Invoke-WebRequest`.
- `-ContentType 'text/plain; charset=utf-8'` is necessary. Without it, Windows PowerShell 5.1
  sends a form content type.
- The block of step 5 uses character codes for `äöü`. Thus the encoding of a script file has no effect.
  The body goes as UTF-8 bytes.
- Step 5 checks the shell output path. The command output goes from the Gogo session through a
  UTF-8 stream of the plugin to the hub, and then to the stream. Step 5 does not check the
  capture of `System.out` (`StdCapture`). That path uses the console charset of the JVM.

## Related documents

- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the WebConsole.
- [testing.md](testing.md): the tests of the bundle.
- [troubleshooting.md](troubleshooting.md#the-terminal-page-shows-no-output): the Terminal page shows no output.
- [`SECURITY.md`](../SECURITY.md): known exposures.
