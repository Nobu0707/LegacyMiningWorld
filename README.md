# LegacyMiningWorld

LegacyMiningWorld 0.3.0は、深層岩が追加される前のJava Edition 1.16.5型の採掘環境をPaper上のカスタムワールドとして生成するプラグインです。対象環境はPaperMC 26.1.2 build 69とJava 25、ビルド方式はGradle Kotlin DSLです。

## 現在の状態

Phase 0（開発基盤）、Phase 1（基本地形）、Phase 2A（決定論的地中岩石engine）、Phase 2B（Paper実ワールド配置）が完了しています。標準Bukkit generator `LegacyMiningWorld`または`LegacyMiningWorld:default`として、水平な基本地形と次の5種類の地中岩石を生成します。

- `DIRT`
- `GRAVEL`
- `GRANITE`
- `DIORITE`
- `ANDESITE`

基本層はY=0～4の旧式岩盤床、Y=5～67の`STONE`、Y=68～69の`DIRT`、Y=70の`GRASS_BLOCK`です。Y<0とY>70は空気のままで、水・溶岩・洞窟・構造物・Vanilla鉱石・`DEEPSLATE`は生成しません。固定スポーンは`(0.5, 71.0, 0.5)`、biomeは全座標`PLAINS`です。

地中岩石はPaperの非推奨でない`BlockPopulator`と`LimitedRegion`だけを通じて配置します。公式1.16.5設定は全featureがvein size 33、attemptはDIRT/GRAVEL/GRANITE/DIORITE/ANDESITEの順に10/8/10/10/10です。origin YはDIRTとGRAVELが0以上256未満、3種のstone variantが0以上80未満です。DIRT/GRAVELも80未満とする当初案とは公式server JARが異なったため、一次資料の256を採用しました。

置換対象は`STONE`、`GRANITE`、`DIORITE`、`ANDESITE`だけです。plannerのDIRT→GRAVEL→GRANITE→DIORITE→ANDESITE順を保つため、後続stone variantは先行stone variantを置換できます。一方、いったん配置されたDIRT/GRAVEL、BEDROCK、AIR、地表、液体、鉱石、DEEPSLATEは置換しません。Y=1～4でBEDROCKにならなかったSTONEは地中岩石に置換され得ますが、Y=0の全面BEDROCKは不変です。

world seedと周囲3×3 source chunksからtarget chunk内の配置だけを再構築します。隣chunkへ直接書かず、共有cacheや保留変更を持ちません。同一plugin version・world seed・chunk座標では同じ結果になりますが、Vanilla heightmap、全biome decoration pipeline、実地形、他featureを再現しないため、Minecraft 1.16.5の同一seedとblock座標単位の完全一致は保証しません。詳細は[Java Edition 1.16.5 地中岩石調査](docs/vanilla-1.16.5-geology.md)と[Paper統合](docs/geology-paper-integration.md)を参照してください。

## generatorの利用

標準Bukkit設定では、対象worldを次のように指定します。

```yaml
worlds:
  legacy_mining_world:
    generator: LegacyMiningWorld
```

generator idは未指定、blank、大小文字を問わない`default`を受理します。Multiverse-Core統合はPhase 4の範囲であり、0.3.0では未検証です。`server/plugins/multiverse-core-5.7.2.jar`は今回の依存、Paperスモーク、release JARのいずれにも使用していません。

## ビルドと検証

Java 25を有効にして、リポジトリ直下で実行します。

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon test --tests 'net.nobu0707.legacyminingworld.geology.*'
./gradlew --no-daemon geologyEngineTest
./gradlew --no-daemon geologyAdapterTest
./gradlew --no-daemon build
./scripts/run-review-checks.sh
```

`run-review-checks.sh`は全JUnit、Phase 2A/2B専用suite、release JAR検査に加え、`build/paper-smoke/`の使い捨てPaper環境を作ります。seed `11652021`でX/Zともchunk -1..0をforce-loadし、追跡済み10 anchor、X/Z境界、地表、BEDROCK、AIR、PLAINSを実blockで確認します。元の`server/`は起動・変更せず、Multiverse-Coreをコピーしません。

スモークには次のローカル専用ファイルが必要です。Git追跡とレビューアーカイブへの収録は禁止です。

```text
server/paper-26.1.2-69.jar
server/eula.txt              # eula=true
```

## レビューアーカイブ

Phaseコミット後に、期待するHEAD件名を検証しながら差分版と全体版を作成します。

```bash
./scripts/make-review-archive.sh "feat: populate legacy geology"
./scripts/make-full-review-archive.sh "feat: populate legacy geology"
```

どちらも最新のレビュー検証を再実行します。Paper本体、Multiverse-Core、`server/`、ビルド成果物、ログ、秘密情報候補はアーカイブから除外されます。

## 後続範囲

Phase 3は石炭、鉄、金、redstone、diamond、lapis等の1.16.5型鉱石を追加します。銅・DEEPSLATE系鉱石は生成しません。Phase 4で初めてMultiverse-Core統合、大量chunk試験、性能と再生成一致を扱います。

## ライセンス

License not yet selected.
