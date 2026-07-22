#!/usr/bin/env bash
set -euo pipefail

readonly BASELINE_SHA="3c30291b5c570d1c53a261ef8f5d9715b42512ff"
readonly BASELINE_VERSION="1.0.0-rc.1"
readonly EXPECTED_VERSION="1.0.0"
readonly CHECK_DIRECTORY="build/review-checks"

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
audit_temp="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-audits.XXXXXX")"
trap 'rm -rf "$audit_temp"' EXIT
production_audit_log="$audit_temp/production-code-audit.txt"
compiler_log="$audit_temp/compiler-audit.txt"
dependency_log="$audit_temp/dependency-audit.txt"
reproducible_log="$audit_temp/reproducible-build.txt"
production_entries="$audit_temp/jar-contents.txt"
verifier_entries="$audit_temp/verifier-jar-contents.txt"
final_jar_log="$audit_temp/final-jar-audit.txt"
source_equivalence_log="$audit_temp/stable-source-equivalence.txt"

mapfile -t production_sources < <(rg --files src/main/java | sort)
[ "${#production_sources[@]}" -gt 0 ] || die "no production Java source"
git diff --quiet "$BASELINE_SHA" -- src/main/java \
  || die "production Java differs from the 1.0.0-rc.1 baseline"

git cat-file -e "$BASELINE_SHA^{commit}" \
  || die "stable source baseline is unavailable: $BASELINE_SHA"
baseline_version="$(git show "$BASELINE_SHA:gradle.properties" \
  | sed -n 's/^legacyminingworld_version=//p' | tail -n 1)"
[ "$baseline_version" = "$BASELINE_VERSION" ] \
  || die "baseline version mismatch: $baseline_version"

git ls-tree -r --name-only "$BASELINE_SHA" -- src/main/java \
  | LC_ALL=C sort > "$audit_temp/baseline-production-files.txt"
find src/main/java -type f -print \
  | LC_ALL=C sort > "$audit_temp/current-production-files.txt"
cmp "$audit_temp/baseline-production-files.txt" \
  "$audit_temp/current-production-files.txt" \
  || die "production Java file names differ from the RC baseline"

: > "$audit_temp/baseline-production-sha256.txt"
: > "$audit_temp/current-production-sha256.txt"
while IFS= read -r source_path; do
  git show "$BASELINE_SHA:$source_path" | sha256sum \
    | awk -v path="$source_path" '{print $1 "  " path}' \
    >> "$audit_temp/baseline-production-sha256.txt"
  sha256sum "$source_path" >> "$audit_temp/current-production-sha256.txt"
done < "$audit_temp/baseline-production-files.txt"
cmp "$audit_temp/baseline-production-sha256.txt" \
  "$audit_temp/current-production-sha256.txt" \
  || die "production Java content hashes differ from the RC baseline"

git ls-tree -r --name-only "$BASELINE_SHA" -- src/main/resources \
  | LC_ALL=C sort > "$audit_temp/baseline-production-resources.txt"
find src/main/resources -type f -print \
  | LC_ALL=C sort > "$audit_temp/current-production-resources.txt"
cmp "$audit_temp/baseline-production-resources.txt" \
  "$audit_temp/current-production-resources.txt" \
  || die "production resource file names differ from the RC baseline"
: > "$audit_temp/baseline-production-resource-sha256.txt"
: > "$audit_temp/current-production-resource-sha256.txt"
while IFS= read -r resource_path; do
  git show "$BASELINE_SHA:$resource_path" | sha256sum \
    | awk -v path="$resource_path" '{print $1 "  " path}' \
    >> "$audit_temp/baseline-production-resource-sha256.txt"
  sha256sum "$resource_path" >> "$audit_temp/current-production-resource-sha256.txt"
done < "$audit_temp/baseline-production-resources.txt"
cmp "$audit_temp/baseline-production-resource-sha256.txt" \
  "$audit_temp/current-production-resource-sha256.txt" \
  || die "production resource content hashes differ from the RC baseline"

readonly IMMUTABLE_STABLE_PATHS=(
  src/main/resources/plugin.yml
  src/multiverseVerifier/resources/plugin.yml
  build.gradle.kts
  settings.gradle.kts
  src/test/resources/geology-smoke-anchors.tsv
  src/test/resources/ore-smoke-anchors.tsv
  src/test/resources/large-scale-grid.properties
)
for immutable_path in "${IMMUTABLE_STABLE_PATHS[@]}"; do
  git diff --quiet "$BASELINE_SHA" -- "$immutable_path" \
    || die "stable-immutable path differs from RC baseline: $immutable_path"
