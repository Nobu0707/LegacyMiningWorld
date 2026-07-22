#!/usr/bin/env bash
set -euo pipefail

readonly VERSION="1.0.1"
readonly BASELINE_SHA="2c487560b0d862df0af0c452c3686a7ca72fade3"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"
[ "$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)" = "$VERSION" ] || die "unexpected version"

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-license-checks.XXXXXX")"
trap 'rm -rf "$temp_dir"' EXIT

git diff --check
git diff --cached --check
[ -z "$(git diff "$BASELINE_SHA" -- src/main/java)" ] || die "production Java differs from baseline"
[ -z "$(git diff -- src/main/java)" ] || die "working production Java differs"

./gradlew --no-daemon clean build -x test > "$temp_dir/gradle-build.txt" 2>&1 \
  || { tail -n 200 "$temp_dir/gradle-build.txt" >&2; die "production build failed"; }
./gradlew --no-daemon multiverseVerifierJar -x test >> "$temp_dir/gradle-build.txt" 2>&1 \
  || { tail -n 200 "$temp_dir/gradle-build.txt" >&2; die "verifier build failed"; }

mkdir -p "$CHECK_DIRECTORY"
cp "$temp_dir/gradle-build.txt" "$CHECK_DIRECTORY/gradle-build.txt"

production_jar="build/libs/LegacyMiningWorld-${VERSION}.jar"
verifier_jar="build/libs/LegacyMiningWorld-MultiverseVerifier-${VERSION}.jar"
[ -r "$production_jar" ] || die "production JAR missing"
[ -r "$verifier_jar" ] || die "verifier JAR missing"

{
  git diff --check
  git diff --cached --check
  printf 'baseline: %s\n' "$BASELINE_SHA"
  printf 'production Java changes: 0 PASS\n'
  cmp src/main/resources/plugin.yml <(git show "$BASELINE_SHA:src/main/resources/plugin.yml")
  printf 'production plugin.yml template: unchanged PASS\n'
  for stable_file in src/test/resources/geology-smoke-anchors.tsv src/test/resources/ore-smoke-anchors.tsv src/test/resources/large-scale-grid.properties; do
    cmp "$stable_file" <(git show "$BASELINE_SHA:$stable_file")
    printf '%s: unchanged PASS\n' "$stable_file"
  done
  baseline_paper="$(git show "$BASELINE_SHA:build.gradle.kts" | grep -F 'compileOnly("io.papermc.paper:paper-api:' | head -n 1)"
  current_paper="$(grep -F 'compileOnly("io.papermc.paper:paper-api:' build.gradle.kts | head -n 1)"
  [ "$baseline_paper" = "$current_paper" ]
  printf 'Paper API dependency: unchanged PASS\n'
  printf 'PASS: git diff and immutable-source checks completed.\n'
} > "$CHECK_DIRECTORY/git-diff-check.txt"

java_count="$(find src/main/java -type f | wc -l)"
java_hash="$(git ls-files src/main/java | LC_ALL=C sort | xargs sha256sum | sha256sum | awk '{print $1}')"
baseline_java_hash="$(git ls-tree -r --name-only "$BASELINE_SHA" src/main/java | LC_ALL=C sort | while IFS= read -r path; do git show "$BASELINE_SHA:$path" | sha256sum | awk -v p="$path" '{print $1 "  " p}'; done | sha256sum | awk '{print $1}')"
{
  printf 'baseline SHA: %s\n' "$BASELINE_SHA"
  printf 'production Java file count: %s\n' "$java_count"
  printf 'current production Java aggregate SHA-256: %s\n' "$java_hash"
  printf 'baseline production Java aggregate SHA-256: %s\n' "$baseline_java_hash"
  printf 'production Java diff: 0 PASS\n'
  printf 'plugin.yml template diff: 0 PASS\n'
  printf 'anchors and large-scale grid diff: 0 PASS\n'
  printf 'PASS: license source equivalence completed.\n'
} > "$CHECK_DIRECTORY/license-source-equivalence.txt"

plugin_version="$(unzip -p "$production_jar" plugin.yml | sed -n 's/^version:[[:space:]]*//p' | tail -n 1)"
[ "$plugin_version" = "$VERSION" ] || die "production plugin.yml version mismatch"
verifier_version="$(unzip -p "$verifier_jar" plugin.yml | sed -n 's/^version:[[:space:]]*//p' | tail -n 1)"
[ "$verifier_version" = "$VERSION" ] || die "verifier plugin.yml version mismatch"
{
  printf 'version: %s\nproduction plugin.yml: %s\nverifier plugin.yml: %s\n' "$VERSION" "$plugin_version" "$verifier_version"
  printf 'plugin.yml version: PASS\n'
} > "$CHECK_DIRECTORY/jar-plugin-yml.txt"
cp "$CHECK_DIRECTORY/jar-plugin-yml.txt" "$CHECK_DIRECTORY/verify-built-jar-version.txt"

jar tf "$production_jar" > "$temp_dir/production-entries.txt"
if grep -Eiq 'geology-smoke-anchors|ore-smoke-anchors|large-scale-grid|integration/|(^|/)org/bukkit/|(^|/)io/papermc/|(^|/)org/mvplugins/|(^|/)org/junit/' "$temp_dir/production-entries.txt"; then
  die "production JAR contains forbidden test or dependency content"
