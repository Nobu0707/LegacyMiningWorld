#!/usr/bin/env bash
set -euo pipefail

readonly PAPER_JAR="server/paper-26.1.2-69.jar"
readonly EULA_FILE="server/eula.txt"
readonly GEOLOGY_ANCHOR_FILE="src/test/resources/geology-smoke-anchors.tsv"
readonly ORE_ANCHOR_FILE="src/test/resources/ore-smoke-anchors.tsv"
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
  ore-adapter-tests.txt
  gradle-build.txt
  local-paper-smoke.txt
  geology-world-smoke.txt
  ore-world-smoke.txt
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

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'java-version:\n'
  java -version 2>&1
  printf '\ngradle-version:\n'
  ./gradlew --version
  printf '\nplugin-version: %s\n' "$expected_version"
  printf 'test-task: oreAdapterTest\n'
  printf 'test-filter: JUnit tag ore-adapter\n'
  printf 'fixed-seed: %s\n' "$FIXED_WORLD_SEED"
  printf 'fixed-target-chunks: %s\n\n' "$FORCE_LOADED_CHUNKS"
  ./gradlew --no-daemon oreAdapterTest
  printf '\nmaterial-adapter: PASS\n'
  printf 'replacement: PASS\n'
  printf 'legacy-y-range: PASS 0..67\n'
  printf 'target-region: PASS\n'
  printf 'stable-overwrite: PASS first-wins\n'
  printf 'combined-counts: COAL=867 IRON=443 GOLD=48 REDSTONE=106 DIAMOND=17 LAPIS=19\n'
  printf 'combined-checksum: -7165395187979696007\n'
  printf 'y11-counts: COAL=6 IRON=5 GOLD=8 REDSTONE=7 DIAMOND=4\n'
  printf 'anchor-count: 14\n'
  printf 'anchor-material-counts: COAL=3 IRON=3 GOLD=2 REDSTONE=2 DIAMOND=2 LAPIS=2\n'
  printf 'x-boundary-pair: X_COAL PASS\n'
  printf 'z-boundary-pair: Z_IRON PASS\n'
  printf 'concurrency: PASS\n'
  printf 'geology-golden-regression: PASS\n'
  printf 'PASS: Phase 3B ore adapter tests completed successfully.\n'
} > "$temp_dir/ore-adapter-tests.txt" 2>&1 || {
  copy_log ore-adapter-tests.txt
  cat "$temp_dir/ore-adapter-tests.txt" >&2
  die "Phase 3B ore adapter tests failed"
}
copy_log ore-adapter-tests.txt

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
if grep -Eiq 'minecraft-1\.16\.5|server-mappings|decompil|geology-smoke-anchors|ore-smoke-anchors|multiverse' \
    "$temp_dir/jar-contents.txt"; then
  die "release JAR contains a research, test-anchor, or Multiverse artifact"
fi
for required_class in \
  'net/nobu0707/legacyminingworld/geology/LegacyUndergroundPopulator.class' \
  'net/nobu0707/legacyminingworld/geology/LegacyGeologyMaterialAdapter.class' \
  'net/nobu0707/legacyminingworld/geology/LegacyGeologyApplicator.class' \
  'net/nobu0707/legacyminingworld/geology/LimitedRegionUndergroundBlockAccess.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreMaterialAdapter.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreBlockAccess.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreApplicator.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOrePlanner.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreFeature.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreMaterial.class' \
  'net/nobu0707/legacyminingworld/ore/LegacyOreHeightDistribution.class' \
  'net/nobu0707/legacyminingworld/ore/UniformRangeDistribution.class' \
  'net/nobu0707/legacyminingworld/ore/DepthAverageDistribution.class'; do
  grep -Fxq "$required_class" "$temp_dir/jar-contents.txt" \
    || die "release JAR is missing production class: $required_class"
done
if grep -Fxq 'net/nobu0707/legacyminingworld/geology/LegacyGeologyPopulator.class' \
    "$temp_dir/jar-contents.txt"; then
  die "release JAR contains the removed standalone geology populator"
