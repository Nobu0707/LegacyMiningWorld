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

## Phase 3 — 鉱石（未実装）

- 石炭、鉄、金、レッドストーン、ダイヤモンド、ラピスラズリ
- Java 1.16.5の分布と鉱脈形状
- 固定PLAINS仕様にはエメラルドとバッドランド追加金を含めない
- 銅およびDEEPSLATE系鉱石を生成しない
- 統計・chunk境界・Paper実機テスト

## Phase 4 — 統合・リリース候補（未実装）

- Multiverse-Coreでgenerator列挙とworld作成を確認
- `server/plugins/multiverse-core-5.7.2.jar`をこのPhaseで初めて使用
- 大量chunk生成、禁止block走査、性能、決定性、再生成一致
- README完成とrelease candidate
