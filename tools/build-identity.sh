#!/usr/bin/env bash
# Writes build-identity.properties, read by app/build.gradle:
#   base=<8-char commit of HEAD>
#   changeset=<12-char digest of uncommitted changes>, empty for a clean tree
# The digest covers the tracked diff against HEAD and the content of untracked,
# non-ignored files (documentation excluded), so two builds report the same
# changeset only when they were built from the same sources.
# Usage: tools/build-identity.sh [output-file]
# Set GIT to the git that owns the checkout (e.g. git.exe for a Windows checkout
# seen from WSL, so line-ending settings match).
set -euo pipefail
root="$(realpath "$(dirname "${BASH_SOURCE[0]}")/..")"
out="${1:-$root/build-identity.properties}"
git_root="$root"
# A Windows git.exe called from WSL needs a Windows path.
[[ "${GIT:-git}" == *.exe ]] && git_root="$(wslpath -w "$root")"
git_cmd=("${GIT:-git}" -C "$git_root" -c core.safecrlf=false)
base=$("${git_cmd[@]}" rev-parse --short=8 HEAD | tr -d '\r')
digest_input=$(mktemp)
trap 'rm -f "$digest_input"' EXIT
# Documentation does not change the APK: editing it must not change the identity.
scope=(-- . ':(exclude)docs' ':(exclude)performance-tests' ':(exclude)*.md')
"${git_cmd[@]}" diff --no-ext-diff --binary HEAD "${scope[@]}" > "$digest_input"
while IFS= read -r -d '' file; do
    printf '%s\0' "$file" >> "$digest_input"
    sha256sum < "$root/$file" >> "$digest_input"
done < <("${git_cmd[@]}" ls-files --others --exclude-standard -z "${scope[@]}")
if [[ -s "$digest_input" ]]; then
    changeset=$(sha256sum < "$digest_input" | cut -c1-12)
else
    changeset=""
fi
printf 'base=%s\nchangeset=%s\n' "$base" "$changeset" > "$out"
cat "$out"
