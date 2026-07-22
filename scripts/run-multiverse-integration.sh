#!/usr/bin/env bash
set -euo pipefail

readonly PAPER_JAR="server/paper-26.1.2-69.jar"
readonly EULA_FILE="server/eula.txt"
readonly MULTIVERSE_JAR="server/plugins/multiverse-core-5.7.2.jar"
readonly CHECK_DIR="build/review-checks"
readonly SMOKE_DIR="build/multiverse-smoke"
readonly WORLD_NAME="legacy_mining_mv_smoke"
readonly FIXED_SEED="11652021"
readonly TIMEOUT_SECONDS=180
readonly EXPECTED_STABLE_VERSION="1.0.0"
readonly EXPECTED_VERSION="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
readonly BUILD_RELEASE_JAR="build/libs/LegacyMiningWorld-${EXPECTED_VERSION}.jar"
readonly RELEASE_JAR="build/release/LegacyMiningWorld-${EXPECTED_VERSION}.jar"
readonly VERIFIER_JAR="build/libs/LegacyMiningWorld-MultiverseVerifier-${EXPECTED_VERSION}.jar"
readonly CREATE_COMMAND="mv create ${WORLD_NAME} normal --seed ${FIXED_SEED} --generator LegacyMiningWorld --no-adjust-spawn"

server_pid=""

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

[ "$EXPECTED_VERSION" = "$EXPECTED_STABLE_VERSION" ] \
  || die "expected stable version $EXPECTED_STABLE_VERSION, found $EXPECTED_VERSION"