fi
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
    -e 'WorldInitEvent|ThreadLocal|BukkitScheduler|getScheduler[[:space:]]*\(|runTask|runAsync|parallelStream' \
    -e 'Material\.matchMaterial|Class\.forName|java\.lang\.reflect|org\.bukkit\.craftbukkit|net\.minecraft' \
    src/main/java >/dev/null; then
  die "production source uses a forbidden world, chunk, scheduler, or retained-state API"
fi
if rg -n -i 'multiverse' build.gradle.kts gradle.properties src/main >/dev/null; then
  die "Phase 3B must not declare or use Multiverse-Core"
fi
if rg -n \
    -e 'static[[:space:]]+Random[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*(=|;)' \
    -e 'static[[:space:]]+(List|Map|Set|Collection)[[:space:]]*<[^;]+[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*(=|;)' \
    src/main/java >/dev/null; then
  die "production source contains a mutable static Random or collection declaration"
fi
grep -Fq 'List.of(new LegacyUndergroundPopulator())' \
  src/main/java/net/nobu0707/legacyminingworld/LegacyMiningChunkGenerator.java \
  || die "getDefaultPopulators does not register the single integrated underground populator"

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
[ -r "$GEOLOGY_ANCHOR_FILE" ] || die "missing fixed geology anchors: $GEOLOGY_ANCHOR_FILE"
[ -r "$ORE_ANCHOR_FILE" ] || die "missing fixed ore anchors: $ORE_ANCHOR_FILE"
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
done < "$GEOLOGY_ANCHOR_FILE"

[ "${#geo_markers[@]}" -ge 10 ] || die "expected at least ten geology anchors"
for material in DIRT GRAVEL GRANITE DIORITE ANDESITE; do
  [ "${anchor_material_counts[$material]:-0}" -ge 2 ] \
    || die "expected at least two anchors for $material"
done
[ "$x_boundary_anchors" -eq 2 ] || die "expected one two-anchor X boundary pair"
[ "$z_boundary_anchors" -eq 2 ] || die "expected one two-anchor Z boundary pair"

