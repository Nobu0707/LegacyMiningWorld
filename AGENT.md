# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- Group ID / package root: `net.nobu0707`
- main class: `net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin`
- 対象: PaperMC 26.1.2 build 69 / Paper API `26.1.2.build.69-stable`
- Java: toolchain・releaseともに25
- current version: 0.3.0（`gradle.properties`）
- completed phase: Phase 2B
- Phase 2B baseline: `1eeddeb1bceace2266fad1cd7741b6a3d1e294f4` / `feat: add deterministic geology engine`
- Phase 2B commit subject: `feat: populate legacy geology`

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
./gradlew --no-daemon build
./scripts/run-review-checks.sh
./scripts/make-review-archive.sh "feat: populate legacy geology"
./scripts/make-full-review-archive.sh "feat: populate legacy geology"
```

## runtime仕様

- Y=70: `GRASS_BLOCK`、Y=68～69: `DIRT`、Y=5～67: 初期`STONE`。
- Y=0: 全面`BEDROCK`。Y=1～4: `y <= random.nextInt(5)`の旧式BEDROCK/STONE床。
- Y<0とY>70はAIR。水、溶岩、洞窟、構造物、DEEPSLATEは生成しない。
- 固定spawnは`(0.5, 71.0, 0.5)`、biomeは全座標PLAINS。
- generator idはnull、blank、大小文字を問わない`default`を受理する。
- Phase 2BはDIRT、GRAVEL、GRANITE、DIORITE、ANDESITEを実ワールドへ配置する。
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

JUnitはmapping、null、replacement順、高度上下端、inaccessible region、target外、負座標、変更不能登録、generator id、並行適用、anchor metadata、4 chunk処理順独立を検証する。Paperスモークでは10/10 anchor、X/Z pair、地表、Y=0 BEDROCK、AIR、PLAINS、negative chunkがPASS済み。Paper SHA-256は`d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b`。

## review重点

- deprecated `populate(World, Random, Chunk)`をoverrideしていないこと。
- world seed取得、planner順、target ownership、Y/region確認、read-before-writeを維持すること。
- `LimitedRegion#getWorld`、`World#getChunkAt/getBlockAt`、scheduler、parallel stream、共有mutable stateがないこと。
- populator listが1要素・変更不能・二重登録なしであること。
- anchor TSVをruntime探索や自動更新で書き換えないこと。
- release JARにtest resource、Multiverse、external runtime libraryが入らないこと。
- review archive作成前にコミットし、作成後もworking treeをcleanに保つこと。

## 後続Phase

- Phase 3: 石炭、鉄、金、redstone、diamond、lapis等の鉱石。銅・DEEPSLATE系鉱石は対象外。
- Phase 4: Multiverse-Core統合、大量chunk、禁止block走査、性能・決定性・再生成一致、release candidate。
