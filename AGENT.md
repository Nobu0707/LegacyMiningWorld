# LegacyMiningWorld 引き継ぎ

## プロジェクト情報

- プロジェクト: LegacyMiningWorld
- Group ID / package root: `net.nobu0707`
- main class: `net.nobu0707.legacyminingworld.LegacyMiningWorldPlugin`
- 対象: PaperMC 26.1.2 build 69 / Paper API `26.1.2.build.69-stable`
- Java: toolchain・コンパイル対象ともに25
- バージョン: `gradle.properties`の`legacyminingworld_version`

## リポジトリ構成とコマンド

- `src/main/`: 最小プラグインと`plugin.yml`
- `src/test/`: Phase 0構成テスト
- `docs/`: Phase計画と設計判断
- `scripts/`: 検証・レビューアーカイブ作成
- `server/`: ローカルPaper本体とEULA。Git追跡・アーカイブ収録禁止

```bash
./gradlew clean test build
./scripts/run-review-checks.sh
./scripts/make-review-archive.sh "expected HEAD subject substring"
./scripts/make-full-review-archive.sh "expected HEAD subject substring"
```

Paperスモークは`run-review-checks.sh`が`build/paper-smoke/`の使い捨てコピーで行う。元の`server/`を直接起動・変更しない。

## 開発規則

- NMS、CraftBukkit内部実装、リフレクションは禁止。公開Paper/Bukkit APIだけを使う。
- 再現基準はJava Edition 1.16.5。
- Phaseごとに新しいCodexスレッドを使う。
- ユーザーの既存変更を消さず、Phase範囲外の整形・改名・機能追加をしない。
- コミットはPhase単位で、命令形のConventional Commit形式を基本とする。
- 作業完了時は、完了Phase、検証結果、設計判断、未実装事項をこの`AGENT.md`へ必ず反映する。

## 確定仕様

- 地表は常にY=70: `GRASS_BLOCK`。
- Y=68～69: `DIRT`。Y=5～67: `STONE`。
- Y=0: `BEDROCK`。Y=1～4: Java 1.16.5の旧式バニラ床相当の`BEDROCK`/`STONE`混在。
- 水、溶岩、洞窟、空洞、ダンジョン、構造物、`DEEPSLATE`と深層岩系鉱石は生成しない。
- 現行世界の通常の`minY=-64`に対し、初期設計ではY=-64～-1を空気のまま残し、Y=0を旧式底面として扱う。
- Multiverse-Coreは必須依存にも`compileOnly`依存にもしない。最終的に標準Bukkitジェネレーターとして公開する。

## 現在の完了状態

Phase 0を完了対象として整備済み。Gradle Wrapper、最小Paperプラグイン、設定テスト、JAR検査、ローカルPaperスモーク、レビューアーカイブ基盤、READMEと開発文書がある。Phase 0では`getDefaultWorldGenerator`をオーバーライドしていないため、未完成ジェネレーターは選択できない。

## 未実装事項

- `ChunkGenerator`、`ChunkData`、`BiomeProvider`、`BlockPopulator`、`LimitedRegion`
- 地形レイヤー、岩盤乱数、スポーン位置
- 地中岩石、鉱石、Chunk境界処理、決定論的seed処理
- Multiverse-Core連携実機試験、大量Chunk試験、分布・性能レポート

## 設計候補と未決事項

- Y<0を空気にする方針は初期設計。Phase 1で公開API上の挙動を検証する。
- 固定PLAINS系`BiomeProvider`とVanilla生成フラグ無効化の具体的実装はPhase 1で確定する。
- 1.16.5の岩盤、岩石、鉱石の定数と乱数手順は、後続Phaseでコード根拠を記録して確定する。
- 固定スポーン座標は未決定。

## 後続Phase

- Phase 1: 基本地形、BiomeProvider、生成無効化、固定スポーン、単体・実機テスト
- Phase 2: 地中岩石、Chunk境界、LimitedRegion、seedと置換ルール
- Phase 3: 1.16.5鉱石、鉱脈形状、統計・境界テスト
- Phase 4: Multiverse統合、大量Chunk走査、性能・決定性、リリース候補

詳細は`docs/development-phases.md`を参照する。

## レビュー重点

- Java 25固定とPaper APIの正確なバージョン、`compileOnly`維持
- `plugin.yml`のversion展開とJAR内容
- Phase外のジェネレーター公開や外部ランタイム依存がないこと
- `server/`、ログ、JAR、秘密情報がGitやアーカイブへ混入しないこと
- スモークが元の`server/`を汚さず、失敗や例外を見逃さないこと
- 後続生成処理のChunk境界、seed決定性、現行minYとの整合
