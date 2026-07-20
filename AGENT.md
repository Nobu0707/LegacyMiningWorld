# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- Group ID / package root: `net.nobu0707`
- main class: `net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin`
- 対象: PaperMC 26.1.2 build 69 / Paper API `26.1.2.build.69-stable`
- Java: toolchain・releaseともに25
- current version: 0.4.0-alpha.1（`gradle.properties`）
- completed phase: Phase 3A
- Phase 3A baseline: `fd7b28e0c6fab39f9ede8a4e8b2fdf83465bd1a9` / `feat: populate legacy geology`
- Phase 3A commit subject: `feat: add deterministic ore engine`
- Phase 3はseed・分布・境界のpure検証とPaper書き込みを分離するため3A/3Bへ分割した。

## 規則とコマンド

- NMS、CraftBukkit内部、reflectionは禁止。公開Paper/Bukkit APIだけを使う。
- ユーザー変更を消さず、Phase外の改名・依存更新・機能追加をしない。
- mutable static state、共有Random、world/chunk cache、pending placement mapを生成処理へ入れない。
- `server/`、`build/`、調査JAR/mapping、生成JAR、logs、archivesをstageしない。
- `server/plugins/multiverse-core-5.7.2.jar`はPhase 4まで使用しない。

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon geologyEngineTest
./gradlew --no-daemon geologyAdapterTest
./gradlew --no-daemon oreEngineTest
./gradlew --no-daemon build
./scripts/run-review-checks.sh
./scripts/make-review-archive.sh "feat: add deterministic ore engine"
./scripts/make-full-review-archive.sh "feat: add deterministic ore engine"
```

## runtime仕様

- Y=70: `GRASS_BLOCK`、Y=68～69: `DIRT`、Y=5～67: 初期`STONE`。
- Y=0: 全面`BEDROCK`。Y=1～4: `y <= random.nextInt(5)`の旧式BEDROCK/STONE床。
- Y<0とY>70はAIR。水、溶岩、洞窟、構造物、DEEPSLATEは生成しない。
- 固定spawnは`(0.5, 71.0, 0.5)`、biomeは全座標PLAINS。
- generator idはnull、blank、大小文字を問わない`default`を受理する。
- Phase 2BはDIRT、GRAVEL、GRANITE、DIORITE、ANDESITEを実ワールドへ配置する。
- Phase 3AのCOAL、IRON、GOLD、REDSTONE、DIAMOND、LAPIS pure engineはPaper実ワールドへ未接続。runtimeで鉱石はまだ生成しない。
- Y=1～4のSTONEは置換され得る。Y=0 BEDROCK、地表DIRT/GRASS_BLOCK、AIRは保護する。

## Phase 2A engine（維持）

| order/salt | Feature | size | attempts | origin Y |
|---:|---|---:|---:|---|
| 0 | DIRT | 33 | 10 | 0以上256未満 |
| 1 | GRAVEL | 33 | 8 | 0以上256未満 |
| 2 | GRANITE | 33 | 10 | 0以上80未満 |
| 3 | DIORITE | 33 | 10 | 0以上80未満 |
| 4 | ANDESITE | 33 | 10 | 0以上80未満 |

world seed/source chunkからJava 1.16.5型decoration/feature seedと楕円体鉱脈を再構築する。target chunkは周囲3×3 source chunksを絶対Z、X、feature、attempt、vein sequence順にstreamし、自chunk内だけを所有する。固定seed `11652021`、target `(0,0)`のplannerは5,564 placements、checksum `-4572519745665027215`。

## Phase 3A ore engine（完了）

Mojang公式1.16.5 version manifest/version JSON/server JAR/server mappingsを再検証した。server JAR SHA-256は`58f329c7d2696526f948470aa6fd0b45545039b64cb75015e64c12194b373da6`、mappings SHA-256は`b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7`。詳細は`docs/vanilla-1.16.5-ores.md`。

| order/salt | Feature | material | size | attempts | origin Y |
|---:|---|---|---:|---:|---|
| 5 | COAL | COAL_ORE | 17 | 20 | uniform 0..127 |
| 6 | IRON | IRON_ORE | 9 | 20 | uniform 0..63 |
| 7 | GOLD | GOLD_ORE | 9 | 2 | uniform 0..31 |
| 8 | REDSTONE | REDSTONE_ORE | 8 | 8 | uniform 0..15 |
| 9 | DIAMOND | DIAMOND_ORE | 8 | 1 | uniform 0..15 |
| 10 | LAPIS | LAPIS_ORE | 7 | 1 | depth-average baseline 16/spread 16 |

合計52 attempts/source chunk。PLAINSの同じ`UNDERGROUND_ORES` stepで地質salt 0～4の後に追加される。seed共通境界は`featureSeed(decorationSeed, int stableSalt)`へ最小拡張し、既存geology overloadとgoldenを維持した。`featureSeed = decorationSeed + stableSalt + 10000 * 6`。

uniformはY用`nextInt`1回。lapisは`baseline + nextInt(spread) + nextInt(spread) - spread`、つまり`nextInt(16) + nextInt(16)`で0..30の三角分布。decorator乱数順はlocal X、local Z、origin Y、shape。lapisだけYを2回消費する。鉱脈は既存`LegacyVeinGenerator`をそのまま再利用し、size 33 golden 106/checksum `9016640837519771701`は不変。

`LegacyOrePlanner`はtarget周囲3×3 source chunksをsource Z、X、feature、attempt、vein sequence順に再構築し、自target X/Zだけをstreamする。max size 17の最大水平到達距離3.6875からradius 1で十分。cache/pending write/共有Randomなし。replacementはSTONE/GRANITE/DIORITE/ANDESITEだけで、既存鉱石、DIRT/GRAVEL、BEDROCK、AIR、液体、DEEPSLATE系はfalse。

同じplugin versionのseed/chunk plan決定性を保証する。Vanilla heightmap早期終了、biome decoration pipeline全体、実地形、置換成功/失敗を再現しないため、旧Vanilla同一seedのblock座標完全一致は保証しない。

## Phase 2Bクラスと責務

- `LegacyGeologyPopulator`: 新しい`populate(WorldInfo, Random, int, int, LimitedRegion)`だけをoverrideするstateless入口。
- `LegacyGeologyMaterialAdapter`: Bukkit `Material`とpure geology型を明示switchで変換。未知Materialは`OTHER`で安全側。
- `LegacyGeologyApplicator`: planner順を維持し、target/Y/region/replacement判定後にだけ書く。summaryを返す。
- `LegacyGeologyBlockAccess`: Paper非依存の小さなtest境界。
- `LimitedRegionGeologyBlockAccess`: 呼び出し中だけLimitedRegionをwrapし、`isInRegion/getType/setType`だけを使う。
- `LegacyMiningChunkGenerator#getDefaultPopulators`: `List.of(new LegacyGeologyPopulator())`をstatic finalで1個だけ共有する。

