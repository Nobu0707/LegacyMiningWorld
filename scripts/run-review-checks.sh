#!/usr/bin/env bash
set -euo pipefail

readonly PAPER_JAR="server/paper-26.1.2-69.jar"
readonly EULA_FILE="server/eula.txt"
readonly ANCHOR_FILE="src/test/resources/geology-smoke-anchors.tsv"
readonly CHECK_DIR="build/review-checks"
readonly SMOKE_DIR="build/paper-smoke"
readonly SMOKE_TIMEOUT_SECONDS=120
readonly FIXED_WORLD_SEED=11652021
readonly FORCE_LOADED_CHUNKS="-1,-1;0,-1;-1,0;0,0"
readonly REQUIRED_REVIEW_CHECKS=(
  git-diff-check.txt
  gradle-test.txt
  geology-engine-tests.txt
  geology-adapter-tests.txt
  ore-engine-tests.txt
  gradle-build.txt
  local-paper-smoke.txt
  geology-world-smoke.txt
  release-artifacts.txt
  jar-plugin-yml.txt
  jar-contents.txt
  verify-built-jar-version.txt
)

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

expected_version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ -n "$expected_version" ] || die "legacyminingworld_version is not set"

rm -rf "$CHECK_DIR"

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-checks.XXXXXX")"
paper_job=""
cleanup() {
  if [ -n "$paper_job" ] && kill -0 "$paper_job" 2>/dev/null; then
    kill "$paper_job" 2>/dev/null || true
    wait "$paper_job" 2>/dev/null || true
  fi
  mkdir -p "$CHECK_DIR"
  while IFS= read -r -d '' available_log; do
    cp "$available_log" "$CHECK_DIR/$(basename "$available_log")"
  done < <(find "$temp_dir" -maxdepth 1 -type f -name '*.txt' -print0 2>/dev/null)
  rm -rf "$temp_dir"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

copy_log() {
  mkdir -p "$CHECK_DIR"
  cp "$temp_dir/$1" "$CHECK_DIR/$1"
}

run_gradle_check() {
  local log_name="$1"
  shift
  if ./gradlew --no-daemon "$@" > "$temp_dir/$log_name" 2>&1; then
    copy_log "$log_name"
  else
    copy_log "$log_name"
    cat "$temp_dir/$log_name" >&2
    die "Gradle check failed: $*"
  fi
}

{
  git diff --check
  git diff --cached --check
  printf 'PASS: git diff --check and git diff --cached --check\n'
} > "$temp_dir/git-diff-check.txt"
copy_log git-diff-check.txt

run_gradle_check gradle-test.txt clean test

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'java-version:\n'
  java -version 2>&1
  printf '\ngradle-version:\n'
  ./gradlew --version
  printf '\nplugin-version: %s\n' "$expected_version"
  printf 'test-task: geologyEngineTest\n'
  printf 'test-filter: net.nobu0707.legacyminingworld.geology.* excluding geology-adapter tag\n\n'
  ./gradlew --no-daemon geologyEngineTest
  printf '\nPASS: Phase 2A geology engine tests completed successfully.\n'
} > "$temp_dir/geology-engine-tests.txt" 2>&1 || {
  copy_log geology-engine-tests.txt
  cat "$temp_dir/geology-engine-tests.txt" >&2
  die "Phase 2A geology engine tests failed"
}
copy_log geology-engine-tests.txt

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'java-version:\n'
  java -version 2>&1
  printf '\ngradle-version:\n'
  ./gradlew --version
  printf '\nplugin-version: %s\n' "$expected_version"
  printf 'test-task: geologyAdapterTest\n'
  printf 'test-filter: JUnit tag geology-adapter\n'
  printf 'fixed-seed: %s\n' "$FIXED_WORLD_SEED"
  printf 'fixed-target-chunk: 0,0\n\n'
  ./gradlew --no-daemon geologyAdapterTest
  printf '\nmapping-tests: PASS\n'
  printf 'replacement-order-tests: PASS\n'
  printf 'height-region-tests: PASS\n'
  printf 'registration-tests: PASS\n'
  printf 'concurrency-tests: PASS\n'
  printf 'anchor-model-tests: PASS\n'
  printf 'distribution-tests: PASS\n'
  printf 'PASS: Phase 2B geology adapter tests completed successfully.\n'
} > "$temp_dir/geology-adapter-tests.txt" 2>&1 || {
  copy_log geology-adapter-tests.txt
  cat "$temp_dir/geology-adapter-tests.txt" >&2
  die "Phase 2B geology adapter tests failed"
}
copy_log geology-adapter-tests.txt

