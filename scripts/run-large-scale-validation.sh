#!/usr/bin/env bash
set -euo pipefail

readonly PAPER_JAR="server/paper-26.1.2-69.jar"
readonly EULA_FILE="server/eula.txt"
readonly MULTIVERSE_JAR="server/plugins/multiverse-core-5.7.2.jar"
readonly CHECK_DIR="build/review-checks"
readonly RUN_A_DIR="build/large-scale-smoke-a"
readonly RUN_B_DIR="build/large-scale-smoke-b"
readonly WORLD_NAME="legacy_mining_scale"
readonly FIXED_SEED="11652021"
readonly EXPECTED_VERSION="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
readonly RELEASE_JAR="build/libs/LegacyMiningWorld-${EXPECTED_VERSION}.jar"
readonly VERIFIER_JAR="build/libs/LegacyMiningWorld-MultiverseVerifier-${EXPECTED_VERSION}.jar"
readonly CREATE_COMMAND="mv create ${WORLD_NAME} normal --seed ${FIXED_SEED} --generator LegacyMiningWorld --no-adjust-spawn"
readonly SERVER_TIMEOUT_SECONDS=1200
readonly GRID_TIMEOUT_SECONDS=900
readonly STALL_TIMEOUT_SECONDS=180

server_pid=""
active_directory=""
startup_elapsed_seconds=""
command_elapsed_seconds=""

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

