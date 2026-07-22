#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_VERSION="1.0.1"
readonly RELEASE_DIRECTORY="build/release"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"
cd "$repo_root"

version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[ "$version" = "$EXPECTED_VERSION" ] || die "expected version $EXPECTED_VERSION, found $version"
[ -s LICENSE ] || die "root LICENSE is missing"
for document in README.md CHANGELOG.md docs/licensing.md docs/installation.md docs/operations.md \
    docs/stable-release.md docs/user-acceptance-checklist.md; do
  [ -s "$document" ] || die "missing release document: $document"
done

./gradlew --no-daemon jar -x test >/dev/null
built_jar="build/libs/LegacyMiningWorld-${version}.jar"
[ -r "$built_jar" ] || die "production JAR missing: $built_jar"
[ "$(find build/libs -maxdepth 1 -type f -name 'LegacyMiningWorld-*.jar' ! -name '*MultiverseVerifier*' | wc -l)" -eq 1 ] \
  || die "expected exactly one production JAR"
[ "$(unzip -p "$built_jar" plugin.yml | sed -n 's/^version:[[:space:]]*//p' | tail -n 1)" = "$version" ] \
  || die "JAR metadata version mismatch"
[ "$(jar tf "$built_jar" | grep -Fxc 'META-INF/LICENSE')" -eq 1 ] || die "JAR LICENSE entry is not unique"
cmp LICENSE <(unzip -p "$built_jar" META-INF/LICENSE) || die "JAR LICENSE differs from root"
jar_sha="$(sha256sum "$built_jar" | awk '{print $1}')"

rm -rf "$RELEASE_DIRECTORY"
mkdir -p "$RELEASE_DIRECTORY" "$CHECK_DIRECTORY"
stage_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-release.XXXXXX")"
extract_parent="$(mktemp -d "${TMPDIR:-/tmp}/legacyminingworld-release-check.XXXXXX")"
cleanup() {
  rm -rf "$stage_parent" "$extract_parent"
}
trap cleanup EXIT

root_name="LegacyMiningWorld-${version}"
stage_root="$stage_parent/$root_name"
mkdir -p "$stage_root"
cp "$built_jar" "$stage_root/LegacyMiningWorld-${version}.jar"
cp LICENSE "$stage_root/LICENSE"

cat > "$stage_root/README.txt" <<EOF
LegacyMiningWorld ${version}

Java Edition 1.16.5型の採掘worldを生成するtechnical stable版です。
License: MIT (SPDX: MIT), Copyright (c) 2026 nobu0707
public distribution is allowed under the MIT License terms.
実際のtag、push、release作成、外部uploadは実施していません。
対応環境: Paper 26.1.2 build 69 / Java 25

server停止中にLegacyMiningWorld-${version}.jarだけをplugins/へ配置してください。
旧JARと同時に配置しないでください。test-only verifier JARは導入しません。
Multiverse-Coreは任意で、production pluginにMultiverse依存はありません。

既存chunkへの遡及生成はありません。同一seedでも旧Vanilla 1.16.5との
block座標完全一致は保証しません。詳細はリポジトリ文書を参照してください。
EOF

cat > "$stage_root/RELEASE_NOTES.md" <<EOF
# LegacyMiningWorld ${version}

MIT Licenseを正式適用し、root/JAR/release packageへLICENSEを収録しました。
1.0.0からproduction Javaとworld-generation logicは変更していません。
production class payloadは1.0.0と同一で、JAR内plugin.ymlの機能外差分は
versionだけです。config/data migrationとworld再作成は不要です。

テスト、Paper smoke、Multiverse smoke、1,089chunk検証は実行していません。
Javaソースと生成ロジックに変更がないため、軽量build、class payload比較、
license/package監査だけを実施しました。

JAR SHA-256: ${jar_sha}

展開後の検証:

    sha256sum -c SHA256SUMS.txt

public-distribution-ready: YES
externally published: NO
tag/push/upload: not performed
EOF

(
  cd "$stage_root"
  LC_ALL=C sha256sum LICENSE "LegacyMiningWorld-${version}.jar" README.txt RELEASE_NOTES.md \
    | LC_ALL=C sort -k2 > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt >/dev/null
)

find "$stage_root" -type d -exec chmod 0755 {} +
find "$stage_root" -type f -exec chmod 0644 {} +
touch -h -d '@0' "$stage_root" "$stage_root"/*

package_name="LegacyMiningWorld-${version}-release.tar.gz"
package_path="$RELEASE_DIRECTORY/$package_name"
tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  -C "$stage_parent" -cf - "$root_name" | gzip -n > "$package_path"

for file in "LegacyMiningWorld-${version}.jar" README.txt RELEASE_NOTES.md SHA256SUMS.txt LICENSE; do
  cp "$stage_root/$file" "$RELEASE_DIRECTORY/$file"
done

tar -xzf "$package_path" -C "$extract_parent"
extracted="$extract_parent/$root_name"
(cd "$extracted" && sha256sum -c SHA256SUMS.txt >/dev/null)
cmp "$built_jar" "$extracted/LegacyMiningWorld-${version}.jar" || die "packaged JAR differs from build JAR"
cmp LICENSE "$extracted/LICENSE" || die "packaged LICENSE differs from root"

mapfile -t packaged_files < <(find "$extracted" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | LC_ALL=C sort)
readonly expected_files=(
  LICENSE
  "LegacyMiningWorld-${version}.jar"
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
)
[ "${packaged_files[*]}" = "${expected_files[*]}" ] || die "release package file list is not exact: ${packaged_files[*]}"

if tar -tzf "$package_path" | grep -Eiq \
  'LICENSE-NOT-SELECTED|MultiverseVerifier|(^|/)server/|(^|/)tests?/|(^|/)src/|\.class$|\.log$|\.git|worlds?/'; then
  die "release package contains forbidden content"
fi
if rg -n 'PLACEHOLDER|TO_BE_FINALIZED|TBD|SNAPSHOT|1\.0\.0-rc\.1|0\.6\.0-alpha\.1' "$extracted" >/dev/null; then
  die "release package contains placeholder or stale version"
fi

mapfile -t release_files < <(find "$RELEASE_DIRECTORY" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | LC_ALL=C sort)
readonly expected_release_files=(
  LICENSE
  "LegacyMiningWorld-${version}-release.tar.gz"
  "LegacyMiningWorld-${version}.jar"
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
)
[ "${release_files[*]}" = "${expected_release_files[*]}" ] || die "build/release file list is not exact"

package_sha="$(sha256sum "$package_path" | awk '{print $1}')"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$version"
  printf 'release JAR: LegacyMiningWorld-%s.jar\n' "$version"
  printf 'release JAR SHA-256: %s\n' "$jar_sha"
  printf 'release package: %s\n' "$package_name"
  printf 'release package SHA-256: %s\n' "$package_sha"
  printf 'package contents: %s\n' "${expected_files[*]}"
  printf 'JAR metadata version: PASS\nSHA256SUMS: PASS\n'
  printf 'packaged/build JAR equality: PASS\n'
  printf 'root/package/JAR LICENSE equality: PASS\n'
  printf 'verifier and forbidden content absent: PASS\n'
  printf 'deterministic metadata: fixed mtime, owner/group 0, sorted tar, gzip -n\n'
  printf 'public-distribution-ready: YES\nexternally published: NO\n'
  printf 'PASS: release package generated and self-checked.\n'
} > "$CHECK_DIRECTORY/release-artifacts.txt"

printf 'Release package: %s\nSHA-256: %s\n' "$package_path" "$package_sha"
