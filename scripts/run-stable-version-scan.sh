#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_VERSION="1.0.0"
readonly CHECK_DIRECTORY="build/review-checks"
readonly RELEASE_DIRECTORY="build/release"
readonly FORBIDDEN_PATTERN='1\.0\.0-rc\.1|0\.6\.0-alpha\.1|SNAPSHOT'

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"
version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$EXPECTED_VERSION" ] || die "expected version $EXPECTED_VERSION"

production_jar="build/libs/LegacyMiningWorld-${version}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${version}.jar"
package_path="$RELEASE_DIRECTORY/LegacyMiningWorld-${version}-release.tar.gz"
for artifact in "$production_jar" "$verifier_jar" "$package_path"; do
  [ -r "$artifact" ] || die "missing stable artifact for version scan: $artifact"
done

current_documents=(
  README.md
  docs/installation.md
  docs/operations.md
  docs/stable-release.md
)
for document in "${current_documents[@]}"; do
  [ -s "$document" ] || die "missing current stable document: $document"
done
grep -Fq 'LegacyMiningWorld `1.0.0`' README.md \
  || die "README current version is not 1.0.0"
grep -Fxq -- '- stable: YES' README.md \
  || die "README does not declare stable YES"
grep -Fxq -- '- release candidate: NO' README.md \
  || die "README does not declare release candidate NO"
grep -Fq '| version | `1.0.0` |' docs/stable-release.md \
  || die "stable release document version is not 1.0.0"
for expected_name in \
    'LegacyMiningWorld-1.0.0.jar' \
    'LegacyMiningWorld-MultiverseVerifier-1.0.0.jar' \
    'LegacyMiningWorld-1.0.0-release.tar.gz'; do
  grep -Fq "$expected_name" README.md \
    || die "README is missing current artifact name: $expected_name"
  grep -Fq "$expected_name" docs/stable-release.md \
    || die "stable release document is missing current artifact name: $expected_name"
done
if rg -n -i \
    -e '(current|現行)[^\n]*(1\.0\.0-rc\.1|0\.6\.0-alpha\.1|SNAPSHOT)' \
    -e 'stable:[[:space:]]*(NO|いいえ)' \
    -e 'release candidate:[[:space:]]*(YES|はい)' \
    -e 'SNAPSHOT' \
    "${current_documents[@]}"; then
  die "current stable document contains stale current-version metadata"
fi

scan_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-version-scan.XXXXXX")"
trap 'rm -rf "$scan_parent"' EXIT
unzip -p "$production_jar" plugin.yml > "$scan_parent/production-plugin.yml"
unzip -p "$verifier_jar" plugin.yml > "$scan_parent/verifier-plugin.yml"
for metadata in "$scan_parent/production-plugin.yml" "$scan_parent/verifier-plugin.yml"; do
  grep -Fxq "version: $version" "$metadata" \
    || die "generated plugin.yml does not contain version $version: $metadata"
  if rg -n -e "$FORBIDDEN_PATTERN" "$metadata"; then
    die "generated plugin.yml contains a stale version"
  fi
done

mapfile -t package_entries < <(tar -tzf "$package_path")
readonly expected_package_entries=(
  "LegacyMiningWorld-${version}/"
  "LegacyMiningWorld-${version}/LICENSE-NOT-SELECTED.txt"
  "LegacyMiningWorld-${version}/LegacyMiningWorld-${version}.jar"
  "LegacyMiningWorld-${version}/README.txt"
  "LegacyMiningWorld-${version}/RELEASE_NOTES.md"
  "LegacyMiningWorld-${version}/SHA256SUMS.txt"
)
[ "${#package_entries[@]}" -eq "${#expected_package_entries[@]}" ] \
  || die "stable package member count is not exact: ${#package_entries[@]}"
for index in "${!expected_package_entries[@]}"; do
  [ "${package_entries[$index]}" = "${expected_package_entries[$index]}" ] \
    || die "stable package member differs at index $index: ${package_entries[$index]}"
done
tar -xzf "$package_path" -C "$scan_parent"
package_root="$scan_parent/LegacyMiningWorld-${version}"
[ -d "$package_root" ] || die "stable package root is missing"
mapfile -t extracted_entries < <(find "$package_root" -mindepth 1 -maxdepth 1 \
  -printf '%f\n' | LC_ALL=C sort)
