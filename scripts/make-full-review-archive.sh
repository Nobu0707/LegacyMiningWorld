#!/usr/bin/env bash
set -euo pipefail

readonly PROJECT_NAME="legacyminingworld"
readonly PROJECT_DISPLAY_NAME="LegacyMiningWorld"
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

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/make-full-review-archive.sh
  ./scripts/make-full-review-archive.sh "expected HEAD subject substring"

Creates legacyminingworld-full-review-<SHA>-<timestamp>.tar.gz and updates
legacyminingworld-full-review-latest.tar.gz.
USAGE
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

is_forbidden_repo_path() {
  case "$1" in
    gradle/wrapper/gradle-wrapper.jar)
      return 1
      ;;
    .git/*|.gradle/*|build/*|server/*|out/*|.idea/*|.vscode/*|*.jar|*.class|*.log|*.tar.gz|*.zip|*.db|*.sqlite|*.tmp|*minecraft-1.16.5*|*server-mappings*|*decompile*|*decompiled*|.env|.env.*|secrets/*|secret/*|private/*|*credential*|*secret*|*password*|*.key|*.pem)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

expected_subject="${1:-}"
head_sha="$(git rev-parse HEAD 2>/dev/null)" || die "HEAD does not exist; commit the phase first"
head_short="$(git rev-parse --short HEAD)"
head_subject="$(git log -1 --pretty=%s)"
if [ -n "$expected_subject" ] && [[ "$head_subject" != *"$expected_subject"* ]]; then
  die "HEAD subject '$head_subject' does not contain '$expected_subject'"
fi

./scripts/run-review-checks.sh

version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ -n "$version" ] || die "legacyminingworld_version is not set"
for check_name in "${REQUIRED_REVIEW_CHECKS[@]}"; do
  [ -f "build/review-checks/$check_name" ] || die "missing review check log: $check_name"
done
grep -Fxq "version: $version" build/review-checks/jar-plugin-yml.txt \
  || die "built plugin.yml version does not match gradle.properties"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive_name="${PROJECT_NAME}-full-review-${head_short}-${timestamp}.tar.gz"
latest_name="${PROJECT_NAME}-full-review-latest.tar.gz"
archive_stage="$(mktemp -d "${TMPDIR:-/tmp}/${PROJECT_NAME}-full-review.XXXXXX")"
trap 'rm -rf "$archive_stage"' EXIT
mkdir -p "$archive_stage/meta" "$archive_stage/repo" "$archive_stage/checks"
: > "$archive_stage/meta/excluded-tracked-files.txt"

{
  printf 'Project: %s\n' "$PROJECT_DISPLAY_NAME"
  printf 'Archive: %s\n' "$archive_name"
  printf 'Created UTC: %s\n' "$timestamp"
  printf 'HEAD SHA: %s\n' "$head_sha"
  printf 'HEAD subject: %s\n' "$head_subject"
  printf 'Scope: safe tracked files at HEAD plus fresh review-check logs.\n'
  printf 'Allowed JAR exception: repo/gradle/wrapper/gradle-wrapper.jar\n'
  if [ -n "$expected_subject" ]; then
    printf 'Expected subject substring: %s\n' "$expected_subject"
  fi
} > "$archive_stage/meta/review-info.txt"

git log --oneline --decorate -n 50 > "$archive_stage/meta/git-log-oneline.txt"
git status --short > "$archive_stage/meta/git-status-short.txt"
git show --stat --oneline --decorate --no-renames HEAD > "$archive_stage/meta/head-stat.txt"
git ls-tree -r --name-only HEAD | sort > "$archive_stage/meta/repo-tree.txt"
git ls-tree -r --name-only HEAD | awk -F/ '{count[$1]++} END {for (path in count) print path, count[path]}' \
  | sort > "$archive_stage/meta/file-counts.txt"

forbidden_tracked=0
while IFS= read -r -d '' tracked_file; do
  if is_forbidden_repo_path "$tracked_file"; then
    printf 'Forbidden tracked path: %s\n' "$tracked_file" >> "$archive_stage/meta/excluded-tracked-files.txt"
    forbidden_tracked=1
    continue
  fi
  target="$archive_stage/repo/$tracked_file"
  mkdir -p "$(dirname "$target")"
  git show "HEAD:$tracked_file" > "$target"
done < <(git ls-tree -rz --name-only HEAD)

if [ "$forbidden_tracked" -ne 0 ]; then
  die "HEAD contains forbidden tracked paths; see the staged exclusion report"
fi

{
  printf 'Gradle compile classpath dependencies\n\n'
  ./gradlew --no-daemon dependencies --configuration compileClasspath
  printf '\nBuild declaration\n\n'
  sed -n '1,240p' build.gradle.kts
} > "$archive_stage/meta/dependency-summary.txt"

{
  printf 'Review check logs copied after a fresh run.\n\n'
  find build/review-checks -maxdepth 1 -type f -name '*.txt' -printf '%f\n' | sort
} > "$archive_stage/checks/review-check-files.txt"
while IFS= read -r -d '' check_log; do
  cp "$check_log" "$archive_stage/checks/$(basename "$check_log")"
done < <(find build/review-checks -maxdepth 1 -type f -name '*.txt' -print0 | sort -z)

if command -v rg >/dev/null 2>&1; then
  rg -n \
    -e 'ChunkGenerator|ChunkData|BlockPopulator|populate\(|LegacyUndergroundPopulator|LegacyOreApplicator|LegacyOreMaterialAdapter|LegacyOreBlockAccess|LimitedRegion|isInRegion|getType|setType|getWorld|getChunkAt|getBlockAt|WorldInfo|BiomeProvider|WorldCreator|getDefaultWorldGenerator|getDefaultPopulators|getPopulators|WorldInitEvent|generateNoise|generateSurface|generateBedrock|getBaseHeight|getFixedSpawnLocation|Biome\.PLAINS|shouldGenerateNoise|shouldGenerateSurface|shouldGenerateCaves|shouldGenerateDecorations|shouldGenerateStructures|shouldGenerateMobs|Material\.BEDROCK|Material\.STONE|Material\.DIRT|Material\.GRASS_BLOCK|Material\.GRANITE|Material\.DIORITE|Material\.ANDESITE|CAVE_AIR|VOID_AIR|LegacyGeology|LegacyVein|LegacyOre|COAL_ORE|IRON_ORE|GOLD_ORE|REDSTONE_ORE|DIAMOND_ORE|LAPIS_ORE|DepthAverage|baseline|spread|stableSalt|featureSeed|UNDERGROUND_ORES|placement|decoration seed|feature seed|source chunk|target chunk|GRANITE|DIORITE|ANDESITE|GRAVEL|Random|seed|scheduler|async|ThreadLocal|geology-smoke-anchors|ore-smoke-anchors|Y11|Multiverse' \
    "$archive_stage/repo" > "$archive_stage/checks/rg-review-signals.txt" || true
  rg -n -i \
    -e 'TODO|FIXME|HACK|XXX|SNAPSHOT|password|token|secret|credential' \
    "$archive_stage/repo" > "$archive_stage/checks/todo-fixme-scan.txt" || true
else
  printf 'rg not available; review signal scan skipped.\n' > "$archive_stage/checks/rg-review-signals.txt"
  printf 'rg not available; TODO/FIXME scan skipped.\n' > "$archive_stage/checks/todo-fixme-scan.txt"
fi

forbidden_staged=0
while IFS= read -r -d '' staged_file; do
  staged_path="${staged_file#"$archive_stage/repo/"}"
  if is_forbidden_repo_path "$staged_path"; then
    printf 'FAIL: %s\n' "$staged_path" >> "$archive_stage/checks/forbidden-files-scan.txt"
    forbidden_staged=1
  fi
done < <(find "$archive_stage/repo" -type f -print0)
[ "$forbidden_staged" -eq 0 ] || die "full review staging contains a forbidden file"
printf 'PASS: no forbidden paths found; gradle-wrapper.jar is the only allowed JAR.\n' \
  > "$archive_stage/checks/forbidden-files-scan.txt"

tar -C "$archive_stage" -czf "$repo_root/$archive_name" .
cp "$repo_root/$archive_name" "$repo_root/$latest_name"

if tar -tzf "$repo_root/$archive_name" \
    | grep -E '(^|/)server/|(^|/)build/|(^|/)\.gradle/|\.log$|\.zip$|\.tar\.gz$|\.class$|\.db$|\.sqlite$|(^|/)(secrets?|private)/|credential|password|\.key$|\.pem$' \
      >/dev/null; then
  die "full review archive contains a forbidden path"
fi

printf 'Created full review archive: %s\n' "$archive_name"
printf 'Updated latest archive: %s\n' "$latest_name"