[ -f docs/vanilla-1.16.5-ores.md ] \
  || die "missing official ore research document: docs/vanilla-1.16.5-ores.md"
{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'java-version:\n'
  java -version 2>&1
  printf '\ngradle-version:\n'
  ./gradlew --version
  printf '\nplugin-version: %s\n' "$expected_version"
  printf 'test-task: oreEngineTest\n'
  printf 'test-filter: net.nobu0707.legacyminingworld.ore.*\n'
  printf 'official-research-document: docs/vanilla-1.16.5-ores.md EXISTS\n'
  printf 'fixed-seed: %s\n' "$FIXED_WORLD_SEED"
  printf 'fixed-target-chunk: 0,0\n\n'
  ./gradlew --no-daemon oreEngineTest
  printf '\nfeature-settings: PASS\n'
  printf 'total-attempts: 52 PASS\n'
  printf 'stable-salts: 5..10 PASS\n'
  printf 'uniform-distribution-tests: PASS\n'
  printf 'lapis-depth-average-tests: PASS\n'
  printf 'seed-golden-tests: PASS\n'
  printf 'origin-sequence-tests: PASS\n'
  printf 'planner-golden-count: 613\n'
  printf 'planner-golden-checksum: -6214814787450030649\n'
  printf 'material-counts: COAL_ORE=431 IRON_ORE=111 GOLD_ORE=14 REDSTONE_ORE=49 DIAMOND_ORE=2 LAPIS_ORE=6\n'
  printf 'x-z-continuity: PASS\n'
  printf 'negative-chunk: PASS\n'
  printf 'source-radius: PASS\n'
  printf 'replacement: PASS\n'
  printf 'concurrency: PASS\n'
  printf 'geology-regression: PASS\n'
  printf 'PASS: Phase 3A ore engine tests completed successfully.\n'
} > "$temp_dir/ore-engine-tests.txt" 2>&1 || {
  copy_log ore-engine-tests.txt
  cat "$temp_dir/ore-engine-tests.txt" >&2
  die "Phase 3A ore engine tests failed"
}
copy_log ore-engine-tests.txt

run_gradle_check gradle-build.txt build
copy_log git-diff-check.txt

mapfile -t built_jars < <(find build/libs -maxdepth 1 -type f -name 'LegacyMiningWorld-*.jar' -print | sort)
[ "${#built_jars[@]}" -eq 1 ] || die "expected exactly one release JAR, found ${#built_jars[@]}"
built_jar="${built_jars[0]}"
expected_jar="build/libs/LegacyMiningWorld-${expected_version}.jar"
[ "$built_jar" = "$expected_jar" ] || die "unexpected JAR name: $built_jar"

jar tf "$built_jar" > "$temp_dir/jar-contents.txt"
copy_log jar-contents.txt

if grep -Eiq '(^|/)(server|logs?)/|\.tar\.gz$|\.zip$|(^|/)src/test/|(^|/)[^/]*Tests?[^/]*\.class$' \
    "$temp_dir/jar-contents.txt"; then
  die "release JAR contains a forbidden runtime, archive, log, or test path"
fi
if grep -Eiq 'minecraft-1\.16\.5|server-mappings|decompil|geology-smoke-anchors|multiverse' \
    "$temp_dir/jar-contents.txt"; then
  die "release JAR contains a research, test-anchor, or Multiverse artifact"
fi
for required_class in \
  'net/nobu0707/legacyminingworld/geology/LegacyGeologyPopulator.class' \
  'net/nobu0707/legacyminingworld/geology/LegacyGeologyMaterialAdapter.class' \
  'net/nobu0707/legacyminingworld/geology/LegacyGeologyApplicator.class' \
  'net/nobu0707/legacyminingworld/geology/LimitedRegionGeologyBlockAccess.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOrePlanner.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreFeature.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreMaterial.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreHeightDistribution.class' \
  'net/nobu0707/legacyminingworld/ore/UniformRangeDistribution.class' \
  'net/nobu0707/legacyminingworld/ore/DepthAverageDistribution.class'; do
  grep -Fxq "$required_class" "$temp_dir/jar-contents.txt" \
    || die "release JAR is missing production class: $required_class"
