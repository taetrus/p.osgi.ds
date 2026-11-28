# WebConsole Terminal: log export, themes and font settings

Date: 2026-11-27. Status: implemented on 2026-11-27 (branch `feat/terminal-export-theme-font`). Bundle: `com.kk.pde.ds.webconsole.terminal`.
Base design: [2026-11-25-webconsole-terminal-design.md](2026-11-25-webconsole-terminal-design.md).

## Intent

The team reads the console output to analyze and fix errors. Today the output can only be
copied from the page by hand. The page also has one fixed look (xterm.js defaults, black
background), which is hard to read for some users and screens.

## Goals

1. **Export.** One click gives a complete plain-text file of the captured console output. A script
   can get the same file with `curl`.
2. **Theme.** The user selects one of four color themes.
3. **Font.** The user selects the font size and the font family.
4. Theme and font stay after a reload and in new tabs of the same browser.

## Non-goals

- No free color pickers for background and foreground.
- No vendored web font. No new dependency, in Java or in the browser.
- No server property for theme or font. They are settings of the viewer.
- No export of a part of the buffer (`from`/`to`), no other file format, no export of the Logback file.

## Anti-goals

- An export that loses lines because the user pressed **Clear**.
- A theme in which an `ERROR` line (ANSI red) is not readable.
- A new URL that exposes more than `terminal/stream` already exposes.

## Approaches

**Export**

| Approach | Trade-off |
|----------|-----------|
| A. Browser: read the xterm scrollback, download a Blob | No new URL. But **Clear** removes lines, wrapped rows must be joined, and no automatic test covers it. |
| **B. Server: `GET terminal/export` from `OutputHub`** (selected) | Complete and independent of the page. Works with `curl`. Tier-1 tests. Cost: one new URL behind the same login. |

B was selected: a cleared screen must not remove the evidence of an error.

**Theme and font**

| Approach | Trade-off |
|----------|-----------|
| **1. Presets** (selected) | Each preset sets all 16 ANSI colors, so log-level colors stay readable. Less freedom. |
| 2. Free background and foreground pickers | Full freedom. But a free background can hide a colored line. |

Preset 1 was selected.

## Design

### Export (server)

- `GET /system/console/terminal/export`. The WebConsole login protects it, as it protects `stream`.
  No token: the request only reads, and `stream` gives the same lines with no token.
- Answer: status 200, `Content-Type: text/plain; charset=UTF-8`,
  `Content-Disposition: attachment; filename="terminal-<yyyyMMdd-HHmmss>.log"`, `Cache-Control: no-store`.
- Body: all lines of `hub.since(0)`, oldest first, one line each, `\n` line ends.
  - `out` and `err` lines: the text, with each escape sequence removed.
  - `cmd` lines: the prompt and the command (`osgi> ss`), so the file shows what the user ran.
  - `done` lines: left out.
  - If the buffer already dropped old lines, the first line is `[<n> earlier lines are gone]`.
- New method `TextSanitizer.plain(String)`: removes the SGR sequences that `clean` keeps.
- The export works when `terminal.shell.enabled=false` and when Gogo is absent.
- The time in the file name is the server time. The lines carry the time stamps that the
  application printed (Logback). The export adds none.

### Toolbar

```
[Search____] [<] [>] [Clear] [x] Follow  [Export]  Theme [Dark v]  Font [Default v] [-] 15 [+]
```

`TerminalPlugin.renderContent` prints the new controls: `#terminal-export` (a button that
opens `terminal/export`. The first version was a link with the `download` attribute, styled
with the Chrome button colors), `#terminal-theme`,
`#terminal-font`, `#terminal-font-smaller`, `#terminal-font-size`, `#terminal-font-larger`.

### Theme and font (browser, `terminal.js`)

- Themes: `Dark` (default, the look of today), `Light`, `High contrast`, `Solarized Dark`. Each is
  an xterm.js `ITheme` object with background, foreground, cursor, selection and the 16 ANSI colors.
- A theme change sets `term.options.theme` and the background of `#terminal` (the padding area).
  `terminal.css` no longer fixes `#000`.
- Font size: 10 to 24 px, step 1, default 15. (The design first said 13. The implementation uses
  15, the xterm.js 6 default, so the first look of the page does not change.)
- Font family: `Default` (the xterm.js default), `Menlo`, `Consolas`, `DejaVu Sans Mono`,
  `Courier New`. Each stack ends with `monospace`. A font that the machine does not have falls back.
- A font change sets `term.options.fontSize` or `term.options.fontFamily`, then fits the
  terminal again and draws the input line again (the column count changes).
- Storage: one `localStorage` key `terminal.settings`, JSON `{theme, fontSize, fontFamily}`.
  An unreadable or out-of-range value gives the default. No storage: the settings last for the page only.

## Tests and documents

- Tier 1: `TextSanitizerTest` (`plain`), `TerminalPluginTest` (export headers, body, line types,
  read-only mode, the new controls in the page).
- No tier-2 test for the export: the tier-2 runtime has no HTTP service. A `curl` against the
  running product checks the live URL.
- Manual: new rows in the manual test procedure of `docs/webconsole-terminal.md` for the export,
  each theme, the font controls and the reload. Browser behavior is "not tested" until such a run.
- Documents to update: `docs/webconsole-terminal.md` (toolbar, HTTP table, limits, manual rows),
  `SECURITY.md` row 7 (the export URL), `CLAUDE.md` (Terminal section, test counts), `README.md` if it lists the features.

## Left to the builder

- The exact colors of each theme, with readable ANSI red, yellow and green on each background.
- The CSS of the new controls.
- Whether the export streams the lines or builds one string (50,000 lines is small for both).
