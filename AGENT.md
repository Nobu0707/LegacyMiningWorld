# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- Group ID / package root: net.nobu0707
- main class: net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin
- 対象: PaperMC 26.1.2 build 69 / Paper API 26.1.2.build.69-stable
- Java: toolchain・releaseともに25
- current version: 0.4.0
- completed phase: Phase 3B
- Phase 3B baseline: 02c5c82b875a7b0d5d560ff46ddd93e4b7a9fa4a / feat: add deterministic ore engine
- Phase 3B commit subject: feat: populate legacy ores
- Phase 3はpure seed/distribution engineの3AとPaper接続の3Bへ分離した。

## 規則とコマンド

- 公開Paper/Bukkit APIのみ。NMS、CraftBukkit内部、reflectionは禁止。
- ユーザー変更を消さず、Phase外の依存更新・機能追加・整形をしない。
- mutable static state、共有Random、world/chunk cache、pending placement、scheduler、parallel streamを生成処理へ入れない。
- server/、build/、調査物、JAR、logs、archivesをstageしない。
- server/plugins/multiverse-core-5.7.2.jarはPhase 4まで使用しない。

    ./gradlew --no-daemon clean test
    ./gradlew --no-daemon geologyEngineTest
    ./gradlew --no-daemon geologyAdapterTest
    ./gradlew --no-daemon oreEngineTest
    ./gradlew --no-daemon oreAdapterTest
    ./gradlew --no-daemon build
    ./scripts/run-review-checks.sh
    ./scripts/make-review-archive.sh "feat: populate legacy ores"
    ./scripts/make-full-review-archive.sh "feat: populate legacy ores"

## runtime仕様

- Y=70 GRASS_BLOCK、Y=68～69 DIRT、Y=5～67初期STONE。
- Y=0全面BEDROCK。Y=1～4はy <= random.nextInt(5)のBEDROCK/STONE床。
- Y<0とY>70はAIR。水、溶岩、洞窟、構造物、DEEPSLATEなし。
- spawn (0.5,71.0,0.5)、biomeはPLAINS。
- 地質5種とCOAL/IRON/GOLD/REDSTONE/DIAMOND/LAPISの6鉱石をruntime生成する。
- emerald、badlands追加gold、copper、DEEPSLATE系鉱石は生成しない。

## Phase 3B統合

- 統合入口: LegacyUndergroundPopulator。
- getDefaultPopulators(World)はstatic finalの変更不能listに統合populatorを正確に1個返す。
- 同じpopulate call内でLegacyGeologyApplicator、次にLegacyOreApplicatorを適用する。順序変更禁止。
- 非推奨でないpopulate(WorldInfo, Random, int, int, LimitedRegion)だけをoverrideする。
- LimitedRegionUndergroundBlockAccessは呼び出し中だけregionを保持し、isInRegion/getType/setTypeだけを使う。getWorld、World/Chunk/Block access、schedulerなし。
- Paperのpassed Randomは使わない。seedは毎回WorldInfo#getSeed()から取得し、population scheduling/thread orderから決定性を分離する。
- ore adapter: LegacyOreMaterialAdapter、LegacyOreBlockAccess、LegacyOreApplicator。
- explicit mapping: COAL_ORE、IRON_ORE、GOLD_ORE、REDSTONE_ORE、DIAMOND_ORE、LAPIS_OREを同名Bukkit Materialへ変換。null拒否、fallbackなし。
- replacement target: STONE、GRANITE、DIORITE、ANDESITEだけ。
- ore application Y: inclusive 0、exclusive 68。Y<0とY>=68はSTONEでもskip。Y=0 BEDROCKは不変、Y=1～4のSTONEだけ置換可能。
- planner stable orderを逐次read-before-writeする。同じ座標では最初の鉱石が勝ち、後続は既存鉱石のためskip。
- target chunk ownership、world height、legacy Y、region、current blockの順でguardする。target外とregion外はread/writeしない。
- applicator/populatorはimmutable service参照とmethod-local summaryだけ。同一instanceの並行テストあり。

## 1.16.5 ore設定

| order/salt | Feature | size | attempts | origin Y |
|---:|---|---:|---:|---|
| 5 | COAL | 17 | 20 | uniform 0..127 |
| 6 | IRON | 9 | 20 | uniform 0..63 |
| 7 | GOLD | 9 | 2 | uniform 0..31 |
| 8 | REDSTONE | 8 | 8 | uniform 0..15 |
| 9 | DIAMOND | 8 | 1 | uniform 0..15 |
| 10 | LAPIS | 7 | 1 | depth-average baseline 16/spread 16 |