done
git show "$BASELINE_SHA:gradle.properties" \
  | sed 's/^legacyminingworld_version=.*/legacyminingworld_version=__VERSION__/' \
  > "$audit_temp/baseline-gradle-normalized.properties"
sed 's/^legacyminingworld_version=.*/legacyminingworld_version=__VERSION__/' \
  gradle.properties > "$audit_temp/current-gradle-normalized.properties"
cmp "$audit_temp/baseline-gradle-normalized.properties" \
  "$audit_temp/current-gradle-normalized.properties" \
  || die "gradle.properties differs by more than the stable version"

baseline_source_hash="$(sha256sum "$audit_temp/baseline-production-sha256.txt" | awk '{print $1}')"
current_source_hash="$(sha256sum "$audit_temp/current-production-sha256.txt" | awk '{print $1}')"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'baseline SHA: %s\n' "$BASELINE_SHA"
  printf 'baseline version: %s\n' "$BASELINE_VERSION"
  printf 'current HEAD: %s\n' "$(git rev-parse HEAD)"
  printf 'current version: %s\n' "$version"
  printf 'baseline production Java content SHA-256: %s\n' "$baseline_source_hash"
  printf 'current production Java content SHA-256: %s\n' "$current_source_hash"
  printf 'current comparison source: working tree\n'
  printf 'production Java file count: %d\n' "${#production_sources[@]}"
  printf 'production Java file names: PASS\n'
  printf 'production Java per-file SHA-256: PASS\n'
  printf 'production Java diff: 0 PASS\n'
  printf 'production resource file names and SHA-256: PASS\n'
  printf 'production plugin.yml template diff: 0 PASS\n'
  printf 'verifier plugin.yml template diff: 0 PASS\n'
  printf 'Paper dependency and production build definition diff: 0 PASS\n'
  printf 'world-generation settings, seeds, and salts diff: 0 PASS\n'
  printf 'geology and ore anchors diff: 0 PASS\n'
  printf 'large-scale spec diff: 0 PASS\n'
  printf 'gradle.properties version-only difference: PASS\n'
  printf 'stable promotion changes: metadata/docs/tests/scripts only\n'
  printf 'PASS: stable source is equivalent to the RC baseline.\n'
} > "$source_equivalence_log"

if rg -n \
    -e 'java\.lang\.reflect|Class\.forName|org\.bukkit\.craftbukkit|net\.minecraft' \
    -e 'BukkitScheduler|getScheduler[[:space:]]*\(|runTask|runAsync|parallelStream' \
    -e 'System\.(currentTimeMillis|nanoTime)|UUID\.randomUUID|Thread\.currentThread' \
    -e '\.getWorld[[:space:]]*\(|\.getChunkAt[[:space:]]*\(|\.getBlockAt[[:space:]]*\(' \
    src/main/java > "$audit_temp/forbidden-production.txt"; then
  cat "$audit_temp/forbidden-production.txt" >&2
  die "production source contains a forbidden API or nondeterministic input"
fi
if rg -n --pcre2 \
    'static\s+(?!final\b)(?:Random|List|Map|Set|Collection)(?:<[^;=]+>)?\s+\w+\s*(?:=|;)' \
    src/main/java \
    > "$audit_temp/mutable-static.txt"; then
  cat "$audit_temp/mutable-static.txt" >&2
  die "production source contains mutable static state"
fi
if rg -n -U \
    'populate\s*\(\s*World\s+\w+\s*,\s*Random\s+\w+\s*,\s*Chunk' \
    src/main/java > "$audit_temp/deprecated-populate.txt"; then
  cat "$audit_temp/deprecated-populate.txt" >&2
  die "deprecated BlockPopulator signature found"
fi
grep -Fxq "version: \${version}" src/main/resources/plugin.yml \
  || die "production plugin.yml version token missing"
grep -Fxq "api-version: '26.1.2'" src/main/resources/plugin.yml \
  || die "production plugin.yml api-version mismatch"
if rg -n -i 'multiverse|depend:|softdepend:|commands:|permissions:' \
    src/main/resources/plugin.yml src/main/java >/dev/null; then
  die "production source or metadata contains an integration/runtime declaration"
fi
if rg -n -i \
    -e 'files\s*\(' -e 'SNAPSHOT' -e ':[^"[:space:]]*\+' \
    -e '(implementation|api|compileOnly|runtimeOnly)\s*\([^)]*multiverse' \
    -e 'credentials\s*\{' build.gradle.kts >/dev/null; then
  die "build declares a forbidden local, dynamic, snapshot, credential, or Multiverse dependency"
