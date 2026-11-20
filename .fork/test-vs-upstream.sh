#!/usr/bin/env bash
# Run the desktop test suite on HEAD and on the official Nuvio release HEAD is based on, then fail
# only if HEAD has test failures that the official release does not. Official Nuvio ships with some
# failing tests; this keeps those from blocking us while still catching anything our changes break.
#
# Usage: .fork/test-vs-upstream.sh            (from the repository root; needs local.properties)
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"
base_tag="$(git describe --tags --abbrev=0 --match '*-alpha' HEAD)"
work="$(mktemp -d)"
echo "Comparing HEAD ($(git rev-parse --short HEAD)) against official ${base_tag}"

run_suite() { # $1 = checkout dir, $2 = output prefix
  local dir="$1" out="$2"
  # Results from an earlier run must not stand in for this one if the build fails before testing.
  rm -rf "$dir/composeApp/build/test-results/desktopTest"
  (cd "$dir" && ./gradlew :composeApp:desktopTest --continue --no-configuration-cache --console=plain >"$out.log" 2>&1) || true
  python3 - "$dir/composeApp/build/test-results/desktopTest" "$out" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
results, out = sys.argv[1], sys.argv[2]
total, failed = 0, []
for path in glob.glob(results + "/*.xml"):
    for case in ET.parse(path).getroot().iter("testcase"):
        total += 1
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f'{case.get("classname")} > {case.get("name")}')
open(out + ".failed", "w").write("\n".join(sorted(set(failed))) + ("\n" if failed else ""))
open(out + ".total", "w").write(str(total))
PY
}

run_suite "$root" "$work/head"
# Tests don't need the native player binaries; skip downloading ~225 MB of Git LFS files.
GIT_LFS_SKIP_SMUDGE=1 git worktree add --quiet --detach "$work/base" "$base_tag"
cp local.properties "$work/base/" 2>/dev/null || true
cp gradle/wrapper/gradle-wrapper.properties "$work/base/gradle/wrapper/" 2>/dev/null || true
run_suite "$work/base" "$work/base"
git worktree remove --force "$work/base"

head_total="$(cat "$work/head.total")"
base_total="$(cat "$work/base.total")"
echo "HEAD: ${head_total} tests, $(grep -c . "$work/head.failed" || true) failing"
echo "${base_tag}: ${base_total} tests, $(grep -c . "$work/base.failed" || true) failing"
if [ "$head_total" -eq 0 ]; then
  echo "No tests ran on HEAD (build or compile failure). Log tail:" >&2
  tail -40 "$work/head.log" >&2
  exit 1
fi
if [ "$base_total" -eq 0 ]; then
  echo "No tests ran on ${base_tag}, so there is nothing to compare against. Log tail:" >&2
  tail -40 "$work/base.log" >&2
  exit 1
fi
new_failures="$(comm -23 "$work/head.failed" "$work/base.failed")"
if [ -n "$new_failures" ]; then
  echo "New test failures compared with ${base_tag}:" >&2
  echo "$new_failures" >&2
  exit 1
fi
echo "OK: no new test failures compared with ${base_tag}."
