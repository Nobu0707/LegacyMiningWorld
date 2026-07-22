# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- Group ID / package root: net.nobu0707
- main class: net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin
- 対象: PaperMC 26.1.2 build 69 / Paper API 26.1.2.build.69-stable
- Java: toolchain・releaseともに25
- current version: 1.0.1（MIT License適用済みtechnical stable、release candidateではない）
- completed phase: Phase 6
- RC baseline: 3c30291b5c570d1c53a261ef8f5d9715b42512ff / chore: prepare 1.0.0 release candidate
- Phase 5 commit subject: chore: promote LegacyMiningWorld 1.0.0
- Phase 6 baseline: 2c487560b0d862df0af0c452c3686a7ca72fade3 / chore: promote LegacyMiningWorld 1.0.0
- Phase 6 commit subject: chore: apply MIT license for 1.0.1
- public distribution ready: yes under MIT terms
- external publication status: not published
- license: MIT License / SPDX MIT / Copyright (c) 2026 nobu0707
- production source lineage baseline: 71b5deb151041f5c9e85a84447a454dbb5ab68a4 / test: validate large-scale generation
- Phase 4A baseline: db23362efe8265a8e491bf5432b4d461bb3e5667 / feat: populate legacy ores
- Phase 4A commit subject: test: verify multiverse integration
- Phase 4B1 baseline: 8eb27eaf0fe6644fceae6fef20d7419f550af862 / test: verify multiverse integration
- Phase 4B1 commit subject: test: validate large-scale generation
- Phase 3はpure seed/distribution engineの3AとPaper接続の3Bへ分離した。

## 規則とコマンド

- 公開Paper/Bukkit APIのみ。NMS、CraftBukkit内部、reflectionは禁止。
- ユーザー変更を消さず、Phase外の依存更新・機能追加・整形をしない。
- mutable static state、共有Random、world/chunk cache、pending placement、scheduler、parallel streamを生成処理へ入れない。
- server/、build/、調査物、JAR、logs、archivesをstageしない。
- server/plugins/multiverse-core-5.7.2.jarはtest-only統合検証だけで使用する。production dependency/release JARへ入れない。

commit前:

    ./gradlew --no-daemon clean build -x test
    ./gradlew --no-daemon multiverseVerifierJar -x test
    ./scripts/run-license-audit.sh
    ./scripts/compare-stable-patch-payload.sh
    ./scripts/make-release-package.sh
    ./scripts/run-public-license-status-scan.sh
    ./scripts/run-license-release-checks.sh

Phase 6ではproduction Javaと生成ロジックが不変なのでtest、Paper/Multiverse smoke、large-scale、clean-room全回帰を実行しない。全軽量項目PASS後、必要な追跡ファイルだけを`chore: apply MIT license for 1.0.1`でcommitする。

commit後:

    ./scripts/write-final-public-ready-release.sh
    ./scripts/make-review-archive.sh "chore: apply MIT license for 1.0.1"
    ./scripts/make-full-review-archive.sh "chore: apply MIT license for 1.0.1"

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
- 通常Paper回帰smoke plugins: LegacyMiningWorld-1.0.0.jarだけ。Multiverse/verifier copied NO

anchorはruntime探索・自動更新禁止。release JARへ含めず、review archiveには含める。

## Phase 4A Multiverse統合