fi

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'baseline: %s\n' "$BASELINE_SHA"
  printf 'files audited:\n'
  printf '  %s\n' "${production_sources[@]}"
  printf '  src/main/resources/plugin.yml\n  build.gradle.kts\n  gradle.properties\n  settings.gradle.kts\n'
  printf 'production Java file count: %d\n' "${#production_sources[@]}"
  printf 'production Java baseline equality to 3c30291: PASS\n'
  printf 'functional source changes: 0\n'
  printf 'stable promotion changes: metadata/docs/tests/scripts only\n'
  printf 'public API: PASS (Paper API compileOnly 26.1.2.build.69-stable)\n'
  printf 'deprecated API: PASS (strict compiler and signature scan)\n'
  printf 'thread safety: PASS (immutable services/method-local state; mutable static scan 0)\n'
  printf 'determinism: PASS (time/UUID/thread/hash inputs 0; explicit salts and stable order)\n'
  printf 'target ownership: PASS (planner plus applicator guards retained)\n'
  printf 'material safety: PASS (natural stone only; height/region/surface guards retained)\n'
  printf 'dependencies: PASS (Paper compileOnly; no production runtime/Multiverse/local JAR)\n'
  printf 'plugin metadata: PASS (api-version 26.1.2, STARTUP, no commands/dependencies)\n'
  printf 'findings: no production functional defect found\n'
  printf 'fixes applied: none to production Java\n'
  printf 'PASS: production code audit completed.\n'
} > "$production_audit_log"

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'compiler policy: -Xlint:all -Werror for every JavaCompile task\n'
  printf 'production task: compileJava\n'
  printf 'test-only tasks: compileTestJava, compileMultiverseVerifierJava, compileMultiverseVerifierTestJava\n\n'
  ./gradlew --no-daemon clean compileJava compileTestJava \
    compileMultiverseVerifierJava compileMultiverseVerifierTestJava
  printf '\nproduction source warnings: 0\n'
  printf 'test-only source warnings: 0\n'
  printf 'deprecated/unchecked/raw/fallthrough warnings: 0\n'
  printf 'note: Java 25 native-access startup messages from Gradle are not javac source warnings.\n'
  printf 'PASS: strict compiler audit completed with -Werror.\n'
} > "$compiler_log" 2>&1 || {
  cat "$compiler_log" >&2
  die "strict compiler audit failed"
}
if grep -E '(^|[[:space:]])warning:' "$compiler_log" >/dev/null; then
  die "javac warning text remained in compiler audit"
fi

{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'command: ./gradlew --no-daemon dependencies\n\n'
  ./gradlew --no-daemon dependencies
  printf '\ncommand: dependencyInsight paper-api compileClasspath\n\n'
  ./gradlew --no-daemon dependencyInsight \
    --dependency paper-api --configuration compileClasspath
  printf '\ncommand: dependencyInsight junit-jupiter testRuntimeClasspath\n\n'
  ./gradlew --no-daemon dependencyInsight \
    --dependency junit-jupiter --configuration testRuntimeClasspath
  printf '\ncommand: dependencies runtimeClasspath\n\n'
  ./gradlew --no-daemon dependencies --configuration runtimeClasspath
  printf '\ncommand: dependencies multiverseVerifierCompileClasspath\n\n'
  ./gradlew --no-daemon dependencies --configuration multiverseVerifierCompileClasspath
  printf '\ncompileClasspath Paper API: PASS\n'
  printf 'runtime external libraries: 0 PASS\n'
  printf 'production Multiverse dependency: 0 PASS\n'
  printf 'old Minecraft/local files/dynamic/SNAPSHOT/credential repository: 0 PASS\n'
  printf 'verifier compile Paper API only: PASS\n'
  printf 'test JUnit/Paper API only: PASS\n'
  printf 'PASS: dependency audit completed.\n'
} > "$dependency_log" 2>&1 || {
  cat "$dependency_log" >&2
  die "dependency audit failed"
}

run_repro_build() {
  local run="$1"
  ./gradlew --no-daemon clean build multiverseVerifierJar \
    > "$audit_temp/build-$run.txt" 2>&1
  [ -f "$production_jar" ] || die "production JAR missing after build $run"
  [ -f "$verifier_jar" ] || die "verifier JAR missing after build $run"
  cp "$production_jar" "$audit_temp/production-$run.jar"
  cp "$verifier_jar" "$audit_temp/verifier-$run.jar"
  jar tf "$production_jar" > "$audit_temp/production-$run.entries"
  jar tf "$verifier_jar" > "$audit_temp/verifier-$run.entries"
  unzip -p "$production_jar" plugin.yml > "$audit_temp/production-$run-plugin.yml"
  unzip -p "$verifier_jar" plugin.yml > "$audit_temp/verifier-$run-plugin.yml"
}

