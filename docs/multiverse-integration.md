# Multiverse-Core 5.7.2統合

## 対象と依存境界

Phase 4A/version 0.5.0-alpha.1では、PaperMC 26.1.2 build 69とJava 25上でMultiverse-Core 5.7.2を標準Bukkit generatorの利用側として検証した。production source、production `plugin.yml`、Gradleのproduction dependencyにはMultiverse APIや依存宣言を追加していない。release JARはMultiverse class、verifier、anchor TSVを含まない。

Multiverse JARの検査結果は次のとおり。

- file: `server/plugins/multiverse-core-5.7.2.jar`
- bytes: 4,454,619
- SHA-256: `574862aa3062af53957fe845de110a386f886445366836c8c63712e11d697400`
- metadata: `plugin.yml`
- name/version: `Multiverse-Core` / `5.7.2`
- main: `org.mvplugins.multiverse.core.MultiverseCore`
- load: metadata未指定のためBukkit既定`POSTWORLD`
- softdepend: Vault、PlaceholderAPI

## 確認したcommand

JAR内の`CreateCommand`/`GeneratorsCommand`定義と実サーバーのhelpを確認した。5.7.2では単独の`mv generators`は一覧を出さず、正確な列挙commandは次になる。

```text
mv help generators
mv help create
mv create --help
mv generators list
mv create legacy_mining_mv_smoke normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
mv list
mv info legacy_mining_mv_smoke
```

`mv generators list`は`LegacyMiningWorld`を列挙した。createはworld `legacy_mining_mv_smoke`、environment `NORMAL`、seed `11652021`、generator `LegacyMiningWorld`として成功し、production pluginへid `default`でgeneratorを要求した。

`--no-adjust-spawn`はJAR内の実flag名である。省略時の`Adjust Spawn: true`はMultiverseの安全地点探索を有効にするため、generatorの固定spawnをそのまま評価する本試験ではfalseにした。Paper/Bukkitが永続化するworld spawnはblock座標`(0,71,0)`で、公開generator APIの固定spawn locationは`(0.5,71.0,0.5)`。verifierは両方を別項目として検査する。

## test-only verifier

`src/multiverseVerifier`を独立source setとし、`LegacyMiningWorld-MultiverseVerifier-0.5.0-alpha.1.jar`を`multiverseVerifierJar` taskで作る。plugin名は`LegacyMiningWorldMultiverseVerifier`、hard dependはtest JAR内だけの`LegacyMiningWorld`と`Multiverse-Core`。Paper APIだけでcompileし、Multiverse API、NMS、CraftBukkit内部、reflection、network、scheduler、block write、world削除を使わない。

console限定commandは次のとおり。

```text
lmwit verify legacy_mining_mv_smoke 11652021
```

main threadでchunks `(-1,-1)`, `(0,-1)`, `(-1,0)`, `(0,0)`を同期load/generateし、biomeを含む`ChunkSnapshot`を取得する。その後はsnapshotだけを読み、worldを変更しない。geology/ore anchor TSVはtest JARには含めるがrelease JARには含めない。

## live world検査

world metadataはname、UUID、seed、NORMAL、minY `-64`、maxYExclusive `320`、generator class、biome provider class、永続spawn、generator fixed spawn、world folderを検査する。clean smoke例のUUIDは`45092815-29d9-4568-b73c-45eac73d7480`で、first/second boot間で一致した。smoke directoryをclean作成し直す場合は新しいUUIDになる。

4チャンク全高393,216 blockを走査した結果:

- Y<0: AIR 65,536
- Y=0: BEDROCK 1,024
- Y=1..4: BEDROCKと非BEDROCKが双方存在し、許可Materialだけ
- Y=68..69: DIRT 2,048
- Y=70: GRASS_BLOCK 1,024
- Y=71..319: AIR 254,976
- geology anchors: 10/10、`X_GRAVEL`、`Z_GRANITE`
- ore anchors: 14/14、`X_COAL`、`Z_IRON`
- Y=11: COAL 6、IRON 5、GOLD 8、REDSTONE 7、DIAMOND 4
- biome: Y=0/11/70/100の全X/Z、合計4,096点がPLAINS
- WATER、LAVA、CAVE_AIR、VOID_AIR、DEEPSLATE/TUFF/CALCITE、copper/emerald/deep ore、nether ore、ANCIENT_DEBRIS: 0
- 許可一覧外の非AIR: 0

## Y=5..67 live-comparable golden

PaperのY=1..4岩盤はgeneratorへ渡されたRandomで決まり、Phase 3B pure modelの座標式test patternとは一致を要求できない。このため岩盤の影響を受けないY=5..67を独立pure-model testとlive snapshotで比較する。

| Material | count |
|---|---:|
| STONE | 51,999 |
| DIRT | 995 |
| GRAVEL | 890 |
| GRANITE | 3,013 |
| DIORITE | 2,867 |
| ANDESITE | 3,279 |
| COAL_ORE | 860 |
| IRON_ORE | 438 |
| GOLD_ORE | 41 |
| REDSTONE_ORE | 94 |
| DIAMOND_ORE | 17 |
| LAPIS_ORE | 19 |

checksumはFNV-1a 64bit系で、chunkはZ昇順・X昇順、各chunk内はY昇順・local Z昇順・local X昇順とし、absolute X/Y/Zのint値とMaterial名の各charをmixする。

- chunk `(-1,-1)`: `-4081461885369063153`
- chunk `(0,-1)`: `-6459175142289166354`
- chunk `(-1,0)`: `4995189412391713686`
- chunk `(0,0)`: `124016103469303630`
- combined: `-7305870198059528782`

## 再起動とfilesystem

first bootでcreate、verify、`save-all`、`stop`を行い、同じ`build/multiverse-smoke`を削除せずsecond bootした。second bootではcreateを行わず、Multiverseの`auto-load: true`から対象worldを自動ロードした。world UUID、seed、environment、generator、min/max、spawn、anchors、Y=11、Y=5..67 counts/checksum、forbidden count、biome結果は完全一致した。

Paper 26はcustom dimensionを従来のserver root直下の別worldではなく、`world/dimensions/minecraft/legacy_mining_mv_smoke/`へ保存する。この配下に4対象region fileとPaper UUID `metadata.dat`が存在し、共有world rootに`level.dat`が存在する。Multiverseの`plugins/Multiverse-Core/worlds.yml`にはgenerator `LegacyMiningWorld`、environment `normal`、seed `11652021`、`auto-load: true`が保存された。production pluginはこのconfigを読み書きしない。

## Phase 4Bへ持ち越す範囲

Phase 4Aは固定4チャンクの全高完全走査と同一保存worldの再読込までである。大量chunk生成、MCA/NBTまたは同等手段による大規模走査、distribution report、performance、2つのclean world間の決定的再生成比較、release candidate化はPhase 4Bへ持ち越す。
