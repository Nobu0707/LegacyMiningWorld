#!/usr/bin/env bash
set -euo pipefail

readonly VERSION="1.0.1"
readonly BASELINE_SHA="2c487560b0d862df0af0c452c3686a7ca72fade3"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

require_marker() {
  local file="$1"
  local marker="$2"
  [ -s "$CHECK_DIRECTORY/$file" ] || die "missing check log: $file"
  grep -Fq "$marker" "$CHECK_DIRECTORY/$file" || die "missing marker in $file: $marker"
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

[ "$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)" = "$VERSION" ] || die "unexpected version"
[ -z "$(git diff "$BASELINE_SHA" -- src/main/java)" ] || die "production Java changed"
[ -z "$(git diff -- src/main/java)" ] || die "uncommitted production Java changed"

require_marker stable-patch-payload-comparison.txt 'production functional payload: IDENTICAL'
require_marker license-audit.txt 'PASS: MIT License audit completed.'
require_marker license-package-reproducibility.txt 'reproducible package equality: PASS'
require_marker public-license-status-scan.txt 'PASS: public license status scan completed.'
require_marker gradle-build.txt 'BUILD SUCCESSFUL'
require_marker jar-contents.txt 'PASS: production JAR content audit completed.'
require_marker verifier-jar-contents.txt 'PASS: verifier JAR content audit completed.'

production_jar="build/libs/LegacyMiningWorld-${VERSION}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${VERSION}.jar"
package="build/release/LegacyMiningWorld-${VERSION}-release.tar.gz"
for artifact in "$production_jar" "$verifier_jar" "$package" LICENSE; do
  [ -r "$artifact" ] || die "missing artifact: $artifact"
done

head_sha="$(git rev-parse HEAD)"
head_subject="$(git log -1 --pretty=%s)"
java_version="$(java -version 2>&1 | head -n 1)"
gradle_version="$(./gradlew --version | sed -n 's/^Gradle /Gradle /p' | head -n 1)"
license_sha="$(sha256sum LICENSE | awk '{print $1}')"
production_sha="$(sha256sum "$production_jar" | awk '{print $1}')"
verifier_sha="$(sha256sum "$verifier_jar" | awk '{print $1}')"
package_sha="$(sha256sum "$package" | awk '{print $1}')"

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$VERSION"
  printf 'HEAD SHA: %s\nHEAD subject: %s\n' "$head_sha" "$head_subject"
  printf 'baseline 1.0.0 SHA: %s\n' "$BASELINE_SHA"
  printf 'Java: %s\nGradle: %s\n' "$java_version" "$gradle_version"
  printf 'license: MIT\nSPDX: MIT\nCopyright: Copyright (c) 2026 nobu0707\n'
  printf 'root LICENSE SHA-256: %s\n' "$license_sha"
  printf 'production Java changes: 0\n'
  printf 'class payload: IDENTICAL\nplugin.yml: version-only\n'
  printf 'tests executed: NO\n'
  printf 'reason: production Java and generation logic unchanged\n'
  printf 'Paper smoke executed: NO\nMultiverse smoke executed: NO\nlarge-scale executed: NO\n'
  printf 'build: PASS\nJAR contents: PASS\nlicense audit: PASS\npackage reproducibility: PASS\n'
  printf 'production JAR filename: %s\nproduction JAR SHA-256: %s\n' "$(basename "$production_jar")" "$production_sha"
  printf 'verifier JAR filename: %s\nverifier JAR SHA-256: %s\n' "$(basename "$verifier_jar")" "$verifier_sha"
  printf 'release package filename: %s\nrelease package SHA-256: %s\n' "$(basename "$package")" "$package_sha"
  printf 'package contents: production JAR, README.txt, RELEASE_NOTES.md, SHA256SUMS.txt, LICENSE\n'
  printf 'public-distribution-ready: YES\nexternally published: NO\n'
  printf 'tag: not created\npush: not performed\nupload: not performed\n'
  printf 'PASS: public-ready 1.0.1 release validation completed.\n'
} > "$CHECK_DIRECTORY/final-public-ready-release.txt"

printf 'Final public-ready summary written: %s/final-public-ready-release.txt\n' "$CHECK_DIRECTORY"
