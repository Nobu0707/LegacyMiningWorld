# LegacyMiningWorld

LegacyMiningWorld は、深層岩が追加される前の Java Edition 1.16.5 相当の採掘環境を Paper 上のカスタムワールドとして再現するためのプラグインです。対象環境は PaperMC 26.1.2 build 69 と Java 25、ビルド方式は Gradle Kotlin DSL です。

## 現在の状態

Phase 0（開発基盤）のみ完了対象です。Paper が安全にロードできる最小プラグイン、テスト、ローカルスモーク、レビューアーカイブ、引き継ぎ文書の基盤を用意しています。`ChunkGenerator`、`BlockPopulator`、地形、鉱石・岩石分布はまだ実装も公開もしていません。

## ビルドと検証

Java 25 を有効にして、リポジトリ直下で実行します。

```bash
./gradlew clean test build
./scripts/run-review-checks.sh
```

`run-review-checks.sh` は単体テスト、ビルド、JAR 検査に加え、使い捨ての `build/paper-smoke/` でローカル Paper を起動・停止し、プラグインのロードを確認します。スモーク単体も同じスクリプトで実施します。

スモークには次の2ファイルが必要です。`server/` はローカル専用であり、Git追跡やレビューアーカイブへの収録は禁止です。

```text
server/paper-26.1.2-69.jar
server/eula.txt              # eula=true
```

## レビューアーカイブ

Phaseのコミット後に、期待するHEAD件名の一部を引数で検証しながら作成できます。どちらのスクリプトも先に最新のレビュー検証を実行します。

```bash
./scripts/make-review-archive.sh "chore: bootstrap legacy mining world project"
./scripts/make-full-review-archive.sh "chore: bootstrap legacy mining world project"
```

差分レビュー版と、HEAD時点の追跡ファイル一式を含む全体レビュー版が作成されます。Paper本体、`server/`、ビルド成果物、ログ、秘密情報候補は除外されます。

## 最終仕様上の注意

現行通常ディメンションの `minY=-64` に対し、初期設計では `Y=-64～-1` を空気のまま残し、`Y=0` を旧式ワールドの底面として扱います。確定済みの基本層は `Y=0` が岩盤、`Y=1～4` が旧式岩盤床、`Y=5～67` が石、`Y=68～69` が土、`Y=70` が草ブロックです。水、溶岩、洞窟、構造物、深層岩は生成しません。詳細なPhase分割は [docs/development-phases.md](docs/development-phases.md) を参照してください。

Phase 4では、Multiverse-Coreから次の形式で利用できる状態を目指します（現在は未実装です）。

```text
/mv create legacy_mining normal --generator LegacyMiningWorld
```

## ライセンス

License not yet selected.