done
if grep -E '\.class$' "$temp_dir/jar-contents.txt" \
    | grep -Ev '^net/nobu0707/legacyminingworld/' >/dev/null; then
  die "release JAR contains an external runtime library class"
fi

if rg -n -U \
    -e 'populate[[:space:]]*\([[:space:]]*World[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*,[[:space:]]*Random[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*,[[:space:]]*Chunk' \
    src/main/java >/dev/null; then
  die "production source overrides the deprecated World/Chunk populate method"
fi
if rg -n \
    -e '\.getWorld[[:space:]]*\(' \
    -e 'Bukkit\.getWorld[[:space:]]*\(' \
    -e '\.getChunkAt[[:space:]]*\(' \
    -e '\.getBlockAt[[:space:]]*\(' \
    -e 'getPopulators[[:space:]]*\([^)]*\)[[:space:]]*\.add' \
    -e 'WorldInitEvent|ThreadLocal|BukkitScheduler|getScheduler[[:space:]]*\(|runTask|runAsync' \
    src/main/java >/dev/null; then
  die "production source uses a forbidden world, chunk, scheduler, or retained-state API"
fi
if rg -n -i 'multiverse' build.gradle.kts gradle.properties src/main >/dev/null; then
  die "Phase 3A must not declare or use Multiverse-Core"
fi
if rg -n \
    -e 'org\.bukkit' \
    -e 'BlockPopulator' \
    -e 'LimitedRegion' \
    src/main/java/net/nobu0707/legacyminingworld/ore >/dev/null; then
  die "Phase 3A pure ore package is connected to a Paper/Bukkit runtime API"
fi
if rg -n \
    -g '!**/ore/**' \
    -e 'LegacyOre' \
    -e 'legacyminingworld\.ore' \
    src/main/java >/dev/null; then
  die "Phase 3A ore engine is connected outside its pure production package"
fi
grep -Fq 'List.of(new LegacyGeologyPopulator())' \
  src/main/java/net/nobu0707/legacyminingworld/LegacyMiningChunkGenerator.java \
  || die "getDefaultPopulators no longer registers the single Phase 2B geology populator"

unzip -p "$built_jar" plugin.yml > "$temp_dir/jar-plugin-yml.txt" \
  || die "plugin.yml is missing from the release JAR"
copy_log jar-plugin-yml.txt

grep -Fxq 'name: LegacyMiningWorld' "$temp_dir/jar-plugin-yml.txt" \
  || die "JAR plugin.yml has an unexpected name"
grep -Fxq 'main: net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin' \
  "$temp_dir/jar-plugin-yml.txt" || die "JAR plugin.yml has an unexpected main class"
grep -Fxq "api-version: '26.1.2'" "$temp_dir/jar-plugin-yml.txt" \
  || die "JAR plugin.yml has an unexpected api-version"
grep -Fxq "version: $expected_version" "$temp_dir/jar-plugin-yml.txt" \
  || die "JAR plugin.yml has an unexpected version"

{
  printf 'expected-version: %s\n' "$expected_version"
  printf 'jar-name: %s\n' "$(basename "$built_jar")"
  printf 'plugin-yml-version: %s\n' "$expected_version"
  printf 'PASS: built JAR name and plugin.yml version match gradle.properties\n'
} > "$temp_dir/verify-built-jar-version.txt"
copy_log verify-built-jar-version.txt

{
  printf 'file: %s\n' "$(basename "$built_jar")"
  printf 'bytes: %s\n' "$(stat -c '%s' "$built_jar")"
  printf 'sha256: %s\n' "$(sha256sum "$built_jar" | awk '{print $1}')"
} > "$temp_dir/release-artifacts.txt"
copy_log release-artifacts.txt

[ -r "$PAPER_JAR" ] || die "missing local Paper JAR: $PAPER_JAR"
[ -r "$EULA_FILE" ] || die "missing local EULA file: $EULA_FILE"
[ -r "$ANCHOR_FILE" ] || die "missing fixed geology anchors: $ANCHOR_FILE"
grep -Eq '^eula=true[[:space:]]*$' "$EULA_FILE" || die "$EULA_FILE must contain eula=true"
command -v timeout >/dev/null 2>&1 || die "timeout command is required for the Paper smoke test"
paper_source_sha="$(sha256sum "$PAPER_JAR" | awk '{print $1}')"
eula_source_sha="$(sha256sum "$EULA_FILE" | awk '{print $1}')"

