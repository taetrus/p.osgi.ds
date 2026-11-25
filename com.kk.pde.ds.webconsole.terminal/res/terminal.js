/*
 * The browser part of the WebConsole terminal. No framework and no build step.
 *
 * Contract (TerminalPlugin.renderContent): the element #terminal has data-token, data-shell,
 * data-prompt and data-scrollback. The toolbar has #terminal-search, #terminal-prev,
 * #terminal-next, #terminal-clear and #terminal-follow. xterm.js, addon-search.js and
 * addon-fit.js load before this file and give the globals Terminal, SearchAddon and FitAddon.
 *
 * The server cleans each output line (only color sequences stay), so this file writes the
 * event data as it is. The input line is edited here and sent to "terminal/exec" on Enter.
 */
(function () {
    'use strict';

    var HISTORY_KEY = 'terminal.history';
    var HISTORY_MAX = 200;
    var ERASE = '\r\x1b[2K\x1b[J';

    var el = document.getElementById('terminal');
    if (!el || typeof Terminal !== 'function') {
        return;
    }

    var token = el.getAttribute('data-token') || '';
    var prompt = el.getAttribute('data-prompt') || '';
    var scrollback = parseInt(el.getAttribute('data-scrollback'), 10);
    if (!(scrollback > 0)) {
        scrollback = 50000;
    }
    var shellOn = el.getAttribute('data-shell') !== 'false';

    var term = new Terminal({ scrollback: scrollback, convertEol: true, cursorBlink: true });
    var fitAddon = new FitAddon.FitAddon();
    var searchAddon = new SearchAddon.SearchAddon();
    term.loadAddon(fitAddon);
    term.loadAddon(searchAddon);
    term.open(el);

    // ---- Layout ----------------------------------------------------------------------------

    function fit() {
        var top = el.getBoundingClientRect().top;
        var height = Math.max(200, window.innerHeight - top - 16);
        el.style.height = height + 'px';
        try {
            fitAddon.fit();
        } catch (e) {
            // The element is not visible yet. The next resize fits again.
        }
    }
    window.addEventListener('resize', fit);
    fit();

    // ---- Input line state ------------------------------------------------------------------

    var buffer = '';
    var cursor = 0;
    var drawnRow = 0; // the row of the terminal cursor inside the drawn input line
    var shown = false; // the input line is on the screen
    var pasted = null; // lines of a paste that wait for Enter or Esc
    var sending = Promise.resolve(); // the fetch chain: one exec at a time, in order

    var history = loadHistory();
    var historyIndex = history.length;
    var draft = '';

    function loadHistory() {
        try {
            var value = JSON.parse(window.sessionStorage.getItem(HISTORY_KEY) || '[]');
            return Array.isArray(value) ? value.slice(-HISTORY_MAX) : [];
        } catch (e) {
            return [];
        }
    }

    function remember(line) {
        if (history.length === 0 || history[history.length - 1] !== line) {
            history.push(line);
            if (history.length > HISTORY_MAX) {
                history.splice(0, history.length - HISTORY_MAX);
            }
            try {
                window.sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history));
            } catch (e) {
                // No storage: the history stays in memory for this page only.
            }
        }
        historyIndex = history.length;
        draft = '';
    }

    // Returns the text that removes the drawn input line. The cursor ends at column 0.
    function eraseText() {
        if (!shown) {
            return '';
        }
        shown = false;
        var up = drawnRow > 0 ? '\x1b[' + drawnRow + 'A' : '';
        drawnRow = 0;
        return up + ERASE;
    }

    // Returns the text that draws the prompt and the buffer, and puts the cursor at its index.
    // One column for each character: wide characters can move the cursor wrongly.
    function drawText() {
        if (!shellOn) {
            return '';
        }
        var cols = Math.max(1, term.cols);
        var text = prompt + buffer;
        var out = text;
        var length = text.length;
        if (length > 0 && length % cols === 0) {
            out += ' \b'; // leaves the pending-wrap state, so the cursor is on the next row
        }
        var endRow = Math.floor(length / cols);
        var target = prompt.length + cursor;
        var row = Math.floor(target / cols);
        var col = target % cols;
        if (endRow > row) {
            out += '\x1b[' + (endRow - row) + 'A';
        }
        out += '\r';
        if (col > 0) {
            out += '\x1b[' + col + 'C';
        }
        drawnRow = row;
        shown = true;
        return out;
    }

    function redraw() {
        term.write(eraseText() + drawText());
    }

    // ---- Output ----------------------------------------------------------------------------

    var follow = true;
    var queue = [];
    var flushTimer = null;
    var writes = 0;
    var holdY = null;

    // Lines go out in one write for each 16 ms. Thus the initial buffer draws the prompt once.
    function show(text) {
        queue.push(text);
        if (flushTimer === null) {
            flushTimer = window.setTimeout(flush, 16);
        }
    }

    function dim(text) {
        show('\x1b[2m' + text + '\x1b[22m');
    }

    // Follow is on, and the user does not look at a search match or a selection.
    function following() {
        return follow && !term.hasSelection() && !(search && document.activeElement === search);
    }

    function flush() {
        flushTimer = null;
        if (queue.length === 0) {
            return;
        }
        var lines = queue.join('\r\n') + '\r\n';
        queue = [];
        var toBottom = following();
        if (!toBottom && holdY === null) {
            holdY = term.buffer.active.viewportY;
        }
        writes++;
        term.write(eraseText() + lines + drawText(), function () {
            writes--;
            if (toBottom) {
                term.scrollToBottom();
            } else if (holdY !== null) {
                term.scrollToLine(holdY);
            }
            if (writes === 0) {
                holdY = null;
            }
        });
    }

    var lostShown = false;
    var source = new EventSource('terminal/stream');

    function onLine(e) {
        lostShown = false;
        show(e.data);
    }
    source.addEventListener('out', onLine);
    source.addEventListener('err', onLine);
    source.addEventListener('cmd', onLine);
    source.addEventListener('gone', function (e) {
        dim('[' + e.data + ' earlier lines are gone]');
    });
    source.addEventListener('done', function () {
        // Nothing more: the prompt is always on the screen.
    });
    source.addEventListener('open', function () {
        lostShown = false;
    });
    var closedShown = false;
    source.addEventListener('error', function () {
        if (source.readyState === EventSource.CLOSED) {
            // The browser does not try again (for example after a non-200 answer during a
            // restart). The token is then old too, so the page becomes read-only.
            if (!closedShown) {
                closedShown = true;
                shellOn = false;
                pasted = null;
                term.write(eraseText());
                dim('[the connection is closed, load the page again]');
            }
        } else if (!lostShown) {
            lostShown = true;
            dim('[connection lost, connecting again]');
        }
    });

    // ---- Commands --------------------------------------------------------------------------

    // Sends one line. The promise gives true when the next line can go.
    function exec(line) {
        return window.fetch('terminal/exec', {
            method: 'POST',
            headers: { 'X-Terminal-Token': token },
            body: line
        }).then(function (res) {
            if (res.status === 202 || res.status === 204) {
                return true;
            }
            if (res.status === 403) {
                dim('[the page is old, load it again]');
            } else if (res.status === 503) {
                shellOn = false;
                term.write(eraseText());
                dim('[shell not available]');
            } else if (res.status === 413) {
                dim('[the line is too long]');
            } else {
                dim('[the command failed: HTTP ' + res.status + ']');
            }
            return false;
        }, function () {
            dim('[the command was not sent: no connection]');
            return false;
        });
    }

    // Sends the lines one after the other. Each fetch completes before the next one starts,
    // also across two calls. After a failure, the other lines of the same call do not go.
    function execAll(lines) {
        sending = sending.then(function () {
            var chain = Promise.resolve(true);
            lines.forEach(function (line) {
                chain = chain.then(function (ok) {
                    if (!ok) {
                        return false;
                    }
                    remember(line);
                    return exec(line);
                });
            });
            return chain;
        });
    }

    function enter() {
        var line = buffer;
        buffer = '';
        cursor = 0;
        if (line.trim().length === 0) {
            // A real console writes an empty prompt line. Only this page shows it.
            show(prompt);
            redraw();
            return;
        }
        redraw();
        execAll([line]);
    }

    // ---- Input -----------------------------------------------------------------------------

    function insert(text) {
        // Printable characters only. A tab becomes a space.
        text = text.replace(/\t/g, ' ').replace(/[\x00-\x1f\x7f]/g, '');
        if (text.length === 0) {
            return;
        }
        buffer = buffer.slice(0, cursor) + text + buffer.slice(cursor);
        cursor += text.length;
        redraw();
    }

    function clearLine() {
        buffer = '';
        cursor = 0;
        historyIndex = history.length;
        redraw();
    }

    function clearScreen() {
        term.clear(); // keeps the row of the cursor, so the input line is drawn again
        redraw();
    }

    function historyMove(step) {
        var next = historyIndex + step;
        if (next < 0 || next > history.length) {
            return;
        }
        if (historyIndex === history.length) {
            draft = buffer;
        }
        historyIndex = next;
        buffer = next === history.length ? draft : history[next];
        cursor = buffer.length;
        redraw();
    }

    function paste(text) {
        var lines = text.replace(/\r\n?/g, '\n').split('\n').filter(function (l) {
            return l.trim().length > 0;
        });
        if (lines.length === 0) {
            return;
        }
        if (lines.length === 1) {
            insert(lines[0]); // one line with a line break at the end: insert it, do not run it
            return;
        }
        pasted = lines;
        dim('[paste: ' + lines.length + ' lines. Enter = run all, Esc = cancel]');
    }

    var KEYS = {
        '\x1b[A': function () { historyMove(-1); },
        '\x1bOA': function () { historyMove(-1); },
        '\x1b[B': function () { historyMove(1); },
        '\x1bOB': function () { historyMove(1); },
        '\x1b[C': function () { if (cursor < buffer.length) { cursor++; redraw(); } },
        '\x1bOC': function () { if (cursor < buffer.length) { cursor++; redraw(); } },
        '\x1b[D': function () { if (cursor > 0) { cursor--; redraw(); } },
        '\x1bOD': function () { if (cursor > 0) { cursor--; redraw(); } },
        '\x1b[H': function () { cursor = 0; redraw(); },
        '\x1bOH': function () { cursor = 0; redraw(); },
        '\x1b[1~': function () { cursor = 0; redraw(); },
        '\x1b[F': function () { cursor = buffer.length; redraw(); },
        '\x1bOF': function () { cursor = buffer.length; redraw(); },
        '\x1b[4~': function () { cursor = buffer.length; redraw(); },
        '\x1b[3~': function () {
            if (cursor < buffer.length) {
                buffer = buffer.slice(0, cursor) + buffer.slice(cursor + 1);
                redraw();
            }
        },
        '\x7f': function () {
            if (cursor > 0) {
                buffer = buffer.slice(0, cursor - 1) + buffer.slice(cursor);
                cursor--;
                redraw();
            }
        },
        '\r': enter,
        '\x03': clearLine,
        '\x0c': clearScreen
    };
    KEYS['\b'] = KEYS['\x7f'];

    term.onData(function (data) {
        if (!shellOn) {
            return;
        }
        if (pasted !== null) {
            if (data === '\r') {
                var lines = pasted;
                pasted = null;
                execAll(lines);
            } else if (data === '\x1b') {
                pasted = null;
                dim('[paste canceled]');
            }
            return;
        }
        var key = KEYS[data];
        if (key) {
            key();
            return;
        }
        if (data.charAt(0) === '\x1b' && data.indexOf('\x1b[200~') !== 0) {
            return; // an other function key
        }
        // A paste: bracketed or not. Text with a line break is a list of commands.
        var text = data.replace(/\x1b\[20[01]~/g, '');
        if (/[\r\n]/.test(text)) {
            paste(text);
        } else {
            insert(text);
        }
    });

    term.attachCustomKeyEventHandler(function (e) {
        if (e.type !== 'keydown') {
            return true;
        }
        var key = (e.key || '').toLowerCase();
        var mod = e.ctrlKey || e.metaKey;
        if (mod && !e.altKey && key === 'c') {
            if (term.hasSelection()) {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(term.getSelection()).catch(function () {
                        dim('[copy failed]');
                    });
                }
                return false; // xterm does not handle the key; the browser copy event also runs
            }
            if (e.ctrlKey && !e.metaKey && shellOn && pasted === null) {
                clearLine();
            }
            return false;
        }
        if (mod && !e.altKey && key === 'v') {
            return false; // the browser paste event gives the text to onData
        }
        if (e.ctrlKey && !e.metaKey && !e.altKey && key === 'l') {
            e.preventDefault(); // xterm does not, so the browser would focus the address bar
            clearScreen();
            return false;
        }
        return true;
    });

    // ---- Toolbar ---------------------------------------------------------------------------

    var search = document.getElementById('terminal-search');
    var prev = document.getElementById('terminal-prev');
    var next = document.getElementById('terminal-next');
    var clear = document.getElementById('terminal-clear');
    var followBox = document.getElementById('terminal-follow');

    function find(back) {
        var value = search ? search.value : '';
        if (value.length === 0) {
            return;
        }
        if (back) {
            searchAddon.findPrevious(value);
        } else {
            searchAddon.findNext(value);
        }
    }

    if (search) {
        search.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                find(e.shiftKey);
            }
        });
    }
    if (prev) {
        prev.addEventListener('click', function () { find(true); });
    }
    if (next) {
        next.addEventListener('click', function () { find(false); });
    }
    if (clear) {
        clear.addEventListener('click', function () {
            clearScreen();
            term.focus();
        });
    }
    if (followBox) {
        follow = followBox.checked;
        followBox.addEventListener('change', function () {
            follow = followBox.checked;
            if (follow) {
                term.scrollToBottom();
            }
        });
    }

    redraw();
    term.focus();
})();
