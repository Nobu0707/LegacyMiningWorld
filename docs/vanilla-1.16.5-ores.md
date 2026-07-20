# Java Edition 1.16.5 鉱石調査

## 調査対象と一次資料

- 調査日: 2026-07-20
- 対象version: Java Edition 1.16.5（version ID `1.16.5`）
- 公式version manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
  - 取得時SHA-256: `4a1ca36883d77dc2c448b6abffe94abf827ec5a5c16c0f7f79de748781545923`
  - 1.16.5 version JSON SHA-1: `fba9f7833e858a1257d810d21a3a9e3c967f9077`
- 公式version JSON: `https://piston-meta.mojang.com/v1/packages/fba9f7833e858a1257d810d21a3a9e3c967f9077/1.16.5.json`
  - 取得時SHA-256: `3eeeab7b3165cc5263dc26ff8fe114fdb718b75a0244efead9d19635d090ba72`
- Mojang公式server JAR
  - SHA-1: `1b557e7b033b583cd9f66746b7a9ab1ec1673ced`
  - SHA-256: `58f329c7d2696526f948470aa6fd0b45545039b64cb75015e64c12194b373da6`
- Mojang公式server mappings
  - SHA-1: `41285beda6d251d190f2bf33beadd4fee187df7a`
  - SHA-256: `b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7`

既存の`/tmp`ファイルはそのまま信用せず、version JSON、server JAR、server mappingsを上記SHA-1/SHA-256と再照合した。公式mappingで難読化名を対応付け、`javap`で公式server JARのバイトコードを確認した。調査物と出力は`/tmp`だけに置き、Git、release JAR、review archiveへ含めない。本書は定数、式、呼び出し関係と挙動の要約だけを記録し、Mojangコード本文を転載していない。

## 関連クラスと確認結果

- `net.minecraft.data.worldgen.Features`: `ORE_COAL`、`ORE_IRON`、`ORE_GOLD`、`ORE_REDSTONE`、`ORE_DIAMOND`、`ORE_LAPIS`のblock、size、decorator連結
- `net.minecraft.data.worldgen.BiomeDefaultFeatures#addDefaultUndergroundVariety`: 地質5 featureの追加
- `BiomeDefaultFeatures#addDefaultOres`: 本Phaseの6 featureの追加順
- `net.minecraft.data.worldgen.biome.VanillaBiomes#plainsBiome`: `addDefaultUndergroundVariety`の直後に`addDefaultOres`を呼ぶ順序
- `ConfiguredFeature#decorated`と`DecoratedFeature`: decoratorの入れ子と実行順
- `Decoratable#range/#squared/#count`: uniform range、square、固定countの構成
- `CountDecorator`: 固定countの回数だけ入力位置を渡す。固定値の取得は追加の`Random`を消費しない
- `SquareDecorator`: X、次にZへそれぞれ`nextInt(16)`を加える
- `RangeDecorator`と`RangeDecoratorConfiguration`: `bottomOffset + nextInt(maximum - topOffset)`
- `DepthAverageDecorator`と`DepthAverageConfigation`: baselineとspreadを使う2回の`nextInt`
- `OreFeature`と`OreConfiguration`: 旧式楕円体鉱脈、heightmap事前判定、target rule test
- `OreConfiguration.Predicates#NATURAL_STONE`: overworld natural stone tag
- `WorldgenRandom#setDecorationSeed/#setFeatureSeed`: decoration/feature seed式
- `GenerationStep.Decoration.UNDERGROUND_ORES`: ordinal 6
- `Mth#sin/#floor/#ceil/#lerp`: 鉱脈形状で使う旧実装の数値処理

## 確定設定

公式値は事前の期待表と一致した。