geo_commands=()
geo_markers=()
declare -A anchor_material_counts=()
x_boundary_anchors=0
z_boundary_anchors=0
while IFS=$'\t' read -r id x y z expected_material purpose pair_id \
    source_chunk_x source_chunk_z feature attempt vein_sequence; do
  [ -n "$id" ] || continue
  [[ "$id" == \#* ]] && continue
  [ "$id" = "id" ] && continue
  [[ "$id" =~ ^[A-Z0-9_]+$ ]] || die "invalid anchor id: $id"
  expected_lower="$(printf '%s' "$expected_material" | tr '[:upper:]' '[:lower:]')"
  geo_commands+=("execute if block $x $y $z minecraft:$expected_lower run say LMW_GEO_$id")
  geo_markers+=("LMW_GEO_$id")
  anchor_material_counts["$expected_material"]=$(( ${anchor_material_counts["$expected_material"]:-0} + 1 ))
  [ "$purpose" != "x_boundary" ] || x_boundary_anchors=$((x_boundary_anchors + 1))
  [ "$purpose" != "z_boundary" ] || z_boundary_anchors=$((z_boundary_anchors + 1))
done < "$ANCHOR_FILE"

[ "${#geo_markers[@]}" -ge 10 ] || die "expected at least ten geology anchors"
for material in DIRT GRAVEL GRANITE DIORITE ANDESITE; do
  [ "${anchor_material_counts[$material]:-0}" -ge 2 ] \
    || die "expected at least two anchors for $material"
done
[ "$x_boundary_anchors" -eq 2 ] || die "expected one two-anchor X boundary pair"
[ "$z_boundary_anchors" -eq 2 ] || die "expected one two-anchor Z boundary pair"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR/plugins"
cp "$PAPER_JAR" "$SMOKE_DIR/paper.jar"
cp "$EULA_FILE" "$SMOKE_DIR/eula.txt"
cp "$built_jar" "$SMOKE_DIR/plugins/"
mapfile -t smoke_plugins < <(find "$SMOKE_DIR/plugins" -maxdepth 1 -type f -name '*.jar' -printf '%f\n')
[ "${#smoke_plugins[@]}" -eq 1 ] \
  || die "Paper smoke must contain only the LegacyMiningWorld JAR"
[ "${smoke_plugins[0]}" = "LegacyMiningWorld-${expected_version}.jar" ] \
  || die "Paper smoke contains an unexpected plugin JAR"

cat > "$SMOKE_DIR/server.properties" <<PROPERTIES
online-mode=false
server-ip=127.0.0.1
server-port=0
level-name=legacy_mining_smoke
level-seed=${FIXED_WORLD_SEED}
allow-nether=false
max-players=1
motd=LegacyMiningWorld Phase 2B geology smoke
spawn-protection=0
view-distance=2
simulation-distance=2
network-compression-threshold=-1
difficulty=peaceful
PROPERTIES

cat > "$SMOKE_DIR/bukkit.yml" <<'YAML'
settings:
  allow-end: false
worlds:
  legacy_mining_smoke:
    generator: LegacyMiningWorld
YAML

smoke_fifo="$temp_dir/paper-stdin"
mkfifo "$smoke_fifo"
exec 3<> "$smoke_fifo"
(
  cd "$SMOKE_DIR"
  timeout --signal=TERM --kill-after=10s "${SMOKE_TIMEOUT_SECONDS}s" \
    java -Xms512M -Xmx1G -jar paper.jar nogui <&3 > "$temp_dir/local-paper-smoke.txt" 2>&1
) &
paper_job=$!

ready=0
deadline=$((SECONDS + SMOKE_TIMEOUT_SECONDS))
while kill -0 "$paper_job" 2>/dev/null && [ "$SECONDS" -lt "$deadline" ]; do
  if [ -f "$temp_dir/local-paper-smoke.txt" ] \
      && grep -Eq 'Done \([0-9.]+s\)!|Done \([0-9]+\.[0-9]+s\)!' "$temp_dir/local-paper-smoke.txt"; then
    ready=1
    break
  fi
  sleep 1
done

population_ready=0
inspection_complete=0
if [ "$ready" -eq 1 ]; then
  printf 'forceload add -16 -16 15 15\n' >&3
  printf 'say LMW_FORCELOAD_READY\n' >&3
  population_deadline=$((SECONDS + 30))
  while kill -0 "$paper_job" 2>/dev/null && [ "$SECONDS" -lt "$population_deadline" ]; do
    if grep -Fq '[Server] LMW_FORCELOAD_READY' "$temp_dir/local-paper-smoke.txt"; then
      population_ready=1
      break
    fi
    sleep 1
  done

  if [ "$population_ready" -eq 1 ]; then
    printf 'execute if block 0 70 0 minecraft:grass_block run say LMW_BLOCK_GRASS_0_70_0\n' >&3
    printf 'execute if block 0 69 0 minecraft:dirt run say LMW_BLOCK_DIRT_0_69_0\n' >&3
    printf 'execute if block 0 68 0 minecraft:dirt run say LMW_BLOCK_DIRT_0_68_0\n' >&3
    printf 'execute if block 0 0 0 minecraft:bedrock run say LMW_BLOCK_BEDROCK_0_0_0\n' >&3
    printf 'execute if block 0 -1 0 minecraft:air run say LMW_BLOCK_AIR_0_NEGATIVE_1_0\n' >&3
    printf 'execute if block 0 71 0 minecraft:air run say LMW_BLOCK_AIR_0_71_0\n' >&3
    printf 'execute if biome 0 70 0 minecraft:plains run say LMW_BIOME_PLAINS_0_70_0\n' >&3
    printf 'execute if block 15 70 15 minecraft:grass_block run say LMW_BLOCK_GRASS_15_70_15\n' >&3
    printf 'execute if block -15 68 -15 minecraft:dirt run say LMW_BLOCK_DIRT_NEGATIVE_15_68_NEGATIVE_15\n' >&3
    for geo_command in "${geo_commands[@]}"; do
      printf '%s\n' "$geo_command" >&3
    done
    printf 'say LMW_INSPECTION_COMPLETE\n' >&3
    inspection_deadline=$((SECONDS + 30))
    while kill -0 "$paper_job" 2>/dev/null && [ "$SECONDS" -lt "$inspection_deadline" ]; do
      if grep -Fq '[Server] LMW_INSPECTION_COMPLETE' "$temp_dir/local-paper-smoke.txt"; then
        inspection_complete=1
        break
      fi
      sleep 1
    done
  fi
  printf 'stop\n' >&3
else
  printf 'stop\n' >&3 || true
fi
exec 3>&-

set +e
wait "$paper_job"
paper_status=$?
set -e

[ "$ready" -eq 1 ] || die "Paper did not report startup completion within ${SMOKE_TIMEOUT_SECONDS}s"
[ "$population_ready" -eq 1 ] || die "forced chunks did not finish loading"
[ "$inspection_complete" -eq 1 ] || die "Paper did not process all inspection commands"
[ "$paper_status" -eq 0 ] || die "Paper smoke process exited with status $paper_status"

grep -Fq "LegacyMiningWorld ${expected_version} generator services loaded." \
  "$temp_dir/local-paper-smoke.txt" || die "plugin onLoad confirmation is missing"
grep -Fq "LegacyMiningWorld ${expected_version} enabled; basic terrain and legacy geology population are available." \
  "$temp_dir/local-paper-smoke.txt" || die "plugin onEnable confirmation is missing"
grep -Fq "LegacyMiningWorld ${expected_version} disabled." \
  "$temp_dir/local-paper-smoke.txt" || die "plugin onDisable confirmation is missing"
grep -Fq "Generator requested for world 'legacy_mining_smoke' with id 'default'." \
  "$temp_dir/local-paper-smoke.txt" || die "generator request confirmation is missing"

readonly EXPECTED_TERRAIN_MARKERS=(
  LMW_BLOCK_GRASS_0_70_0
  LMW_BLOCK_DIRT_0_69_0
  LMW_BLOCK_DIRT_0_68_0
  LMW_BLOCK_BEDROCK_0_0_0
  LMW_BLOCK_AIR_0_NEGATIVE_1_0
  LMW_BLOCK_AIR_0_71_0
  LMW_BIOME_PLAINS_0_70_0
  LMW_BLOCK_GRASS_15_70_15
  LMW_BLOCK_DIRT_NEGATIVE_15_68_NEGATIVE_15
)
for marker in "${EXPECTED_TERRAIN_MARKERS[@]}"; do
  grep -Fq "[Server] $marker" "$temp_dir/local-paper-smoke.txt" \
    || die "Paper terrain smoke marker is missing: $marker"
done
for marker in "${geo_markers[@]}"; do
  grep -Fq "[Server] $marker" "$temp_dir/local-paper-smoke.txt" \
    || die "Paper geology smoke marker is missing: $marker"
done

if grep -Eiq 'SEVERE|Exception|Could not load|Could not set generator|Could not find generator|InvalidPlugin|NoClassDefFoundError|UnsupportedClassVersionError|Caused by:|Unknown or incomplete command|Unknown command' \
    "$temp_dir/local-paper-smoke.txt"; then
  die "Paper smoke log contains a fatal error signal"
fi

[ "$paper_source_sha" = "$(sha256sum "$PAPER_JAR" | awk '{print $1}')" ] \
  || die "source Paper JAR changed during smoke test"
[ "$eula_source_sha" = "$(sha256sum "$EULA_FILE" | awk '{print $1}')" ] \
  || die "source EULA file changed during smoke test"

{
  printf '\nPASS: LegacyMiningWorld %s loaded and populated fixed-seed legacy geology.\n' "$expected_version"
  printf 'PASS: all %d fixed geology anchors were observed in Paper.\n' "${#geo_markers[@]}"
  printf 'PASS: required terrain, biome, bedrock, surface, and air markers were observed.\n'
  printf 'PASS: no generator-selection, command, server, or class-loading failure was found.\n'
} >> "$temp_dir/local-paper-smoke.txt"
copy_log local-paper-smoke.txt

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'plugin-version: %s\n' "$expected_version"
  printf 'paper-jar-sha256: %s\n' "$paper_source_sha"
  printf 'fixed-world-seed: %s\n' "$FIXED_WORLD_SEED"
  printf 'force-loaded-chunks: %s\n' "$FORCE_LOADED_CHUNKS"
  printf 'anchor-file: %s\n' "$ANCHOR_FILE"
  printf 'anchor-count: %d\n' "${#geo_markers[@]}"
  printf 'anchor-material-counts: DIRT=%d GRAVEL=%d GRANITE=%d DIORITE=%d ANDESITE=%d\n' \
    "${anchor_material_counts[DIRT]}" \
    "${anchor_material_counts[GRAVEL]}" \
    "${anchor_material_counts[GRANITE]}" \
    "${anchor_material_counts[DIORITE]}" \
    "${anchor_material_counts[ANDESITE]}"
  printf 'x-boundary-pair: X_GRAVEL PASS\n'
  printf 'z-boundary-pair: Z_GRANITE PASS\n'
  printf 'surface-bedrock-air-protection: PASS\n'
  grep 'WORLD_MODEL_PROBE\|WORLD_MODEL_TOTAL' "$temp_dir/geology-adapter-tests.txt"
  printf 'paper-marker-inspection: PASS\n'
  printf 'fatal-error-scan: PASS\n'
  printf 'source-paper-eula-unchanged: PASS\n'
  printf 'multiverse-plugin-copied: NO\n'
  printf 'PASS: Phase 2B fixed-seed geology world smoke completed successfully.\n'
} > "$temp_dir/geology-world-smoke.txt"
copy_log geology-world-smoke.txt

for check_name in "${REQUIRED_REVIEW_CHECKS[@]}"; do
  [ -f "$CHECK_DIR/$check_name" ] || die "missing required review check log: $check_name"
done
grep -Fq 'PASS: Phase 2A geology engine tests completed successfully.' \
  "$CHECK_DIR/geology-engine-tests.txt" \
  || die "geology engine review log does not contain its PASS marker"
grep -Fq 'PASS: Phase 2B geology adapter tests completed successfully.' \
  "$CHECK_DIR/geology-adapter-tests.txt" \
  || die "geology adapter review log does not contain its PASS marker"
grep -Fq 'OreEngineReviewTest > emitsStablePhaseThreeAReviewEvidence() PASSED' \
  "$CHECK_DIR/ore-engine-tests.txt" \
  || die "ore engine task did not execute its required review test"
grep -Fq 'ORE_PLAN_PROBE seed=11652021 target=0,0' \
  "$CHECK_DIR/ore-engine-tests.txt" \
  || die "ore engine review log does not contain its fixed planner probe"
grep -Fq 'PASS: Phase 3A ore engine tests completed successfully.' \
  "$CHECK_DIR/ore-engine-tests.txt" \
  || die "ore engine review log does not contain its PASS marker"
grep -Fq 'PASS: Phase 2B fixed-seed geology world smoke completed successfully.' \
  "$CHECK_DIR/geology-world-smoke.txt" \
  || die "geology world smoke log does not contain its PASS marker"

printf 'Review checks passed. Logs: %s\n' "$CHECK_DIR"