fi
[ "$(grep -Fxc 'META-INF/LICENSE' "$temp_dir/production-entries.txt")" -eq 1 ] || die "production LICENSE entry invalid"
{
  cat "$temp_dir/production-entries.txt"
  printf '\ntest classes/resources absent: PASS\nanchor TSV absent: PASS\n'
  printf 'Paper/Multiverse/external dependency classes absent: PASS\n'
  printf 'META-INF/LICENSE unique: PASS\n'
  printf 'PASS: production JAR content audit completed.\n'
} > "$CHECK_DIRECTORY/jar-contents.txt"

jar tf "$verifier_jar" > "$temp_dir/verifier-entries.txt"
for required in plugin.yml geology-smoke-anchors.tsv ore-smoke-anchors.tsv large-scale-grid.properties META-INF/LICENSE; do
  grep -Fxq "$required" "$temp_dir/verifier-entries.txt" || die "verifier JAR missing $required"
done
[ "$(grep -Fxc 'META-INF/LICENSE' "$temp_dir/verifier-entries.txt")" -eq 1 ] || die "verifier LICENSE entry invalid"
if grep -Eiq '(^|/)org/bukkit/|(^|/)io/papermc/|(^|/)org/mvplugins/|(^|/)org/junit/' "$temp_dir/verifier-entries.txt"; then
  die "verifier JAR contains external dependency classes"
fi
{
  cat "$temp_dir/verifier-entries.txt"
  printf '\nrequired verifier resources: PASS\nexternal dependency classes absent: PASS\n'
  printf 'META-INF/LICENSE unique: PASS\n'
  printf 'PASS: verifier JAR content audit completed.\n'
} > "$CHECK_DIRECTORY/verifier-jar-contents.txt"

cmp LICENSE <(unzip -p "$production_jar" META-INF/LICENSE) || die "production LICENSE mismatch"
cmp LICENSE <(unzip -p "$verifier_jar" META-INF/LICENSE) || die "verifier LICENSE mismatch"
{
  printf 'root LICENSE SHA-256: %s\n' "$(sha256sum LICENSE | awk '{print $1}')"
  printf 'production META-INF/LICENSE SHA-256: %s\n' "$(unzip -p "$production_jar" META-INF/LICENSE | sha256sum | awk '{print $1}')"
  printf 'verifier META-INF/LICENSE SHA-256: %s\n' "$(unzip -p "$verifier_jar" META-INF/LICENSE | sha256sum | awk '{print $1}')"
  printf 'root/JAR license byte equality: PASS\n'
} > "$CHECK_DIRECTORY/license-source-equivalence-jars.txt"

./scripts/compare-stable-patch-payload.sh
./scripts/make-release-package.sh
first_package="build/release/LegacyMiningWorld-${VERSION}-release.tar.gz"
cp "$first_package" "$temp_dir/first-package.tar.gz"
first_sha="$(sha256sum "$first_package" | awk '{print $1}')"
./scripts/make-release-package.sh
second_sha="$(sha256sum "$first_package" | awk '{print $1}')"
cmp "$temp_dir/first-package.tar.gz" "$first_package" || die "release package is not reproducible"
{
  printf 'first package SHA-256: %s\nsecond package SHA-256: %s\n' "$first_sha" "$second_sha"
  printf 'reproducible package equality: PASS\n'
  printf 'PASS: license package reproducibility completed.\n'
} > "$CHECK_DIRECTORY/license-package-reproducibility.txt"

./scripts/run-license-audit.sh
./scripts/run-public-license-status-scan.sh

{
  printf 'version: %s\n' "$VERSION"
  printf 'production JAR: %s SHA-256 %s\n' "$(basename "$production_jar")" "$(sha256sum "$production_jar" | awk '{print $1}')"
  printf 'verifier JAR: %s SHA-256 %s\n' "$(basename "$verifier_jar")" "$(sha256sum "$verifier_jar" | awk '{print $1}')"
  printf 'release package: %s SHA-256 %s\n' "$(basename "$first_package")" "$(sha256sum "$first_package" | awk '{print $1}')"
  printf 'tests executed: NO\nPaper smoke: NO\nMultiverse smoke: NO\nlarge-scale validation: NO\n'
  printf 'PASS: lightweight release artifacts recorded.\n'
} > "$CHECK_DIRECTORY/release-artifacts-summary.txt"

required_logs=(
  git-diff-check.txt license-source-equivalence.txt stable-patch-payload-comparison.txt
  gradle-build.txt jar-plugin-yml.txt jar-contents.txt verifier-jar-contents.txt
  release-artifacts.txt verify-built-jar-version.txt license-audit.txt
  license-package-reproducibility.txt public-license-status-scan.txt
)
for log in "${required_logs[@]}"; do
  [ -s "$CHECK_DIRECTORY/$log" ] || die "required lightweight log missing: $log"
done

./scripts/write-final-public-ready-release.sh
printf 'PASS: all lightweight 1.0.1 license release checks completed.\n'
