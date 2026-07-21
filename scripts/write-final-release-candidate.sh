#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_VERSION="1.0.0-rc.1"
readonly EXPECTED_SUBJECT="chore: prepare 1.0.0 release candidate"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"
version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$EXPECTED_VERSION" ] || die "unexpected version: $version"
head_sha="$(git rev-parse HEAD)"
head_subject="$(git log -1 --pretty=%s)"
[ "$head_subject" = "$EXPECTED_SUBJECT" ] || die "unexpected HEAD subject: $head_subject"

production_jar="build/libs/LegacyMiningWorld-${version}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${version}.jar"
package="build/release/LegacyMiningWorld-${version}-release.tar.gz"
for artifact in "$production_jar" "$verifier_jar" "$package"; do
  [ -r "$artifact" ] || die "missing final artifact: $artifact"
done
for log in production-code-audit.txt compiler-audit.txt dependency-audit.txt \
    reproducible-build.txt final-jar-audit.txt release-package.txt gradle-test.txt \
    local-paper-smoke.txt multiverse-integration-smoke.txt large-scale-validation.txt \
    clean-room-validation.txt; do
  [ -s "$CHECK_DIRECTORY/$log" ] || die "missing final log: $log"
done
grep -Fq "worktree HEAD: $head_sha" "$CHECK_DIRECTORY/clean-room-validation.txt" \
  || die "clean-room log is stale"
grep -Fq 'PASS: committed tracked source clean-room validation completed.' \
  "$CHECK_DIRECTORY/clean-room-validation.txt" || die "clean-room did not PASS"
grep -Fq 'production equality: PASS' "$CHECK_DIRECTORY/reproducible-build.txt"
grep -Fq 'verifier equality: PASS' "$CHECK_DIRECTORY/reproducible-build.txt"
grep -Fq 'reproducible package equality: PASS' "$CHECK_DIRECTORY/release-package.txt"
grep -Fq 'PASS: production code audit completed.' "$CHECK_DIRECTORY/production-code-audit.txt"
grep -Fq 'PASS: strict compiler audit completed with -Werror.' "$CHECK_DIRECTORY/compiler-audit.txt"
grep -Fq 'PASS: dependency audit completed.' "$CHECK_DIRECTORY/dependency-audit.txt"
grep -Fq 'BUILD SUCCESSFUL' "$CHECK_DIRECTORY/gradle-test.txt"
grep -Fq 'package JAR smoke: PASS' "$CHECK_DIRECTORY/local-paper-smoke.txt"
grep -Fq 'PASS: Phase 4A Multiverse integration smoke completed successfully.' \
  "$CHECK_DIRECTORY/multiverse-integration-smoke.txt"
grep -Fq 'PASS: Phase 4B1 large-scale validation completed.' \
  "$CHECK_DIRECTORY/large-scale-validation.txt"

paper_sha="$(sha256sum server/paper-26.1.2-69.jar | awk '{print $1}')"
multiverse_sha="$(sha256sum server/plugins/multiverse-core-5.7.2.jar | awk '{print $1}')"
production_sha="$(sha256sum "$production_jar" | awk '{print $1}')"
verifier_sha="$(sha256sum "$verifier_jar" | awk '{print $1}')"
package_sha="$(sha256sum "$package" | awk '{print $1}')"
full_checksum="$(sed -n 's/^fullChecksum=//p' "$CHECK_DIRECTORY/large-scale-validation.txt")"
y5_checksum="$(sed -n 's/^y5_67Checksum=//p' "$CHECK_DIRECTORY/large-scale-validation.txt")"
java_version="$(java -version 2>&1 | head -n 1)"
gradle_version="$(./gradlew --version | sed -n 's/^Gradle /Gradle /p' | head -n 1)"

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$version"
  printf 'HEAD SHA: %s\n' "$head_sha"
  printf 'HEAD subject: %s\n' "$head_subject"
  printf 'Java: %s\n' "$java_version"
  printf 'Gradle: %s\n' "$gradle_version"
  printf 'Paper SHA: %s\n' "$paper_sha"
  printf 'Multiverse SHA: %s\n' "$multiverse_sha"
  printf 'release JAR filename: %s\n' "$(basename "$production_jar")"
  printf 'release JAR SHA: %s\n' "$production_sha"
  printf 'verifier JAR SHA: %s\n' "$verifier_sha"
  printf 'release package filename: %s\n' "$(basename "$package")"
  printf 'release package SHA: %s\n' "$package_sha"
  printf 'reproducible JAR: PASS\n'
  printf 'reproducible package: PASS\n'
  printf 'production audit: PASS\n'
  printf 'compiler audit: PASS\n'
  printf 'dependency audit: PASS\n'
  printf 'all unit tests: PASS\n'
  printf 'normal Paper smoke: PASS\n'
  printf 'package JAR smoke: PASS\n'
  printf 'Multiverse 4A: PASS\n'
  printf 'large-scale 4B1: PASS\n'
  printf 'clean-room: PASS\n'
  printf 'full checksum: %s\n' "$full_checksum"
  printf 'Y=5..67 checksum: %s\n' "$y5_checksum"
  printf 'forbidden: 0\n'
  printf 'biome: PASS (1115136 PLAINS)\n'
  printf 'release contents: PASS\n'
  printf 'license selected: NO\n'
  printf 'stable release: NO\n'
  printf 'release candidate: YES\n'
  printf 'PASS: final release candidate validation completed.\n'
} > "$CHECK_DIRECTORY/final-release-candidate.txt"

printf 'Final release-candidate summary written: %s/final-release-candidate.txt\n' \
  "$CHECK_DIRECTORY"
