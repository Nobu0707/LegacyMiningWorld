# Changelog

## 1.0.1 — 2026-07-22

### License and packaging

- MIT License（SPDX: `MIT`、Copyright (c) 2026 nobu0707）を採用し、root `LICENSE`を追加しました。
- production JARとtest-only verifier JARへ`META-INF/LICENSE`を追加し、release packageへ`LICENSE`を追加しました。
- packageから`LICENSE-NOT-SELECTED.txt`を除去し、public distributionをMIT条件で可能にしました。

### Compatibility

- `1.0.0`からproduction Java、production `plugin.yml`テンプレート、Paper dependency、world-generation設定を変更していません。
- production class payloadは`1.0.0`と同一で、生成済み`plugin.yml`の差はversionだけです。
- config/data migrationとworld再作成は不要で、world-generation結果とno retro-generation仕様は不変です。

### Validation

- testを除外したproduction/verifier build、class payload比較、JAR content検査、license/package監査、package二重生成による再現可能性確認だけを実施しました。
- unit test、Paper smoke、Multiverse smoke、1,089chunk検証、clean-room全回帰は実施していません。production Javaと生成ロジックに変更がないためです。
- tag、push、GitHub Release、Maven publishing、外部uploadは実施していません。

## 1.0.0 — 2026-07-22

### Stable

- `1.0.0-rc.1`をproduction機能の変更なしでtechnical stable `1.0.0`へ昇格しました。
- stable statusはYES、release candidate statusはNOです。ライセンス未選択のためprivate/internal deploymentだけを対象とします。

### Added

- stable production JARと再現可能なstable release packageを追加しました。
- RC/stable payload比較、stable source同一性、stable version scan、stable package監査、最終technical acceptanceを追加しました。
- 人間がゲームクライアントと実運用環境で確認する未記入のユーザー受入チェックリストを追加しました。Codexはこの手動受入を実施していません。

### Validation

- RC baseline `3c30291b5c570d1c53a261ef8f5d9715b42512ff`とstableのproduction Java、production `plugin.yml` template、Paper依存、world-generation設定、anchors、large-scale specが同一であることを確認しました。
- RC/stable production class entry名と各class bytesが同一で、JAR内`plugin.yml`の差がversionだけであることを確認しました。
- 全unit/integration/large-scale回帰、通常Paper、package JAR Paper、Multiverse create/restart、commit済みtracked sourceだけのclean-room validationがPASSしました。
- production/verifier JARとstable release packageの再現可能性を確認しました。
- 1,089chunkのfull checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、禁止block 0、未知non-AIR 0、biome 1,115,136点すべてPLAINSを維持しました。

### Compatibility

- 対応環境はPaper 26.1.2 build 69とJava 25です。
- Multiverse-Core 5.7.2は任意です。production pluginはMultiverseへ依存しません。
- production pluginにcommand、config、runtime database、runtime data、外部runtime libraryはありません。

### Upgrade from 1.0.0-rc.1

- serverを停止し、backup後に`LegacyMiningWorld-1.0.0-rc.1.jar`を`LegacyMiningWorld-1.0.0.jar`へ置換するだけで更新できます。両JARを同時に配置しないでください。
- RCから設定変更はなく、config migration、database migration、runtime data migration、world再作成は不要です。
- RCとstableのproduction class payloadは同一です。既存の生成済みchunkは変更されず、新規chunkの生成仕様も変わりません。

### Known limitations

- 同一seedでも旧Vanilla 1.16.5とblock座標単位の完全一致は保証しません。
- 既存chunkは遡及生成・再生成されません。初回導入では新しい空worldを使用してください。
- 性能は環境依存です。24時間耐久試験と広範な利用者実機試験は未実施です。
- ユーザー受入チェックリストは作成済みですが、Codexによるゲームクライアント確認は未実施です。

### Distribution notice

- ライセンスは未選択です。stable packageはopen-source licenseや再配布許可を付与しません。
- `1.0.0`はprivate/internal deployment用のtechnical stableであり、public distributionは実施していません。
- Git tag、push、GitHub Release、Maven publishing、外部uploadは実施していません。

## 1.0.0-rc.1 — 2026-07-21

これはstable版ではなく、1.0.0へ向けたrelease candidateです。

### Added

- Java Edition 1.16.5型の固定採掘地形と旧式岩盤床を追加しました。
- DIRT、GRAVEL、GRANITE、DIORITE、ANDESITEの5地質を追加しました。
- COAL、IRON、GOLD、REDSTONE、DIAMOND、LAPISの6鉱石を追加しました。
- Y=11を含む固定seed回帰、全座標PLAINS、固定spawnを実装・検証しました。
- 再現可能なproduction/verifier JARとrelease package、クリーンルーム検証を追加しました。

### Validation

- Paper 26.1.2 build 69 / Java 25でunit、地質、鉱石、通常Paper smokeを完走しました。
- Multiverse-Core 5.7.2でgenerator列挙、world作成、保存、再起動、自動再読込を確認しました。
- 固定seedの1,089chunkをA1/A2/B1で検証し、生成順、保存再読込、別clean worldで同一結果を確認しました。
- full checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、禁止block 0、biome全件PLAINSを確認しました。

### Compatibility

- 対応環境はPaper 26.1.2 build 69とJava 25です。
- Multiverse-Core 5.7.2は任意です。production pluginはMultiverseへ依存しません。
- production pluginにcommand、config、runtime database、外部runtime libraryはありません。

### Known limitations

- 同一seedでも旧Vanilla 1.16.5とblock座標単位の完全一致は保証しません。
- 既存chunkは遡及生成・再生成されません。新しい空worldで使用してください。
- 実測性能は環境依存です。24時間耐久試験と広範な利用者実機試験は未実施です。
- ライセンスは未選択で、再配布条件は未定です。

### Not included

- 水、溶岩、洞窟、構造物、DEEPSLATE、copper、emerald、badlands追加goldは生成しません。
- test-only Multiverse verifier、Paper/Multiverse JAR、server、world、検証ログは配布物に含みません。
- tag、push、外部publish、stable `1.0.0`化はこの候補に含みません。

### Upgrade notes

- serverを停止し、backup後に旧LegacyMiningWorld JARを1本だけこのproduction JARへ置換してください。
- test worldで既存worldの読込と新規chunk生成を確認してから本番へ適用してください。
- verifier JARはserverへ導入しないでください。rollback用に旧JAR、world、Multiverse `worlds.yml`を保管してください。
