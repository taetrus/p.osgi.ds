# WebConsole Terminal: Export, Themes and Font Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a plain-text export of the captured console output, four color themes, and font size and family controls to the Terminal page of the WebConsole.

**Architecture:** The export is a new read-only `GET terminal/export` in `TerminalPlugin`, fed from `OutputHub`, with all escape sequences removed by a new `TextSanitizer.plain`. Theme and font are browser-only: `terminal.js` sets xterm.js options at run time and keeps the selection in `localStorage`. `TerminalPlugin.renderContent` prints the new toolbar controls.

**Tech Stack:** Java 8 bytecode, `javax.servlet` 3.1, Felix WebConsole 4.x, xterm.js 6.0.0 (vendored, no change), JUnit 5 + Mockito (tier 1), no build step for the browser files.

**Spec:** `docs/superpowers/specs/2026-11-27-terminal-export-theme-font-design.md`

## Global Constraints

- Bundle: `com.kk.pde.ds.webconsole.terminal` only. No other module changes.
- No new dependency, in Java or in the browser. No vendored web font. No change to the files listed in `res/VERSIONS.txt`.
- Shipped code is Java 8 (`JavaSE-1.8`): no `var`, no `List.of`, no API from after Java 8.
- `terminal.js` keeps its style: ES5, `var`, `'use strict'`, no framework, each DOM lookup guarded with `if (el)`.
- No field or method signature of `TerminalPlugin` names a Gogo type (`NoGogoTest` checks this).
- Tests go in `src_test/`. `*Test` is tier 1. Never use `src/test/java`.
- Run Maven with the wrapper and JDK 21: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw ...`.
- Tier-1 command for this bundle:
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test -pl com.kk.pde.ds.target,com.kk.pde.ds.webconsole.terminal -Dtest=<Class>`
- Documents follow ASD-STE100. `tools/doccheck/check-docs.sh` must exit 0.
- Commits: conventional subject with scope `webconsole`. End each message with the attribution lines of the session.
- Export answer, exact values: status 200, `Content-Type: text/plain; charset=UTF-8`,
  `Content-Disposition: attachment; filename="terminal-<yyyyMMdd-HHmmss>.log"`, `Cache-Control: no-store`.
- Themes, exact names: `Dark` (default), `Light`, `High contrast`, `Solarized Dark`.
- Font size: 10 to 24 px, step 1, default 13. Families: `Default`, `Menlo`, `Consolas`, `DejaVu Sans Mono`, `Courier New`.
- `localStorage` key: `terminal.settings`, JSON `{theme, fontSize, fontFamily}`.

## Review Focus

1. **The buffer is full and old lines are gone.** A reader of the file must see that the start is missing. Expected: the first line of the export is `[<n> earlier lines are gone]`. Test: Task 2.
2. **The buffer is empty.** Expected: status 200 and an empty body, not an error. Test: Task 2.
3. **A line has non-ASCII text** (a Turkish log message). Expected: UTF-8 bytes. The content type with the charset must be set before `getWriter()`. Test: Task 2.
4. **`localStorage` holds bad data** (not JSON, `fontSize: 999`, an unknown theme name, or storage is blocked). Expected: the defaults, and no script error that stops the terminal. Check: Task 4, browser steps.
5. **The font changes while a long input line is on the screen.** The column count changes. Expected: the input line is drawn again and is intact, the terminal fills its box. Check: Task 4, browser steps.

## File Structure

| File | Change |
|------|--------|
| `src/.../TextSanitizer.java` | New `plain(String)`. |
| `src/.../TerminalPlugin.java` | New `EXPORT_PATH` route and `export(...)`. New toolbar controls in `renderContent`. Class Javadoc names the export. |
| `res/terminal.js` | New section "Settings" (themes, font, storage, controls). Header comment lists the new control ids. |
| `res/terminal.css` | Styles of the new controls. `#terminal` background no longer fixed in a way that blocks the theme. |
| `src_test/.../TextSanitizerTest.java`, `TerminalPluginTest.java` | New tests. |
| `docs/webconsole-terminal.md`, `SECURITY.md`, `CLAUDE.md`, `docs/build.md`, `docs/testing.md`, the spec | Document updates (Task 5). |

All paths below are relative to `com.kk.pde.ds.webconsole.terminal/` unless they start with `docs/` or are root files. `src/...` means `src/com/kk/pde/ds/webconsole/terminal/`.

---

### Task 1: `TextSanitizer.plain`

**Files:**
- Modify: `src/.../TextSanitizer.java`
- Test: `src_test/.../TextSanitizerTest.java`

