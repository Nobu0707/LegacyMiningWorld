#!/usr/bin/env bash
set -euo pipefail

readonly PROJECT_NAME="legacyminingworld"
readonly PROJECT_DISPLAY_NAME="LegacyMiningWorld"
readonly EMPTY_TREE_SHA="4b825dc642cb6eb9a060e54bf8d69288fbee4904"
readonly REQUIRED_VERSION_CHECK="build/review-checks/jar-plugin-yml.txt"
readonly REQUIRED_REVIEW_CHECKS=(
  git-diff-check.txt
  gradle-test.txt
  geology-engine-tests.txt
  geology-adapter-tests.txt
  ore-engine-tests.txt
  ore-adapter-tests.txt
  multiverse-verifier-tests.txt
  large-scale-model-tests.txt
  large-scale-verifier-tests.txt
  region-header-tool-tests.txt
  gradle-build.txt
  local-paper-smoke.txt
  geology-world-smoke.txt
  ore-world-smoke.txt
  release-artifacts.txt
  jar-plugin-yml.txt
  jar-contents.txt
  verifier-jar-contents.txt
  verify-built-jar-version.txt
  multiverse-jar-inspection.txt
  multiverse-first-boot.txt
  multiverse-second-boot.txt
  multiverse-world-scan.txt
  multiverse-integration-smoke.txt
  large-scale-a1-boot.txt
  large-scale-a2-boot.txt
  large-scale-b1-boot.txt
  large-scale-world-a1.txt
  large-scale-world-a2.txt
  large-scale-world-b1.txt
  large-scale-determinism.txt
  large-scale-distribution.txt
  large-scale-performance.txt
  large-scale-region-headers.txt
  large-scale-validation.txt
  large-scale-expected-chunks.txt
  large-scale-a1-chunks.txt
  large-scale-a2-chunks.txt
  large-scale-b1-chunks.txt
  large-scale-expected-ore-heights.txt
  large-scale-a1-ore-heights.txt
  large-scale-a2-ore-heights.txt
  large-scale-b1-ore-heights.txt
)

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/make-review-archive.sh
  ./scripts/make-review-archive.sh "expected HEAD subject substring"

Creates legacyminingworld-review-<SHA>-<timestamp>.tar.gz and updates
legacyminingworld-review-latest.tar.gz.
USAGE
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

expected_version() {
  sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1
}

sanitize_name() {
  printf '%s' "$1" | sed 's#[^A-Za-z0-9._-]#_#g'
}