| stable order / salt | Feature | material | vein size | attempts/chunk | origin Y |
|---:|---|---|---:|---:|---|
| 5 | COAL | COAL_ORE | 17 | 20 | uniform 0以上128未満 |
| 6 | IRON | IRON_ORE | 9 | 20 | uniform 0以上64未満 |
| 7 | GOLD | GOLD_ORE | 9 | 2 | uniform 0以上32未満 |
| 8 | REDSTONE | REDSTONE_ORE | 8 | 8 | uniform 0以上16未満 |
| 9 | DIAMOND | DIAMOND_ORE | 8 | 1 | uniform 0以上16未満 |
| 10 | LAPIS | LAPIS_ORE | 7 | 1 | depth-average baseline 16 / spread 16 |

1 source chunkあたりの合計attempt数は52。PLAINSでは同じ`UNDERGROUND_ORES` stepへDIRT、GRAVEL、GRANITE、DIORITE、ANDESITEの順で地質featureが追加され、その直後に表の6鉱石が追加される。したがって既存地質salt 0～4を維持し、鉱石には5～10を明示する。enum ordinalはseed契約へ使わない。badlands追加金とmountain emeraldは別メソッドであり、固定PLAINS仕様の本engineには含めない。

## decoratorと高度分布

uniform鉱石はconfigured featureへ`range(maxY)`、`squared()`、`count(attempts)`の順にwrapperを追加している。wrapperは外側から実行されるため、各attemptの実際の乱数順は次になる。

1. fixed countがattempt位置を渡す
2. squareがlocal Xへ`nextInt(16)`
3. squareがlocal Zへ`nextInt(16)`
4. rangeがorigin Yへ`nextInt(maxY)`
5. `OreFeature`が鉱脈形状乱数を消費する

uniform式は`minInclusive + nextInt(maxExclusive - minInclusive)`で、本設定のminはすべて0。clamp、surface補正、浮動小数、Gaussianは使わず、1 attemptにつきY用の乱数呼び出しは1回である。

lapisはdepth-averageを適用後に`squared()`を適用し、`count(1)`は明示されない。squareが単一入力位置をX/Zへ展開するためattemptは暗黙に1回で、X、Zの後にY用乱数を2回消費する。

```text
originY = baseline + nextInt(spread) + nextInt(spread) - spread
        = nextInt(16) + nextInt(16)
```

結果は0以上30以下。0と30が各1組、15が16組の組合せを持つ三角分布であり、`nextInt(31)`のuniformへ近似しない。

## seed、形状、失敗時の乱数

seedはPhase 2Aと同じ`LegacyDecorationSeed`を使う。Java `long` overflowは2の補数のままとする。

1. `new Random(worldSeed)`から2つの`nextLong()`を得て各最下位bitを1にする。
2. source chunk block原点X/Zとの積和をworld seedとXORし、decoration seedを得る。
3. `featureSeed = decorationSeed + stableSalt + 10000 * 6`とする。6は`UNDERGROUND_ORES` ordinal。
4. featureごとに新しい`Random(featureSeed)`を作り、全attemptを順に消費する。

`OreFeature`形状はPhase 2Aで実装済みの`LegacyVeinGenerator`と同じである。角度、size/8の両端、Yずれ、size個の補間楕円体、sine曲線、包含除去、bounding box、block中心判定、BitSet重複除去を再利用し、鉱石用形状を複製しない。既存size 33 golden（106候補、checksum `9016640837519771701`）は変更しない。

公式`OreFeature#place`は角度と両端Yの乱数を消費した後、保守的bounding boxの最低Yと`OCEAN_FLOOR_WG` heightmapを比較する。全対象columnが低い場合は楕円体用`nextDouble()`を消費せず失敗する。Phase 3A plannerはworld/heightmapを受け取らない純粋候補engineなので、この早期終了を実行せず、常に形状候補を生成する。この差は後続attemptのrandom stateも変え得るため、旧Vanilla同一seedの完全座標一致を保証しない要因として明示する。

## replacement、radius、ownership

公式JAR内`data/minecraft/tags/blocks/base_stone_overworld.json`は次の4 blockだけを含む。

