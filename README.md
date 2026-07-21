# LegacyMiningWorld

LegacyMiningWorld `1.0.0-rc.1`は、深層岩追加前のJava Edition 1.16.5型採掘環境をPaper上のカスタムworldとして生成するrelease candidateです。stable版ではありません。

対応環境はPaper 26.1.2 build 69 / Java 25です。Multiverse-Core 5.7.2は任意で、production pluginにMultiverse依存はありません。

## 生成内容

- 地形: Y=0～4の旧式岩盤床、Y=5～67のSTONE、Y=68～69のDIRT、Y=70のGRASS_BLOCK
- 地質: DIRT、GRAVEL、GRANITE、DIORITE、ANDESITE
- 鉱石: COAL、IRON、GOLD、REDSTONE、DIAMOND、LAPIS。Y=11を含む旧式分布
- Y<0とY>70: AIR
- biome: 全座標PLAINS
- 固定spawn: generator API `(0.5, 71.0, 0.5)`、永続block `(0, 71, 0)`

水、溶岩、洞窟、構造物、DEEPSLATE、copper、emerald、badlands追加goldは生成しません。同一seedでも旧Vanilla 1.16.5とblock座標単位の完全一致は保証しません。既存chunkは遡及生成されないため、新しい空worldで使用してください。

## 導入

server停止中にproduction JAR `LegacyMiningWorld-1.0.0-rc.1.jar`だけを`plugins/`へ配置します。`LegacyMiningWorld-MultiverseVerifier-1.0.0-rc.1.jar`はtest-onlyであり、serverへ導入しないでください。

Multiverse-Core 5.7.2での作成例:

```text
mv generators list
mv create legacy_mining normal --seed <seed> --generator LegacyMiningWorld --no-adjust-spawn
```

詳細は[導入手順](docs/installation.md)、[運用ガイド](docs/operations.md)、[release candidate情報](docs/release-candidate.md)、[CHANGELOG](CHANGELOG.md)を参照してください。

## 検証結果

Phase 0～4B2を完了し、固定seed `11652021`で通常Paper 4chunk smoke、Multiverse create/restart、自動再読込、1,089chunkのA1 forward / A2 existing / B1 reverseを実行しました。

- A1/A2/B1のper-chunk report、Material count、鉱石histogram: 完全一致
- full checksum: `-56844145234233245`
- Y=5..67 checksum: `-7581040318536063180`
- forbidden block / unknown non-AIR: `0 / 0`
- biome: 1,115,136点すべてPLAINS
- target MCA chunk missing: 0
- production JavaはPhase 4B1 baseline `71b5deb151041f5c9e85a84447a454dbb5ab68a4`と同一
- `-Xlint:all -Werror`: production/testとも警告0
- production runtime外部library / Multiverse dependency / scheduler / reflection / NMS: 0
- production/verifier JARの2回clean build: byte一致
- release packageの2回生成: byte一致
- commit済みtracked sourceだけのclean-room full validation: PASS

実測の1,089chunk grid時間は各約108.9秒でしたが、性能値は環境依存です。詳細は[大規模検証](docs/large-scale-validation.md)と[Multiverse統合](docs/multiverse-integration.md)にあります。

## 配布候補

- production JAR: `LegacyMiningWorld-1.0.0-rc.1.jar`
- JAR SHA-256: `abead261a33ef1415c27f9a9832a51d7383c33bb712286a0e92942fce65b6161`
- package: `LegacyMiningWorld-1.0.0-rc.1-release.tar.gz`
- package SHA-256: `c9029ce0587a116c71bc13e368be771be810ea1c03024f4002e6a6a99bab17a3`

package内の`SHA256SUMS.txt`は`sha256sum -c SHA256SUMS.txt`で検証できます。ライセンスは未選択で、packageの`LICENSE-NOT-SELECTED.txt`はopen-source licenseや再配布許可を意味しません。

## ビルドと監査

```text
./gradlew --no-daemon clean test
./gradlew --no-daemon geologyEngineTest
./gradlew --no-daemon geologyAdapterTest
./gradlew --no-daemon oreEngineTest
./gradlew --no-daemon oreAdapterTest
./gradlew --no-daemon multiverseVerifierTest
./gradlew --no-daemon largeScaleVerifierTest
./gradlew --no-daemon largeScaleModelTest
./gradlew --no-daemon build multiverseVerifierJar
python3 -m unittest scripts/test_inspect_region_headers.py
./scripts/run-review-checks.sh
./scripts/make-release-package.sh
./scripts/run-clean-room-validation.sh
```

Paper/Multiverse smokeには追跡しない`server/paper-26.1.2-69.jar`、`server/eula.txt`、`server/plugins/multiverse-core-5.7.2.jar`が必要です。期待するPhase 4B2コミット件名は`chore: prepare 1.0.0 release candidate`です。

clean-room合格後のレビューアーカイブ:

```text
./scripts/make-review-archive.sh "chore: prepare 1.0.0 release candidate"
./scripts/make-full-review-archive.sh "chore: prepare 1.0.0 release candidate"
```

stable `1.0.0`への昇格、利用者実機受入、長時間試験、ライセンス決定、tag/publishは別作業です。