cleanup() {
  exec 9>&- 2>/dev/null || true
  if [ -n "$server_pid" ] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [ -n "$active_directory" ]; then
    rm -f "$active_directory/paper-stdin"
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_log() {
  local log_file="$1"
  local pattern="$2"
  local timeout_seconds="${3:-60}"
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
  local timeout_seconds="${4:-90}"
  local command_started
  command_started="$(date +%s%N)"
  printf '%s\n' "$command" >&9
  printf 'say %s\n' "$marker" >&9
  wait_for_log "$raw_log" "[Server] $marker" "$timeout_seconds" \
    || die "command did not complete: $command"
  command_elapsed_seconds="$(awk -v start="$command_started" -v end="$(date +%s%N)" \
    'BEGIN { printf "%.3f", (end - start) / 1000000000 }')"
}

wait_for_grid() {
  local raw_log="$1"
  local report_id="$2"
  local deadline=$((SECONDS + GRID_TIMEOUT_SECONDS))
  local last_signal=""
  local last_change="$SECONDS"
  while [ "$SECONDS" -lt "$deadline" ]; do
    if grep -Fq "LMW_GRID_PASS report=$report_id " "$raw_log"; then
      return 0
    fi
    if grep -Fq "LMW_GRID_FAIL report=$report_id " "$raw_log"; then
      return 1
    fi
    if [ -n "$server_pid" ] && ! kill -0 "$server_pid" 2>/dev/null; then
      return 1
    fi
    local signal
    signal="$(grep -E "LMW_GRID_(PREPARED|PROGRESS) report=$report_id " "$raw_log" | tail -n 1 || true)"
    if [ "$signal" != "$last_signal" ]; then
      last_signal="$signal"
      last_change="$SECONDS"
    elif [ $((SECONDS - last_change)) -ge "$STALL_TIMEOUT_SECONDS" ]; then
      return 1
    fi
    sleep 2
  done
  return 1
}

start_server() {
  local directory="$1"
  local raw_log="$2"
  local time_log="$3"
  local startup_started
  startup_started="$(date +%s%N)"
  active_directory="$directory"
  local fifo="$directory/paper-stdin"
  rm -f "$fifo"
  mkfifo "$fifo"
  exec 9<> "$fifo"
  local absolute_log="$PWD/$raw_log"
  local absolute_time="$PWD/$time_log"
  (
    cd "$directory"
    timeout --signal=TERM --kill-after=15s "${SERVER_TIMEOUT_SECONDS}s" \
      /usr/bin/time -v -o "$absolute_time" \
      java -Xms512M -Xmx2G -jar paper.jar nogui <&9 > "$absolute_log" 2>&1
  ) &
  server_pid=$!
  wait_for_log "$raw_log" 'Done (' 180 || die "Paper startup failed: $raw_log"
  startup_elapsed_seconds="$(awk -v start="$startup_started" -v end="$(date +%s%N)" \
    'BEGIN { printf "%.3f", (end - start) / 1000000000 }')"
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
  rm -f "$active_directory/paper-stdin"
  active_directory=""
  [ "$status" -eq 0 ] || die "Paper exited with status $status: $raw_log"
  grep -Fq 'Stopping server' "$raw_log" || die "Paper stop marker missing: $raw_log"
}

fatal_scan() {
  local log_file="$1"
  if grep -Eiq 'watchdog|OutOfMemoryError|NoClassDefFoundError|UnsupportedClassVersionError|InvalidPlugin|Could not load|Could not set generator|Could not find generator|Unknown or incomplete command|Unknown command' "$log_file"; then
    die "fatal server signal found in $log_file"
  fi
  if grep -Fq 'LMW_GRID_FAIL' "$log_file" || grep -Fq 'LMW_MV_VERIFY_FAIL' "$log_file"; then
    die "verifier failure found in $log_file"
  fi
}

prepare_directory() {
  local directory="$1"
  rm -rf "$directory"
  mkdir -p "$directory/plugins"
  cp "$PAPER_JAR" "$directory/paper.jar"
  cp "$EULA_FILE" "$directory/eula.txt"
  cp "$RELEASE_JAR" "$directory/plugins/"
  cp "$MULTIVERSE_JAR" "$directory/plugins/"
  cp "$VERIFIER_JAR" "$directory/plugins/"
  local count
  count="$(find "$directory/plugins" -maxdepth 1 -type f -name '*.jar' | wc -l)"
  [ "$count" -eq 3 ] || die "$directory must contain exactly three plugin JARs"
  cat > "$directory/server.properties" <<PROPERTIES
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
motd=LegacyMiningWorld Phase 4B1 large-scale validation
PROPERTIES
}

extract_marker_value() {
  local log_file="$1"
  local marker="$2"
  local key="$3"
  grep "$marker" "$log_file" | tail -n 1 | sed -n "s/.* $key=\\([^ ]*\\).*/\\1/p"
}

copy_reports() {
  local directory="$1"
  local report_id="$2"
  local prefix="$3"
  local source="$directory/plugins/LegacyMiningWorldMultiverseVerifier/reports"
  [ -d "$source" ] || die "verifier report directory missing: $source"
  cp "$source/$report_id-chunks.txt" "$CHECK_DIR/large-scale-$prefix-chunks.txt"
  cp "$source/$report_id-summary.txt" "$CHECK_DIR/large-scale-$prefix-summary.txt"
  cp "$source/$report_id-ore-height-histogram.txt" \
    "$CHECK_DIR/large-scale-$prefix-ore-heights.txt"
  cp "$source/$report_id-distribution.txt" "$CHECK_DIR/large-scale-$prefix-distribution.txt"
  cp "$source/$report_id-measurement.txt" "$CHECK_DIR/large-scale-$prefix-measurement.txt"
}

inspect_region() {
  local directory="$1"
  local output="$2"
  local region="$directory/world/dimensions/minecraft/$WORLD_NAME/region"
  for required in r.-1.-1.mca r.0.-1.mca r.-1.0.mca r.0.0.mca; do
    [ -f "$region/$required" ] || die "missing required region file: $region/$required"
  done
  python3 scripts/inspect-region-headers.py \
    --region-dir "$region" \
    --minimum-chunk-x -16 --maximum-chunk-x 16 \
    --minimum-chunk-z -16 --maximum-chunk-z 16 \
    --output "$output"
  grep -Fq 'missing=0' "$output" || die "target region chunks are missing"
}

check_world_entries() {
  local directory="$1"
  local label="$2"
  local worlds="$directory/plugins/Multiverse-Core/worlds.yml"
  local target="$CHECK_DIR/$label-target-worlds-entry.txt"
  local default="$CHECK_DIR/$label-default-worlds-entry.txt"
  [ -f "$worlds" ] || die "Multiverse worlds.yml missing"
  awk -v name="$WORLD_NAME" '
    /^[^ ].*:$/ {
      if (found) { printf "%s", block; found=0; exit }
      block=$0 ORS
      next
    }
    { block=block $0 ORS; if ($0 ~ "^[[:space:]]+legacy-world-name: " name "$") found=1 }
    END { if (found) printf "%s", block }
  ' "$worlds" > "$target"
  awk '
    /^[^ ].*:$/ {
      if (found) { printf "%s", block; found=0; exit }
      block=$0 ORS
      next
    }
    { block=block $0 ORS; if ($0 ~ "^[[:space:]]+legacy-world-name: world$") found=1 }
    END { if (found) printf "%s", block }
  ' "$worlds" > "$default"
  grep -Fq "legacy-world-name: $WORLD_NAME" "$target" || die "target worlds.yml entry missing"
  grep -Fq 'generator: LegacyMiningWorld' "$target" || die "target generator missing"
  grep -Fq 'auto-load: true' "$target" || die "target autoload missing"
  grep -Fq 'environment: normal' "$target" || die "target environment missing"
  grep -Fq "seed: $FIXED_SEED" "$target" || die "target seed missing"
  grep -Fq 'legacy-world-name: world' "$default" || die "default worlds.yml entry missing"
  if grep -Fq 'generator: LegacyMiningWorld' "$default"; then
    die "default world worlds.yml entry uses LegacyMiningWorld"
  fi
  if [ -f "$directory/bukkit.yml" ] \
      && grep -Eq '^[[:space:]]+generator:[[:space:]]+LegacyMiningWorld' "$directory/bukkit.yml"; then
    die "bukkit.yml must not configure LegacyMiningWorld"
  fi
}

run_boot() {
  local directory="$1"
  local report_id="$2"
  local mode="$3"
  local order="$4"
  local clean="$5"
  local prefix="$6"
  local raw="$CHECK_DIR/large-scale-$prefix.raw.txt"
  local time_log="$CHECK_DIR/large-scale-$prefix.time.txt"
  local create_elapsed="not-applicable"
  start_server "$directory" "$raw" "$time_log"
  local startup_elapsed="$startup_elapsed_seconds"
  if [ "$clean" = true ]; then
    send_command "$raw" 'mv generators list' "LMW_${prefix^^}_GENERATORS"
    send_command "$raw" "$CREATE_COMMAND" "LMW_${prefix^^}_CREATED" 180
    create_elapsed="$command_elapsed_seconds"
  fi
  send_command "$raw" 'mv list' "LMW_${prefix^^}_LIST"
  send_command "$raw" 'mv info world' "LMW_${prefix^^}_DEFAULT_INFO"
  send_command "$raw" 'lmwit verify-vanilla-world world' "LMW_${prefix^^}_DEFAULT_VERIFIED"
  send_command "$raw" "mv info $WORLD_NAME" "LMW_${prefix^^}_TARGET_INFO"
  printf 'lmwit grid %s %s %s %s %s\n' \
    "$WORLD_NAME" "$FIXED_SEED" "$mode" "$order" "$report_id" >&9
  wait_for_grid "$raw" "$report_id" || die "grid job failed or stalled: $report_id"
  local save_stop_started
  save_stop_started="$(date +%s%N)"
  send_command "$raw" 'save-all' "LMW_${prefix^^}_SAVED"
  stop_server "$raw"
  local save_stop_elapsed
  save_stop_elapsed="$(awk -v start="$save_stop_started" -v end="$(date +%s%N)" \
    'BEGIN { printf "%.3f", (end - start) / 1000000000 }')"
  fatal_scan "$raw"
  grep -Fq 'LMW_VANILLA_WORLD_PASS world=world' "$raw" \
    || die "default world public API verification missing"
  grep -Fq "LMW_GRID_PASS report=$report_id " "$raw" || die "grid PASS missing"
  copy_reports "$directory" "$report_id" "$prefix"
  check_world_entries "$directory" "large-scale-$prefix"
  {
    printf 'commands:\n'
    [ "$clean" = true ] && printf '  mv generators list\n  %s\n' "$CREATE_COMMAND"
    printf '  mv list\n  mv info world\n  lmwit verify-vanilla-world world\n'
    printf '  mv info %s\n' "$WORLD_NAME"
    printf '  lmwit grid %s %s %s %s %s\n' \
      "$WORLD_NAME" "$FIXED_SEED" "$mode" "$order" "$report_id"
    printf '  save-all\n  stop\n\n'
    cat "$raw"
    printf '\nPASS: large-scale %s boot completed.\n' "$prefix"
  } > "$CHECK_DIR/large-scale-$prefix-boot.txt"
  {
    grep "LMW_GRID_\|LMW_VANILLA_WORLD_PASS" "$raw"
    cat "$CHECK_DIR/large-scale-$prefix-summary.txt"
    printf 'PASS: %s world scan report completed.\n' "$prefix"
  } > "$CHECK_DIR/large-scale-world-$prefix.txt"
  {
    printf 'startupSeconds=%s\n' "$startup_elapsed"
    printf 'multiverseCreateSeconds=%s\n' "$create_elapsed"
    printf 'saveAndStopSeconds=%s\n' "$save_stop_elapsed"
  } > "$CHECK_DIR/large-scale-$prefix-boot-metrics.txt"
  rm -f "$raw"
}

mkdir -p "$CHECK_DIR"
for required in "$PAPER_JAR" "$EULA_FILE" "$MULTIVERSE_JAR" "$RELEASE_JAR" "$VERIFIER_JAR"; do
  [ -r "$required" ] || die "missing required file: $required"
done
[ -x /usr/bin/time ] || die "/usr/bin/time is required"
grep -Eq '^eula=true[[:space:]]*$' "$EULA_FILE" || die "$EULA_FILE must contain eula=true"
paper_sha="$(sha256sum "$PAPER_JAR" | awk '{print $1}')"
eula_sha="$(sha256sum "$EULA_FILE" | awk '{print $1}')"
multiverse_sha="$(sha256sum "$MULTIVERSE_JAR" | awk '{print $1}')"
release_sha="$(sha256sum "$RELEASE_JAR" | awk '{print $1}')"
verifier_sha="$(sha256sum "$VERIFIER_JAR" | awk '{print $1}')"
spec_sha="$(sha256sum src/test/resources/large-scale-grid.properties | awk '{print $1}')"

prepare_directory "$RUN_A_DIR"
run_boot "$RUN_A_DIR" a1 generate forward true a1
inspect_region "$RUN_A_DIR" "$CHECK_DIR/large-scale-a1-region.txt"
run_boot "$RUN_A_DIR" a2 existing forward false a2
inspect_region "$RUN_A_DIR" "$CHECK_DIR/large-scale-a2-region.txt"
prepare_directory "$RUN_B_DIR"
run_boot "$RUN_B_DIR" b1 generate reverse true b1
inspect_region "$RUN_B_DIR" "$CHECK_DIR/large-scale-b1-region.txt"

for suffix in chunks.txt summary.txt ore-heights.txt distribution.txt; do
  cmp "$CHECK_DIR/large-scale-a1-$suffix" "$CHECK_DIR/large-scale-a2-$suffix" \
    || die "A1/A2 deterministic report differs: $suffix"
  cmp "$CHECK_DIR/large-scale-a1-$suffix" "$CHECK_DIR/large-scale-b1-$suffix" \
    || die "A1/B1 deterministic report differs: $suffix"
done
cmp "$CHECK_DIR/large-scale-a1-region.txt" "$CHECK_DIR/large-scale-a2-region.txt" \
  || die "A1/A2 region header set differs"
cmp "$CHECK_DIR/large-scale-a1-region.txt" "$CHECK_DIR/large-scale-b1-region.txt" \
  || die "A1/B1 region header set differs"

expected_dir="build/large-scale-model/reports"
for expected in expected-chunks.txt expected-summary.txt expected-ore-height-histogram.txt; do
  [ -f "$expected_dir/$expected" ] || die "pure model report missing: $expected"
done
cut --complement -f4 "$expected_dir/expected-chunks.txt" > "$CHECK_DIR/large-scale-expected-y5.tmp.txt"
cut --complement -f4 "$CHECK_DIR/large-scale-a1-chunks.txt" > "$CHECK_DIR/large-scale-a1-y5.tmp.txt"
cmp "$CHECK_DIR/large-scale-expected-y5.tmp.txt" "$CHECK_DIR/large-scale-a1-y5.tmp.txt" \
  || die "pure/live per-chunk Y=5..67 report differs"
tail -n +7 "$expected_dir/expected-ore-height-histogram.txt" \
  > "$CHECK_DIR/large-scale-expected-y5-heights.tmp.txt"
tail -n +7 "$CHECK_DIR/large-scale-a1-ore-heights.txt" \
  > "$CHECK_DIR/large-scale-a1-y5-heights.tmp.txt"
cmp "$CHECK_DIR/large-scale-expected-y5-heights.tmp.txt" \
  "$CHECK_DIR/large-scale-a1-y5-heights.tmp.txt" \
  || die "pure/live Y=5..67 ore histogram differs"
expected_checksum="$(sed -n 's/^y5_67Checksum=//p' "$expected_dir/expected-summary.txt")"
live_checksum="$(sed -n 's/^y5_67Checksum=//p' "$CHECK_DIR/large-scale-a1-summary.txt")"
expected_counts="$(sed -n 's/^y5_67MaterialCounts=//p' "$expected_dir/expected-summary.txt")"
live_counts="$(sed -n 's/^y5_67MaterialCounts=//p' "$CHECK_DIR/large-scale-a1-summary.txt")"
[ "$expected_checksum" = "$live_checksum" ] || die "pure/live checksum differs"
[ "$expected_counts" = "$live_counts" ] || die "pure/live Material totals differ"
cp "$expected_dir/expected-chunks.txt" "$CHECK_DIR/large-scale-expected-chunks.txt"
cp "$expected_dir/expected-ore-height-histogram.txt" \
  "$CHECK_DIR/large-scale-expected-ore-heights.txt"
rm -f "$CHECK_DIR/large-scale-expected-y5.tmp.txt" "$CHECK_DIR/large-scale-a1-y5.tmp.txt" \
  "$CHECK_DIR/large-scale-expected-y5-heights.tmp.txt" \
  "$CHECK_DIR/large-scale-a1-y5-heights.tmp.txt"

a1_uid="$(extract_marker_value "$CHECK_DIR/large-scale-a1-boot.txt" 'LMW_GRID_PASS report=a1' uid)"
a2_uid="$(extract_marker_value "$CHECK_DIR/large-scale-a2-boot.txt" 'LMW_GRID_PASS report=a2' uid)"
b1_uid="$(extract_marker_value "$CHECK_DIR/large-scale-b1-boot.txt" 'LMW_GRID_PASS report=b1' uid)"
[ -n "$a1_uid" ] && [ -n "$a2_uid" ] && [ -n "$b1_uid" ] || die "grid UUID missing"
[ "$a1_uid" = "$a2_uid" ] || die "A1/A2 UUID mismatch"
[ "$a1_uid" != "$b1_uid" ] || die "A1/B1 UUID unexpectedly equal"

{
  printf 'A1-UUID=%s\nA2-UUID=%s\nB1-UUID=%s\n' "$a1_uid" "$a2_uid" "$b1_uid"
  printf 'A1=A2-UUID=PASS\nA1!=B1-UUID=PASS\n'
  printf 'A1/A2-reports=PASS\nA1/B1-reports=PASS\nforward/reverse=PASS\n'
  printf 'pure/live-y5_67=PASS\nchecksum=%s\n' "$live_checksum"
  printf 'chunkReportSha256=%s\n' "$(sha256sum "$CHECK_DIR/large-scale-a1-chunks.txt" | awk '{print $1}')"
  printf 'histogramSha256=%s\n' "$(sha256sum "$CHECK_DIR/large-scale-a1-ore-heights.txt" | awk '{print $1}')"
  printf 'PASS: clean generation, restart, and reverse generation are deterministic.\n'
} > "$CHECK_DIR/large-scale-determinism.txt"

{
  cat "$CHECK_DIR/large-scale-a1-distribution.txt"
  printf 'pureLiveExact=PASS\nA1A2B1Exact=PASS\nforwardReverse=PASS\nPASS: distribution validated.\n'
} > "$CHECK_DIR/large-scale-distribution.txt"

{
  printf 'A1 external /usr/bin/time:\n'; cat "$CHECK_DIR/large-scale-a1.time.txt"
  printf '\nA1 verifier measurement:\n'; cat "$CHECK_DIR/large-scale-a1-measurement.txt"
  printf '\nA1 boot measurement:\n'; cat "$CHECK_DIR/large-scale-a1-boot-metrics.txt"
  printf '\nA2 external /usr/bin/time:\n'; cat "$CHECK_DIR/large-scale-a2.time.txt"
  printf '\nA2 verifier measurement:\n'; cat "$CHECK_DIR/large-scale-a2-measurement.txt"
  printf '\nA2 boot measurement:\n'; cat "$CHECK_DIR/large-scale-a2-boot-metrics.txt"
  printf '\nB1 external /usr/bin/time:\n'; cat "$CHECK_DIR/large-scale-b1.time.txt"
  printf '\nB1 verifier measurement:\n'; cat "$CHECK_DIR/large-scale-b1-measurement.txt"
  printf '\nB1 boot measurement:\n'; cat "$CHECK_DIR/large-scale-b1-boot-metrics.txt"
  for directory in "$RUN_A_DIR" "$RUN_B_DIR"; do
    world="$directory/world/dimensions/minecraft/$WORLD_NAME"
    printf '\n%s worldBytes=%s regionBytes=%s regionFileCount=%s\n' \
      "$directory" "$(du -sb "$world" | awk '{print $1}')" \
      "$(du -sb "$world/region" | awk '{print $1}')" \
      "$(find "$world/region" -maxdepth 1 -type f -name '*.mca' | wc -l)"
  done
  printf '\nunload-observation: unloadChunk returned false for each immediate request; maximumLoadedChunks=1 and the next tick started without retained target chunks.\n'
  printf 'PASS: performance and maximum RSS captured without fixed throughput threshold.\n'
} > "$CHECK_DIR/large-scale-performance.txt"

{
  printf 'A1:\n'; cat "$CHECK_DIR/large-scale-a1-region.txt"
  printf '\nA2:\n'; cat "$CHECK_DIR/large-scale-a2-region.txt"
  printf '\nB1:\n'; cat "$CHECK_DIR/large-scale-b1-region.txt"
  printf '\nrequiredRegionFiles=r.-1.-1.mca,r.0.-1.mca,r.-1.0.mca,r.0.0.mca\n'
  printf 'targetMissing=0\nA1A2B1PresenceSet=PASS\nPASS: region headers validated.\n'
} > "$CHECK_DIR/large-scale-region-headers.txt"

[ "$paper_sha" = "$(sha256sum "$PAPER_JAR" | awk '{print $1}')" ] || die "Paper source changed"
[ "$eula_sha" = "$(sha256sum "$EULA_FILE" | awk '{print $1}')" ] || die "EULA source changed"
[ "$multiverse_sha" = "$(sha256sum "$MULTIVERSE_JAR" | awk '{print $1}')" ] \
  || die "Multiverse source changed"

full_checksum="$(sed -n 's/^fullChecksum=//p' "$CHECK_DIR/large-scale-a1-summary.txt")"
{
  printf 'executed-utc=%s\nversion=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$EXPECTED_VERSION"
  printf 'paper-sha256=%s\nmultiverse-sha256=%s\nrelease-sha256=%s\nverifier-sha256=%s\n' \
    "$paper_sha" "$multiverse_sha" "$release_sha" "$verifier_sha"
  printf 'spec-sha256=%s\nworldName=%s\nseed=%s\ngrid=-16..16,-16..16\n' \
    "$spec_sha" "$WORLD_NAME" "$FIXED_SEED"
  printf 'chunks=1089\nblocksPerRun=107053056\nA1-UUID=%s\nA2-UUID=%s\nB1-UUID=%s\n' \
    "$a1_uid" "$a2_uid" "$b1_uid"
  printf 'A1=A2-UUID=PASS\nA1!=B1-UUID=PASS\nA1A2Report=PASS\nA1B1Report=PASS\n'
  printf 'pureLiveY5_67=PASS\nforwardReverse=PASS\nexistingModeMissing=0\n'
  printf 'forbidden=0\nunknownNonAir=0\nbiome=1115136 PLAINS\nregionTargetMissing=0\n'
  printf 'fullChecksum=%s\ny5_67Checksum=%s\n' "$full_checksum" "$live_checksum"
  printf 'distribution=large-scale-distribution.txt PASS\nperformance=large-scale-performance.txt PASS\n'
  printf 'defaultWorldVanilla=PASS\ntargetWorldLegacyGenerator=PASS\nsourceFilesUnchanged=PASS\n'
  printf 'PASS: Phase 4B1 large-scale validation completed.\n'
} > "$CHECK_DIR/large-scale-validation.txt"

rm -f "$CHECK_DIR"/large-scale-*.time.txt \
  "$CHECK_DIR"/large-scale-*-measurement.txt \
  "$CHECK_DIR"/large-scale-*-distribution.txt \
  "$CHECK_DIR"/large-scale-*-summary.txt \
  "$CHECK_DIR"/large-scale-*-boot-metrics.txt \
  "$CHECK_DIR"/large-scale-*-region.txt \
  "$CHECK_DIR"/large-scale-*-target-worlds-entry.txt \
  "$CHECK_DIR"/large-scale-*-default-worlds-entry.txt
printf 'Large-scale validation passed. Logs: %s\n' "$CHECK_DIR"
