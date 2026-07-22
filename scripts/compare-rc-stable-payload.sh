#!/usr/bin/env bash
set -euo pipefail

readonly BASELINE_SHA="3c30291b5c570d1c53a261ef8f5d9715b42512ff"
readonly EXPECTED_RC_VERSION="1.0.0-rc.1"
readonly EXPECTED_STABLE_VERSION="1.0.0"
readonly EXPECTED_RC_JAR_SHA256="abead261a33ef1415c27f9a9832a51d7383c33bb712286a0e92942fce65b6161"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"

stable_version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$stable_version" = "$EXPECTED_STABLE_VERSION" ] \
  || die "expected stable version $EXPECTED_STABLE_VERSION, found $stable_version"
git cat-file -e "$BASELINE_SHA^{commit}" \
  || die "RC baseline commit is unavailable: $BASELINE_SHA"
baseline_version="$(git show "$BASELINE_SHA:gradle.properties" \
  | sed -n 's/^legacyminingworld_version=//p' | tail -n 1)"
[ "$baseline_version" = "$EXPECTED_RC_VERSION" ] \
  || die "baseline version mismatch: $baseline_version"

comparison_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-payload.XXXXXX")"
baseline_root="$comparison_parent/rc-worktree"
worktree_added=0
cleanup() {
  if [ "$worktree_added" -eq 1 ]; then
    git worktree remove --force "$baseline_root" >/dev/null 2>&1 || true
  fi
  rm -rf "$comparison_parent"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

git worktree add --detach "$baseline_root" "$BASELINE_SHA" \
  > "$comparison_parent/worktree.txt" 2>&1
worktree_added=1

cat > "$comparison_parent/comparison-build.init.gradle" <<'GRADLE'
gradle.beforeProject { project ->
    def comparisonBuildDir = System.getenv('LEGACYMININGWORLD_COMPARISON_BUILD_DIR')
    if (comparisonBuildDir == null || comparisonBuildDir.isBlank()) {
        throw new GradleException('LEGACYMININGWORLD_COMPARISON_BUILD_DIR is required')
    }
    project.layout.buildDirectory.set(new File(comparisonBuildDir))
}
GRADLE

(
  cd "$baseline_root"
  LEGACYMININGWORLD_COMPARISON_BUILD_DIR="$comparison_parent/rc-build" \
    ./gradlew --no-daemon \
      --init-script "$comparison_parent/comparison-build.init.gradle" clean jar
) > "$comparison_parent/rc-build.txt" 2>&1 \
  || { tail -n 200 "$comparison_parent/rc-build.txt" >&2; die "RC clean build failed"; }

rc_jar="$comparison_parent/rc-build/libs/LegacyMiningWorld-${EXPECTED_RC_VERSION}.jar"
[ -r "$rc_jar" ] || die "RC build did not produce $rc_jar"
cp "$rc_jar" "$comparison_parent/rc.jar"

LEGACYMININGWORLD_COMPARISON_BUILD_DIR="$comparison_parent/stable-build" \
  ./gradlew --no-daemon \
    --init-script "$comparison_parent/comparison-build.init.gradle" clean jar \
    > "$comparison_parent/stable-build.txt" 2>&1 \
  || { tail -n 200 "$comparison_parent/stable-build.txt" >&2; die "stable clean build failed"; }
stable_jar="$comparison_parent/stable-build/libs/LegacyMiningWorld-${EXPECTED_STABLE_VERSION}.jar"
[ -r "$stable_jar" ] || die "stable build did not produce $stable_jar"
cp "$stable_jar" "$comparison_parent/stable.jar"

jar tf "$comparison_parent/rc.jar" \
  | LC_ALL=C sort > "$comparison_parent/rc-all-entries.txt"
jar tf "$comparison_parent/stable.jar" \
  | LC_ALL=C sort > "$comparison_parent/stable-all-entries.txt"
grep -E '^net/nobu0707/legacyminingworld/.*\.class$' \
  "$comparison_parent/rc-all-entries.txt" \
  > "$comparison_parent/rc-class-entries.txt"
grep -E '^net/nobu0707/legacyminingworld/.*\.class$' \
  "$comparison_parent/stable-all-entries.txt" \
  > "$comparison_parent/stable-class-entries.txt"
[ -s "$comparison_parent/rc-class-entries.txt" ] \
  || die "RC production class set is empty"
if grep -Fq '/integration/' "$comparison_parent/rc-class-entries.txt" \
    || grep -Fq '/integration/' "$comparison_parent/stable-class-entries.txt"; then
  die "production JAR contains integration verifier classes"
fi
cmp "$comparison_parent/rc-class-entries.txt" \
  "$comparison_parent/stable-class-entries.txt" \
  || die "RC/stable production class entry names differ"

hash_classes() {
  local jar_path="$1"
  local entry_file="$2"
  local output_file="$3"
  local entry_sha
  : > "$output_file"
  while IFS= read -r entry; do
    entry_sha="$(unzip -p "$jar_path" "$entry" | sha256sum | awk '{print $1}')"
    printf '%s  %s\n' "$entry_sha" "$entry" >> "$output_file"
  done < "$entry_file"
}

hash_classes "$comparison_parent/rc.jar" \
  "$comparison_parent/rc-class-entries.txt" "$comparison_parent/rc-class-sha256.txt"
hash_classes "$comparison_parent/stable.jar" \
  "$comparison_parent/stable-class-entries.txt" "$comparison_parent/stable-class-sha256.txt"
cmp "$comparison_parent/rc-class-sha256.txt" \
  "$comparison_parent/stable-class-sha256.txt" \
  || die "RC/stable production class bytes differ"

unzip -p "$comparison_parent/rc.jar" META-INF/MANIFEST.MF \
  > "$comparison_parent/rc-manifest.txt"
unzip -p "$comparison_parent/stable.jar" META-INF/MANIFEST.MF \
  > "$comparison_parent/stable-manifest.txt"
cmp "$comparison_parent/rc-manifest.txt" "$comparison_parent/stable-manifest.txt" \
  || die "RC/stable manifests differ"

unzip -p "$comparison_parent/rc.jar" plugin.yml > "$comparison_parent/rc-plugin.yml"
unzip -p "$comparison_parent/stable.jar" plugin.yml > "$comparison_parent/stable-plugin.yml"
grep -Fxq "version: $EXPECTED_RC_VERSION" "$comparison_parent/rc-plugin.yml" \
  || die "RC plugin.yml version is not $EXPECTED_RC_VERSION"
grep -Fxq "version: $EXPECTED_STABLE_VERSION" "$comparison_parent/stable-plugin.yml" \
  || die "stable plugin.yml version is not $EXPECTED_STABLE_VERSION"
sed -E 's/^version: .*/version: __VERSION__/' "$comparison_parent/rc-plugin.yml" \
  > "$comparison_parent/rc-plugin-normalized.yml"
sed -E 's/^version: .*/version: __VERSION__/' "$comparison_parent/stable-plugin.yml" \
  > "$comparison_parent/stable-plugin-normalized.yml"
cmp "$comparison_parent/rc-plugin-normalized.yml" \
  "$comparison_parent/stable-plugin-normalized.yml" \
  || die "plugin.yml differs by more than version"

rc_sha="$(sha256sum "$comparison_parent/rc.jar" | awk '{print $1}')"
stable_sha="$(sha256sum "$comparison_parent/stable.jar" | awk '{print $1}')"
[ "$rc_sha" = "$EXPECTED_RC_JAR_SHA256" ] \
  || die "rebuilt RC JAR SHA-256 differs from the accepted RC artifact: $rc_sha"
class_count="$(wc -l < "$comparison_parent/rc-class-entries.txt")"
mkdir -p "$CHECK_DIRECTORY"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'baseline SHA: %s\n' "$BASELINE_SHA"
  printf 'baseline version: %s\n' "$EXPECTED_RC_VERSION"
  printf 'stable version: %s\n' "$EXPECTED_STABLE_VERSION"
  printf 'RC JAR SHA-256: %s\n' "$rc_sha"
  printf 'stable JAR SHA-256: %s\n' "$stable_sha"
  printf 'class entry count: %s\n' "$class_count"
  printf 'class entry names: PASS\n'
  printf 'per-class SHA-256: PASS\n'
  printf 'integration verifier classes in production JAR: 0 PASS\n'
  printf 'manifest version-independent difference: 0 PASS\n'
  printf 'plugin.yml version-only: PASS\n'
  printf 'production functional payload: IDENTICAL\n'
  printf 'PASS: RC and stable production payload comparison completed.\n'
} > "$CHECK_DIRECTORY/rc-stable-payload-comparison.txt"

printf 'RC/stable production payload is identical (%s classes).\n' "$class_count"
