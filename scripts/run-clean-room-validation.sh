#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_VERSION="1.0.0"
readonly EXPECTED_SUBJECT="chore: promote LegacyMiningWorld 1.0.0"
readonly PAPER_SOURCE="server/paper-26.1.2-69.jar"
readonly EULA_SOURCE="server/eula.txt"
readonly MULTIVERSE_SOURCE="server/plugins/multiverse-core-5.7.2.jar"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

if [ "${LEGACYMININGWORLD_CLEAN_ROOM:-0}" = "1" ]; then
  printf 'clean-room recursion prevented: LEGACYMININGWORLD_CLEAN_ROOM=1\n' >&2
  exit 2
fi

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"
version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$EXPECTED_VERSION" ] || die "expected version $EXPECTED_VERSION"
head_sha="$(git rev-parse HEAD)"
head_subject="$(git log -1 --pretty=%s)"
[ "$head_subject" = "$EXPECTED_SUBJECT" ] \
  || die "clean-room requires committed Phase 5 stable HEAD; found: $head_subject"
git diff --quiet || die "tracked working tree modifications must be committed"
git diff --cached --quiet || die "index modifications must be committed"
main_status="$(git status --short)"
[ -z "$main_status" ] || die "main working tree must be clean: $main_status"

for source in "$PAPER_SOURCE" "$EULA_SOURCE" "$MULTIVERSE_SOURCE"; do
  [ -r "$source" ] || die "missing clean-room input: $source"
done
main_jar="build/libs/LegacyMiningWorld-${version}.jar"
main_verifier="build/libs/LegacyMiningWorld-MultiverseVerifier-${version}.jar"
main_package="build/release/LegacyMiningWorld-${version}-release.tar.gz"
for artifact in "$main_jar" "$main_verifier" "$main_package"; do
  [ -r "$artifact" ] || die "missing main artifact: $artifact"
done
main_jar_sha="$(sha256sum "$main_jar" | awk '{print $1}')"
main_verifier_sha="$(sha256sum "$main_verifier" | awk '{print $1}')"
main_package_sha="$(sha256sum "$main_package" | awk '{print $1}')"
main_check_dir="build/review-checks"
for required_log in normal-review-state.txt production-code-audit.txt \
    compiler-audit.txt dependency-audit.txt reproducible-build.txt \
    final-jar-audit.txt stable-source-equivalence.txt \
    rc-stable-payload-comparison.txt release-package.txt \
    stable-release-package.txt stable-version-scan.txt stable-acceptance.txt \
    local-paper-smoke.txt multiverse-integration-smoke.txt \
    large-scale-validation.txt; do
  [ -s "$main_check_dir/$required_log" ] \
    || die "missing main stable review log: $required_log"
done
current_source_sha="$(git ls-files -z | LC_ALL=C sort -z \
  | xargs -0 sha256sum | sha256sum | awk '{print $1}')"
grep -Fxq "version: $version" "$main_check_dir/normal-review-state.txt" \
  || die "main normal-review state has a stale version"
grep -Fxq "source content SHA-256: $current_source_sha" \
  "$main_check_dir/normal-review-state.txt" \
  || die "main normal-review state has stale source content"
main_full_checksum="$(sed -n 's/^fullChecksum=//p' \
  "$main_check_dir/large-scale-validation.txt" | tail -n 1)"
main_y5_checksum="$(sed -n 's/^y5_67Checksum=//p' \
  "$main_check_dir/large-scale-validation.txt" | tail -n 1)"
[ "$main_full_checksum" = "-56844145234233245" ] \
  || die "main full checksum is not the stable golden"
[ "$main_y5_checksum" = "-7581040318536063180" ] \
  || die "main Y=5..67 checksum is not the stable golden"
grep -Fq 'forbidden=0' "$main_check_dir/large-scale-validation.txt" \
  || die "main forbidden count is not zero"
grep -Fq 'unknownNonAir=0' "$main_check_dir/large-scale-validation.txt" \
  || die "main unknown non-AIR count is not zero"
grep -Fq 'biome=1115136 PLAINS' "$main_check_dir/large-scale-validation.txt" \
  || die "main biome golden is missing"
paper_sha="$(sha256sum "$PAPER_SOURCE" | awk '{print $1}')"
eula_sha="$(sha256sum "$EULA_SOURCE" | awk '{print $1}')"
multiverse_sha="$(sha256sum "$MULTIVERSE_SOURCE" | awk '{print $1}')"
tracked_count="$(git ls-files | wc -l)"