Paperから渡された`Random`はplanner seedに使わない。population schedulingから独立させるため`WorldInfo#getSeed()`とsource chunkを使う。`LimitedRegion#getWorld`、World/Chunk block access、scheduler、async、physics/fluid updateは使わず、LimitedRegion参照を呼び出し後へ保存しない。

置換可能なのは`STONE`、`GRANITE`、`DIORITE`、`ANDESITE`だけ。planner順のため後続stone variantは先行variantを置換できるが、DIRT/GRAVEL配置後は後続featureも置換できない。BEDROCK、AIR、地表、液体、鉱石、DEEPSLATE、未知Materialは置換不可。

## 固定検証値

- seed: `11652021`
- anchor: `src/test/resources/geology-smoke-anchors.tsv`
- anchor数: 10（5 material各2）
- X pair `X_GRAVEL`: `(-1,65,-12)` / `(0,66,-12)`、source `(-1,-1)`、GRAVEL attempt 3
- Z pair `Z_GRANITE`: `(-11,19,-1)` / `(-11,19,0)`、source `(-1,-1)`、GRANITE attempt 6
- force-load: target chunks `(-1,-1)`, `(0,-1)`, `(-1,0)`, `(0,0)`
- 4 chunk合計: DIRT 3,132 / GRAVEL 960 / GRANITE 3,187 / DIORITE 3,080 / ANDESITE 3,470 / STONE 54,778 / BEDROCK 3,073 / GRASS_BLOCK 1,024 / AIR 320,512
- combined checksum: `-8052018879515985261`
- target `(0,0)`: applied 3,180 / skipped height 0 / outside target 0 / outside region 0 / not replaceable 2,384 / checksum `8312497771964804421`

