#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_RC_VERSION="1.0.0-rc.1"
readonly RELEASE_DIRECTORY="build/release"
readonly CHECK_DIRECTORY="build/review-checks"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || die "not inside a git repository"
cd "$repo_root"

version="$(sed -n 's/^legacyminingworld_version=//p' gradle.properties | tail -n 1)"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-rc\.[0-9]+$ ]] \
  || die "version is not an rc version: $version"
[ "$version" = "$EXPECTED_RC_VERSION" ] \
  || die "expected $EXPECTED_RC_VERSION, found $version"

for document in CHANGELOG.md docs/installation.md docs/operations.md docs/release-candidate.md; do
  [ -s "$document" ] || die "missing release document: $document"
done

./gradlew --no-daemon jar >/dev/null

mapfile -t built_jars < <(find build/libs -maxdepth 1 -type f \
  -name 'LegacyMiningWorld-*.jar' \
  ! -name 'LegacyMiningWorld-MultiverseVerifier-*.jar' -print | sort)
[ "${#built_jars[@]}" -eq 1 ] \
  || die "expected exactly one production JAR, found ${#built_jars[@]}"
built_jar="${built_jars[0]}"
expected_jar="build/libs/LegacyMiningWorld-${version}.jar"
[ "$built_jar" = "$expected_jar" ] || die "unexpected production JAR: $built_jar"
plugin_version="$(unzip -p "$built_jar" plugin.yml \
  | sed -n 's/^version:[[:space:]]*//p' | tail -n 1)"
[ "$plugin_version" = "$version" ] \
  || die "JAR metadata version mismatch: $plugin_version"
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

cat > "$stage_root/README.txt" <<EOF
LegacyMiningWorld ${version}

これはstable版ではなくrelease candidateです。
対応環境: Paper 26.1.2 build 69 / Java 25

導入時は LegacyMiningWorld-${version}.jar だけをserver停止中にplugins/へ配置してください。
test-only verifier JARは導入しないでください。Multiverse-Coreは任意です。

Multiverse-Core 5.7.2での作成例:
mv generators list
mv create legacy_mining normal --seed <seed> --generator LegacyMiningWorld --no-adjust-spawn

詳細はリポジトリのdocs/installation.md、docs/operations.md、
docs/release-candidate.mdを参照してください。
EOF

cat > "$stage_root/RELEASE_NOTES.md" <<EOF
# LegacyMiningWorld ${version}

Java Edition 1.16.5型の固定採掘地形、旧式岩盤床、5地質、6鉱石を生成する
release candidateです。stable版ではありません。

Paper 26.1.2 build 69 / Java 25、Multiverse-Core 5.7.2で検証しました。
固定seedの1,089チャンクについてA1/A2/B1の決定性、禁止物0、PLAINS、
full checksum -56844145234233245、Y=5..67 checksum
-7581040318536063180を確認しています。

既存チャンクは再生成されません。旧Vanilla 1.16.5の同一seedとblock座標の
完全一致は保証しません。ライセンスは未選択です。

JAR SHA-256: ${jar_sha}

展開後の検証:

    sha256sum -c SHA256SUMS.txt
EOF

cat > "$stage_root/LICENSE-NOT-SELECTED.txt" <<'EOF'
この配布候補ではライセンスはまだ選択されていません。
これはopen-source licenseの付与を意味しません。
再配布条件は未定であり、このファイルは正式なLICENSEではありません。
ライセンスはユーザーが後で選択します。
EOF

(
  cd "$stage_root"
  LC_ALL=C sha256sum \
    "LICENSE-NOT-SELECTED.txt" \
    "LegacyMiningWorld-${version}.jar" \
    "README.txt" \
    "RELEASE_NOTES.md" \
    | LC_ALL=C sort -k2 > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt >/dev/null
)

find "$stage_root" -type d -exec chmod 0755 {} +
find "$stage_root" -type f -exec chmod 0644 {} +
touch -h -d '@0' "$stage_root" "$stage_root"/*

package_name="LegacyMiningWorld-${version}-release.tar.gz"
package_path="$RELEASE_DIRECTORY/$package_name"
tar --sort=name \
  --mtime='UTC 1970-01-01' \
  --owner=0 --group=0 --numeric-owner \
  -C "$stage_parent" -cf - "$root_name" \
  | gzip -n > "$package_path"

cp "$stage_root/LegacyMiningWorld-${version}.jar" "$RELEASE_DIRECTORY/"
cp "$stage_root/README.txt" "$RELEASE_DIRECTORY/"
cp "$stage_root/RELEASE_NOTES.md" "$RELEASE_DIRECTORY/"
cp "$stage_root/SHA256SUMS.txt" "$RELEASE_DIRECTORY/"
cp "$stage_root/LICENSE-NOT-SELECTED.txt" "$RELEASE_DIRECTORY/"

tar -xzf "$package_path" -C "$extract_parent"
extracted="$extract_parent/$root_name"
(
  cd "$extracted"
  sha256sum -c SHA256SUMS.txt >/dev/null
)
cmp "$built_jar" "$extracted/LegacyMiningWorld-${version}.jar" \
  || die "packaged JAR differs from build/libs JAR"

mapfile -t packaged_files < <(find "$extracted" -mindepth 1 -maxdepth 1 \
  -type f -printf '%f\n' | LC_ALL=C sort)
readonly expected_files=(
  LICENSE-NOT-SELECTED.txt
  "LegacyMiningWorld-${version}.jar"
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
)
[ "${packaged_files[*]}" = "${expected_files[*]}" ] \
  || die "release package file list is not exact: ${packaged_files[*]}"
if tar -tzf "$package_path" | grep -Eiq \
    'MultiverseVerifier|(^|/)server/|(^|/)tests?/|(^|/)src/|\.class$|\.log$|\.git|worlds?/' ; then
  die "release package contains forbidden content"
fi
if rg -n 'PLACEHOLDER|TO_BE_FINALIZED|TBD' "$extracted" >/dev/null; then
  die "release package contains a placeholder"
fi

package_sha="$(sha256sum "$package_path" | awk '{print $1}')"
{
  printf 'executed UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'version: %s\n' "$version"
  printf 'release JAR: LegacyMiningWorld-%s.jar\n' "$version"
  printf 'release JAR SHA-256: %s\n' "$jar_sha"
  printf 'release package: %s\n' "$package_name"
  printf 'release package SHA-256: %s\n' "$package_sha"
  printf 'package contents: %s\n' "${expected_files[*]}"
  printf 'JAR metadata version: PASS\n'
  printf 'SHA256SUMS: PASS\n'
  printf 'packaged/build JAR equality: PASS\n'
  printf 'forbidden content scan: PASS\n'
  printf 'reproducible metadata: fixed mtime, owner/group 0, sorted tar, gzip -n\n'
  printf 'PASS: release package generated and self-checked.\n'
} > "$CHECK_DIRECTORY/release-package.txt"

printf 'Release package: %s\n' "$package_path"
printf 'SHA-256: %s\n' "$package_sha"
