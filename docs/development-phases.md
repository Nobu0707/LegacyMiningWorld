# 開発Phase

各Phaseは完了時に`AGENT.md`を更新します。後続Phaseへ未検証事項を持ち越す場合は、実装済みと区別して明記します。

## Phase 0 — 開発基盤（完了）

- Java 25 / Gradle Kotlin DSLプロジェクト
- Paperがロードできる最小プラグイン
- JUnit、JAR検査、ローカルPaperスモーク
- 差分レビュー・全体レビューアーカイブ

## Phase 1 — 基本地形（完了、version 0.2.0）

- `ChunkGenerator`と固定PLAINS `BiomeProvider`
- Y=70の草、Y=68～69の土、Y=5～67の石
- Y=0～4の旧式岩盤床、Y<0の空気
- Vanilla noise、surface、caves、decorations、structuresの無効化
- 固定スポーンとPaper実機スモーク

## Phase 2A — 地中岩石の純粋engine（完了、version 0.3.0-alpha.1）

- Mojang公式Java 1.16.5 server JAR/mappingによる一次資料調査
- DIRT、GRAVEL、GRANITE、DIORITE、ANDESITEの設定、seed、旧式楕円体鉱脈
- target chunk ownershipと周囲3×3 source chunkからの境界再構築
- replacement、negative chunk、golden値、並行性の単体テスト
- Paper runtimeへは未接続の純粋Java境界

## Phase 2B — Paper地中岩石配置（完了、version 0.3.0）

- 非推奨でない`BlockPopulator#populate(WorldInfo, Random, int, int, LimitedRegion)`
- `LimitedRegion`限定のMaterial adapterと順序保持applicator
- `getDefaultPopulators`によるstateless populator 1個の変更不能登録
- 5種類の実ワールド配置、固定seed 10 anchor、X/Z境界pair
- 地表・岩盤・空気保護、4チャンクdistribution、並行性、Phase 1/2A回帰
- release JAR・review checks・2種review archive

Multiverse-CoreはPhase 2Bでは依存・コピー・実行していない。

## Phase 3A — 鉱石pure engine（完了、version 0.4.0-alpha.1）

- Mojang公式Java 1.16.5 server JAR/mappingによる鉱石固有の一次資料調査
- COAL、IRON、GOLD、REDSTONE、DIAMOND、LAPISの6設定とstable salts 5～10
- uniform rangeとlapis depth-average、X/Z/Y/shapeの正確な乱数順
- `LegacyDecorationSeed`の互換拡張と既存`LegacyVeinGenerator`再利用
- 52 attempts/source chunk、target ownership、3×3 source neighborhood、X/Z/4chunk境界
- replacement、negative/large chunk、golden count/checksum、6 material count、並行性テスト
- Paper runtimeへは未接続。既存geology BlockPopulatorとLimitedRegion adapterは変更しない

Phase 3は、seed・分布・境界の純粋検証とPaper書き込みを分離して実装リスクを下げるため3A/3Bへ分割した。

## Phase 3B — Paper鉱石配置（完了、version 0.4.0）

- 単一stateless underground populator内でgeologyの後にoreを適用
- LimitedRegion限定のBukkit Material adapterとread-before-write applicator
- 6鉱石のruntime生成、固定ore anchor 14件、COAL X境界、IRON Z境界
- Y=11でCOAL/IRON/GOLD/REDSTONE/DIAMONDを固定実block検査
- combined 4chunk counts/checksum、並行性、geology 10 anchor/golden回帰
- 固定PLAINS仕様にはemeraldとbadlands追加goldを含めない
- copperおよびDEEPSLATE系鉱石を生成しない
- release JAR・review checks・2種review archive

Multiverse-CoreはPhase 3Bでも依存・コピー・実行していない。

## Phase 4A — Multiverse統合（完了、version 0.5.0-alpha.1）

- Multiverse-Core 5.7.2のJAR metadata、SHA-256、実command helpを確認
- `mv generators list`で`LegacyMiningWorld`を列挙
- `mv create legacy_mining_mv_smoke normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn`で固定worldを作成
- Paper APIだけに依存するtest-only verifier source set/JAR
- generator、seed、NORMAL、高度、spawn、PLAINS、geology 10/10、ore 14/14、Y=11、X/Z pairを実world検査
- chunks X/Z=-1..0の全高393,216 blockをsnapshot完全走査し、禁止Materialと未知非AIRが0
- 岩盤randomと分離したY=5..67 counts/checksum `-7305870198059528782`をpure/live独立経路で固定
- stop後のsecond bootでMultiverse auto-load、同じUUID/seed/generator/checksumを確認
- production Multiverse依存なし、release JARとtest verifier JARを分離
- 通常Paper単体スモークを回帰

## Phase 4B — release candidate（未実装）

- 大量chunk生成
- MCA/NBTまたは同等手段による大規模完全走査
- distribution report
- performance測定
- 2つのclean worldを使う決定的再生成比較
- world save/reload robustnessの範囲拡大
- release artifact最終化、version 1.0.0-rc.1候補
- ライセンスはユーザー決定まで未選択を維持
