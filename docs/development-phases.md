# 開発Phase

各Phaseは新しいCodexスレッドで実施し、完了時に `AGENT.md` を更新します。後続Phaseへ未検証事項を持ち越す場合は、実装済みと区別して明記します。

## Phase 0 — 開発基盤（完了）

- 再現可能なJava 25 / Gradle Kotlin DSLプロジェクト
- Paperがロードできる最小プラグイン
- `AGENT.md`、README、レビュー基盤
- JUnitテスト、JAR検査、ローカルPaperスモーク
- 差分レビュー・全体レビューアーカイブ

Phase 0 時点ではジェネレーター本体を実装・公開しませんでした。

## Phase 1 — 基本地形（完了）

- `ChunkGenerator`の登録と固定PLAINS系`BiomeProvider`
- Y=70の草、Y=68～69の土、Y=5～67の石
- Y=0～4の旧式岩盤床、Y<0の空気
- Vanilla noise、surface、caves、decorations、structuresの無効化
- 固定スポーン位置
- 地形レイヤー単体テストとPaperスモーク

バージョン 0.2.0 で、公開 Paper/Bukkit API の分割生成メソッドを用いて実装・実機確認済みです。

## Phase 2A — 地中岩石の純粋エンジン（完了）

- Mojang公式Java 1.16.5 server JARと公式mappingによる一次資料調査
- 土、砂利、花崗岩、閃緑岩、安山岩の設定値と追加順を確定
- Java `Random`によるdecoration/feature seedと旧式楕円体鉱脈形状
- target chunk ownership、周囲3×3 source chunkからの境界再構築
- natural stone置換規則、negative chunk、golden値、並行性の単体テスト
- Paper runtimeへは未接続

バージョン 0.3.0-alpha.1 で純粋Java engineとPhase 2B向けstreaming API境界までを実装済みです。旧Vanillaの同一seedとblock座標単位の完全一致は保証せず、同一plugin version・world seed・chunk座標に対するplanの決定性を保証します。

## Phase 2B — Paper地中岩石配置（未実装）

- `BlockPopulator`、`LimitedRegion`、`getDefaultPopulators`
- 純粋block kindとBukkit `Material`のadapter
- 5種類の実ワールド配置と固定seed Paperスモーク
- cross-chunk実機確認とdistribution sanity check

完了バージョンは0.3.0とする。

## Phase 3 — 鉱石（未実装）

- 石炭、鉄、金、レッドストーン、ダイヤモンド、ラピスラズリ
- Java 1.16.5の分布と鉱脈形状の再現
- 銅および深層岩系鉱石を生成しない
- 固定PLAINS世界の基本仕様にはエメラルドとバッドランド追加金を含めない
- 統計テストとChunk境界テスト

## Phase 4 — 統合・リリース候補（未実装）

- Multiverse-Coreで `/mv generators` と `/mv create legacy_mining normal --generator LegacyMiningWorld` を確認
- ローカル専用`server/plugins/multiverse-core-5.7.2.jar`をこのPhaseで初めて使用
- 大量Chunk生成試験
- 水、溶岩、洞窟、構造物、深層岩がないことの走査
- 鉱石分布レポート
- 性能、決定性、再生成一致の確認
- README完成、リリース候補、full review archive