- STONE
- GRANITE
- DIORITE
- ANDESITE

DIRT、GRAVEL、BEDROCK、AIR、液体、地表block、既存鉱石、DEEPSLATE/TUFF/CALCITE、その他は置換しない。Phase 3Bでは地質適用後の自然石4種を鉱石へ置換できるが、先に配置済みの鉱石は後続featureから保護する。Y=0のBEDROCKとY<0のAIRも変えない。

最大sizeはCOALの17。原点からの最大水平到達距離は、端点偏位`17 / 8 = 2.125`と楕円体半径上限`(2 * (17 / 16) + 1) / 2 = 1.5625`の和3.6875である。2 chunk離れたsourceの最近block中心までの16.5より小さいため、targetのX/Z各±1、3×3 source neighborhoodで全候補を包含できる。

`LegacyOrePlanner`はtarget chunkだけを所有する。source chunkをZ昇順、次にX昇順で列挙し、その中でfeature stable order、attempt、vein sequence順に再構築する。targetの絶対X/Z範囲内だけをsinkへ渡し、隣targetは同じsource veinをseedから再生成する。隣chunkへの直接書き込み、cache、pending map、共有Randomは使わない。負座標は絶対範囲比較で扱い、現実的なworld border座標はint block座標に安全に変換し、int範囲を越えるchunk入力は例外で拒否する。

pure plannerは同一座標へ届く複数feature候補をstable順のまますべてstreamする。Phase 3Bで現在blockを逐次読み、最初の鉱石配置後はreplacement predicateがfalseになることで先行featureを優先する。

## Phase 3Aの保証範囲とPhase 3B

Phase 3Aが再現するのは、公式6設定、feature順とexplicit salt、decoration/feature seed式、decorator由来X/Z/Y乱数順、uniform/depth-average、Java `Random`、旧式鉱脈形状、target ownership、境界再構築、および同じplugin version内の決定性である。

Vanilla biome decoration pipeline全体、他feature、heightmap早期終了、実block置換の成功/失敗、Vanilla noise地形、洞窟、水、溶岩は再現しない。LegacyMiningWorldは固定Y=70/PLAINSで、Phase 2Aと同様にfeatureごとのRandomを独立再構築する。このため「1.16.5の設定・分布・形状に準拠」と「1.16.5同一seedのblock座標完全一致」は同義ではなく、後者を保証しない。

Phase 3A時点ではPaper runtimeへ接続しなかった。`getDefaultPopulators`、`LegacyGeologyPopulator`、`LimitedRegion` adapter、Bukkit ore Material変換は変更せず、実ワールドには鉱石を置かなかった。後続Phase 3Bの設計として、単一のstateless underground populator内で既存geology applicatorを先に、ore applicatorを後に同期適用する方式を選定した。`getDefaultPopulators`は変更不能な1要素listとし、同じpopulate呼び出し内だけで`LimitedRegion`を使う。Multiverse-CoreはPhase 4まで使用しない。

## Phase 3B runtime接続

Phase 3B/version 0.4.0で6鉱石をPaper runtimeへ接続した。単一`LegacyUndergroundPopulator`内でgeology後にoreを適用し、明示Material adapter、read-before-write、target ownership、適用Y=0..67を使用する。Y<0とY>=68のcandidateは現在blockに関係なくskipする。

固定seed `11652021`の4chunk combined countはCOAL 867、IRON 443、GOLD 48、REDSTONE 106、DIAMOND 17、LAPIS 19、checksumは`-7165395187979696007`。Y=11にはCOAL 6、IRON 5、GOLD 8、REDSTONE 7、DIAMOND 4が残り、14固定anchor、COAL X境界pair、IRON Z境界pairをPaper実block検査へ使用する。geology-only goldenと10既存anchorは不変である。

このruntime接続も旧Vanilla同一seedとのblock座標完全一致を保証しない。Multiverse-Core統合と大量chunk完全走査はPhase 4で扱う。
