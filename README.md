# LegacyMiningWorld

LegacyMiningWorld 0.2.0 は、深層岩が追加される前の Java Edition 1.16.5 相当の採掘環境を Paper 上のカスタムワールドとして再現するためのプラグインです。対象環境は PaperMC 26.1.2 build 69 と Java 25、ビルド方式は Gradle Kotlin DSL です。

## 現在の状態

Phase 0（開発基盤）と Phase 1（基本地形）が完了しています。標準 Bukkit ジェネレーター `LegacyMiningWorld` または `LegacyMiningWorld:default` として利用でき、全座標に固定 PLAINS の水平地形を生成します。

基本層は Y=0～4 の旧式岩盤床、Y=5～67 の `STONE`、Y=68～69 の `DIRT`、Y=70 の `GRASS_BLOCK` です。Y<0 と Y>70 は空気のままで、水・溶岩・洞窟・装飾・構造物・Vanilla 鉱石・深層岩は生成しません。固定スポーンは `(0.5, 71.0, 0.5)` です。

鉱石、花崗岩・閃緑岩・安山岩、地中の土・砂利塊、`BlockPopulator` と `LimitedRegion` は未実装です。これらは Phase 2 以降で追加します。

## ビルドと検証

Java 25 を有効にして、リポジトリ直下で実行します。

```bash
./gradlew clean test build
./scripts/run-review-checks.sh
```

`run-review-checks.sh` は単体テスト、ビルド、JAR 検査に加え、使い捨ての `build/paper-smoke/` で `LegacyMiningWorld` をデフォルトワールドのジェネレーターに指定します。実ブロック層、PLAINS バイオーム、ロードと正常停止まで検査します。

スモークには次の2ファイルが必要です。`server/` はローカル専用であり、Git追跡やレビューアーカイブへの収録は禁止です。

```text
server/paper-26.1.2-69.jar
server/eula.txt              # eula=true
```

## レビューアーカイブ

Phaseのコミット後に、期待するHEAD件名の一部を引数で検証しながら作成できます。どちらのスクリプトも先に最新のレビュー検証を実行します。

```bash
./scripts/make-review-archive.sh "feat: add legacy terrain generator"
./scripts/make-full-review-archive.sh "feat: add legacy terrain generator"
```

差分レビュー版と、HEAD時点の追跡ファイル一式を含む全体レビュー版が作成されます。Paper本体、`server/`、ビルド成果物、ログ、秘密情報候補は除外されます。

## 最終仕様上の注意

現行通常ディメンションの `minY=-64` は変更せず、`Y=-64～-1` を空気のまま残し、`Y=0` を旧式ワールドの底面として扱います。この構成は Paper 実機スモークで確認済みです。詳細な Phase 分割は [docs/development-phases.md](docs/development-phases.md) を参照してください。

標準 Bukkit の generator 公開は実装済みですが、Multiverse-Core 本体を使った統合試験は Phase 4 の対象であり、まだ完了していません。最終的には次の形式での利用を確認します。

```text
/mv create legacy_mining normal --generator LegacyMiningWorld
```

## ライセンス

License not yet selected.