cleanup() {
  exec 9>&- 2>/dev/null || true
  if [ -n "$server_pid" ] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_log() {
  local log_file="$1"
  local pattern="$2"
  local timeout_seconds="${3:-30}"
  local deadline=$((SECONDS + timeout_seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -f "$log_file" ] && grep -Fq "$pattern" "$log_file"; then
      return 0
    fi
    if [ -n "$server_pid" ] && ! kill -0 "$server_pid" 2>/dev/null; then
      return 1
    fi
    sleep 1
  done
  return 1
}

send_command() {
  local raw_log="$1"
  local command="$2"
  local marker="$3"
  local timeout_seconds="${4:-45}"
  printf '%s\n' "$command" >&9
  printf 'say %s\n' "$marker" >&9
  wait_for_log "$raw_log" "[Server] $marker" "$timeout_seconds" \
    || die "command did not complete: $command"
}

start_server() {
  local raw_log="$1"
  local fifo="$SMOKE_DIR/paper-stdin"
  rm -f "$fifo"
  mkfifo "$fifo"
  exec 9<> "$fifo"
  (
    cd "$SMOKE_DIR"
    timeout --signal=TERM --kill-after=15s "${TIMEOUT_SECONDS}s" \
      java -Xms512M -Xmx1G -jar paper.jar nogui <&9 > "$OLDPWD/$raw_log" 2>&1
  ) &
  server_pid=$!
  wait_for_log "$raw_log" 'Done (' "$TIMEOUT_SECONDS" \
    || die "Paper did not finish startup: $raw_log"
}

stop_server() {
  local raw_log="$1"
  printf 'stop\n' >&9
  exec 9>&-
  set +e
  wait "$server_pid"
  local status=$?
  set -e
  server_pid=""
  [ "$status" -eq 0 ] || die "Paper exited with status $status: $raw_log"
  grep -Fq 'Stopping server' "$raw_log" || die "Paper stop marker missing: $raw_log"
}

fatal_scan() {
  local log_file="$1"
  if grep -Eiq 'SEVERE|Exception|Caused by:|NoClassDefFoundError|UnsupportedClassVersionError|InvalidPlugin|Could not load|Could not set generator|Could not find generator|Unknown or incomplete command|Unknown command' \
      "$log_file"; then
    die "fatal signal found in $log_file"
  fi
  if grep -Fq 'LMW_MV_VERIFY_FAIL' "$log_file"; then
    die "verifier failure found in $log_file"
  fi
}

extract_marker() {
  local log_file="$1"
  local marker="$2"
  grep "$marker" "$log_file" | sed "s/^.*${marker}/${marker}/" | tail -n 1
}

mkdir -p "$CHECK_DIR"
[ -r "$PAPER_JAR" ] || die "missing Paper JAR: $PAPER_JAR"
[ -r "$EULA_FILE" ] || die "missing EULA: $EULA_FILE"
[ -r "$MULTIVERSE_JAR" ] || die "missing Multiverse JAR: $MULTIVERSE_JAR"
[ -r "$BUILD_RELEASE_JAR" ] || die "missing build release JAR: $BUILD_RELEASE_JAR"
[ -r "$RELEASE_JAR" ] || die "missing packaged release JAR: $RELEASE_JAR"
cmp "$BUILD_RELEASE_JAR" "$RELEASE_JAR" || die "packaged/build release JAR mismatch"
[ -r "$VERIFIER_JAR" ] || die "missing verifier JAR: $VERIFIER_JAR"
grep -Eq '^eula=true[[:space:]]*$' "$EULA_FILE" || die "$EULA_FILE must contain eula=true"

paper_source_sha="$(sha256sum "$PAPER_JAR" | awk '{print $1}')"
eula_source_sha="$(sha256sum "$EULA_FILE" | awk '{print $1}')"
multiverse_source_sha="$(sha256sum "$MULTIVERSE_JAR" | awk '{print $1}')"
metadata_path="$(jar tf "$MULTIVERSE_JAR" | grep -E '^(paper-plugin|plugin)\.yml$' | head -n 1)"
[ -n "$metadata_path" ] || die "Multiverse plugin metadata is missing"
metadata_file="$CHECK_DIR/multiverse-plugin-metadata.yml"
unzip -p "$MULTIVERSE_JAR" "$metadata_path" > "$metadata_file"
grep -Fxq 'name: Multiverse-Core' "$metadata_file" || die "unexpected Multiverse name"
grep -Fxq 'version: 5.7.2' "$metadata_file" || die "unexpected Multiverse version"
grep -Fxq 'main: org.mvplugins.multiverse.core.MultiverseCore' "$metadata_file" \
  || die "unexpected Multiverse main"

{
  printf 'file: %s\n' "$MULTIVERSE_JAR"
  printf 'bytes: %s\n' "$(stat -c '%s' "$MULTIVERSE_JAR")"
  printf 'sha256: %s\n' "$multiverse_source_sha"
  printf 'metadata-path: %s\n' "$metadata_path"
  printf 'plugin-name: Multiverse-Core\n'
  printf 'version: 5.7.2\n'
  printf 'main: org.mvplugins.multiverse.core.MultiverseCore\n'
  printf 'load-timing: metadata default POSTWORLD\n'
  printf 'dependencies: softdepend Vault, PlaceholderAPI\n'
  printf 'command-root: mv (runtime help)\n'
  printf 'generator-list-command: mv generators list\n'
  printf 'create-command: %s\n' "$CREATE_COMMAND"
  printf 'PASS: Multiverse-Core JAR metadata and runtime command contract inspected.\n'
} > "$CHECK_DIR/multiverse-jar-inspection.txt"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR/plugins"
cp "$PAPER_JAR" "$SMOKE_DIR/paper.jar"
cp "$EULA_FILE" "$SMOKE_DIR/eula.txt"
cp "$RELEASE_JAR" "$SMOKE_DIR/plugins/"
cp "$MULTIVERSE_JAR" "$SMOKE_DIR/plugins/"
cp "$VERIFIER_JAR" "$SMOKE_DIR/plugins/"
mapfile -t plugin_jars < <(find "$SMOKE_DIR/plugins" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort)
[ "${#plugin_jars[@]}" -eq 3 ] || die "Multiverse smoke must contain exactly three plugin JARs"

cat > "$SMOKE_DIR/server.properties" <<PROPERTIES
level-name=world
online-mode=false
server-ip=127.0.0.1
server-port=0
allow-nether=false
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
difficulty=peaceful
motd=LegacyMiningWorld Phase 4A Multiverse smoke
PROPERTIES

first_raw="$CHECK_DIR/multiverse-first-boot.raw.txt"
start_server "$first_raw"
send_command "$first_raw" 'mv version' 'LMW_MV_FIRST_VERSION'
send_command "$first_raw" 'mv help generators' 'LMW_MV_FIRST_GENERATOR_HELP'
send_command "$first_raw" 'mv help create' 'LMW_MV_FIRST_CREATE_HELP'
send_command "$first_raw" 'mv create --help' 'LMW_MV_FIRST_CREATE_DASH_HELP'
send_command "$first_raw" 'mv generators list' 'LMW_MV_FIRST_GENERATORS'
send_command "$first_raw" "$CREATE_COMMAND" 'LMW_MV_FIRST_CREATED' 90
send_command "$first_raw" 'mv list' 'LMW_MV_FIRST_LIST'
send_command "$first_raw" "mv info $WORLD_NAME" 'LMW_MV_FIRST_INFO'
send_command "$first_raw" "lmwit verify $WORLD_NAME $FIXED_SEED" 'LMW_MV_FIRST_VERIFIED' 90
send_command "$first_raw" 'save-all' 'LMW_MV_FIRST_SAVED'
stop_server "$first_raw"

grep -Fq 'LegacyMiningWorld' "$first_raw" || die "LegacyMiningWorld did not initialize"
grep -Fq 'Multiverse-Core v5.7.2' "$first_raw" || die "Multiverse-Core did not initialize"
grep -Fq 'LegacyMiningWorldMultiverseVerifier' "$first_raw" \
  || die "test-only verifier did not initialize"
grep -Fq '====[ Multiverse Generator List ]====' "$first_raw" \
  || die "generator list output missing"
grep -Fq 'LegacyMiningWorld' "$first_raw" || die "LegacyMiningWorld generator not listed"
grep -Fq "World '$WORLD_NAME' created!" "$first_raw" || die "Multiverse create failed"
grep -Fq "Generator requested for world '$WORLD_NAME' with id 'default'." "$first_raw" \
  || die "standard Bukkit generator request missing"
grep -Fq 'LMW_MV_VERIFY_PASS' "$first_raw" || die "first verifier PASS missing"
fatal_scan "$first_raw"

worlds_yml="$SMOKE_DIR/plugins/Multiverse-Core/worlds.yml"
[ -f "$worlds_yml" ] || die "Multiverse worlds.yml missing"
grep -Fq "legacy-world-name: $WORLD_NAME" "$worlds_yml" || die "world name missing from worlds.yml"
grep -Fq 'generator: LegacyMiningWorld' "$worlds_yml" || die "generator missing from worlds.yml"
grep -Fq 'auto-load: true' "$worlds_yml" || die "autoload missing from worlds.yml"
grep -Fq 'environment: normal' "$worlds_yml" || die "environment missing from worlds.yml"
grep -Fq "seed: $FIXED_SEED" "$worlds_yml" || die "seed missing from worlds.yml"
world_root="$SMOKE_DIR/world"
custom_world_folder="$world_root/dimensions/minecraft/$WORLD_NAME"
[ -d "$custom_world_folder" ] || die "custom Paper dimension folder missing"
[ -f "$world_root/level.dat" ] || die "Paper world root level.dat missing"
[ -f "$custom_world_folder/data/paper/metadata.dat" ] \
  || die "custom world Paper UUID metadata missing"
[ -d "$custom_world_folder/region" ] || die "custom world region folder missing"
find "$custom_world_folder/region" -maxdepth 1 -type f -name '*.mca' | grep -q . \
  || die "custom world region files missing"
[ "$custom_world_folder" != "$world_root/dimensions/minecraft/overworld" ] \
  || die "custom and default dimensions overlap"
if [ -f "$SMOKE_DIR/bukkit.yml" ] && grep -Eq '^[[:space:]]+generator:[[:space:]]+LegacyMiningWorld' "$SMOKE_DIR/bukkit.yml"; then
  die "Multiverse smoke must not use bukkit.yml generator configuration"
fi

first_summary="$(extract_marker "$first_raw" 'LMW_MV_VERIFY_PASS')"
first_meta="$(extract_marker "$first_raw" 'LMW_MV_WORLD_META')"
first_scan="$(extract_marker "$first_raw" 'LMW_MV_SCAN_PASS')"
first_uid="$(printf '%s\n' "$first_summary" | sed -n 's/.* uid=\([^ ]*\).*/\1/p')"
first_checksum="$(printf '%s\n' "$first_summary" | sed -n 's/.* checksum=\([^ ]*\).*/\1/p')"
[ -n "$first_uid" ] || die "first UUID missing"
[ -n "$first_checksum" ] || die "first checksum missing"

{
  printf 'exact-commands:\n'
  printf '  mv version\n'
  printf '  mv help generators\n'
  printf '  mv help create\n'
  printf '  mv create --help\n'
  printf '  mv generators list\n'
  printf '  %s\n' "$CREATE_COMMAND"
  printf '  mv list\n'
  printf '  mv info %s\n' "$WORLD_NAME"
  printf '  lmwit verify %s %s\n' "$WORLD_NAME" "$FIXED_SEED"
  printf '  save-all\n'
  printf '  stop\n\n'
  cat "$first_raw"
  printf '\nPASS: first boot, generator list, Multiverse create, verifier, save, and stop succeeded.\n'
} > "$CHECK_DIR/multiverse-first-boot.txt"

second_raw="$CHECK_DIR/multiverse-second-boot.raw.txt"
start_server "$second_raw"
send_command "$second_raw" 'mv version' 'LMW_MV_SECOND_VERSION'
send_command "$second_raw" 'mv list' 'LMW_MV_SECOND_LIST'
send_command "$second_raw" "mv info $WORLD_NAME" 'LMW_MV_SECOND_INFO'
send_command "$second_raw" "lmwit verify $WORLD_NAME $FIXED_SEED" 'LMW_MV_SECOND_VERIFIED' 90
send_command "$second_raw" 'save-all' 'LMW_MV_SECOND_SAVED'
stop_server "$second_raw"

grep -Fq "Generator requested for world '$WORLD_NAME' with id 'default'." "$second_raw" \
  || die "Multiverse restart did not request the generator"
grep -Fq "$WORLD_NAME" "$second_raw" || die "custom world was not auto-loaded"
grep -Fq 'LMW_MV_VERIFY_PASS' "$second_raw" || die "second verifier PASS missing"
if grep -Fq "World '$WORLD_NAME' created!" "$second_raw"; then
  die "second boot unexpectedly recreated the world"
fi
fatal_scan "$second_raw"

second_summary="$(extract_marker "$second_raw" 'LMW_MV_VERIFY_PASS')"
second_meta="$(extract_marker "$second_raw" 'LMW_MV_WORLD_META')"
second_scan="$(extract_marker "$second_raw" 'LMW_MV_SCAN_PASS')"
second_uid="$(printf '%s\n' "$second_summary" | sed -n 's/.* uid=\([^ ]*\).*/\1/p')"
second_checksum="$(printf '%s\n' "$second_summary" | sed -n 's/.* checksum=\([^ ]*\).*/\1/p')"
[ "$first_summary" = "$second_summary" ] || die "first and second verifier summaries differ"
[ "$first_meta" = "$second_meta" ] || die "first and second world metadata differ"
[ "$first_scan" = "$second_scan" ] || die "first and second scan summaries differ"
[ "$first_uid" = "$second_uid" ] || die "world UUID changed across restart"
[ "$first_checksum" = "$second_checksum" ] || die "world checksum changed across restart"

{
  printf 'exact-commands:\n'
  printf '  mv version\n'
  printf '  mv list\n'
  printf '  mv info %s\n' "$WORLD_NAME"
  printf '  lmwit verify %s %s\n' "$WORLD_NAME" "$FIXED_SEED"
  printf '  save-all\n'
  printf '  stop\n\n'
  cat "$second_raw"
  printf '\nPASS: second boot auto-loaded the same world and verifier result.\n'
} > "$CHECK_DIR/multiverse-second-boot.txt"

{
  printf 'world: %s\n' "$WORLD_NAME"
  printf 'seed: %s\n' "$FIXED_SEED"
  printf 'first-meta: %s\n' "$first_meta"
  printf 'first-scan: %s\n' "$first_scan"
  printf 'second-meta: %s\n' "$second_meta"
  printf 'second-scan: %s\n' "$second_scan"
  grep 'LMW_MV_TERRAIN_PASS\|LMW_MV_GEOLOGY_ANCHORS_PASS\|LMW_MV_ORE_ANCHORS_PASS\|LMW_MV_Y11_PASS\|LMW_MV_BOUNDARIES_PASS\|LMW_MV_BIOME_PASS' "$first_raw"
  printf 'worlds-yml: generator, NORMAL environment, seed, and auto-load PASS\n'
  printf 'full-range: minY=-64 maxYExclusive=320 four chunks\n'
  printf 'forbidden-material-count: 0\n'
  printf 'PASS: fixed four-chunk full-height live world scan completed successfully.\n'
} > "$CHECK_DIR/multiverse-world-scan.txt"

[ "$paper_source_sha" = "$(sha256sum "$PAPER_JAR" | awk '{print $1}')" ] \
  || die "source Paper JAR changed"
[ "$eula_source_sha" = "$(sha256sum "$EULA_FILE" | awk '{print $1}')" ] \
  || die "source EULA changed"
[ "$multiverse_source_sha" = "$(sha256sum "$MULTIVERSE_JAR" | awk '{print $1}')" ] \
  || die "source Multiverse JAR changed"

{
  printf 'executed-at-utc: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'plugin-version: %s\n' "$EXPECTED_VERSION"
  printf 'paper-sha256: %s\n' "$paper_source_sha"
  printf 'multiverse-version: 5.7.2\n'
  printf 'multiverse-sha256: %s\n' "$multiverse_source_sha"
  printf 'verifier-sha256: %s\n' "$(sha256sum "$VERIFIER_JAR" | awk '{print $1}')"
  printf 'plugins: %s\n' "${plugin_jars[*]}"
  printf 'generator-list-command: mv generators list PASS\n'
  printf 'create-command: %s PASS\n' "$CREATE_COMMAND"
  printf 'first-uuid: %s\n' "$first_uid"
  printf 'first-checksum: %s\n' "$first_checksum"
  printf 'restart-autoload: PASS\n'
  printf 'second-uuid: %s\n' "$second_uid"
  printf 'second-checksum: %s\n' "$second_checksum"
  printf 'restart-equality: PASS\n'
  printf 'worlds-yml: PASS\n'
  printf 'source-paper-eula-multiverse-unchanged: PASS\n'
  printf 'stable-version: PASS\n'
  printf 'PASS: Phase 4A Multiverse integration smoke completed successfully.\n'
} > "$CHECK_DIR/multiverse-integration-smoke.txt"

rm -f "$first_raw" "$second_raw" "$metadata_file"
printf 'Multiverse integration smoke passed. Logs: %s\n' "$CHECK_DIR"