### Phase 3A ore golden

- seed: `11652021`
- target `(0,0)`: 613 candidates / checksum `-6214814787450030649`
- target `(0,0)` material counts: COAL 431 / IRON 111 / GOLD 14 / REDSTONE 49 / DIAMOND 2 / LAPIS 6
- target `(-1,0)`: 607 / checksum `1707629220185779456`
- target `(0,-1)`: 626 / checksum `7380527893828012375`
- target `(-1,-1)`: 604 / checksum `3576546210841447872`
- 4 target合計: COAL 1,685 / IRON 506 / GOLD 52 / REDSTONE 167 / DIAMOND 19 / LAPIS 21
- X boundary: COAL source `(-1,0)`, attempt 0, 20 candidates across X=-1/0
- Z boundary: IRON source `(0,0)`, attempt 7, 4 candidates across Z=-1/0
- 4chunk intersection: IRON source `(0,0)`, attempt 18
- origin sequenceは各featureのfirst/last origin、first-attempt sentinel、final sentinelをgolden化済み。
- same plannerの32並行呼び出しでcount/checksum一致、例外なし。ExecutorServiceは`shutdownNow`済み。
- geology回帰: planner 5,564/checksum `-4572519745665027215`、4chunk checksum `-8052018879515985261`、vein shape 106/checksum `9016640837519771701`すべて不変。

JUnitはmapping、null、replacement順、高度上下端、inaccessible region、target外、負座標、変更不能登録、generator id、並行適用、anchor metadata、4 chunk処理順独立を検証する。Paperスモークでは10/10 anchor、X/Z pair、地表、Y=0 BEDROCK、AIR、PLAINS、negative chunkがPASS済み。Paper SHA-256は`d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b`。

## review重点

- deprecated `populate(World, Random, Chunk)`をoverrideしていないこと。
- world seed取得、planner順、target ownership、Y/region確認、read-before-writeを維持すること。
- `LimitedRegion#getWorld`、`World#getChunkAt/getBlockAt`、scheduler、parallel stream、共有mutable stateがないこと。
- populator listが1要素・変更不能・二重登録なしであること。
- anchor TSVをruntime探索や自動更新で書き換えないこと。
- release JARにtest resource、Multiverse、external runtime libraryが入らないこと。
- salts 5～10、attempts 52、lapisの2-call三角分布、origin sentinel、planner/boundary/concurrency goldenを維持すること。
- ore packageがBukkit、BlockPopulator、LimitedRegionへ依存せず、`LegacyMiningChunkGenerator#getDefaultPopulators`がPhase 2Bのgeology 1要素のままであること。
- Paper smokeはgeology 10 anchorだけを検証し、Phase 3Aでruntime鉱石の存在を要求しないこと。
- review archive作成前にコミットし、作成後もworking treeをcleanに保つこと。

## 後続Phase

- Phase 3B/version 0.4.0: 単一stateless underground populator内でgeology適用後にore適用。LimitedRegion/Bukkit Material adapter、固定ore anchor、X/Z境界、Y=11、distribution sanity、geology回帰。銅・DEEPSLATE系、emerald、badlands追加goldは対象外。
- Phase 4: Multiverse-Core統合、大量chunk、禁止block走査、性能・決定性・再生成一致、release candidate。