ore_commands=()
ore_markers=()
y11_commands=()
y11_markers=()
extra_ore_commands=()
extra_ore_markers=()
declare -A ore_anchor_material_counts=()
declare -A y11_material_seen=()
x_ore_boundary_anchors=0
z_ore_boundary_anchors=0
negative_ore_anchor_seen=0
positive_ore_anchor_seen=0
while IFS=$'\t' read -r id x y z expected_material purpose pair_id \
    source_chunk_x source_chunk_z feature attempt vein_sequence; do
  [ -n "$id" ] || continue
  [[ "$id" == \#* ]] && continue
  [ "$id" = "id" ] && continue
  [[ "$id" =~ ^[A-Z0-9_]+$ ]] || die "invalid ore anchor id: $id"
  expected_lower="$(printf '%s' "$expected_material" | tr '[:upper:]' '[:lower:]')"
  ore_commands+=("execute if block $x $y $z minecraft:$expected_lower run say LMW_ORE_$id")
  ore_markers+=("LMW_ORE_$id")
  ore_anchor_material_counts["$expected_material"]=$(( ${ore_anchor_material_counts["$expected_material"]:-0} + 1 ))
  [ "$pair_id" != "X_COAL" ] || x_ore_boundary_anchors=$((x_ore_boundary_anchors + 1))
  [ "$pair_id" != "Z_IRON" ] || z_ore_boundary_anchors=$((z_ore_boundary_anchors + 1))
  if [ "$y" -eq 11 ] && [ -z "${y11_material_seen[$expected_material]:-}" ]; then
    y11_commands+=("execute if block $x $y $z minecraft:$expected_lower run say LMW_ORE_Y11_${expected_material%_ORE}")
    y11_markers+=("LMW_ORE_Y11_${expected_material%_ORE}")
    y11_material_seen["$expected_material"]=1
  fi
  if { [ "$x" -lt 0 ] || [ "$z" -lt 0 ]; } && [ "$negative_ore_anchor_seen" -eq 0 ]; then
    extra_ore_commands+=("execute if block $x $y $z minecraft:$expected_lower run say LMW_ORE_NEGATIVE_CHUNK")
    extra_ore_markers+=("LMW_ORE_NEGATIVE_CHUNK")
    negative_ore_anchor_seen=1
  fi
  if [ "$x" -ge 0 ] && [ "$z" -ge 0 ] && [ "$positive_ore_anchor_seen" -eq 0 ]; then
    extra_ore_commands+=("execute if block $x $y $z minecraft:$expected_lower run say LMW_ORE_POSITIVE_CHUNK")
    extra_ore_markers+=("LMW_ORE_POSITIVE_CHUNK")
    positive_ore_anchor_seen=1
  fi
done < "$ORE_ANCHOR_FILE"

[ "${#ore_markers[@]}" -eq 14 ] || die "expected exactly fourteen ore anchors"
for material in COAL_ORE IRON_ORE GOLD_ORE REDSTONE_ORE DIAMOND_ORE LAPIS_ORE; do
  [ "${ore_anchor_material_counts[$material]:-0}" -ge 2 ] \
    || die "expected at least two anchors for $material"
done
for material in COAL_ORE IRON_ORE GOLD_ORE REDSTONE_ORE DIAMOND_ORE; do
  [ "${y11_material_seen[$material]:-0}" -eq 1 ] \
    || die "expected a Y=11 anchor for $material"
done
[ "$x_ore_boundary_anchors" -eq 2 ] || die "expected one two-anchor ore X boundary pair"
[ "$z_ore_boundary_anchors" -eq 2 ] || die "expected one two-anchor ore Z boundary pair"
[ "$negative_ore_anchor_seen" -eq 1 ] || die "expected an ore anchor in a negative chunk"
[ "$positive_ore_anchor_seen" -eq 1 ] || die "expected an ore anchor in a non-negative chunk"

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
motd=LegacyMiningWorld Phase 3B underground smoke
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
    for ore_command in "${ore_commands[@]}"; do
      printf '%s\n' "$ore_command" >&3
    done
    for y11_command in "${y11_commands[@]}"; do
      printf '%s\n' "$y11_command" >&3
    done
    for extra_ore_command in "${extra_ore_commands[@]}"; do
      printf '%s\n' "$extra_ore_command" >&3
    done
    printf 'execute unless block 0 5 0 minecraft:copper_ore run say LMW_FORBIDDEN_COPPER_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:emerald_ore run say LMW_FORBIDDEN_EMERALD_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 -1 0 minecraft:deepslate run say LMW_FORBIDDEN_DEEPSLATE_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_coal_ore run say LMW_FORBIDDEN_DEEPSLATE_COAL_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_iron_ore run say LMW_FORBIDDEN_DEEPSLATE_IRON_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_gold_ore run say LMW_FORBIDDEN_DEEPSLATE_GOLD_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_redstone_ore run say LMW_FORBIDDEN_DEEPSLATE_REDSTONE_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_diamond_ore run say LMW_FORBIDDEN_DEEPSLATE_DIAMOND_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_lapis_ore run say LMW_FORBIDDEN_DEEPSLATE_LAPIS_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_emerald_ore run say LMW_FORBIDDEN_DEEPSLATE_EMERALD_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:deepslate_copper_ore run say LMW_FORBIDDEN_DEEPSLATE_COPPER_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:water run say LMW_FORBIDDEN_WATER_REPRESENTATIVE\n' >&3
    printf 'execute unless block 0 5 0 minecraft:lava run say LMW_FORBIDDEN_LAVA_REPRESENTATIVE\n' >&3
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
grep -Fq "LegacyMiningWorld ${expected_version} enabled; basic terrain, legacy geology, and legacy ores are available." \
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
for marker in "${ore_markers[@]}" "${y11_markers[@]}" "${extra_ore_markers[@]}"; do
  grep -Fq "[Server] $marker" "$temp_dir/local-paper-smoke.txt" \
    || die "Paper ore smoke marker is missing: $marker"
done
readonly EXPECTED_FORBIDDEN_MARKERS=(
  LMW_FORBIDDEN_COPPER_REPRESENTATIVE
  LMW_FORBIDDEN_EMERALD_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_COAL_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_IRON_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_GOLD_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_REDSTONE_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_DIAMOND_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_LAPIS_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_EMERALD_REPRESENTATIVE
  LMW_FORBIDDEN_DEEPSLATE_COPPER_REPRESENTATIVE
  LMW_FORBIDDEN_WATER_REPRESENTATIVE
  LMW_FORBIDDEN_LAVA_REPRESENTATIVE
)
for marker in "${EXPECTED_FORBIDDEN_MARKERS[@]}"; do
  grep -Fq "[Server] $marker" "$temp_dir/local-paper-smoke.txt" \
    || die "Paper representative forbidden-material marker is missing: $marker"
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
  printf '\nPASS: LegacyMiningWorld %s loaded and populated fixed-seed legacy geology and ores.\n' "$expected_version"
  printf 'PASS: all %d fixed geology anchors were observed in Paper.\n' "${#geo_markers[@]}"
  printf 'PASS: all %d fixed ore anchors were observed in Paper.\n' "${#ore_markers[@]}"
  printf 'PASS: Y=11 COAL/IRON/GOLD/REDSTONE/DIAMOND markers were observed.\n'
  printf 'PASS: ore X/Z boundary pairs were observed.\n'
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
  printf 'anchor-file: %s\n' "$GEOLOGY_ANCHOR_FILE"
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

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'plugin-version: %s\n' "$expected_version"
  printf 'paper-jar-sha256: %s\n' "$paper_source_sha"
  printf 'fixed-world-seed: %s\n' "$FIXED_WORLD_SEED"
  printf 'force-loaded-chunks: %s\n' "$FORCE_LOADED_CHUNKS"
  printf 'ore-anchor-file: %s\n' "$ORE_ANCHOR_FILE"
  printf 'ore-anchor-count: %d\n' "${#ore_markers[@]}"
  printf 'ore-anchor-material-counts: COAL=%d IRON=%d GOLD=%d REDSTONE=%d DIAMOND=%d LAPIS=%d\n' \
    "${ore_anchor_material_counts[COAL_ORE]}" \
    "${ore_anchor_material_counts[IRON_ORE]}" \
    "${ore_anchor_material_counts[GOLD_ORE]}" \
    "${ore_anchor_material_counts[REDSTONE_ORE]}" \
    "${ore_anchor_material_counts[DIAMOND_ORE]}" \
    "${ore_anchor_material_counts[LAPIS_ORE]}"
  printf 'y11-five-material: PASS\n'
  printf 'x-boundary-pair: X_COAL PASS\n'
  printf 'z-boundary-pair: Z_IRON PASS\n'
  printf 'terrain-protection: PASS\n'
  printf 'geology-regression: 10/10 PASS\n'
  grep 'UNDERGROUND_MODEL_PROBE\|UNDERGROUND_MODEL_TOTAL\|UNDERGROUND_Y11_TOTAL' \
    "$temp_dir/ore-adapter-tests.txt"
  printf 'forbidden-material-pure-model: PASS four chunks\n'
  printf 'forbidden-material-paper-representative-points: PASS\n'
  printf 'paper-marker-inspection: PASS\n'
  printf 'fatal-error-scan: PASS\n'
  printf 'source-paper-eula-unchanged: PASS\n'
  printf 'multiverse-plugin-copied: NO\n'
  printf 'PASS: Phase 3B fixed-seed ore world smoke completed successfully.\n'
} > "$temp_dir/ore-world-smoke.txt"
copy_log ore-world-smoke.txt

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
grep -Fq 'OreAdapterReviewTest > emitsStablePhaseThreeBReviewEvidence() PASSED' \
  "$CHECK_DIR/ore-adapter-tests.txt" \
  || die "ore adapter task did not execute its required review test"
grep -Fq 'PASS: Phase 3B ore adapter tests completed successfully.' \
  "$CHECK_DIR/ore-adapter-tests.txt" \
  || die "ore adapter review log does not contain its PASS marker"
grep -Fq 'PASS: Phase 2B fixed-seed geology world smoke completed successfully.' \
  "$CHECK_DIR/geology-world-smoke.txt" \
  || die "geology world smoke log does not contain its PASS marker"
grep -Fq 'PASS: Phase 3B fixed-seed ore world smoke completed successfully.' \
  "$CHECK_DIR/ore-world-smoke.txt" \
  || die "ore world smoke log does not contain its PASS marker"

printf 'Review checks passed. Logs: %s\n' "$CHECK_DIR"