**Interfaces:**
- Produces: `public static String plain(String text)`. It returns `clean(text)` with each SGR sequence (`ESC [ <params> m`) also removed. The result has no `ESC` character.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void plainRemovesColorSequences() {
    assertEquals("ERROR boom", TextSanitizer.plain("\u001b[31mERROR\u001b[0m boom"));
    assertEquals("x", TextSanitizer.plain("\u001b[1;38;5;196mx\u001b[m"));
}

@Test
void plainRemovesWhatCleanRemoves() {
    assertEquals("ab\tc", TextSanitizer.plain("a\u001b[2Jb\tc\u0007\u001b"));
}

@Test
void plainKeepsTextWithoutEscapes() {
    assertEquals("İstanbul [31m ok", TextSanitizer.plain("İstanbul [31m ok"));
}
```

- [ ] **Step 2: Run** `-Dtest=TextSanitizerTest`. Expected: compile error, `plain` not found.
- [ ] **Step 3: Implement `plain`.** Call `clean`, then remove what `clean` keeps. After `clean`, each remaining `ESC` starts an SGR sequence, so one precompiled `Pattern` (`\u001b\[[0-9;:]*m`) is enough. Update the class Javadoc with one sentence about `plain`.
- [ ] **Step 4: Run** `-Dtest=TextSanitizerTest`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(webconsole): add TextSanitizer.plain for text with no color codes`

---

### Task 2: `GET terminal/export`

**Files:**
- Modify: `src/.../TerminalPlugin.java` (constants near line 54, `doGet` near line 174, new private method, class Javadoc)
- Test: `src_test/.../TerminalPluginTest.java`

**Interfaces:**
- Consumes: `TextSanitizer.plain(String)`, `OutputHub.since(0)` (`Batch.dropped`, `Batch.lines`; `Line.type` is `out`, `err`, `cmd` or `done`; a `cmd` text already contains the prompt).
- Produces: the URL `terminal/export` (relative to `/system/console/`), used by the toolbar link of Task 3.

Behavior:
- Route: `private static final String EXPORT_PATH = "/" + LABEL + "/export";`, checked in `doGet` next to `STREAM_PATH`. No token check. No `shellEnabled()` check.
- Headers first, in this order before `getWriter()`: `setContentType("text/plain; charset=UTF-8")`, `setHeader("Content-Disposition", ...)`, `setHeader("Cache-Control", "no-store")`.
- File name time: server local time, `SimpleDateFormat("yyyyMMdd-HHmmss")`.
- Body: if `batch.dropped > 0`, first the line `[<dropped> earlier lines are gone]`. Then each `out`, `err` and `cmd` line as `TextSanitizer.plain(line.text)` followed by `\n`. No `done` lines. Write with `pw.print(text); pw.print('\n');` (never `println`: the line end must be `\n` on Windows too).

- [ ] **Step 1: Write the failing tests.** Add a helper and four tests:

```java
private String export() throws Exception {
    StringWriter body = new StringWriter();
    when(res.getWriter()).thenReturn(new PrintWriter(body));
    when(req.getPathInfo()).thenReturn("/terminal/export");
    plugin().doGet(req, res);
    return body.toString();
}

@Test
void exportGivesPlainLinesWithoutDoneMarkers() throws Exception {
    hub.append("out", "\u001b[31mERROR\u001b[0m boom");
    hub.append("cmd", "osgi> ss");
    hub.append("err", "İstanbul");
    hub.append("done", "");
    assertEquals("ERROR boom\nosgi> ss\nİstanbul\n", export());
}

@Test
void exportSetsDownloadHeadersBeforeTheWriter() throws Exception {
    export();
    InOrder order = inOrder(res);
    order.verify(res).setContentType("text/plain; charset=UTF-8");
    order.verify(res).getWriter();
    verify(res).setHeader("Cache-Control", "no-store");
    verify(res).setHeader(eq("Content-Disposition"),
            matches("attachment; filename=\"terminal-\\d{8}-\\d{6}\\.log\""));
    verify(res, never()).sendError(anyInt());
}

@Test
void exportOfAnEmptyHubIsEmpty() throws Exception {
    assertEquals("", export());
}

@Test
void exportReportsGoneLinesAndWorksReadOnly() throws Exception {
    props.put("terminal.shell.enabled", "false");
    when(shell.available()).thenReturn(false);
    for (int i = 1; i <= 103; i++) {      // the hub of setUp holds 100 lines
        hub.append("out", "line " + i);
    }
    String text = export();
    assertTrue(text.startsWith("[3 earlier lines are gone]\nline 4\n"), text);
    assertTrue(text.endsWith("line 103\n"), text);
}
```

Add the imports `org.mockito.InOrder`, `Mockito.inOrder`, `ArgumentMatchers.eq`, `matches`, `anyInt`.

- [ ] **Step 2: Run** `-Dtest=TerminalPluginTest`. Expected: the four new tests fail (the request falls to `super.doGet`).
- [ ] **Step 3: Implement** the route and `private void export(HttpServletResponse res) throws IOException`. Add `{@code GET terminal/export}` to the class Javadoc.
- [ ] **Step 4: Run** `-Dtest=TerminalPluginTest`, then the full tier 1 of the bundle (no `-Dtest`). Expected: all pass, `NoGogoTest` included.
- [ ] **Step 5: Commit** `feat(webconsole): export the console output as a text file`

---

### Task 3: Toolbar controls

**Files:**
- Modify: `src/.../TerminalPlugin.java` (`renderContent`, after the Follow label)
- Modify: `res/terminal.css`
- Test: `src_test/.../TerminalPluginTest.java`

**Interfaces:**
- Consumes: the URL `terminal/export` (Task 2).
- Produces: these element ids and option values, which Task 4 reads:

| Id | Element |
|----|---------|
| `terminal-export` | `<a href="terminal/export" download>Export</a>` |
| `terminal-theme` | `<select>`. Option values: `dark`, `light`, `contrast`, `solarized`. Labels: `Dark`, `Light`, `High contrast`, `Solarized Dark`. |
| `terminal-font` | `<select>`. Option values: `default`, `menlo`, `consolas`, `dejavu`, `courier`. Labels: `Default`, `Menlo`, `Consolas`, `DejaVu Sans Mono`, `Courier New`. |
| `terminal-font-smaller` | `<button type="button" title="Smaller font">-</button>` |
| `terminal-font-size` | `<span>13</span>` |
| `terminal-font-larger` | `<button type="button" title="Larger font">+</button>` |

The two selects sit in `<label>Theme ...</label>` and `<label>Font ...</label>`. The server prints no selected state. Task 4 sets it.

- [ ] **Step 1: Write the failing test**

```java
@Test
void pageContainsExportThemeAndFontControls() throws Exception {
    StringWriter page = new StringWriter();
    when(res.getWriter()).thenReturn(new PrintWriter(page));
    plugin().renderContent(req, res);
    String html = page.toString();
    assertTrue(html.contains("id=\"terminal-export\" href=\"terminal/export\" download"), html);
    for (String id : new String[] { "terminal-theme", "terminal-font", "terminal-font-smaller",
            "terminal-font-size", "terminal-font-larger" }) {
        assertTrue(html.contains("id=\"" + id + "\""), id);
    }
    for (String value : new String[] { "dark", "light", "contrast", "solarized",
            "default", "menlo", "consolas", "dejavu", "courier" }) {
        assertTrue(html.contains("<option value=\"" + value + "\">"), value);
    }
}
```

- [ ] **Step 2: Run** `-Dtest=TerminalPluginTest`. Expected: the new test fails.
- [ ] **Step 3: Implement** the HTML in `renderContent`.
- [ ] **Step 4: CSS.** In `res/terminal.css`: give `#terminal-export` the look of a toolbar button (border, padding, no underline, `margin-left: auto` is not used: the controls stay in one row and wrap with `flex-wrap: wrap` on `#terminal-toolbar`). Give `#terminal-font-size` a fixed width of `2em`, centered. Keep `background: #000` on `#terminal` as the value before the script runs. Update the comment: `terminal.js` sets the background from the theme.
- [ ] **Step 5: Run** `-Dtest=TerminalPluginTest`. Expected: all pass (`resServesOnlyKnownNames` still passes: no new file name).
- [ ] **Step 6: Commit** `feat(webconsole): add export, theme and font controls to the toolbar`

---

### Task 4: Theme and font in `terminal.js`

**Files:**
- Modify: `res/terminal.js`

**Interfaces:**
- Consumes: the ids and option values of Task 3. Existing functions `redraw()`, `fitAddon.fit()`, `term.focus()`.
- Produces: nothing for later tasks.

No automatic test exists for the browser files in this repository. The checks of this task are browser steps (Step 4).

- [ ] **Step 1: Add the data,** in a new section `// ---- Settings ----` after the Layout section.

```js
var SETTINGS_KEY = 'terminal.settings';
var FONT_MIN = 10, FONT_MAX = 24, FONT_DEFAULT = 13;
var MONO = ', monospace';
var FONTS = {
    'default': null,   // the xterm.js default, read from term.options.fontFamily before a change
    menlo: 'Menlo, Monaco' + MONO,
    consolas: 'Consolas, "Lucida Console"' + MONO,
    dejavu: '"DejaVu Sans Mono", "Liberation Mono"' + MONO,
    courier: '"Courier New", Courier' + MONO
};
var THEMES = {
    dark: { background: '#000000', foreground: '#ffffff' },
    light: {
        background: '#ffffff', foreground: '#1f2328', cursor: '#1f2328', cursorAccent: '#ffffff',
        selectionBackground: '#b6d7ff',
        black: '#24292f', red: '#cf222e', green: '#116329', yellow: '#7d4e00',
        blue: '#0969da', magenta: '#8250df', cyan: '#1b7c83', white: '#6e7781',
        brightBlack: '#57606a', brightRed: '#a40e26', brightGreen: '#1a7f37', brightYellow: '#633c01',
        brightBlue: '#218bff', brightMagenta: '#a475f9', brightCyan: '#3192aa', brightWhite: '#8c959f'
    },
    contrast: {
        background: '#000000', foreground: '#ffffff', cursor: '#ffff00', cursorAccent: '#000000',
        selectionBackground: '#ffffff', selectionForeground: '#000000',
        black: '#000000', red: '#ff5555', green: '#55ff55', yellow: '#ffff55',
        blue: '#6ca8ff', magenta: '#ff77ff', cyan: '#55ffff', white: '#ffffff',
        brightBlack: '#b0b0b0', brightRed: '#ff8080', brightGreen: '#80ff80', brightYellow: '#ffff80',
        brightBlue: '#99c2ff', brightMagenta: '#ff99ff', brightCyan: '#80ffff', brightWhite: '#ffffff'
    },
    solarized: {
        background: '#002b36', foreground: '#93a1a1', cursor: '#93a1a1', cursorAccent: '#002b36',
        selectionBackground: '#274642',
        black: '#073642', red: '#dc322f', green: '#859900', yellow: '#b58900',
        blue: '#268bd2', magenta: '#d33682', cyan: '#2aa198', white: '#eee8d5',
        brightBlack: '#657b83', brightRed: '#cb4b16', brightGreen: '#859900', brightYellow: '#b58900',
        brightBlue: '#268bd2', brightMagenta: '#6c71c4', brightCyan: '#2aa198', brightWhite: '#fdf6e3'
    }
};
```

- [ ] **Step 2: Add the functions.**
  - `loadSettings()`: reads `SETTINGS_KEY` from `window.localStorage` in a `try`. Returns `{theme, fontSize, fontFamily}`. Each field falls back alone: `theme` must be an own key of `THEMES` (else `dark`), `fontFamily` an own key of `FONTS` (else `default`), `fontSize` an integer from `FONT_MIN` to `FONT_MAX` (else `FONT_DEFAULT`). Use `Object.prototype.hasOwnProperty.call`, so `"constructor"` is not a theme.
  - `saveSettings()`: writes the JSON in a `try`. A failure is silent (same rule as the history).
  - `applyTheme()`: `term.options.theme = THEMES[settings.theme]; el.style.background = THEMES[settings.theme].background;`
  - `applyFont()`: sets `term.options.fontSize` and `term.options.fontFamily` (for `default`, the value saved from `term.options.fontFamily` right after `new Terminal(...)`). Then `try { fitAddon.fit(); } catch (e) {}` and `redraw()`. Call `fitAddon.fit()` directly: the existing `fit()` returns early when the height did not change. Also write the size into `#terminal-font-size`.
- [ ] **Step 3: Apply and wire.**
  - Apply `applyTheme()` and `applyFont()` one time at start. `applyFont()` calls `redraw()`, which needs the input-line state. Thus do the start call in the Toolbar section at the end of the file, before the existing final `redraw()`.
  - In the Toolbar section: set the `value` of the two selects from the settings. `change` on a select and `click` on `-` / `+` update the setting, apply it, save it, and call `term.focus()`. `-` and `+` stop at the limits.
  - Update the header comment of the file: add the six new ids to the contract.
- [ ] **Step 4: Check in a browser.** Build and start the product:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify
./distribution/scripts/run.sh
```

Open `http://localhost:8080/system/console/terminal` (`admin`/`admin`). Use the Claude in Chrome tools, or headless Chrome over CDP (see the memory note) when they are absent. Record each result for Task 5:

| # | Action | Expected result |
|---|--------|-----------------|
| a | Run `xyz`. Select each theme. | Each theme changes background and text at once. The red `CommandNotFoundException` line is readable in each. No band of the old color at the edge of `#terminal`. |
| b | Press `+` to 24 and `-` to 10. | The size label follows and stops at 24 and at 10. The terminal fills its box at each size. |
| c | Type a line longer than the width. Change the font size. | The input line stays intact and the cursor is at its end. |
| d | Select each font family. | The font changes, or falls back to a monospace font. The columns stay aligned (`ss` output). |
| e | Reload. Open a second tab. | Theme, size and family are the same. The selects show them. |
| f | In the browser console: `localStorage.setItem('terminal.settings','{bad')`, reload. Then `'{"theme":"x","fontSize":999,"fontFamily":"constructor"}'`, reload. | Dark, 13, Default. The terminal works. No error in the console. |
| g | Click **Export**. | A file `terminal-<date>-<time>.log` downloads. It has no escape characters. It contains the `osgi> xyz` line. |
| h | Click **Clear**, then **Export**. | The file still has the earlier lines. |
| i | `curl -s -u admin:admin -D - http://localhost:8080/system/console/terminal/export \| head -20` | `HTTP/1.1 200`, `Content-Type: text/plain;charset=utf-8` (Jetty can change the case), the `Content-Disposition` header, then log lines. |
| j | The same `curl` with no `-u`. | Status 401. No log line. |

A failed row is a bug: fix it before the commit. Stop the product after the run.

- [ ] **Step 5: Commit** `feat(webconsole): select the theme and the font of the terminal`

---

### Task 5: Documents

**Files:**
- Modify: `docs/webconsole-terminal.md`, `SECURITY.md`, `CLAUDE.md`, `docs/build.md`, `docs/testing.md`, `docs/superpowers/specs/2026-11-27-terminal-export-theme-font-design.md`

Use the `anthropic-skills:asd-ste100` skill for the text. State only what Task 4 Step 4 or a test showed. Mark each other fact "not tested" (for example a Windows browser run).

- [ ] **Step 1: `docs/webconsole-terminal.md`.**
  - "Keys and toolbar" table: rows for **Export**, **Theme**, **Font**, `-` / `+`. Below the table: the settings stay in `localStorage` of the browser (key `terminal.settings`), for each browser, not for each user or server.
  - "How it works": the ASCII diagram (near line 103) and the URL table get `GET /system/console/terminal/export` with the headers, the body rules (`cmd` lines kept, `done` lines left out, color codes removed, the `[n earlier lines are gone]` first line) and a `curl` example. The parts table: `TextSanitizer` and `TerminalPlugin` rows name the export.
  - "Limits": the export holds only the last `terminal.scrollback.lines` lines, and nothing from before the bundle start. A font family shows only if the machine of the viewer has it.
  - "Security": the export gives the same lines as `stream`, behind the same login, with no token.
  - "Manual test procedure": add rows 15 and up from rows a to j of Task 4, with the date and the browser of the run.
- [ ] **Step 2: `SECURITY.md` row 7.** Add that `/system/console/terminal/export` downloads the captured output as one file, behind the same login.
- [ ] **Step 3: Test counts.** Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify` and read the `Tests run:` totals. Tier 1 was 124 and this plan adds 8 tests (3 + 4 + 1): expect 132, tier 2 stays 13, total 145. Use the numbers that the build prints. Update `CLAUDE.md:92`, `docs/build.md:625` and `docs/testing.md:533` (also the total "137" where it shows). Search: `git grep -n "124\|137" -- '*.md' ':!docs/superpowers'`.
- [ ] **Step 4: `CLAUDE.md` Terminal section.** One sentence: the toolbar exports the output (`terminal/export`) and selects theme and font.
- [ ] **Step 5: The spec.** Set the status to implemented, with the date.
- [ ] **Step 6: Run** `tools/doccheck/check-docs.sh`. Expected: exit 0. No Windows command of the docs changed, so `.github/verify-windows/verify.ps1` needs no change. Confirm with `git diff main -- docs | grep "curl.exe"` (expected: no removed or changed line).
- [ ] **Step 7: Commit** `docs(webconsole): describe the export, the themes and the font settings`

---

## After the last task

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify`: BUILD SUCCESS with the totals of Task 5 Step 3.
2. `tools/doccheck/check-docs.sh`: exit 0.
3. Open a pull request from `feat/terminal-export-theme-font` to `main`. List the updated documents in the description. Wait for the 4 CI jobs.
