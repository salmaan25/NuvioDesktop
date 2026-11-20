#!/usr/bin/env bash
# Checks the fork's in-player Quality and "Other languages" menus (player-ui/salmaan-versions.js)
# against the official controls page, in headless Chrome. Run after merging an official release:
#   .fork/player-ui-check/run.sh
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
chrome="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$root/composeApp/src/desktopMain/resources/player-ui/." "$work/"
cp "$root/.fork/player-ui-check/harness.js" "$work/"
python3 - "$work" <<'PY'
import sys
work = sys.argv[1]
page = open(work + "/controls.html").read()
stub = '<script>window.__sent=[];window.webkit={messageHandlers:{player:{postMessage:m=>window.__sent.push(m)}}};</script>'
assert "<head>" in page and '<script src="salmaan-versions.js"></script>' in page, "controls.html no longer loads salmaan-versions.js"
page = page.replace("<head>", "<head>" + stub, 1).replace(
    '<script src="salmaan-versions.js"></script>',
    '<script src="salmaan-versions.js"></script>\n  <script src="harness.js"></script>', 1)
open(work + "/test.html", "w").write(page)
PY
"$chrome" --headless=new --disable-gpu --allow-file-access-from-files --user-data-dir="$work/profile" \
  --virtual-time-budget=10000 --dump-dom "file://$work/test.html" 2>/dev/null >"$work/dom.html"
python3 - "$work/dom.html" <<'PY'
import html, re, sys
m = re.search(r'<pre id="harnessResults">(.*?)</pre>', open(sys.argv[1]).read(), re.S)
if not m:
    sys.exit("No results: the page or harness failed to load.")
lines = html.unescape(m.group(1)).splitlines()
print("\n".join(lines))
passed = sum(l.startswith("PASS") for l in lines)
print(f"{passed} of {len(lines)} checks passed")
sys.exit(0 if passed == len(lines) else 1)
PY
