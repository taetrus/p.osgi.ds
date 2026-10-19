#!/bin/bash
# Check the project documentation: ASD-STE100 style and relative links.
# CI runs this script. Run it locally from any directory: tools/doccheck/check-docs.sh
#
# Checked files: the root docs and docs/*.md. The design records under
# docs/superpowers/ keep their original text, so the STE check skips them.
# Their links are still checked.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

STE_FILES=(README.md CLAUDE.md SECURITY.md docs/*.md)
LINK_FILES=("${STE_FILES[@]}" docs/superpowers/*/*.md)

status=0

echo "== ASD-STE100 lint (ste-lint.py, 0 hard violations allowed)"
python3 -I tools/doccheck/ste-lint.py "${STE_FILES[@]}" || status=1

echo
echo "== Relative links and anchors"
python3 -I tools/doccheck/check_links.py "${LINK_FILES[@]}" || status=1

exit $status
