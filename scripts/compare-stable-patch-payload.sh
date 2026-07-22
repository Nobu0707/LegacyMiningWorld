#!/usr/bin/env bash
set -euo pipefail

readonly BASELINE_SHA="2c487560b0d862df0af0c452c3686a7ca72fade3"
readonly BASELINE_VERSION="1.0.0"
readonly CURRENT_VERSION="1.0.1"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$CURRENT_VERSION" ] || die "expected version $CURRENT_VERSION, found $version"
git cat-file -e "$BASELINE_SHA^{commit}" || die "baseline commit is unavailable: $BASELINE_SHA"
[ "$(git show "$BASELINE_SHA:gradle.properties" | sed -n 's/^legacyminingworld_version=//p' | tail -n 1)" = "$BASELINE_VERSION" ] \
  || die "baseline version is not $BASELINE_VERSION"
[ -z "$(git diff "$BASELINE_SHA" -- src/main/java)" ] || die "production Java differs from baseline"

comparison_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-patch.XXXXXX")"
baseline_root="$comparison_parent/baseline-worktree"
worktree_added=0
cleanup() {
  if [ "$worktree_added" -eq 1 ]; then
    git worktree remove --force "$baseline_root" >/dev/null 2>&1 || true
  fi
  rm -rf "$comparison_parent"
}
trap cleanup EXIT

git worktree add --detach "$baseline_root" "$BASELINE_SHA" > "$comparison_parent/worktree.txt" 2>&1
worktree_added=1

cat > "$comparison_parent/build-dir.init.gradle" <<'GRADLE'
gradle.beforeProject { project ->
    def target = System.getenv('LEGACYMININGWORLD_COMPARISON_BUILD_DIR')
    if (target == null || target.isBlank()) {
        throw new GradleException('LEGACYMININGWORLD_COMPARISON_BUILD_DIR is required')
    }
    project.layout.buildDirectory.set(new File(target))
}
GRADLE

(
  cd "$baseline_root"
  LEGACYMININGWORLD_COMPARISON_BUILD_DIR="$comparison_parent/baseline-build" \
    ./gradlew --no-daemon --init-script "$comparison_parent/build-dir.init.gradle" clean jar -x test
) > "$comparison_parent/baseline-build.txt" 2>&1 \
  || { tail -n 200 "$comparison_parent/baseline-build.txt" >&2; die "baseline build failed"; }

LEGACYMININGWORLD_COMPARISON_BUILD_DIR="$comparison_parent/current-build" \
  ./gradlew --no-daemon --init-script "$comparison_parent/build-dir.init.gradle" clean jar -x test \
  > "$comparison_parent/current-build.txt" 2>&1 \
  || { tail -n 200 "$comparison_parent/current-build.txt" >&2; die "current build failed"; }

baseline_jar="$comparison_parent/baseline-build/libs/LegacyMiningWorld-${BASELINE_VERSION}.jar"
current_jar="$comparison_parent/current-build/libs/LegacyMiningWorld-${CURRENT_VERSION}.jar"
[ -r "$baseline_jar" ] || die "baseline JAR missing"
[ -r "$current_jar" ] || die "current JAR missing"

jar tf "$baseline_jar" | LC_ALL=C sort | grep -E '^net/nobu0707/legacyminingworld/.*\.class$' \
  > "$comparison_parent/baseline-classes.txt"
jar tf "$current_jar" | LC_ALL=C sort | grep -E '^net/nobu0707/legacyminingworld/.*\.class$' \
  > "$comparison_parent/current-classes.txt"
[ -s "$comparison_parent/baseline-classes.txt" ] || die "production class set is empty"
cmp "$comparison_parent/baseline-classes.txt" "$comparison_parent/current-classes.txt" \
  || die "class entry names differ"

hash_classes() {
  local jar_path="$1"
  local entries="$2"
  local output="$3"
  : > "$output"
  while IFS= read -r entry; do
    printf '%s  %s\n' "$(unzip -p "$jar_path" "$entry" | sha256sum | awk '{print $1}')" "$entry" >> "$output"
  done < "$entries"
}
hash_classes "$baseline_jar" "$comparison_parent/baseline-classes.txt" "$comparison_parent/baseline-class-sha.txt"
hash_classes "$current_jar" "$comparison_parent/current-classes.txt" "$comparison_parent/current-class-sha.txt"
cmp "$comparison_parent/baseline-class-sha.txt" "$comparison_parent/current-class-sha.txt" \
  || die "production class bytes differ"

unzip -p "$baseline_jar" plugin.yml > "$comparison_parent/baseline-plugin.yml"
unzip -p "$current_jar" plugin.yml > "$comparison_parent/current-plugin.yml"
grep -Fxq "version: $BASELINE_VERSION" "$comparison_parent/baseline-plugin.yml" || die "baseline plugin version mismatch"
grep -Fxq "version: $CURRENT_VERSION" "$comparison_parent/current-plugin.yml" || die "current plugin version mismatch"
sed -E 's/^version: .*/version: __VERSION__/' "$comparison_parent/baseline-plugin.yml" > "$comparison_parent/baseline-plugin-normalized.yml"
sed -E 's/^version: .*/version: __VERSION__/' "$comparison_parent/current-plugin.yml" > "$comparison_parent/current-plugin-normalized.yml"
cmp "$comparison_parent/baseline-plugin-normalized.yml" "$comparison_parent/current-plugin-normalized.yml" \
  || die "plugin.yml differs by more than version"

[ "$(jar tf "$current_jar" | grep -Fxc 'META-INF/LICENSE')" -eq 1 ] || die "current JAR LICENSE entry is not unique"
cmp LICENSE <(unzip -p "$current_jar" META-INF/LICENSE) || die "current JAR LICENSE differs from root LICENSE"
class_count="$(wc -l < "$comparison_parent/baseline-classes.txt")"
mkdir -p "$CHECK_DIRECTORY"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'baseline SHA: %s\n' "$BASELINE_SHA"
  printf 'baseline version: %s\n' "$BASELINE_VERSION"
  printf 'current version: %s\n' "$CURRENT_VERSION"
  printf 'class entry count: %s\n' "$class_count"
  printf 'class entry names identical: PASS\n'
  printf 'per-class SHA-256 identical: PASS\n'
  printf 'plugin.yml version-only: PASS\n'
  printf 'current META-INF/LICENSE: PASS\n'
  printf 'production functional payload: IDENTICAL\n'
  printf 'PASS: stable patch payload comparison completed.\n'
} > "$CHECK_DIRECTORY/stable-patch-payload-comparison.txt"

printf 'Stable patch payload is identical (%s classes).\n' "$class_count"
