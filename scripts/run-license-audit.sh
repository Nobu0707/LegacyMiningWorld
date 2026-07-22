#!/usr/bin/env bash
set -euo pipefail

readonly VERSION="1.0.1"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

[ -f LICENSE ] && [ -s LICENSE ] || die "root LICENSE is missing or empty"
[ ! -L LICENSE ] || die "root LICENSE must be a regular file"
head -n 1 LICENSE | grep -Fxq 'MIT License' || die "LICENSE title mismatch"
grep -Fxq 'Copyright (c) 2026 nobu0707' LICENSE || die "copyright mismatch"
grep -Fq 'Permission is hereby granted, free of charge' LICENSE || die "permission clause missing"
grep -Fq 'this permission notice shall be included' LICENSE || die "notice condition missing"
grep -Fq 'THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND' LICENSE || die "warranty disclaimer missing"
if rg -n 'PLACEHOLDER|TO_BE_FINALIZED|TBD|<year>|<copyright' LICENSE >/dev/null; then
  die "LICENSE contains a placeholder"
fi

production_jar="build/libs/LegacyMiningWorld-${VERSION}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${VERSION}.jar"
package="build/release/LegacyMiningWorld-${VERSION}-release.tar.gz"
for jar_path in "$production_jar" "$verifier_jar"; do
  [ -r "$jar_path" ] || die "missing JAR: $jar_path"
  [ "$(jar tf "$jar_path" | grep -Fxc 'META-INF/LICENSE')" -eq 1 ] || die "$jar_path does not contain exactly one META-INF/LICENSE"
  cmp LICENSE <(unzip -p "$jar_path" META-INF/LICENSE) || die "$jar_path LICENSE differs from root"
done

[ -r "$package" ] || die "release package missing: $package"
extract_root="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-license-audit.XXXXXX")"
trap 'rm -rf "$extract_root"' EXIT
tar -xzf "$package" -C "$extract_root"
package_root="$extract_root/LegacyMiningWorld-${VERSION}"
cmp LICENSE "$package_root/LICENSE" || die "package LICENSE differs from root"
if find "$package_root" -name 'LICENSE-NOT-SELECTED.txt' -print -quit | grep -q .; then
  die "release package contains LICENSE-NOT-SELECTED.txt"
fi

for document in README.md docs/licensing.md docs/stable-release.md docs/installation.md docs/operations.md docs/user-acceptance-checklist.md; do
  [ -s "$document" ] || die "missing current document: $document"
  grep -Fq '1.0.1' "$document" || die "$document does not state version 1.0.1"
  grep -Fq 'MIT' "$document" || die "$document does not state MIT"
done
grep -Fq 'SPDX' README.md || die "README does not state SPDX"
grep -Fq 'SPDX' docs/licensing.md || die "licensing doc does not state SPDX"
if rg -n -i 'license not selected|ライセンス未選択|private/internal only|public distribution requires license decision|LICENSE-NOT-SELECTED' \
    README.md docs/licensing.md docs/stable-release.md docs/installation.md docs/operations.md docs/user-acceptance-checklist.md >/dev/null; then
  die "stale current license status remains"
fi

mkdir -p "$CHECK_DIRECTORY"
license_sha="$(sha256sum LICENSE | awk '{print $1}')"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$VERSION"
  printf 'license: MIT\nSPDX: MIT\n'
  printf 'root LICENSE SHA-256: %s\n' "$license_sha"
  printf 'root LICENSE canonical clauses: PASS\n'
  printf 'production JAR META-INF/LICENSE unique and byte-identical: PASS\n'
  printf 'verifier JAR META-INF/LICENSE unique and byte-identical: PASS\n'
  printf 'release package LICENSE byte-identical: PASS\n'
  printf 'LICENSE-NOT-SELECTED absent: PASS\n'
  printf 'current documentation license status: PASS\n'
  printf 'PASS: MIT License audit completed.\n'
} > "$CHECK_DIRECTORY/license-audit.txt"

printf 'PASS: MIT License audit completed.\n'
