#!/usr/bin/env bash
set -euo pipefail

readonly PAPER_JAR="server/paper-26.1.2-69.jar"
readonly EULA_FILE="server/eula.txt"
readonly CHECK_DIR="build/review-checks"
readonly SMOKE_DIR="build/paper-smoke"
readonly SMOKE_TIMEOUT_SECONDS=120

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

expected_version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ -n "$expected_version" ] || die "legacyminingworld_version is not set"

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-checks.XXXXXX")"
paper_job=""
cleanup() {
  if [ -f "$temp_dir/local-paper-smoke.txt" ]; then
    mkdir -p "$CHECK_DIR"
    cp "$temp_dir/local-paper-smoke.txt" "$CHECK_DIR/local-paper-smoke.txt"
  fi
  if [ -n "$paper_job" ] && kill -0 "$paper_job" 2>/dev/null; then
    kill "$paper_job" 2>/dev/null || true
    wait "$paper_job" 2>/dev/null || true
  fi
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
    mkdir -p "$CHECK_DIR"
    cp "$temp_dir/$log_name" "$CHECK_DIR/$log_name"
    cat "$temp_dir/$log_name" >&2
    die "Gradle check failed: $*"
  fi
}

run_gradle_check gradle-test.txt clean test
run_gradle_check gradle-build.txt build

{
  git diff --check
  git diff --cached --check
  printf 'PASS: git diff --check and git diff --cached --check\n'
} > "$temp_dir/git-diff-check.txt"
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
grep -Eq '^eula=true[[:space:]]*$' "$EULA_FILE" || die "$EULA_FILE must contain eula=true"
command -v timeout >/dev/null 2>&1 || die "timeout command is required for the Paper smoke test"
paper_source_sha="$(sha256sum "$PAPER_JAR" | awk '{print $1}')"
eula_source_sha="$(sha256sum "$EULA_FILE" | awk '{print $1}')"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR/plugins"
cp "$PAPER_JAR" "$SMOKE_DIR/paper.jar"
cp "$EULA_FILE" "$SMOKE_DIR/eula.txt"
cp "$built_jar" "$SMOKE_DIR/plugins/"

cat > "$SMOKE_DIR/server.properties" <<'PROPERTIES'
online-mode=false
server-ip=127.0.0.1
server-port=0
level-name=legacy_mining_smoke
allow-nether=false
max-players=1
motd=LegacyMiningWorld Phase 1 generator smoke
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

if [ "$ready" -eq 1 ]; then
  printf 'forceload add -16 -16 15 15\n' >&3
  sleep 3
  printf 'execute if block 0 70 0 minecraft:grass_block run say LMW_BLOCK_GRASS_0_70_0\n' >&3
  printf 'execute if block 0 69 0 minecraft:dirt run say LMW_BLOCK_DIRT_0_69_0\n' >&3
  printf 'execute if block 0 68 0 minecraft:dirt run say LMW_BLOCK_DIRT_0_68_0\n' >&3
  printf 'execute if block 0 67 0 minecraft:stone run say LMW_BLOCK_STONE_0_67_0\n' >&3
  printf 'execute if block 0 5 0 minecraft:stone run say LMW_BLOCK_STONE_0_5_0\n' >&3
  printf 'execute if block 0 0 0 minecraft:bedrock run say LMW_BLOCK_BEDROCK_0_0_0\n' >&3
  printf 'execute if block 0 -1 0 minecraft:air run say LMW_BLOCK_AIR_0_NEGATIVE_1_0\n' >&3
  printf 'execute if block 0 71 0 minecraft:air run say LMW_BLOCK_AIR_0_71_0\n' >&3
  printf 'execute if biome 0 70 0 minecraft:plains run say LMW_BIOME_PLAINS_0_70_0\n' >&3
  printf 'execute if block 15 70 15 minecraft:grass_block run say LMW_BLOCK_GRASS_15_70_15\n' >&3
  printf 'execute if block -15 68 -15 minecraft:dirt run say LMW_BLOCK_DIRT_NEGATIVE_15_68_NEGATIVE_15\n' >&3
  sleep 3
  printf 'stop\n' >&3
else
  printf 'stop\n' >&3 || true
fi
exec 3>&-

set +e
wait "$paper_job"
paper_status=$?
set -e

if [ "$ready" -ne 1 ]; then
  copy_log local-paper-smoke.txt
  die "Paper did not report startup completion within ${SMOKE_TIMEOUT_SECONDS}s"
fi
if [ "$paper_status" -ne 0 ]; then
  copy_log local-paper-smoke.txt
  die "Paper smoke process exited with status $paper_status"
fi

if ! grep -Fq "LegacyMiningWorld ${expected_version} generator services loaded." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onLoad confirmation is missing from Paper log"
fi
if ! grep -Fq "LegacyMiningWorld ${expected_version} enabled; Phase 1 basic terrain generator is available." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onEnable confirmation is missing from Paper log"
fi
if ! grep -Fq "LegacyMiningWorld ${expected_version} disabled." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onDisable confirmation is missing from Paper log"
fi

if ! grep -Fq "Generator requested for world 'legacy_mining_smoke' with id 'default'." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "generator request confirmation is missing from Paper log"
fi

readonly EXPECTED_SMOKE_MARKERS=(
  LMW_BLOCK_GRASS_0_70_0
  LMW_BLOCK_DIRT_0_69_0
  LMW_BLOCK_DIRT_0_68_0
  LMW_BLOCK_STONE_0_67_0
  LMW_BLOCK_STONE_0_5_0
  LMW_BLOCK_BEDROCK_0_0_0
  LMW_BLOCK_AIR_0_NEGATIVE_1_0
  LMW_BLOCK_AIR_0_71_0
  LMW_BIOME_PLAINS_0_70_0
  LMW_BLOCK_GRASS_15_70_15
  LMW_BLOCK_DIRT_NEGATIVE_15_68_NEGATIVE_15
)
for marker in "${EXPECTED_SMOKE_MARKERS[@]}"; do
  if ! grep -Fq "[Server] $marker" "$temp_dir/local-paper-smoke.txt"; then
    copy_log local-paper-smoke.txt
    die "Paper generator smoke marker is missing: $marker"
  fi
done

if grep -Eiq 'SEVERE|Exception|Could not load|Could not set generator|Could not find generator|InvalidPlugin|NoClassDefFoundError|UnsupportedClassVersionError|Caused by:|Unknown or incomplete command' \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "Paper smoke log contains a fatal error signal"
fi

[ "$paper_source_sha" = "$(sha256sum "$PAPER_JAR" | awk '{print $1}')" ] \
  || die "source Paper JAR changed during smoke test"
[ "$eula_source_sha" = "$(sha256sum "$EULA_FILE" | awk '{print $1}')" ] \
  || die "source EULA file changed during smoke test"

{
  printf '\nPASS: LegacyMiningWorld %s generator loaded, generated the configured world, and shut down cleanly.\n' "$expected_version"
  printf 'PASS: required terrain blocks at and around the origin match the Phase 1 profile.\n'
  printf 'PASS: the origin biome is minecraft:plains.\n'
  printf 'PASS: no generator-selection, command, server, or class-loading failure was found.\n'
} >> "$temp_dir/local-paper-smoke.txt"
copy_log local-paper-smoke.txt

printf 'Review checks passed. Logs: %s\n' "$CHECK_DIR"
