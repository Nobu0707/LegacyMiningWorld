# LegacyMiningWorld

LegacyMiningWorld 0.4.0-alpha.1は、深層岩が追加される前のJava Edition 1.16.5型の採掘環境をPaper上のカスタムワールドとして生成するプラグインです。対象環境はPaperMC 26.1.2 build 69とJava 25、ビルド方式はGradle Kotlin DSLです。

## 現在の状態

Phase 0（開発基盤）、Phase 1（基本地形）、Phase 2A/2B（地中岩石）、Phase 3A（決定論的鉱石pure engine）が完了しています。Phase 3は、複雑なseed・高度分布・境界再構築をPaper書き込みから分離して検証するため3A/3Bへ分割しました。

現在のPaper runtimeは、標準Bukkit generator `LegacyMiningWorld`または`LegacyMiningWorld:default`として、水平な基本地形と次の5種類の地中岩石を生成します。

- `DIRT`
- `GRAVEL`
- `GRANITE`
- `DIORITE`
- `ANDESITE`

基本層はY=0～4の旧式岩盤床、Y=5～67の`STONE`、Y=68～69の`DIRT`、Y=70の`GRASS_BLOCK`です。Y<0とY>70は空気のままで、水・溶岩・洞窟・構造物・`DEEPSLATE`は生成しません。固定スポーンは`(0.5, 71.0, 0.5)`、biomeは全座標`PLAINS`です。Phase 3Aの鉱石engineは完成していますが、Paper実ワールドへはまだ接続していないため、0.4.0-alpha.1のruntimeには鉱石が生成されません。

地中岩石はPaperの非推奨でない`BlockPopulator`と`LimitedRegion`だけを通じて配置します。公式1.16.5設定は全featureがvein size 33、attemptはDIRT/GRAVEL/GRANITE/DIORITE/ANDESITEの順に10/8/10/10/10です。origin YはDIRTとGRAVELが0以上256未満、3種のstone variantが0以上80未満です。DIRT/GRAVELも80未満とする当初案とは公式server JARが異なったため、一次資料の256を採用しました。

置換対象は`STONE`、`GRANITE`、`DIORITE`、`ANDESITE`だけです。plannerのDIRT→GRAVEL→GRANITE→DIORITE→ANDESITE順を保つため、後続stone variantは先行stone variantを置換できます。一方、いったん配置されたDIRT/GRAVEL、BEDROCK、AIR、地表、液体、鉱石、DEEPSLATEは置換しません。Y=1～4でBEDROCKにならなかったSTONEは地中岩石に置換され得ますが、Y=0の全面BEDROCKは不変です。

world seedと周囲3×3 source chunksからtarget chunk内の配置だけを再構築します。隣chunkへ直接書かず、共有cacheや保留変更を持ちません。同一plugin version・world seed・chunk座標では同じ結果になりますが、Vanilla heightmap、全biome decoration pipeline、実地形、他featureを再現しないため、Minecraft 1.16.5の同一seedとblock座標単位の完全一致は保証しません。詳細は[Java Edition 1.16.5 地中岩石調査](docs/vanilla-1.16.5-geology.md)、[Java Edition 1.16.5 鉱石調査](docs/vanilla-1.16.5-ores.md)、[Paper地質統合](docs/geology-paper-integration.md)を参照してください。

## Phase 3A 鉱石pure engine

Mojang公式1.16.5 server JARとmappingを一次資料として、次の6鉱石の設定、feature seed、decorator乱数順、旧式楕円体鉱脈、target ownership、X/Z境界再構築を純粋Javaで固定しました。既存`LegacyVeinGenerator`を再利用し、地質salt 0～4とgolden値は変更していません。

| salt | Feature | material | size | attempts/chunk | origin Y |
|---:|---|---|---:|---:|---|
| 5 | COAL | `COAL_ORE` | 17 | 20 | uniform 0..127 |
| 6 | IRON | `IRON_ORE` | 9 | 20 | uniform 0..63 |
| 7 | GOLD | `GOLD_ORE` | 9 | 2 | uniform 0..31 |
| 8 | REDSTONE | `REDSTONE_ORE` | 8 | 8 | uniform 0..15 |
| 9 | DIAMOND | `DIAMOND_ORE` | 8 | 1 | uniform 0..15 |
| 10 | LAPIS | `LAPIS_ORE` | 7 | 1 | depth-average baseline 16 / spread 16 |

合計は52 attempts/source chunkです。uniformは上端排他の`nextInt`を1回、lapisは`nextInt(16) + nextInt(16)`を使い0..30へ分布する三角分布です。Y=11は6鉱石すべてのorigin候補範囲に含まれます。1.16.5準拠範囲に銅、DEEPSLATE系鉱石、emerald、badlands追加goldは含めません。

固定seed `11652021`、target `(0,0)`のpure planは613 candidates、checksum `-6214814787450030649`です。内訳はCOAL 431、IRON 111、GOLD 14、REDSTONE 49、DIAMOND 2、LAPIS 6。これは候補planであり、Paper worldへ適用済みのblock数ではありません。

## generatorの利用

標準Bukkit設定では、対象worldを次のように指定します。

```yaml
worlds:
  legacy_mining_world:
    generator: LegacyMiningWorld
```

generator idは未指定、blank、大小文字を問わない`default`を受理します。Multiverse-Core統合はPhase 4の範囲であり、0.4.0-alpha.1では未検証です。`server/plugins/multiverse-core-5.7.2.jar`は今回の依存、Paperスモーク、release JARのいずれにも使用していません。

## ビルドと検証

Java 25を有効にして、リポジトリ直下で実行します。

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon test --tests 'net.nobu0707.legacyminingworld.geology.*'
./gradlew --no-daemon geologyEngineTest
./gradlew --no-daemon geologyAdapterTest
./gradlew --no-daemon oreEngineTest
./gradlew --no-daemon build
./scripts/run-review-checks.sh
```

`run-review-checks.sh`は全JUnit、Phase 2A/2B/3A専用suite、release JAR検査に加え、`build/paper-smoke/`の使い捨てPaper環境を作ります。seed `11652021`でX/Zともchunk -1..0をforce-loadし、追跡済み10 geology anchor、X/Z境界、地表、BEDROCK、AIR、PLAINSを実blockで確認します。Phase 3AではPaper鉱石を成功条件にせず、既存geology runtimeの回帰だけを検査します。元の`server/`は起動・変更せず、Multiverse-Coreをコピーしません。

スモークには次のローカル専用ファイルが必要です。Git追跡とレビューアーカイブへの収録は禁止です。

```text
server/paper-26.1.2-69.jar
server/eula.txt              # eula=true
```

## レビューアーカイブ

Phaseコミット後に、期待するHEAD件名を検証しながら差分版と全体版を作成します。

```bash
./scripts/make-review-archive.sh "feat: add deterministic ore engine"
./scripts/make-full-review-archive.sh "feat: add deterministic ore engine"
```

どちらも最新のレビュー検証を再実行します。Paper本体、Multiverse-Core、`server/`、ビルド成果物、ログ、秘密情報候補はアーカイブから除外されます。

## 後続範囲

Phase 3B/version 0.4.0で、単一のstateless underground populator内にgeology適用後のore適用を接続し、LimitedRegion/Bukkit Material adapter、実world anchor、Y=11と分布sanityを検証します。Phase 4で初めてMultiverse-Core統合、大量chunk試験、性能と再生成一致を扱います。

## ライセンス

License not yet selected.