- Multiverse JAR: name `Multiverse-Core`、version 5.7.2、main `org.mvplugins.multiverse.core.MultiverseCore`、metadata `plugin.yml`、4,454,619 bytes、SHA-256 `574862aa3062af53957fe845de110a386f886445366836c8c63712e11d697400`。
- 正確な列挙commandは`mv generators list`。`LegacyMiningWorld`を列挙した。
- create commandは`mv create legacy_mining_mv_smoke normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn`。worldはNORMAL、seed 11652021、generator `LegacyMiningWorld`、autoload true。
- test-only mainは`net.nobu0707.legacyminingworld.integration.MultiverseIntegrationVerifierPlugin`。source setは`multiverseVerifier`、現行JARは`LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`。Paper APIだけでcompileし、Multiverse API、world block write、NMS、reflection、networkを使わない。
- console commandは`lmwit verify legacy_mining_mv_smoke 11652021`。chunks `(-1,-1),(0,-1),(-1,0),(0,0)`を同期loadし、`ChunkSnapshot`でminY -64からmaxYExclusive 320まで393,216 blockを読む。
- clean smoke例UUID `45092815-29d9-4568-b73c-45eac73d7480`はfirst/second bootで一致。clean directory再作成ごとにUUID自体は新規になる。
- Bukkit永続spawn blockは`(0,71,0)`、generator fixed spawnは`(0.5,71.0,0.5)`。Paper公開API上の表現差として両方を検査する。
- geology anchors 10/10、ore anchors 14/14、X pairs `X_GRAVEL`/`X_COAL`、Z pairs `Z_GRANITE`/`Z_IRON`。
- Y=11はSTONE 733 / DIRT 47 / GRAVEL 19 / GRANITE 76 / DIORITE 34 / ANDESITE 85 / COAL 6 / IRON 5 / GOLD 8 / REDSTONE 7 / DIAMOND 4。
- Y=5..67 live countsはSTONE 51,999 / DIRT 995 / GRAVEL 890 / GRANITE 3,013 / DIORITE 2,867 / ANDESITE 3,279 / COAL 860 / IRON 438 / GOLD 41 / REDSTONE 94 / DIAMOND 17 / LAPIS 19。
- Y=5..67 chunk checksumsは`(-1,-1)=-4081461885369063153`、`(0,-1)=-6459175142289166354`、`(-1,0)=4995189412391713686`、`(0,0)=124016103469303630`、combined `-7305870198059528782`。
- Y<0 AIR 65,536、Y=0 BEDROCK 1,024、Y=1..4はBEDROCK/非BEDROCK双方ありで許可Materialだけ、Y=68..69 DIRT 2,048、Y=70 GRASS 1,024、Y>=71 AIR 254,976。
- 禁止Materialと許可外非AIRは4chunk全高で0。Y=0/11/70/100のbiome 4,096点はすべてPLAINS。
- first bootでcreate/verify/save/stop後、同じsmoke directoryのsecond bootがworlds.ymlから自動loadした。UUID、seed、generator、spawn、anchors、counts、checksum、forbidden、biomeが一致。
- Paper 26の実worldは`world/dimensions/minecraft/legacy_mining_mv_smoke/`。region 4ファイル、Paper UUID metadata、共有`world/level.dat`、Multiverse `worlds.yml`を検査する。
- production Javaコード変更なし。production `plugin.yml`へMultiverse depend/softdependなし。release JARへverifier、anchor TSV、Multiverse/Paper classなし。通常Paperスモークはrelease JARだけで回帰PASS。

## Phase 4B1 大規模検証

