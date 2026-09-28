#!/bin/bash
# Quick compile check of native sources without a full build: runs each file's
# real compile command (from compile_commands.json of the last Gradle configure
# of :emulator-core) with -fsyntax-only.
#
# Usage: tools/native_syntax_check.sh <path suffix>...  e.g. gpu/command_processor.cc
#        tools/native_syntax_check.sh --changed         .cc/.cpp files changed vs HEAD
# Headers are checked through the sources that include them - pass those.
# Needs one configure first, e.g.:
#   ./gradlew :emulator-core:configureCMakeRelease[arm64-v8a]
# (or any native build).
cd "$(git rev-parse --show-toplevel)" || exit 1
DB=$(ls -d emulator-core/.cxx/Release/*/arm64-v8a/compile_commands.json 2>/dev/null | head -1)
if [ -z "$DB" ]; then
  echo "no compile_commands.json - configure the native build first (see the header)"
  exit 1
fi
if [ "${1:-}" = "--changed" ]; then
  set -- $(git diff --name-only HEAD -- '*.cc' '*.cpp' | sed 's|^.*/src/xenia/||')
  [ $# -gt 0 ] || { echo "no changed sources"; exit 0; }
fi
python3 - "$DB" "$@" <<'EOF'
import json, shlex, subprocess, sys
db = json.load(open(sys.argv[1]))
rc = 0
for suffix in sys.argv[2:]:
    entry = next((e for e in db if e["file"].endswith(suffix)), None)
    if entry is None:
        print("NO ENTRY", suffix)
        rc = 1
        continue
    args = entry.get("arguments") or shlex.split(entry["command"])
    out, skip = [], False
    for a in args:
        if skip:
            skip = False
            continue
        if a in ("-o", "-MF", "-MT", "-MQ"):
            skip = True
            continue
        if a in ("-MD", "-MMD", "-c") or a.startswith("-flto"):
            continue
        out.append(a)
    p = subprocess.run(out + ["-fsyntax-only"], cwd=entry["directory"],
                       capture_output=True, text=True)
    print(("OK   " if p.returncode == 0 else "FAIL ") + suffix)
    if p.returncode != 0:
        print("\n".join(p.stderr.splitlines()[:40]))
        rc = 1
sys.exit(rc)
EOF
