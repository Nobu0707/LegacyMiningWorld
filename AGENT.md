# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- プロジェクト: LegacyMiningWorld
- Group ID / package root: `net.nobu0707`
- main class: `net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin`
- 対象: PaperMC 26.1.2 build 69 / Paper API `26.1.2.build.69-stable`
- Java: toolchain・コンパイル対象ともに25
- current version: 0.3.0-alpha.1（`gradle.properties`）
- current completed phase: Phase 2A
- Phase 2A baseline: `488770af4113c3c84fc13af93b3432bab4271bde` / `feat: add legacy terrain generator`

## リポジトリ構成とコマンド

- `src/main/java/.../geology/`: Phase 2Aの純粋Java engine。Paper API非依存
- `src/test/`: Phase 1回帰とPhase 2A設定・seed・形状・境界・並行性テスト
- `docs/vanilla-1.16.5-geology.md`: Mojang公式一次資料の調査記録と保証範囲
- `scripts/`: 検証・レビューアーカイブ作成
- `server/`: ローカルPaper本体、EULA、後続用Multiverse-Core。Git追跡・アーカイブ収録禁止

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon test --tests 'net.nobu0707.legacyminingworld.geology.*'
./gradlew --no-daemon build
./scripts/run-review-checks.sh
./scripts/make-review-archive.sh "feat: add deterministic geology engine"
./scripts/make-full-review-archive.sh "feat: add deterministic geology engine"
```

Paperスモークは`run-review-checks.sh`が`build/paper-smoke/`の使い捨てコピーで行う。元の`server/`を直接起動・変更しない。`build/review-checks/geology-engine-tests.txt`はPhase 2A専用test、固定seed/chunk、plan count/checksumを記録する。

## 開発規則

- NMS、CraftBukkit内部実装、reflectionは禁止。公開Paper/Bukkit APIだけを使う。
- 再現基準はJava Edition 1.16.5。設定・乱数順の変更は`docs/vanilla-1.16.5-geology.md`の一次資料根拠とgolden testを同時に確認する。
- ユーザーの既存変更を消さず、Phase範囲外の整形・改名・依存更新・機能追加をしない。
- 可変static state、共有Random、chunk/world永久cache、HashMap/HashSet iteration order依存を生成処理へ入れない。
- コミットはPhase単位の命令形Conventional Commitとし、`server/`、`build/`、調査JAR/mapping/decompile結果、生成JAR、log、archiveをstageしない。

## Phase 1 runtime仕様（維持）

- Y=70: `GRASS_BLOCK`、Y=68～69: `DIRT`、Y=5～67: `STONE`。
- Y=0: `BEDROCK`。Y=1～4: `y <= random.nextInt(5)`の旧式床。Y<0とY>70はAIR。
- 水、溶岩、洞窟、空洞、構造物、`DEEPSLATE`と深層岩系鉱石は生成しない。
- 固定spawnは`(0.5, 71.0, 0.5)`、biomeは全座標PLAINS。
- Vanilla noise、surface、caves、decorations、structuresはfalse。mobsはPaper既定を維持。
- generator idはnull、blank、大小文字を問わない`default`を受理する。

## Phase 2A実装

Phase 2は、形状・seed・chunk境界をPaper書き込みから分離して検証するため2A/2Bへ分割した。2Aはengineと単体テストまでであり、runtime behaviorはPhase 1から変えていない。

### 公式1.16.5設定

| order/salt | Feature | size | attempts | origin Y |
|---:|---|---:|---:|---|
| 0 | DIRT | 33 | 10 | 0以上256未満 |
| 1 | GRAVEL | 33 | 8 | 0以上256未満 |
| 2 | GRANITE | 33 | 10 | 0以上80未満 |
| 3 | DIORITE | 33 | 10 | 0以上80未満 |
| 4 | ANDESITE | 33 | 10 | 0以上80未満 |

当初期待表のDIRT/GRAVEL上限80は公式server JARと異なり、1.16.5公式値256を採用した。合計48 attempts/source chunk。追加順は表の順。

### クラスと責務

- `LegacyGeologyFeature` / `LegacyGeologyMaterial` / `LegacyGeologySettings`: 明示的stable order/saltと変更不能な設定一覧。
- `LegacyDecorationSeed`: 公式`WorldgenRandom#setDecorationSeed`相当の2 odd long係数と、`decorationSeed + stableSalt + 10000 * 6`のfeature seed。Java long overflowを仕様とする。
- `LegacyVeinGenerator`: 公式`OreFeature`型の角度、両端、33節補間、旧式sine curve、楕円体包含除去、bounding box、block中心判定、BitSet重複抑制。sinkへstreaming出力。
- `LegacyGeologyPlanner`: target chunk ownership。周囲3×3 source chunksを絶対Z昇順、X昇順、feature order、attempt、vein sequence順で再構築する。
- `LegacyPlacement` / `LegacyPlacementSink`: Phase 2Bの`LimitedRegion` adapterへ渡す絶対座標と安定順序metadata。
- `LegacyBlockKind` / `LegacyReplaceableBlock`: `STONE`、`GRANITE`、`DIORITE`、`ANDESITE`だけを置換可能とする純粋predicate。

