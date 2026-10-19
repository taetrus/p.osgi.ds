#!/usr/bin/env python3
"""Check the relative links and #anchors in Markdown files.

For each [text](target) outside fenced code blocks:
  - skip absolute URLs (http:, https:, mailto:, ...)
  - the target file or folder must exist, relative to the Markdown file
  - a #anchor must match a heading in the target .md file (GitHub slug rules)

Usage:
    check_links.py FILE [FILE ...]

Exit 1 when a link is broken. Standard library only.
"""
import os
import re
import sys

FENCE = re.compile(r"^(```|~~~)")
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$")
URL_SCHEME = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:")


def strip_fenced_code(lines):
    """Yield (line_number, line) for lines outside fenced code blocks."""
    in_fence = False
    for number, line in enumerate(lines, 1):
        if FENCE.match(line.strip()):
            in_fence = not in_fence
            continue
        if not in_fence:
            yield number, line


def github_slug(heading):
    """Approximate GitHub's heading anchor: lowercase, drop punctuation, spaces to hyphens."""
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", heading)  # [text](url) -> text
    text = text.replace("`", "").replace("*", "")
    text = text.strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)  # keeps letters, digits, _, -, space
    return text.replace(" ", "-")


_anchor_cache = {}


def anchors_of(path):
    if path not in _anchor_cache:
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        seen = {}
        anchors = set()
        for _, line in strip_fenced_code(lines):
            m = HEADING.match(line)
            if not m:
                continue
            slug = github_slug(m.group(1))
            count = seen.get(slug, 0)
            anchors.add(slug if count == 0 else "%s-%d" % (slug, count))
            seen[slug] = count + 1
        # Explicit HTML anchors: <a id="x"> or <a name="x">
        with open(path, encoding="utf-8") as f:
            for m in re.finditer(r"<a\s+(?:id|name)=\"([^\"]+)\"", f.read()):
                anchors.add(m.group(1).lower())
        _anchor_cache[path] = anchors
    return _anchor_cache[path]


def check(md_file):
    errors = []
    with open(md_file, encoding="utf-8") as f:
        lines = f.read().splitlines()
    base = os.path.dirname(md_file)
    for number, line in strip_fenced_code(lines):
        line = re.sub(r"`[^`]*`", "", line)  # ignore inline code
        for m in LINK.finditer(line):
            target = m.group(1)
            if URL_SCHEME.match(target):
                continue
            path, _, anchor = target.partition("#")
            resolved = os.path.normpath(os.path.join(base, path)) if path else md_file
            if not os.path.exists(resolved):
                errors.append("%s:%d: missing target: %s" % (md_file, number, target))
                continue
            if anchor and resolved.endswith(".md") and os.path.isfile(resolved):
                if anchor.lower() not in anchors_of(resolved):
                    errors.append("%s:%d: missing anchor: %s" % (md_file, number, target))
    return errors


def main(argv):
    if not argv:
        print(__doc__.strip())
        return 2
    errors = []
    for md_file in argv:
        errors.extend(check(md_file))
    for e in errors:
        print(e)
    print("%d file(s) checked, %d broken link(s)" % (len(argv), len(errors)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