- production Java変更なし。production plugin.ymlのMultiverse depend/softdepend、Gradle Multiverse dependency、NMS、reflectionも引き続きなし。
- specは`src/test/resources/large-scale-grid.properties`。world `legacy_mining_scale`、seed 11652021、chunk X/Z=-16..16、1,089 unique chunks、Y=-64..319、107,053,056 blocks/run。
- A1=`build/large-scale-smoke-a` clean/forward/generate、A2=same directory restart/existing/forward、B1=`build/large-scale-smoke-b` separate clean/reverse/generate。
- 検証run例UUID: A1/A2 `0c3251c0-6c50-4617-9acc-6147320f65d7`、B1 `103efa49-e790-4bd7-9820-a7f556b1a874`。clean再実行ごとに値は変わるがA1=A2、A1!=B1が必須。
- A1/A2/B1 deterministic chunk report SHA-256 `e5e26ff1bdac20270d314098728c418ed5faf50501d08e913cf39bacc1b14e27`。Y=0..67 ore histogram SHA-256 `961eebd1e9d4443e1a30134fa43c4a706bd29d5dff8667953aab1da85bd9e55c`。
- full checksum `-56844145234233245`、Y=5..67 live/pure checksum `-7581040318536063180`。pure expected chunks SHA-256 `5facaf32cf760ce463b4bcde25529cae5febd672f507e5d41df6c4de6daac575`。
- Y=5..67 counts: STONE 14,092,844 / DIRT 294,241 / GRAVEL 233,506 / GRANITE 815,647 / DIORITE 854,559 / ANDESITE 869,347 / COAL 230,273 / IRON 126,309 / GOLD 11,175 / REDSTONE 27,327 / DIAMOND 3,554 / LAPIS 4,610。
- Y=11: STONE 220,845 / DIRT 4,950 / GRAVEL 3,984 / GRANITE 12,730 / DIORITE 13,676 / ANDESITE 12,862 / COAL 3,757 / IRON 1,984 / GOLD 383 / REDSTONE 2,959 / DIAMOND 360 / LAPIS 294。
- full counts: AIR 87,259,392 / BEDROCK 836,842 / STONE 14,533,144 / DIRT 862,399 / GRAVEL 240,577 / GRANITE 841,786 / DIORITE 880,031 / ANDESITE 897,704 / GRASS 278,784 / COAL 237,464 / IRON 130,507 / GOLD 12,041 / REDSTONE 33,398 / DIAMOND 4,196 / LAPIS 4,791。
- forbidden 0、unknown non-AIR 0、biome Y=0/11/70/100は1,115,136件すべてPLAINS。各layer volumeもexact一致。
- regionは4 MCA file、target missing 0。spawn準備で-27..27の3,025chunkが存在し、target外extra 1,936件。presence setはA1/A2/B1で一致。
- performance例: grid A1/A2/B1 108.931/109.010/108.929秒、9.997/9.990/9.997 chunks/s、約982,000 blocks/s。startup 13.932/8.019/12.028秒、create 1.004/対象外/1.003秒、process wall 2:20.62/2:15.62/2:21.21、maximum RSS 1,206,200/1,207,252/1,281,088 KiB。world約12.44MB、region 12,439,552 bytes、4 files。
- `unloadChunk`は各段階でfalseとなり2,178件/runを記録したが、maximum loaded chunksは1で次tickへ保持されなかった。値と理由を隠さずperformance reportへ残す。
- default `world`の公開API結果はgenerator null、biome provider null、folder overworld。Multiverse default entryのgeneratorは空、target entryだけがLegacyMiningWorld。
- verifier jobはconsole/main thread限定、同時1件、cancel/disable cleanupあり。prepareを指定順、scan/reportをcanonical順とし、`runTaskTimer`で1tick 1chunk、snapshotは1個だけ保持する。
- pure modelはY=5..67だけを比較する。Paper bedrock Randomを含むY=1..4または全高のpure一致とは報告しない。full checksumはlive A1/A2/B1間だけで比較する。
- source: `LargeScaleGridSpec`、`GridJob`、`GridAccumulator`、`GridReportWriter`、`GridStatistics`、`LargeScaleModelReportTest`、`scripts/inspect-region-headers.py`、`scripts/run-large-scale-validation.sh`。詳細は`docs/large-scale-validation.md`。
- review logs: `large-scale-model-tests.txt`、`large-scale-verifier-tests.txt`、`region-header-tool-tests.txt`、A1/A2/B1 boot/world/chunk/histogram、`large-scale-determinism.txt`、`large-scale-distribution.txt`、`large-scale-performance.txt`、`large-scale-region-headers.txt`、`large-scale-validation.txt`。
- Phase 4B1時点のJAR: `LegacyMiningWorld-0.6.0-alpha.1.jar` SHA-256 `6295400c1eb9db5becef261aba20a60339f14e7f16272105550b7344922a4a75`とtest-only verifier SHA-256 `3656f0022912ef78f7631fd39f42eea63216002f80542b2fb0678e01ee6638e8`。Phase 4B2でもproduction class/goldenは不変。

## 保証範囲

同じplugin version・world seed・target chunkに対する決定性、1.16.5設定・seed式・分布・形状、target ownershipを保証する。Vanilla heightmap早期終了、全decoration pipeline、noise地形、洞窟等は再現しないため、Minecraft 1.16.5の同一seedとblock座標完全一致は保証しない。

