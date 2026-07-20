# LegacyMiningWorld

LegacyMiningWorld 0.5.0-alpha.1は、深層岩追加前のJava Edition 1.16.5型採掘環境をPaper上のカスタムワールドとして生成するプラグインです。対象はPaperMC 26.1.2 build 69、Java 25、Gradle Kotlin DSLです。

## 現在の状態

Phase 0、Phase 1、Phase 2A/2B、Phase 3A/3B、Phase 4Aが完了しています。runtimeは水平な基本地形、5種類の地中岩石、6種類の旧式鉱石を生成し、Multiverse-Core 5.7.2から標準Bukkit generatorとして列挙・作成・再読込できることを実機確認済みです。

- 地形: Y=0～4の旧式岩盤床、Y=5～67のSTONE、Y=68～69のDIRT、Y=70のGRASS_BLOCK
- 地質: DIRT、GRAVEL、GRANITE、DIORITE、ANDESITE
- 鉱石: COAL_ORE、IRON_ORE、GOLD_ORE、REDSTONE_ORE、DIAMOND_ORE、LAPIS_ORE
- Y<0とY>70: AIR
- biome: 全座標PLAINS
- 固定spawn: (0.5, 71.0, 0.5)

水、溶岩、洞窟、構造物、銅、emerald、DEEPSLATEと深層岩鉱石、badlands追加goldは生成しません。

## Java 1.16.5型鉱石設定

| salt | Feature | material | size | attempts/chunk | origin Y |
|---:|---|---|---:|---:|---|
| 5 | COAL | COAL_ORE | 17 | 20 | uniform 0..127 |
| 6 | IRON | IRON_ORE | 9 | 20 | uniform 0..63 |
| 7 | GOLD | GOLD_ORE | 9 | 2 | uniform 0..31 |
| 8 | REDSTONE | REDSTONE_ORE | 8 | 8 | uniform 0..15 |
| 9 | DIAMOND | DIAMOND_ORE | 8 | 1 | uniform 0..15 |
| 10 | LAPIS | LAPIS_ORE | 7 | 1 | depth-average baseline 16 / spread 16 |

lapisはnextInt(16) + nextInt(16)による0..30の三角分布です。合計52 attempts/source chunkをsource Z、source X、feature、attempt、vein sequenceの安定順で再構築します。

## Paper地下生成

LegacyMiningChunkGenerator#getDefaultPopulatorsは、変更不能listにLegacyUndergroundPopulatorを正確に1個返します。同じ非推奨でないpopulate(WorldInfo, Random, int, int, LimitedRegion)呼び出し内で、地質を先、鉱石を後に同期適用します。

読み書きは呼び出し中のLimitedRegion#isInRegion/getType/setTypeだけを使います。World、Chunk、Blockの別経路、scheduler、cache、pending writeは使いません。Paperから渡されたRandomは消費せず、WorldInfo#getSeed()とsource chunkから同じplugin version内の決定性を保ちます。

鉱石が置換できるのはSTONE、GRANITE、DIORITE、ANDESITEだけです。現在blockを候補ごとに1回読み、最初の鉱石が置換した後は後続鉱石をskipするため先行featureが勝ちます。DIRT、GRAVEL、BEDROCK、AIR、地表、液体、既存鉱石、DEEPSLATE系、未知blockは保護します。

Paper適用高度は0 <= Y < 68です。Y=0は範囲内でもBEDROCKなので不変、Y=1～4はSTONE部分だけ置換可能です。Y<0の候補とY>=68の候補は、仮にSTONEがあっても適用しません。

## 固定seed検証

seed 11652021、chunks X/Z=-1..0のcombined modelを固定しています。

| Material | 4 chunk count |
|---|---:|
| COAL_ORE | 867 |
| IRON_ORE | 443 |
| GOLD_ORE | 48 |
| REDSTONE_ORE | 106 |
| DIAMOND_ORE | 17 |
| LAPIS_ORE | 19 |
| STONE | 53,542 |
| DIRT / GRAVEL | 3,132 / 960 |
| GRANITE / DIORITE / ANDESITE | 3,098 / 3,017 / 3,358 |
| BEDROCK / GRASS_BLOCK / AIR | 3,073 / 1,024 / 320,512 |

combined checksumは-7165395187979696007です。Y=11の4チャンク合計はCOAL 6、IRON 5、GOLD 8、REDSTONE 7、DIAMOND 4で、固定Paper anchorでも5種類を実block確認しました。LAPISは別高度の固定anchorを使います。

src/test/resources/ore-smoke-anchors.tsvには14件を固定し、COAL/IRON/GOLD/REDSTONE/DIAMOND/LAPISを3/3/2/2/2/2件含めます。COALのX=-1/0境界pair、IRONのZ=-1/0境界pair、Y=11主要5鉱石を含みます。既存geology anchor 10件と地質X/Z境界pairも統合後に10/10維持します。test中やruntimeでanchorを探索・更新しません。