readonly expected_extracted_entries=(
  LICENSE-NOT-SELECTED.txt
  "LegacyMiningWorld-${version}.jar"
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
)
[ "${#extracted_entries[@]}" -eq "${#expected_extracted_entries[@]}" ] \
  || die "extracted package entry count is not exact: ${#extracted_entries[@]}"
for index in "${!expected_extracted_entries[@]}"; do
  [ "${extracted_entries[$index]}" = "${expected_extracted_entries[$index]}" ] \
    || die "extracted package entry differs at index $index: ${extracted_entries[$index]}"
  [ -f "$package_root/${expected_extracted_entries[$index]}" ] \
    && [ ! -L "$package_root/${expected_extracted_entries[$index]}" ] \
    || die "extracted package member is not a regular file: ${expected_extracted_entries[$index]}"
done
packaged_jar="$package_root/LegacyMiningWorld-${version}.jar"
cmp "$production_jar" "$packaged_jar" \
  || die "packaged production JAR differs from build/libs"
unzip -p "$packaged_jar" plugin.yml > "$scan_parent/packaged-plugin.yml"
grep -Fxq "version: $version" "$scan_parent/packaged-plugin.yml" \
  || die "packaged production plugin.yml does not contain version $version"
if rg -n -e "$FORBIDDEN_PATTERN" "$scan_parent/packaged-plugin.yml"; then
  die "packaged production plugin.yml contains a stale version"
fi
if find "$package_root" -mindepth 1 -maxdepth 1 -printf '%f\n' \
    | rg -n -i 'rc|alpha|MultiverseVerifier'; then
  die "stable package contains an old or verifier filename"
fi
if rg -n -e "$FORBIDDEN_PATTERN" \
    "$package_root/README.txt" "$package_root/RELEASE_NOTES.md" \
    "$package_root/LICENSE-NOT-SELECTED.txt" "$package_root/SHA256SUMS.txt"; then
  die "stable package metadata contains a stale version"
fi

mapfile -t release_files < <(find "$RELEASE_DIRECTORY" -mindepth 1 -maxdepth 1 \
  -type f -printf '%f\n' | LC_ALL=C sort)
if find "$RELEASE_DIRECTORY" -mindepth 1 -maxdepth 1 ! -type f -print -quit \
    | grep -q .; then
  die "build/release contains a directory, symlink, or other non-file entry"
fi
readonly expected_release_files=(
  LICENSE-NOT-SELECTED.txt
  "LegacyMiningWorld-${version}-release.tar.gz"
  "LegacyMiningWorld-${version}.jar"
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
)
[ "${#release_files[@]}" -eq "${#expected_release_files[@]}" ] \
  || die "build/release file count is not exact: ${#release_files[@]}"
for index in "${!expected_release_files[@]}"; do
  [ "${release_files[$index]}" = "${expected_release_files[$index]}" ] \
    || die "build/release file differs at index $index: ${release_files[$index]}"
done

optional_logs=(stable-acceptance.txt final-stable-release.txt)
for log_name in "${optional_logs[@]}"; do
  if [ -e "$CHECK_DIRECTORY/$log_name" ] \
      && rg -n -e "$FORBIDDEN_PATTERN" "$CHECK_DIRECTORY/$log_name"; then
    die "current stable summary contains a stale version: $log_name"
  fi
done

mkdir -p "$CHECK_DIRECTORY"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$version"
  printf 'current documents: PASS\n'
  printf 'production plugin.yml: PASS\n'
  printf 'verifier plugin.yml: PASS\n'
  printf 'release directory exact filenames: PASS\n'
  printf 'package filenames and metadata: PASS\n'
  printf 'packaged/build JAR equality and packaged plugin.yml: PASS\n'
  printf 'forbidden current-version strings: 1.0.0-rc.1, 0.6.0-alpha.1, SNAPSHOT\n'
  printf 'historical documents excluded intentionally: CHANGELOG.md, docs/release-candidate.md, AGENT.md, docs/development-phases.md, docs/design-decisions.md\n'
  printf 'PASS: stable current-version scan completed.\n'
} > "$CHECK_DIRECTORY/stable-version-scan.txt"

printf 'Stable current-version scan passed.\n'