Phase 5の通常Paper回帰smokeはstable packageから展開したLegacyMiningWorld-1.0.0.jarだけを使い、4 chunksをforce-loadした。地形、geology 10/10、ore 14/14、Y11主要5鉱石、X/Z pair、fatal scan、source Paper/EULA hash、Multiverse copied NOを確認済み。Phase 6ではJava不変のため再実行していない。4chunk完全走査を1,089chunk走査とは報告しない。

## review重点

- integrated populator 1個、geology→ore、二重適用なし。
- LimitedRegion call scope、getWorld/direct block access/schedulerなし。
- passed Random非依存、WorldInfo seed。
- Y<0/Y>=68書込なし、surface/AIR/BEDROCK/DIRT/GRAVEL保護。
- stable first-wins、target外書込なし、parallel/cache/shared mutable stateなし。
- geology golden、ore engine golden、combined golden、Y11、14 anchors、境界pair。
- release JARにtest resources/Multiverse/external libraryなし。
- verifierはtest-only、console/read-only、release JARと分離。
- `mv generators list`、Multiverse command create、first/second boot、autoload、UUID/checksum一致。
- Y=5..67 golden、4chunk全高禁止Material 0、PLAINS 4,096点。
- large-scale spec、1,089 unique chunks、107,053,056 blocks/run、A1/A2/B1 report、region header、performance、default world vanilla。
- Phase 6 archive前に`chore: apply MIT license for 1.0.1`でコミットし、作成後もworking tree clean。

## Phase 4B2 release candidate

- production Javaはbaseline `71b5deb151041f5c9e85a84447a454dbb5ab68a4`と同一。final auditで機能欠陥は見つからず、production修正は0。
- 全JavaCompileへ`-Xlint:all -Werror`を適用し、production/test-onlyともsource warning 0。deprecated signature、NMS/reflection、scheduler、mutable static、非決定入力0。
- dependency auditはPaper API compileOnlyだけをproduction compileに使用し、runtime external library 0、production Multiverse依存0を確認。
- production JAR SHA-256 `abead261a33ef1415c27f9a9832a51d7383c33bb712286a0e92942fce65b6161`、verifier SHA-256 `9f320801c8f6a9cb5faafb6b9124825c0e5441082cbc929f457dffbe5a891563`。二重clean buildでbyte一致。
- package `LegacyMiningWorld-1.0.0-rc.1-release.tar.gz` SHA-256 `c9029ce0587a116c71bc13e368be771be810ea1c03024f4002e6a6a99bab17a3`。内容はproduction JAR、README.txt、RELEASE_NOTES.md、SHA256SUMS.txt、LICENSE-NOT-SELECTED.txtだけ。
- packageは固定mtime/owner/orderと`gzip -n`で二重生成一致。test-only verifierは非配布。
- clean-roomはcommit済み同一HEADのtracked sourceだけをworktreeへ展開し、外部入力Paper/EULA/Multiverseだけをコピーして全reviewを再実行する。最終HEADと結果は`build/review-checks/clean-room-validation.txt`へ記録。
- `multiverseVerifierTest`と`largeScaleVerifierTest`をtagで分離。unload metricは失敗ではなくPaper ticket lifecycle観測を表す`immediateUnloadRejected`へ改名。
- region header parserは各fileを1回だけparseし、canonical filename、truncation、offset/count、large offset、duplicate chunkを検査。
- full checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、fixed distributions、forbidden 0、PLAINS 1,115,136件は不変。
- Phase 4B2時点ではライセンス未選択、stableではなく、tag/push/publishも行わなかった。

## Phase 5 stable promotion