size 33の端点偏位4.125と最大楕円体半径2.5625から水平到達距離は6.6875未満であり、source neighborhood radiusは1で十分。target外X/Zはsinkへ出さず、隣targetで同じsource veinを再構築する。隣chunkへ直接書かず、cacheを持たない。

同一plugin version・world seed・target chunkではthread数や処理順によらず同じplanになる。Minecraft 1.16.5同一seedとのblock座標完全一致は保証しない。Vanilla heightmap事前判定、全biome pipeline、実地形、他featureをPhase 2Aは再現しないためである。

固定seed `11652021`、target `(0,0)`のgolden planは5,564 placements、checksum `-4572519745665027215`。固定単一vein seed `123456789`、origin `(15,40,15)`は106 candidates、checksum `9016640837519771701`。

## テストとthread-safety

- 5 featureの全設定、salt一意性、変更不能list、合計48 attempts
- seed 0、正負、`Long.MIN_VALUE`/`MAX_VALUE`、負・大規模chunkのgolden values
- origin X/Z範囲とDIRT/GRAVEL 0～255、3 stone variants 0～79
- 旧式veinのcount/checksum/bounds/乱数後続値、座標重複なし
- X/Z境界と4 chunk交点の再構築union、target外placementなし、negative boundary
- source radiusの数式、stable order、target処理順非依存
- replacement enum全ケース、BEDROCK/DIRT/GRAVEL/AIR/液体/鉱石/DEEPSLATE置換不可
- 同一plannerを複数threadから呼ぶ一致試験。sleepやglobal lockなし
- Phase 1 terrain、bedrock、PLAINS provider、fixed spawn、Vanilla flag、plugin.yml/version回帰

plannerはimmutableな`LegacyVeinGenerator`参照だけを持ち、各呼び出しでlocal Random/BitSet/listを作る。static fieldはprimitive定数と変更不能設定だけで、placementやRandomを保持しない。

## Paper・Multiverseの現状

- Phase 2A engineはPaper runtimeへ未接続。
- `BlockPopulator`未登録、`getDefaultPopulators`未実装。
- `LimitedRegion`未使用。地中岩石はまだ実ワールドに配置されない。
- Multiverse-Core依存・runtime使用・実機試験は未実施。
- `server/plugins/multiverse-core-5.7.2.jar`はPhase 4用のローカル非追跡ファイルとして存在する。

## 後続Phase

- Phase 2B / version 0.3.0: `BlockPopulator`、`LimitedRegion`、Paper Material adapter、実ワールド5種類、fixed-seed/cross-chunk/distribution smoke。
- Phase 3: 石炭、鉄、金、redstone、diamond、lapis等の鉱石。Phase 2A/2Bへ前倒ししない。銅・DEEPSLATE系は生成しない。
- Phase 4: Multiverse-Core統合、`server/plugins/multiverse-core-5.7.2.jar`使用、大量chunk完全走査、性能・決定性、release candidate。

## レビュー重点

- 公式1.16.5値とコード・文書・golden testの一致
- feature salt/orderがenum ordinalやhashCodeに依存しないこと
- Random共有、時刻seed、`Math.random`、`ThreadLocalRandom`、可変cacheがないこと
- target外書き込みなし、負座標、3×3 radius、境界union、replacement順
- pure engineにBukkit World/Chunk/Block、`BlockPopulator`、`LimitedRegion`参照がないこと
- Phase 1 Paperスモークが0.3.0-alpha.1でPASSし、runtime geologyを誤って成功条件にしていないこと
- release JARへtest、server、Mojang調査物、Multiverse-Coreが混入しないこと
- commit後にworking tree cleanで、2種のreview archiveが最新HEAD subjectを検証して作られること