これはJava 1.16.5の設定・seed式・分布・形状に準拠したLegacyMiningWorld上の決定論的生成です。Vanilla heightmap早期終了、全biome decoration pipeline、noise地形を再現しないため、Minecraft 1.16.5の同一seedとblock座標単位の完全一致は保証しません。

詳細は[地質調査](docs/vanilla-1.16.5-geology.md)、[鉱石調査](docs/vanilla-1.16.5-ores.md)、[Paper地質統合](docs/geology-paper-integration.md)、[Paper鉱石統合](docs/ore-paper-integration.md)、[Multiverse統合](docs/multiverse-integration.md)を参照してください。

## generatorの利用

標準Bukkit設定では対象worldを次のように指定します。

    worlds:
      legacy_mining_world:
        generator: LegacyMiningWorld

generator idは未指定、blank、大小文字を問わないdefaultを受理します。Multiverse-Core 5.7.2で実際に使用したcommandは次です。5.7.2の列挙commandは`mv generators`ではなく`mv generators list`です。

    mv generators list
    mv create legacy_mining_mv_smoke normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn

列挙結果に`LegacyMiningWorld`が現れ、固定seed `11652021`、`NORMAL`、generator `LegacyMiningWorld`で作成されました。`--no-adjust-spawn`はMultiverseの安全地点探索でgeneratorの固定spawnを変更しないために使用します。Bukkitの永続spawn blockは`(0,71,0)`、`ChunkGenerator#getFixedSpawnLocation`は設計値`(0.5,71.0,0.5)`です。

## Phase 4A Multiverse実機検証

test-only source setの`MultiverseIntegrationVerifierPlugin`をrelease JARとは別の`LegacyMiningWorld-MultiverseVerifier-0.5.0-alpha.1.jar`へ構築します。Paper APIだけでcompileし、Multiverse API、NMS、reflection、block writeを使いません。`lmwit verify legacy_mining_mv_smoke 11652021`はconsole限定で4チャンク`(-1,-1)`～`(0,0)`を同期loadし、`ChunkSnapshot`からY=-64..319を完全走査します。

- geology anchors 10/10、ore anchors 14/14、地質・鉱石X/Z境界pair: PASS
- Y=11: COAL 6、IRON 5、GOLD 8、REDSTONE 7、DIAMOND 4
- Y=5..67: 64,512 blocks、checksum `-7305870198059528782`
- Y=5..67 ores: COAL 860、IRON 438、GOLD 41、REDSTONE 94、DIAMOND 17、LAPIS 19
- Y<0 AIR 65,536、Y=0 BEDROCK 1,024、Y=68..69 DIRT 2,048、Y=70 GRASS_BLOCK 1,024、Y>=71 AIR 254,976
- 禁止Materialと許可一覧外の非AIR: 0
- PLAINS: 4,096点
- first boot後に保存・停止し、同じworldをMultiverseがsecond bootでauto-load
- first/secondのUUID、seed、environment、generator、spawn、anchors、counts、checksumが一致

Phase 4Aのclean smoke例ではUUID `45092815-29d9-4568-b73c-45eac73d7480`が2回のbootで一致しました。clean smokeを作り直すごとにUUID自体は新規生成されます。Paper 26のworld実体は`world/dimensions/minecraft/legacy_mining_mv_smoke/`、Multiverse設定は`plugins/Multiverse-Core/worlds.yml`に保存されます。

production Javaコード、production `plugin.yml`、production dependencyにはMultiverse固有要素を追加していません。verifier class、anchor TSV、Multiverse/Paper classはrelease JARに入りません。

## ビルドと検証

    ./gradlew --no-daemon clean test
    ./gradlew --no-daemon geologyEngineTest
    ./gradlew --no-daemon geologyAdapterTest
    ./gradlew --no-daemon oreEngineTest
    ./gradlew --no-daemon oreAdapterTest
    ./gradlew --no-daemon multiverseVerifierTest
    ./gradlew --no-daemon build
    ./gradlew --no-daemon multiverseVerifierJar
    ./scripts/run-review-checks.sh

通常Paperスモークには追跡しないserver/paper-26.1.2-69.jarとserver/eula.txtが必要です。使い捨てbuild/paper-smokeへLegacyMiningWorld-0.5.0-alpha.1.jarだけをコピーし、固定4チャンクの地形、geology 10 anchors、ore 14 anchors、Y=11、X/Z境界を回帰検査します。別のbuild/multiverse-smokeにはrelease、Multiverse-Core 5.7.2、test-only verifierの正確に3 JARだけをコピーします。

Phaseコミットの期待件名は`test: verify multiverse integration`です。コミット後のレビューアーカイブは次で作成します。

    ./scripts/make-review-archive.sh "test: verify multiverse integration"
    ./scripts/make-full-review-archive.sh "test: verify multiverse integration"

## 後続範囲

Phase 4Bでは大量chunk生成、MCA/NBTまたは同等手段による大規模完全走査、分布レポート、性能測定、clean world同士の決定的再生成一致、release candidate化を扱います。Phase 4Aの4チャンク完全走査を大規模検証とは扱いません。

## ライセンス

License not yet selected.