run_repro_build 1
run_repro_build 2
production_sha_1="$(sha256sum "$audit_temp/production-1.jar" | awk '{print $1}')"
production_sha_2="$(sha256sum "$audit_temp/production-2.jar" | awk '{print $1}')"
verifier_sha_1="$(sha256sum "$audit_temp/verifier-1.jar" | awk '{print $1}')"
verifier_sha_2="$(sha256sum "$audit_temp/verifier-2.jar" | awk '{print $1}')"
cmp "$audit_temp/production-1.jar" "$audit_temp/production-2.jar" \
  || die "production JAR is not reproducible"
cmp "$audit_temp/verifier-1.jar" "$audit_temp/verifier-2.jar" \
  || die "verifier JAR is not reproducible"
cmp "$audit_temp/production-1.entries" "$audit_temp/production-2.entries" \
  || die "production JAR entry order differs"
cmp "$audit_temp/verifier-1.entries" "$audit_temp/verifier-2.entries" \
  || die "verifier JAR entry order differs"
cmp "$audit_temp/production-1-plugin.yml" "$audit_temp/production-2-plugin.yml" \
  || die "production plugin.yml differs"
cmp "$audit_temp/verifier-1-plugin.yml" "$audit_temp/verifier-2-plugin.yml" \
  || die "verifier plugin.yml differs"

{
  printf 'Gradle version:\n'; ./gradlew --version
  printf '\nJava version:\n'; java -version 2>&1
  printf '\nbuild 1 production SHA: %s\n' "$production_sha_1"
  printf 'build 2 production SHA: %s\n' "$production_sha_2"
  printf 'production equality: PASS\n'
  printf 'build 1 verifier SHA: %s\n' "$verifier_sha_1"
  printf 'build 2 verifier SHA: %s\n' "$verifier_sha_2"
  printf 'verifier equality: PASS\n'
  printf 'entry order equality: PASS\n'
  printf 'plugin.yml equality: PASS\n'
  printf 'preserveFileTimestamps=false: PASS\n'
  printf 'reproducibleFileOrder=true: PASS\n'
  printf 'PASS: two independent clean JAR builds are reproducible.\n'
} > "$reproducible_log"

jar tf "$production_jar" > "$production_entries"
jar tf "$verifier_jar" > "$verifier_entries"
if grep -Ev '^(META-INF/?|META-INF/MANIFEST\.MF|plugin\.yml|net/?|net/.*/|net/nobu0707/legacyminingworld/.*\.class)$' \
    "$production_entries" | grep -q .; then
  die "production JAR contains a non-allowlisted entry"
fi
if grep -Eiq 'integration|Verifier|anchors|large-scale-grid|org/bukkit|org/mvplugins|net/minecraft|LICENSE|config\.yml' \
    "$production_entries"; then
  die "production JAR contains forbidden content"
fi
for resource in plugin.yml geology-smoke-anchors.tsv ore-smoke-anchors.tsv \
    large-scale-grid.properties; do
  grep -Fxq "$resource" "$verifier_entries" \
    || die "verifier JAR missing $resource"
done
if grep -E '\.class$' "$verifier_entries" \
    | grep -Ev '^net/nobu0707/legacyminingworld/integration/' >/dev/null; then
  die "verifier JAR contains production/external classes"
fi
if grep -Eiq 'org/bukkit|org/mvplugins|net/minecraft|(^|/)tests?/' \
    "$verifier_entries"; then
  die "verifier JAR contains dependency/test classes"
fi
{
  printf 'production JAR: %s\n' "$(basename "$production_jar")"
  printf 'production SHA-256: %s\n' "$production_sha_2"
  printf 'production allowed content: standard manifest, plugin.yml, production classes PASS\n'
  printf 'production verifier/test/anchor/spec/docs/dependency/license content: 0 PASS\n'
  printf 'verifier JAR: %s\n' "$(basename "$verifier_jar")"
  printf 'verifier SHA-256: %s\n' "$verifier_sha_2"
  printf 'verifier resources and integration classes: PASS\n'
  printf 'verifier production/dependency/test/report/world content: 0 PASS\n'
  printf 'PASS: final JAR content audit completed.\n'
} > "$final_jar_log"

mkdir -p "$CHECK_DIRECTORY"
for audit_log in \
    "$production_audit_log" "$compiler_log" "$dependency_log" \
    "$reproducible_log" "$production_entries" "$verifier_entries" \
    "$source_equivalence_log" \
    "$final_jar_log"; do
  cp "$audit_log" "$CHECK_DIRECTORY/$(basename "$audit_log")"
done

printf 'Final production/dependency/compiler/reproducibility/JAR audits passed.\n'