- version `1.0.0`。technical stableはYES、release candidateはNO。ライセンス未選択のためprivate/internal deployment専用で、public releaseではない。
- RC baselineは`3c30291b5c570d1c53a261ef8f5d9715b42512ff` / `chore: prepare 1.0.0 release candidate`。
- `src/main/java/**`、production `plugin.yml` template、Paper dependency、feature/seed/salts、anchors、large-scale specはbaselineとbyte-for-byte同一。functional production source changesは0。
- RC/stable production class entry名とper-class SHAは同一。JAR内`plugin.yml`の差はversion `1.0.0-rc.1`→`1.0.0`だけで、production functional payloadはIDENTICAL。
- compiler/dependency audit、全unit/integration/large-scale回帰、通常Paper、package JAR Paper、Multiverse create/restart、A1/A2/B1、stable version scanはPASS。
- production JAR `LegacyMiningWorld-1.0.0.jar` SHA-256 `95fc4798970d28a8b095cc5fd276348dbb893c831d697e7efb8c9960d62e419f`。
- test-only verifier `LegacyMiningWorld-MultiverseVerifier-1.0.0.jar` SHA-256 `e797a5e9ffc3bf496c20f88ea6da65468af78c291d8129e8515f9100cc8ae7e7`。server/release packageへ導入しない。
- package `LegacyMiningWorld-1.0.0-release.tar.gz` SHA-256 `dd469700f986f8f2aa41ac8f846608a331245e3a95b181c54adcafb7823a29f4`。production JAR、README.txt、RELEASE_NOTES.md、SHA256SUMS.txt、LICENSE-NOT-SELECTED.txtだけを含む。
- production/verifier JARとstable packageは2回生成でbyte一致。committed tracked sourceだけのstable clean-roomもmain側のJAR/verifier/package SHAと一致してPASS。
- full checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、forbidden 0、unknown non-AIR 0、PLAINS 1,115,136件を維持。
- `docs/installation.md`と`docs/operations.md`はstable/RC upgrade向けに更新済み。`docs/stable-release.md`に最終matrixを記録する。
- `docs/user-acceptance-checklist.md`は作成済みだが、Codexはゲームクライアント・実運用環境で実施も記入もしていない。
- tagは作成せず、push、GitHub Release、Maven publish、外部uploadも実施しない。
- review archiveへstable JAR、stable package、verifier JAR、server/world、RC artifact本体を含めない。artifactのfilename/SHA/contentsはREADME、stable release文書、stable review logへ記録する。
- Phase 5時点ではoptional Phase 6をlicense選択と外部公開の後続作業としていた。Phase 6ではMIT/public-ready packageまでを実施し、Git tag、GitHub Release、署名・外部公開は行わない。

## Phase 6 MIT License / 1.0.1 public-ready patch

- version `1.0.1`。MIT License / SPDX `MIT` / Copyright (c) 2026 nobu0707。root `LICENSE`が正本。
- baselineは`2c487560b0d862df0af0c452c3686a7ca72fade3`。`src/main/java/**`、production `plugin.yml` template、Paper dependency、generator/seed/salt/placement、anchors、large-scale specはbyte不変。
- production/verifier JARへ`META-INF/LICENSE`、release packageへ`LICENSE`を1件収録し、root LICENSEとbyte一致させる。
- production class payloadは1.0.0とIDENTICAL、JAR内`plugin.yml`はversion-only。world generation結果は不変。
- config/data migrationとworld再作成は不要。既存chunkへのretro-generationは行わず、旧Vanilla 1.16.5とのblock座標完全一致は保証しない。
- Phase 6ではtest除外build、payload比較、JAR/license/package監査、package再現可能性だけを実行する。unit test、Paper/Multiverse smoke、1,089chunk検証、clean-room全回帰は実行しない。
- production JARは`LegacyMiningWorld-1.0.1.jar`、test-only verifierは`LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`。verifierをserver/packageへ導入しない。
- release packageは`LegacyMiningWorld-1.0.1-release.tar.gz`で、production JAR、README.txt、RELEASE_NOTES.md、SHA256SUMS.txt、LICENSEだけを含む。
- production JAR SHA-256は`cb63c1d31fbc95fe9da262e900a3072687c9b4d66c28d11cdcf01cfa4cba65f4`、verifierは`90641e05b211c3e90eb10152463e6afa0da866ad52f0a766b7436141ad16fea1`、packageは`e17ba6ee75f6e3bdcc0a114b1f641956f7925597dc5ae9f4cb0f85900ce02c8b`、LICENSEは`8b7be028c1e6da4e34647010a229cc8b85c715c13cbe03959ccf8bbbd5e4e2d8`。
- public-distribution-readyはYES、externally publishedはNO。tag、push、GitHub Release、Maven/Modrinth/Hangar等への投稿、外部uploadは実施しない。