lapisはnextInt(16)+nextInt(16)の三角分布。source Z、X、feature、attempt、vein sequence順。Phase 3A target (0,0)は613 candidates/checksum -6214814787450030649で不変。geology plannerは5,564/checksum -4572519745665027215、geology-only 4chunk checksum -8052018879515985261で不変。

## Phase 3B固定値

seed 11652021、target chunks (-1,-1),(0,-1),(-1,0),(0,0)。

- combined counts: AIR 320512 / BEDROCK 3073 / STONE 53542 / DIRT 3132 / GRAVEL 960 / GRANITE 3098 / DIORITE 3017 / ANDESITE 3358 / GRASS_BLOCK 1024 / COAL 867 / IRON 443 / GOLD 48 / REDSTONE 106 / DIAMOND 17 / LAPIS 19
- combined checksum: -7165395187979696007
- Y=11: STONE 733 / DIRT 47 / GRAVEL 19 / GRANITE 76 / DIORITE 34 / ANDESITE 85 / COAL 6 / IRON 5 / GOLD 8 / REDSTONE 7 / DIAMOND 4
- forbidden pure-model count: copper、emerald、deepslate、全deep ore、water、lavaすべて0
- geology anchor: src/test/resources/geology-smoke-anchors.tsv 10/10維持
- ore anchor: src/test/resources/ore-smoke-anchors.tsv 14件。material counts COAL 3 / IRON 3 / GOLD 2 / REDSTONE 2 / DIAMOND 2 / LAPIS 2
- X pair X_COAL: (-1,22,3) sequence 6 / (0,22,3) sequence 0、source (-1,0)、COAL attempt 0
- Z pair Z_IRON: (0,34,-1) sequence 2 / (1,34,0) sequence 0、source (0,0)、IRON attempt 7
- Y11 anchors: COAL (-9,11,-10)、IRON (8,11,0)、GOLD (-9,11,14) と (2,11,13)、REDSTONE (-16,11,1) と (13,11,1)、DIAMOND (14,11,-4) と (13,11,-4)
- LAPIS anchors: (-4,16,-10)、(-4,17,-10)
- Paper smoke: Paper SHA-256 d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b、geology 10/10、ore 14/14、Y11 five materials、X_COAL、Z_IRON、terrain protection、fatal scan、source hashすべてPASS
- Paper smoke plugins: LegacyMiningWorld-0.4.0.jarだけ。Multiverse copied NO

anchorはruntime探索・自動更新禁止。release JARへ含めず、review archiveには含める。

## 保証範囲

同じplugin version・world seed・target chunkに対する決定性、1.16.5設定・seed式・分布・形状、target ownershipを保証する。Vanilla heightmap早期終了、全decoration pipeline、noise地形、洞窟等は再現しないため、Minecraft 1.16.5の同一seedとblock座標完全一致は保証しない。

Paper smokeはLegacyMiningWorld-0.4.0.jarだけを使い、4 chunksをforce-loadした。地形、geology 10/10、ore 14/14、Y11主要5鉱石、X/Z pair、fatal scan、source Paper/EULA hash、Multiverse copied NOがPASS。Phase 3Bの禁止materialはpure modelで4chunk全体を厳密に0確認し、Paperではanchor周辺と代表座標だけを確認した。Paper 4chunk完全走査とは報告しない。

## review重点

- integrated populator 1個、geology→ore、二重適用なし。
- LimitedRegion call scope、getWorld/direct block access/schedulerなし。
- passed Random非依存、WorldInfo seed。
- Y<0/Y>=68書込なし、surface/AIR/BEDROCK/DIRT/GRAVEL保護。
- stable first-wins、target外書込なし、parallel/cache/shared mutable stateなし。
- geology golden、ore engine golden、combined golden、Y11、14 anchors、境界pair。
- release JARにtest resources/Multiverse/external libraryなし。
- archive前にfeat: populate legacy oresでコミットし、作成後もworking tree clean。

## Phase 4

server/plugins/multiverse-core-5.7.2.jarを初めて使用し、/mv generators、/mv create、生成world検査、大量chunk、禁止block完全走査、distribution report、性能、決定性・再生成一致、release candidate化を行う。