clean_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-clean-room.XXXXXX")"
clean_root="$clean_parent/worktree"
clean_log="$clean_parent/clean-room-execution.txt"
summary_temp="$clean_parent/stable-clean-room-validation.txt"
worktree_added=0
cleanup() {
  if [ "$worktree_added" -eq 1 ]; then
    git worktree remove --force "$clean_root" >/dev/null 2>&1 || true
  fi
  rm -rf "$clean_parent"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

git worktree add --detach "$clean_root" "$head_sha" > "$clean_log" 2>&1
worktree_added=1
mkdir -p "$clean_root/server/plugins"
cp "$PAPER_SOURCE" "$clean_root/server/paper-26.1.2-69.jar"
cp "$EULA_SOURCE" "$clean_root/server/eula.txt"
cp "$MULTIVERSE_SOURCE" "$clean_root/server/plugins/multiverse-core-5.7.2.jar"

run_clean() {
  local label="$1"
  shift
  printf '\nclean-room task: %s\n' "$label" >> "$clean_log"
  (
    cd "$clean_root"
    LEGACYMININGWORLD_CLEAN_ROOM=1 "$@"
  ) >> "$clean_log" 2>&1 || {
    tail -n 200 "$clean_log" >&2
    die "clean-room task failed: $label"
  }
}

run_clean git-status-before git status --short
run_clean gradle-clean-test ./gradlew --no-daemon clean test
run_clean geology-engine ./gradlew --no-daemon geologyEngineTest
run_clean geology-adapter ./gradlew --no-daemon geologyAdapterTest
run_clean ore-engine ./gradlew --no-daemon oreEngineTest
run_clean ore-adapter ./gradlew --no-daemon oreAdapterTest
run_clean multiverse-verifier ./gradlew --no-daemon multiverseVerifierTest
run_clean large-scale-verifier ./gradlew --no-daemon largeScaleVerifierTest
run_clean large-scale-model ./gradlew --no-daemon largeScaleModelTest
run_clean gradle-build ./gradlew --no-daemon build
run_clean verifier-jar ./gradlew --no-daemon multiverseVerifierJar
run_clean region-header-tests python3 -m unittest scripts/test_inspect_region_headers.py
run_clean review-checks ./scripts/run-review-checks.sh

if (
  cd "$clean_root"
  LEGACYMININGWORLD_CLEAN_ROOM=1 ./scripts/run-clean-room-validation.sh \
    > "$clean_parent/recursion.txt" 2>&1
); then
  die "clean-room recursion guard unexpectedly allowed recursion"
fi
grep -Fq 'clean-room recursion prevented' "$clean_parent/recursion.txt" \
  || die "clean-room recursion guard signal missing"

clean_jar="$clean_root/build/libs/LegacyMiningWorld-${version}.jar"
clean_verifier="$clean_root/build/libs/LegacyMiningWorld-MultiverseVerifier-${version}.jar"
clean_package="$clean_root/build/release/LegacyMiningWorld-${version}-release.tar.gz"
clean_jar_sha="$(sha256sum "$clean_jar" | awk '{print $1}')"
clean_verifier_sha="$(sha256sum "$clean_verifier" | awk '{print $1}')"
clean_package_sha="$(sha256sum "$clean_package" | awk '{print $1}')"
[ "$main_jar_sha" = "$clean_jar_sha" ] || die "main/clean production JAR differs"
[ "$main_verifier_sha" = "$clean_verifier_sha" ] || die "main/clean verifier JAR differs"
[ "$main_package_sha" = "$clean_package_sha" ] || die "main/clean release package differs"

check_dir="$clean_root/build/review-checks"
for required_log in \
    production-code-audit.txt compiler-audit.txt dependency-audit.txt \
    reproducible-build.txt final-jar-audit.txt stable-source-equivalence.txt \
    rc-stable-payload-comparison.txt release-package.txt stable-release-package.txt \
    stable-version-scan.txt stable-acceptance.txt normal-review-state.txt \
    local-paper-smoke.txt multiverse-integration-smoke.txt \
    large-scale-validation.txt; do
  [ -s "$check_dir/$required_log" ] || die "clean review log missing: $required_log"
done
grep -Fq 'PASS: production code audit completed.' "$check_dir/production-code-audit.txt"
grep -Fq 'PASS: strict compiler audit completed with -Werror.' "$check_dir/compiler-audit.txt"
grep -Fq 'PASS: dependency audit completed.' "$check_dir/dependency-audit.txt"
grep -Fq 'PASS: two independent clean JAR builds are reproducible.' \
  "$check_dir/reproducible-build.txt"
grep -Fq 'PASS: release package generated and self-checked.' "$check_dir/release-package.txt"
grep -Fq 'reproducible package equality: PASS' "$check_dir/stable-release-package.txt"
grep -Fq 'PASS: stable source is equivalent to the RC baseline.' \
  "$check_dir/stable-source-equivalence.txt"
grep -Fq 'production functional payload: IDENTICAL' \
  "$check_dir/rc-stable-payload-comparison.txt"
grep -Fq 'PASS: stable current-version scan completed.' \
  "$check_dir/stable-version-scan.txt"
grep -Fq 'PASS: stable technical acceptance checks completed.' \
  "$check_dir/stable-acceptance.txt"
grep -Fq 'PASS: Phase 4A Multiverse integration smoke completed successfully.' \
  "$check_dir/multiverse-integration-smoke.txt"
grep -Fq 'PASS: Phase 4B1 large-scale validation completed.' \
  "$check_dir/large-scale-validation.txt"
grep -Fxq "version: $version" "$check_dir/normal-review-state.txt" \
  || die "clean normal-review state has a stale version"
grep -Fxq "source content SHA-256: $current_source_sha" \
  "$check_dir/normal-review-state.txt" \
  || die "clean normal-review state has stale source content"

clean_full_checksum="$(sed -n 's/^fullChecksum=//p' \
  "$check_dir/large-scale-validation.txt" | tail -n 1)"
clean_y5_checksum="$(sed -n 's/^y5_67Checksum=//p' \
  "$check_dir/large-scale-validation.txt" | tail -n 1)"
[ "$clean_full_checksum" = "$main_full_checksum" ] \
  || die "main/clean full checksum differs"
[ "$clean_y5_checksum" = "$main_y5_checksum" ] \
  || die "main/clean Y=5..67 checksum differs"
grep -Fq 'forbidden=0' "$check_dir/large-scale-validation.txt" \
  || die "clean forbidden count is not zero"
grep -Fq 'unknownNonAir=0' "$check_dir/large-scale-validation.txt" \
  || die "clean unknown non-AIR count is not zero"
grep -Fq 'biome=1115136 PLAINS' "$check_dir/large-scale-validation.txt" \
  || die "clean biome golden is missing"

git -C "$clean_root" diff --quiet || die "clean worktree tracked files changed"
git -C "$clean_root" diff --cached --quiet || die "clean worktree index changed"
clean_status="$(git -C "$clean_root" status --short)"
[ -z "$clean_status" ] || die "clean worktree has non-ignored changes: $clean_status"
[ "$paper_sha" = "$(sha256sum "$PAPER_SOURCE" | awk '{print $1}')" ] \
  || die "main Paper input changed"
[ "$eula_sha" = "$(sha256sum "$EULA_SOURCE" | awk '{print $1}')" ] \
  || die "main EULA input changed"
[ "$multiverse_sha" = "$(sha256sum "$MULTIVERSE_SOURCE" | awk '{print $1}')" ] \
  || die "main Multiverse input changed"

review_log_count="$(find "$check_dir" -maxdepth 1 -type f -name '*.txt' | wc -l)"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'worktree HEAD: %s\n' "$head_sha"
  printf 'HEAD subject: %s\n' "$head_subject"
  printf 'version: %s\n' "$version"
  printf 'source tracked file count: %s\n' "$tracked_count"
  printf 'Paper SHA: %s\n' "$paper_sha"
  printf 'EULA SHA: %s\n' "$eula_sha"
  printf 'Multiverse SHA: %s\n' "$multiverse_sha"
  printf 'all test tasks: PASS\n'
  printf 'normal Paper smoke: PASS\n'
  printf 'package JAR Paper smoke: PASS\n'
  printf 'Multiverse 4A: PASS\n'
  printf 'large scale 4B1: PASS\n'
  printf 'stable source equivalence: PASS\n'
  printf 'RC/stable class payload: IDENTICAL\n'
  printf 'release JAR SHA: %s\n' "$clean_jar_sha"
  printf 'verifier JAR SHA: %s\n' "$clean_verifier_sha"
  printf 'release package SHA: %s\n' "$clean_package_sha"
  printf 'main/clean JAR equality: PASS\n'
  printf 'main/clean verifier equality: PASS\n'
  printf 'main/clean package equality: PASS\n'
  printf 'main/clean full checksum equality: PASS (%s)\n' "$clean_full_checksum"
  printf 'main/clean Y=5..67 checksum equality: PASS (%s)\n' "$clean_y5_checksum"
  printf 'main/clean forbidden and unknown non-AIR: 0 PASS\n'
  printf 'main/clean biome: 1115136 PLAINS PASS\n'
  printf 'review log count: %s\n' "$review_log_count"
  printf 'review log required list: PASS\n'
  printf 'recursion prevention: PASS\n'
  printf 'source files unchanged: PASS\n'
  printf 'tracked working tree clean: PASS\n'
  printf 'PASS: committed tracked source clean-room validation completed.\n'
} > "$summary_temp"

mkdir -p "$repo_root/build/review-checks"
cp "$summary_temp" "$repo_root/build/review-checks/stable-clean-room-validation.txt"
cp "$summary_temp" "$repo_root/build/review-checks/clean-room-validation.txt"
worktree_added=0
git worktree remove --force "$clean_root"

"$repo_root/scripts/write-final-stable-release.sh"
printf 'Clean-room validation passed for %s.\n' "$head_sha"
