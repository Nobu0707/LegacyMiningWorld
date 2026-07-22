# LegacyMiningWorld

LegacyMiningWorld `1.0.1`は、深層岩追加前のJava Edition 1.16.5型採掘環境をPaper上のカスタムworldとして生成するtechnical stable版です。

- stable: YES
- release candidate: NO
- license: MIT License
- SPDX identifier: `MIT`
- copyright: Copyright (c) 2026 nobu0707
- public distribution allowed under MIT terms
- actual external publication not performed

ライセンスの正本は[LICENSE](LICENSE)、説明は[ライセンス文書](docs/licensing.md)を参照してください。対応環境はPaper 26.1.2 build 69 / Java 25です。Multiverse-Core 5.7.2は任意で、production pluginにMultiverse依存はありません。

## 生成内容

- Y=0～4: 旧式岩盤床、Y=5～67: STONE、Y=68～69: DIRT、Y=70: GRASS_BLOCK
- 地質: DIRT、GRAVEL、GRANITE、DIORITE、ANDESITE
- 鉱石: COAL、IRON、GOLD、REDSTONE、DIAMOND、LAPIS
- Y<0とY>70: AIR、biome: PLAINS、固定spawn: `(0.5, 71.0, 0.5)`

水、溶岩、洞窟、構造物、DEEPSLATE、copper、emerald、badlands追加goldは生成しません。同一seedでも旧Vanilla 1.16.5とのblock座標完全一致は保証しません。既存chunkへの遡及生成は行わないため、初回導入では新しい空worldを使用してください。

## 導入

server停止中にproduction JAR `LegacyMiningWorld-1.0.1.jar`だけを`plugins/`へ配置します。旧JARと重複させず、test-only verifier `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`はserverへ導入しないでください。

Multiverse-Core 5.7.2での作成例:

```text
mv generators list
mv create legacy_mining normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

詳細は[導入手順](docs/installation.md)、[運用ガイド](docs/operations.md)、[stable release情報](docs/stable-release.md)、[ユーザー受入チェックリスト](docs/user-acceptance-checklist.md)、[CHANGELOG](CHANGELOG.md)を参照してください。

## 1.0.1の変更と検証

`1.0.0`からの変更はversion、MIT License、配布文書、配布packageだけです。`src/main/java/**`、production `plugin.yml`テンプレート、Paper依存、generator設定、seed、feature salt、placement、anchors、large-scale specは変更していません。

- production class entry名と各class SHA-256: `1.0.0`と同一
- JAR内`plugin.yml`: version `1.0.0`から`1.0.1`への変更だけ
- world generation結果: 不変
- config/data migration: なし
- world再作成: 不要（既存chunkへのretro-generationもなし）
- production/verifier JARの`META-INF/LICENSE`: root LICENSEとbyte一致
- release packageの`LICENSE`: root LICENSEとbyte一致
- lightweight build / payload comparison / license audit / package reproducibility: PASS

production Javaと生成ロジックに変更がないため、unit test、Paper smoke、Multiverse smoke、1,089chunk検証、clean-room全回帰は今回実行していません。Phase 5で完了済みの重い検証結果を1.0.1で再実行したとは扱いません。

## 成果物

- production JAR: `LegacyMiningWorld-1.0.1.jar`
- production JAR SHA-256: `cb63c1d31fbc95fe9da262e900a3072687c9b4d66c28d11cdcf01cfa4cba65f4`
- test-only verifier JAR: `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`
- verifier JAR SHA-256: `90641e05b211c3e90eb10152463e6afa0da866ad52f0a766b7436141ad16fea1`
- release package: `LegacyMiningWorld-1.0.1-release.tar.gz`
- release package SHA-256: `e17ba6ee75f6e3bdcc0a114b1f641956f7925597dc5ae9f4cb0f85900ce02c8b`
- root LICENSE SHA-256: `8b7be028c1e6da4e34647010a229cc8b85c715c13cbe03959ccf8bbbd5e4e2d8`

packageにはproduction JAR、`README.txt`、`RELEASE_NOTES.md`、`SHA256SUMS.txt`、`LICENSE`だけを含みます。verifier、test、source、server、world、review logは含みません。

Git tagは作成せず、push、GitHub Release、Maven publishing、Modrinth/Hangar等への投稿、外部uploadも実施していません。

## 軽量release checks

```text
./scripts/run-license-release-checks.sh
./scripts/make-review-archive.sh "chore: apply MIT license for 1.0.1"
./scripts/make-full-review-archive.sh "chore: apply MIT license for 1.0.1"
```

これらはtest、Paper、Multiverse、large-scale validationを実行しません。
