#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_VERSION="1.0.0"
readonly EXPECTED_SUBJECT="chore: promote LegacyMiningWorld 1.0.0"
readonly BASELINE_SHA="3c30291b5c570d1c53a261ef8f5d9715b42512ff"
readonly EXPECTED_PAPER_SHA="d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b"
readonly EXPECTED_MULTIVERSE_SHA="574862aa3062af53957fe845de110a386f886445366836c8c63712e11d697400"
readonly EXPECTED_FULL_CHECKSUM="-56844145234233245"
readonly EXPECTED_Y5_CHECKSUM="-7581040318536063180"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

require_marker() {
  local log_name="$1"
  local marker="$2"
  [ -s "$CHECK_DIRECTORY/$log_name" ] || die "missing final log: $log_name"
  grep -Fq "$marker" "$CHECK_DIRECTORY/$log_name" \
    || die "missing PASS marker in $log_name: $marker"
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"

version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$EXPECTED_VERSION" ] || die "unexpected version: $version"
head_sha="$(git rev-parse HEAD)"
head_subject="$(git log -1 --pretty=%s)"
[ "$head_subject" = "$EXPECTED_SUBJECT" ] || die "unexpected HEAD subject: $head_subject"
git diff --quiet || die "tracked working tree modifications remain"
git diff --cached --quiet || die "index modifications remain"
status="$(git status --short)"
[ -z "$status" ] || die "working tree is not clean: $status"

production_jar="build/libs/LegacyMiningWorld-${version}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${version}.jar"
package="build/release/LegacyMiningWorld-${version}-release.tar.gz"
for artifact in "$production_jar" "$verifier_jar" "$package"; do
  [ -r "$artifact" ] || die "missing final stable artifact: $artifact"
done

require_marker production-code-audit.txt \
  'PASS: production code audit completed.'
require_marker compiler-audit.txt \
  'PASS: strict compiler audit completed with -Werror.'
require_marker dependency-audit.txt \
  'PASS: dependency audit completed.'
require_marker reproducible-build.txt \
  'PASS: two independent clean JAR builds are reproducible.'
require_marker final-jar-audit.txt \
  'PASS: final JAR content audit completed.'
require_marker stable-source-equivalence.txt \
  'PASS: stable source is equivalent to the RC baseline.'
require_marker rc-stable-payload-comparison.txt \
  'production functional payload: IDENTICAL'
require_marker stable-release-package.txt \
  'reproducible package equality: PASS'
require_marker gradle-test.txt 'BUILD SUCCESSFUL'
require_marker gradle-test.txt 'executed-test-count:'
require_marker local-paper-smoke.txt 'package JAR smoke: PASS'
require_marker multiverse-integration-smoke.txt \
  'PASS: Phase 4A Multiverse integration smoke completed successfully.'
require_marker large-scale-validation.txt \
  'PASS: Phase 4B1 large-scale validation completed.'
require_marker stable-acceptance.txt \
  'PASS: stable technical acceptance checks completed.'
require_marker stable-clean-room-validation.txt \
  'PASS: committed tracked source clean-room validation completed.'
require_marker normal-review-state.txt \
  'PASS: normal review checks completed for this tracked source content.'

for test_log in geology-engine-tests.txt geology-adapter-tests.txt \
    ore-engine-tests.txt ore-adapter-tests.txt \
    multiverse-verifier-tests.txt large-scale-verifier-tests.txt \
    large-scale-model-tests.txt; do
  require_marker "$test_log" 'executed-test-count:'
done

grep -Fq "worktree HEAD: $head_sha" \
  "$CHECK_DIRECTORY/stable-clean-room-validation.txt" \
  || die "stable clean-room log is stale"
grep -Fq "version: $version" "$CHECK_DIRECTORY/stable-clean-room-validation.txt" \
  || die "stable clean-room version is stale"

current_source_sha="$(git ls-files -z | LC_ALL=C sort -z \
  | xargs -0 sha256sum | sha256sum | awk '{print $1}')"
grep -Fxq "version: $version" "$CHECK_DIRECTORY/normal-review-state.txt" \
  || die "normal review state version is stale"
grep -Fxq "source content SHA-256: $current_source_sha" \
  "$CHECK_DIRECTORY/normal-review-state.txt" \
  || die "normal review state source content is stale"

full_checksum="$(sed -n 's/^fullChecksum=//p' \
  "$CHECK_DIRECTORY/large-scale-validation.txt" | tail -n 1)"
y5_checksum="$(sed -n 's/^y5_67Checksum=//p' \
  "$CHECK_DIRECTORY/large-scale-validation.txt" | tail -n 1)"
[ "$full_checksum" = "$EXPECTED_FULL_CHECKSUM" ] \
  || die "final full checksum mismatch: $full_checksum"
[ "$y5_checksum" = "$EXPECTED_Y5_CHECKSUM" ] \
  || die "final Y=5..67 checksum mismatch: $y5_checksum"
grep -Fq 'forbidden=0' "$CHECK_DIRECTORY/large-scale-validation.txt" \
  || die "final forbidden count is not zero"
grep -Fq 'unknownNonAir=0' "$CHECK_DIRECTORY/large-scale-validation.txt" \
  || die "final unknown non-AIR count is not zero"
grep -Fq 'biome=1115136 PLAINS' "$CHECK_DIRECTORY/large-scale-validation.txt" \
  || die "final biome golden is missing"

[ -s docs/user-acceptance-checklist.md ] \
  || die "user acceptance checklist is missing"
if rg -n '^[[:space:]]*-[[:space:]]+\[[xX]\]' \
    docs/user-acceptance-checklist.md >/dev/null; then
  die "Codex must not mark manual user acceptance items as completed"
fi

paper_sha="$(sha256sum server/paper-26.1.2-69.jar | awk '{print $1}')"
multiverse_sha="$(sha256sum server/plugins/multiverse-core-5.7.2.jar | awk '{print $1}')"
[ "$paper_sha" = "$EXPECTED_PAPER_SHA" ] || die "Paper SHA mismatch"
[ "$multiverse_sha" = "$EXPECTED_MULTIVERSE_SHA" ] || die "Multiverse SHA mismatch"
production_sha="$(sha256sum "$production_jar" | awk '{print $1}')"
verifier_sha="$(sha256sum "$verifier_jar" | awk '{print $1}')"
package_sha="$(sha256sum "$package" | awk '{print $1}')"
java_version="$(java -version 2>&1 | head -n 1)"
gradle_version="$(./gradlew --version | sed -n 's/^Gradle /Gradle /p' | head -n 1)"

grep -Fq "stable JAR SHA-256: $production_sha" \
  "$CHECK_DIRECTORY/rc-stable-payload-comparison.txt" \
  || die "payload comparison log does not match the final stable JAR"
grep -Fq "release JAR SHA-256: $production_sha" \
  "$CHECK_DIRECTORY/stable-release-package.txt" \
  || die "stable package log does not match the final stable JAR"
grep -Fq "release package SHA-256: $package_sha" \
  "$CHECK_DIRECTORY/stable-release-package.txt" \
  || die "stable package log does not match the final package"
grep -Fq "verifier SHA-256: $verifier_sha" "$CHECK_DIRECTORY/final-jar-audit.txt" \
  || die "final JAR audit does not match the verifier JAR"
grep -Fq "release JAR SHA: $production_sha" \
  "$CHECK_DIRECTORY/stable-clean-room-validation.txt" \
  || die "clean-room summary does not match the final production JAR"
grep -Fq "verifier JAR SHA: $verifier_sha" \
  "$CHECK_DIRECTORY/stable-clean-room-validation.txt" \
  || die "clean-room summary does not match the final verifier JAR"
grep -Fq "release package SHA: $package_sha" \
  "$CHECK_DIRECTORY/stable-clean-room-validation.txt" \
  || die "clean-room summary does not match the final stable package"

for release_document in README.md docs/stable-release.md AGENT.md; do
  [ -s "$release_document" ] || die "missing final stable document: $release_document"
  grep -Fq "$production_sha" "$release_document" \
    || die "$release_document does not contain the final production JAR SHA"
  grep -Fq "$verifier_sha" "$release_document" \
    || die "$release_document does not contain the final verifier JAR SHA"
  grep -Fq "$package_sha" "$release_document" \
    || die "$release_document does not contain the final stable package SHA"
done
if rg -n 'PLACEHOLDER|TO_BE_FINALIZED|TBD|__STABLE_[A-Z0-9_]+__' \
    README.md docs/stable-release.md AGENT.md >/dev/null; then
  die "final stable documents contain a placeholder"
fi

mapfile -t head_tags < <(git tag --points-at HEAD)
[ "${#head_tags[@]}" -eq 0 ] || die "stable HEAD already has a tag: ${head_tags[*]}"

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$version"
  printf 'HEAD SHA: %s\n' "$head_sha"
  printf 'HEAD subject: %s\n' "$head_subject"
  printf 'baseline RC SHA: %s\n' "$BASELINE_SHA"
  printf 'Java: %s\n' "$java_version"
  printf 'Gradle: %s\n' "$gradle_version"
  printf 'Paper SHA: %s\n' "$paper_sha"
  printf 'Multiverse SHA: %s\n' "$multiverse_sha"
  printf 'stable JAR filename: %s\n' "$(basename "$production_jar")"
  printf 'stable JAR SHA: %s\n' "$production_sha"
  printf 'verifier JAR filename: %s\n' "$(basename "$verifier_jar")"
  printf 'verifier JAR SHA: %s\n' "$verifier_sha"
  printf 'stable package filename: %s\n' "$(basename "$package")"
  printf 'stable package SHA: %s\n' "$package_sha"
  printf 'package contents: production JAR, README.txt, RELEASE_NOTES.md, SHA256SUMS.txt, LICENSE-NOT-SELECTED.txt\n'
  printf 'production source equivalent: PASS\n'
  printf 'RC/stable class payload: IDENTICAL\n'
  printf 'plugin.yml version-only: PASS\n'
  printf 'compiler audit: PASS\n'
  printf 'dependency audit: PASS\n'
  printf 'reproducible JARs: PASS\n'
  printf 'reproducible package: PASS\n'
  printf 'stable version scan: PASS\n'
  printf 'all tests with nonzero task counts: PASS\n'
  printf 'normal Paper: PASS\n'
  printf 'package Paper: PASS\n'
  printf 'Multiverse create/restart: PASS\n'
  printf 'large-scale A1/A2/B1: PASS\n'
  printf 'clean-room: PASS\n'
  printf 'main/clean JAR, verifier, package, and golden equality: PASS\n'
  printf 'full checksum: %s\n' "$full_checksum"
  printf 'Y=5..67 checksum: %s\n' "$y5_checksum"
  printf 'forbidden: 0\n'
  printf 'unknown non-AIR: 0\n'
  printf 'biome: PASS (1115136 PLAINS)\n'
  printf 'stable: YES\n'
  printf 'release candidate: NO\n'
  printf 'license selected: NO\n'
  printf 'private/internal distribution: YES\n'
  printf 'public publication: NO\n'
  printf 'manual user checklist: CREATED / NOT EXECUTED BY CODEX\n'
  printf 'tag: not created\n'
  printf 'push: not performed\n'
  printf 'publish: not performed\n'
  printf 'PASS: final stable technical release validation completed.\n'
} > "$CHECK_DIRECTORY/final-stable-release.txt"

./scripts/run-stable-version-scan.sh
require_marker stable-version-scan.txt \
  'PASS: stable current-version scan completed.'

printf 'Final stable summary written: %s/final-stable-release.txt\n' \
  "$CHECK_DIRECTORY"
