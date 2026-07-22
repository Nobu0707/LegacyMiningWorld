# LegacyMiningWorld

LegacyMiningWorld `1.0.0`は、深層岩追加前のJava Edition 1.16.5型採掘環境をPaper上のカスタムworldとして生成するtechnical stable版です。

- stable: YES
- release candidate: NO
- distribution: private/internal deploymentのみ
- license: 未選択

対応環境はPaper 26.1.2 build 69 / Java 25です。Multiverse-Core 5.7.2は任意で、production pluginにMultiverse依存はありません。technical stableとpublic releaseは別であり、ライセンス決定前の公開配布・再配布は認めていません。

## 生成内容

- 地形: Y=0～4の旧式岩盤床、Y=5～67のSTONE、Y=68～69のDIRT、Y=70のGRASS_BLOCK
- 地質: DIRT、GRAVEL、GRANITE、DIORITE、ANDESITE
- 鉱石: COAL、IRON、GOLD、REDSTONE、DIAMOND、LAPIS。Y=11を含む旧式分布
- Y<0とY>70: AIR
- biome: 全座標PLAINS
- 固定spawn: generator API `(0.5, 71.0, 0.5)`、永続block `(0, 71, 0)`

水、溶岩、洞窟、構造物、DEEPSLATE、copper、emerald、badlands追加goldは生成しません。同一seedでも旧Vanilla 1.16.5とblock座標単位の完全一致は保証しません。既存chunkへの遡及生成は行わないため、初回導入では新しい空worldを使用してください。

## 導入

server停止中にproduction JAR `LegacyMiningWorld-1.0.0.jar`だけを`plugins/`へ配置します。test-only verifier `LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`はserverへ導入しないでください。

Multiverse-Core 5.7.2での作成例:

```text
mv generators list
mv create legacy_mining normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

詳細は[導入手順](docs/installation.md)、[運用ガイド](docs/operations.md)、[stable release情報](docs/stable-release.md)、[ユーザー受入チェックリスト](docs/user-acceptance-checklist.md)、[1.0.0-rc.1履歴](docs/release-candidate.md)、[CHANGELOG](CHANGELOG.md)を参照してください。

## Phase 5検証結果

Phase 0～5を完了し、RC baseline `3c30291b5c570d1c53a261ef8f5d9715b42512ff`からproduction機能を変更せずstableへ昇格しました。

- `src/main/java/**`、production `plugin.yml` template、Paper dependency、world-generation設定、anchors、large-scale spec: RC baselineと同一
- RC/stable production class entry名とclass bytes: 同一
- JAR内`plugin.yml`: version `1.0.0-rc.1`から`1.0.0`への変更だけ
- `-Xlint:all -Werror`: production/test-onlyとも警告0
- production runtime外部library / Multiverse dependency / scheduler / reflection / NMS: 0
- unit / integration / large-scale回帰: PASS
- 通常Paper smoke / package JAR Paper smoke: PASS
- Multiverse create / restart / auto-load: PASS
- production/verifier JARとrelease packageの再現可能性: PASS
- commit済みtracked sourceだけのclean-room validation: PASS

固定seed `11652021`で通常Paper 4chunk smoke、Multiverse create/restart、自動再読込、1,089chunkのA1 forward / A2 existing / B1 reverseを検証しました。

- A1/A2/B1のper-chunk report、Material count、鉱石histogram: 完全一致
- full checksum: `-56844145234233245`
- Y=5..67 checksum: `-7581040318536063180`
- forbidden block / unknown non-AIR: `0 / 0`
- biome: 1,115,136点すべてPLAINS
- target MCA chunk missing: 0

実測の1,089chunk grid時間はRC検証環境で各約108.9秒でしたが、性能値は環境依存です。詳細は[大規模検証](docs/large-scale-validation.md)と[Multiverse統合](docs/multiverse-integration.md)にあります。

ユーザーがゲームクライアントと実運用環境で行う[受入チェックリスト](docs/user-acceptance-checklist.md)は作成済みですが、Codexは実施・記入していません。

## stable成果物

- production JAR: `LegacyMiningWorld-1.0.0.jar`
- production JAR SHA-256: `95fc4798970d28a8b095cc5fd276348dbb893c831d697e7efb8c9960d62e419f`
- test-only verifier JAR: `LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`
- verifier JAR SHA-256: `e797a5e9ffc3bf496c20f88ea6da65468af78c291d8129e8515f9100cc8ae7e7`
- release package: `LegacyMiningWorld-1.0.0-release.tar.gz`
- release package SHA-256: `dd469700f986f8f2aa41ac8f846608a331245e3a95b181c54adcafb7823a29f4`

packageにはproduction JAR、`README.txt`、`RELEASE_NOTES.md`、`SHA256SUMS.txt`、`LICENSE-NOT-SELECTED.txt`だけを含みます。`SHA256SUMS.txt`は`sha256sum -c SHA256SUMS.txt`で検証できます。verifier JAR、test、source、server、world、review logは含みません。

`LICENSE-NOT-SELECTED.txt`はopen-source licenseでも再配布許可でもありません。このpackageはprivate/internal deployment専用であり、public distributionには別途ライセンス決定が必要です。

## ビルドと監査

commit前に次を実行します。

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
./scripts/compare-rc-stable-payload.sh
```

上記がすべてPASSした後、追跡対象だけを`chore: promote LegacyMiningWorld 1.0.0`でcommitします。commit後に同じHEADを使って次を実行します。

```text
./scripts/run-clean-room-validation.sh
./scripts/write-final-stable-release.sh
```

Paper/Multiverse smokeには追跡しない`server/paper-26.1.2-69.jar`、`server/eula.txt`、`server/plugins/multiverse-core-5.7.2.jar`が必要です。Phase 5コミット件名は`chore: promote LegacyMiningWorld 1.0.0`です。

clean-room合格後のレビューアーカイブ:

```text
./scripts/make-review-archive.sh "chore: promote LegacyMiningWorld 1.0.0"
./scripts/make-full-review-archive.sh "chore: promote LegacyMiningWorld 1.0.0"
```

stable `1.0.0`の技術受入は完了していますが、tagは作成せず、push、GitHub Release、Maven publishing、外部uploadも実施していません。将来のoptional Phase 6で、ユーザーの明示承認のもとライセンスとpublic publicationを扱います。
