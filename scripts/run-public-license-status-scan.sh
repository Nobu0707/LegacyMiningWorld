#!/usr/bin/env bash
set -euo pipefail

readonly VERSION="1.0.1"
readonly CHECK_DIRECTORY="build/review-checks"
readonly CURRENT_DOCUMENTS=(
  README.md
  docs/licensing.md
  docs/stable-release.md
  docs/installation.md
  docs/operations.md
  docs/user-acceptance-checklist.md
)

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

for document in "${CURRENT_DOCUMENTS[@]}"; do
  [ -s "$document" ] || die "missing current document: $document"
done
if rg -n -i \
  -e 'license not selected' \
  -e 'ライセンス未選択' \
  -e 'private/internal only' \
  -e 'public distribution requires license decision' \
  -e 'LICENSE-NOT-SELECTED' \
  -e 'current version: 1\.0\.0([^-]|$)' \
  -e 'current version: 1\.0\.0-rc\.1' \
  -e 'current version: 0\.6\.0-alpha\.1' \
  "${CURRENT_DOCUMENTS[@]}" >/dev/null; then
  die "stale current release status found"
fi

grep -Fq '1.0.1' README.md || die "README current version missing"
grep -Fq 'MIT' README.md || die "README MIT status missing"
grep -Fq 'public distribution allowed under MIT terms' README.md \
  || die "README public distribution statement missing"
grep -Fq 'actual external publication not performed' README.md \
  || die "README external publication status missing"

package="build/release/LegacyMiningWorld-${VERSION}-release.tar.gz"
[ -r "$package" ] || die "release package missing"
package_listing="$(tar -tzf "$package")"
grep -Fq "LegacyMiningWorld-${VERSION}/LICENSE" <<< "$package_listing" || die "package LICENSE missing"
if grep -Fq 'LICENSE-NOT-SELECTED' <<< "$package_listing"; then
  die "stale license marker remains in package"
fi

mkdir -p "$CHECK_DIRECTORY"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$VERSION"
  printf 'MIT current status: PASS\n'
  printf 'public distribution allowed under MIT terms: PASS\n'
  printf 'actual external publication not performed: PASS\n'
  printf 'stale current version/license phrases absent: PASS\n'
  printf 'release package stale marker absent: PASS\n'
  printf 'PASS: public license status scan completed.\n'
} > "$CHECK_DIRECTORY/public-license-status-scan.txt"

printf 'PASS: public license status scan completed.\n'
