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
max-players=1
motd=LegacyMiningWorld Phase 0 smoke
spawn-protection=0
view-distance=2
simulation-distance=2
network-compression-threshold=-1
PROPERTIES

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

if ! grep -Fq "LegacyMiningWorld ${expected_version} Phase 0 foundation loaded." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onLoad confirmation is missing from Paper log"
fi
if ! grep -Fq "LegacyMiningWorld ${expected_version} enabled; the world generator is not implemented in Phase 0." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onEnable confirmation is missing from Paper log"
fi
if ! grep -Fq "LegacyMiningWorld ${expected_version} disabled." \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "plugin onDisable confirmation is missing from Paper log"
fi

if grep -Eiq 'SEVERE|Exception|Could not load|InvalidPlugin|NoClassDefFoundError|UnsupportedClassVersionError|Caused by:' \
    "$temp_dir/local-paper-smoke.txt"; then
  copy_log local-paper-smoke.txt
  die "Paper smoke log contains a fatal error signal"
fi

[ "$paper_source_sha" = "$(sha256sum "$PAPER_JAR" | awk '{print $1}')" ] \
  || die "source Paper JAR changed during smoke test"
[ "$eula_source_sha" = "$(sha256sum "$EULA_FILE" | awk '{print $1}')" ] \
  || die "source EULA file changed during smoke test"

{
  printf '\nPASS: LegacyMiningWorld %s loaded, enabled, and disabled cleanly.\n' "$expected_version"
  printf 'PASS: no SEVERE, Exception, Could not load, InvalidPlugin, or class-loading failure was found.\n'
} >> "$temp_dir/local-paper-smoke.txt"
copy_log local-paper-smoke.txt

printf 'Review checks passed. Logs: %s\n' "$CHECK_DIR"
