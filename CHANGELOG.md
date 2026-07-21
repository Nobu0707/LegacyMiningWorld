# Changelog

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