is_excluded_path() {
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

version="$(expected_version)"
[ -n "$version" ] || die "legacyminingworld_version is not set"
[ -f "$REQUIRED_VERSION_CHECK" ] || die "missing required check log: $REQUIRED_VERSION_CHECK"
for check_name in "${REQUIRED_REVIEW_CHECKS[@]}"; do
  [ -f "build/review-checks/$check_name" ] || die "missing review check log: $check_name"
done
grep -Fxq "version: $version" "$REQUIRED_VERSION_CHECK" \
  || die "$REQUIRED_VERSION_CHECK does not contain version: $version"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive_name="${PROJECT_NAME}-review-${head_short}-${timestamp}.tar.gz"
latest_name="${PROJECT_NAME}-review-latest.tar.gz"
archive_stage="$(mktemp -d "${TMPDIR:-/tmp}/${PROJECT_NAME}-review.XXXXXX")"
trap 'rm -rf "$archive_stage"' EXIT

mkdir -p \
  "$archive_stage/meta" \
  "$archive_stage/diff" \
  "$archive_stage/file-diffs" \
  "$archive_stage/files" \
  "$archive_stage/working-file-diffs" \
  "$archive_stage/working-files" \
  "$archive_stage/checks"
: > "$archive_stage/meta/excluded-files.txt"

if parent_sha="$(git rev-parse --verify HEAD^ 2>/dev/null)"; then
  diff_base="$parent_sha"
else
  diff_base="$EMPTY_TREE_SHA"
fi

{
  printf 'Project: %s\n' "$PROJECT_DISPLAY_NAME"
  printf 'Archive: %s\n' "$archive_name"
  printf 'Created UTC: %s\n' "$timestamp"
  printf 'HEAD SHA: %s\n' "$head_sha"
  printf 'HEAD subject: %s\n' "$head_subject"
  printf 'Diff base: %s\n' "$diff_base"
  if [ -n "$expected_subject" ]; then
    printf 'Expected subject substring: %s\n' "$expected_subject"
  fi
} > "$archive_stage/meta/review-info.txt"

git log --oneline --decorate -n 30 > "$archive_stage/meta/git-log-oneline.txt"
git status --short > "$archive_stage/meta/git-status-short.txt"
git show --stat --oneline --decorate --no-renames HEAD > "$archive_stage/meta/head-stat.txt"
git diff-tree --root --no-commit-id --name-status -r HEAD > "$archive_stage/meta/head-name-status.txt"
git diff-tree --root --no-commit-id --name-only -r HEAD > "$archive_stage/meta/changed-files.txt"
git show --format=fuller --patch --no-ext-diff --no-renames HEAD > "$archive_stage/diff/head-full-diff.txt"
git diff --no-ext-diff --no-renames > "$archive_stage/diff/working-tree-diff.txt"
git diff --cached --no-ext-diff --no-renames > "$archive_stage/diff/index-diff.txt"
git ls-files -m -d -o --exclude-standard > "$archive_stage/meta/working-tree-files.txt"

{
  git diff --check "$diff_base" HEAD
  git diff --check
  git diff --cached --check
  printf 'PASS: committed, working-tree, and index diff checks passed.\n'
} > "$archive_stage/checks/git-diff-check.txt"

while IFS= read -r -d '' changed_file; do
  if is_excluded_path "$changed_file"; then
    printf 'Excluded from HEAD snapshot: %s\n' "$changed_file" >> "$archive_stage/meta/excluded-files.txt"
    continue
  fi
  safe_name="$(sanitize_name "$changed_file")"
  git show --format= --patch --no-ext-diff --no-renames HEAD -- "$changed_file" \
    > "$archive_stage/file-diffs/${safe_name}.diff.txt" || true
  target="$archive_stage/files/$changed_file"
  mkdir -p "$(dirname "$target")"
  if git cat-file -e "HEAD:$changed_file" 2>/dev/null; then
    git show "HEAD:$changed_file" > "$target"
  else
    printf 'Deleted in HEAD: %s\n' "$changed_file" > "${target}.deleted.txt"
  fi
done < <(git diff-tree --root --no-commit-id --name-only -r -z HEAD)

while IFS= read -r -d '' working_file; do
  if is_excluded_path "$working_file"; then
    printf 'Excluded from working snapshot: %s\n' "$working_file" >> "$archive_stage/meta/excluded-files.txt"
    continue
  fi
  safe_name="$(sanitize_name "$working_file")"
  git diff --no-ext-diff --no-renames -- "$working_file" \
    > "$archive_stage/working-file-diffs/${safe_name}.diff.txt" || true
  target="$archive_stage/working-files/$working_file"
  mkdir -p "$(dirname "$target")"
  if [ -f "$working_file" ]; then
    cp "$working_file" "$target"
  elif [ ! -e "$working_file" ]; then
    printf 'Deleted in working tree: %s\n' "$working_file" > "${target}.deleted.txt"
  fi
done < <(git ls-files -m -d -o --exclude-standard -z)

{
  printf '%s review signal search\n\n' "$PROJECT_DISPLAY_NAME"
  if command -v rg >/dev/null 2>&1; then
    rg -n --hidden \
      --glob '!.git/**' --glob '!build/**' --glob '!.gradle/**' --glob '!server/**' \
      --glob '!*.jar' --glob '!*.log' --glob '!*.tar.gz' --glob '!*.zip' \
      -e 'ChunkGenerator|ChunkData|BlockPopulator|populate\(|LegacyUndergroundPopulator|LegacyOreApplicator|LegacyOreMaterialAdapter|LegacyOreBlockAccess|LimitedRegion|isInRegion|getType|setType|getWorld|getChunkAt|getBlockAt|WorldInfo|BiomeProvider|WorldCreator|getDefaultWorldGenerator|getDefaultPopulators|getPopulators|WorldInitEvent|generateNoise|generateSurface|generateBedrock|getBaseHeight|getFixedSpawnLocation|Biome\.PLAINS|shouldGenerateNoise|shouldGenerateSurface|shouldGenerateCaves|shouldGenerateDecorations|shouldGenerateStructures|shouldGenerateMobs|Material\.BEDROCK|Material\.STONE|Material\.DIRT|Material\.GRASS_BLOCK|Material\.GRANITE|Material\.DIORITE|Material\.ANDESITE|CAVE_AIR|VOID_AIR|LegacyGeology|LegacyVein|LegacyOre|COAL_ORE|IRON_ORE|GOLD_ORE|REDSTONE_ORE|DIAMOND_ORE|LAPIS_ORE|DepthAverage|baseline|spread|stableSalt|featureSeed|UNDERGROUND_ORES|placement|decoration seed|feature seed|source chunk|target chunk|GRANITE|DIORITE|ANDESITE|GRAVEL|Random|seed|scheduler|async|ThreadLocal|geology-smoke-anchors|ore-smoke-anchors|Y11|Multiverse|multiverse-core-5\.7\.2|mv generators|mv create|LegacyMiningWorldMultiverseVerifier|ChunkSnapshot|world UUID|autoload|worlds\.yml|multiverse-smoke|forbidden material|Y5\.\.67|large-scale|grid|1089|107053056|generate|existing|forward|reverse|fullChecksum|region header|Maximum resident set size|runTaskTimer|unloadChunk|isChunkGenerated|verify-vanilla-world|large-scale-grid\.properties' \
      . || true
  else
    printf 'rg not available; review signal scan skipped.\n'
  fi
} > "$archive_stage/checks/rg-review-signals.txt"

{
  printf 'Review check logs copied after a fresh run.\n\n'
  find build/review-checks -maxdepth 1 -type f -name '*.txt' -printf '%f\n' | sort
} > "$archive_stage/checks/review-check-files.txt"
while IFS= read -r -d '' check_log; do
  cp "$check_log" "$archive_stage/checks/$(basename "$check_log")"
done < <(find build/review-checks -maxdepth 1 -type f -name '*.txt' -print0 | sort -z)

tar -C "$archive_stage" -czf "$repo_root/$archive_name" .
cp "$repo_root/$archive_name" "$repo_root/$latest_name"

printf 'Created review archive: %s\n' "$archive_name"
printf 'Updated latest archive: %s\n' "$latest_name"
