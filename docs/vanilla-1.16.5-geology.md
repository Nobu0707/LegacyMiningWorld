# Java Edition 1.16.5 地中岩石調査

## 調査対象と一次資料

- 調査日: 2026-07-20
- 対象: Java Edition 1.16.5（version ID `1.16.5`）
- 公式version manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
  - 取得時SHA-256: `4a1ca36883d77dc2c448b6abffe94abf827ec5a5c16c0f7f79de748781545923`
  - 1.16.5 version JSON SHA-1: `fba9f7833e858a1257d810d21a3a9e3c967f9077`
- 公式version JSON: `https://piston-meta.mojang.com/v1/packages/fba9f7833e858a1257d810d21a3a9e3c967f9077/1.16.5.json`
- 参照JAR: Mojang公式Java server JAR
  - SHA-1: `1b557e7b033b583cd9f66746b7a9ab1ec1673ced`
  - SHA-256: `58f329c7d2696526f948470aa6fd0b45545039b64cb75015e64c12194b373da6`
- 参照mapping: Mojang公式1.16.5 server mappings
  - SHA-1: `41285beda6d251d190f2bf33beadd4fee187df7a`
  - SHA-256: `b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7`

調査ファイルは`/tmp`だけへ取得し、公式mappingでクラス名を対応付けたうえで`javap`によるserver JARのバイトコード確認を行った。JAR、mapping、逆コンパイル結果はリポジトリへ収録しない。本書はクラス名、メソッド名、定数、式と処理概要のみを記録し、Mojangのソース本文を転載していない。

## 関連する公式クラスとメソッド

- `net.minecraft.data.worldgen.Features`: `ORE_DIRT`、`ORE_GRAVEL`、`ORE_GRANITE`、`ORE_DIORITE`、`ORE_ANDESITE`の設定とdecorator連結
- `net.minecraft.data.worldgen.BiomeDefaultFeatures#addDefaultUndergroundVariety`: 5 featureの追加順
- `net.minecraft.world.level.levelgen.feature.ConfiguredFeature#decorated`
- `net.minecraft.world.level.levelgen.Decoratable#range`、`#squared`、`#count`
- `net.minecraft.world.level.levelgen.placement.RangeDecorator#place`
- `net.minecraft.world.level.levelgen.placement.SquareDecorator#place`
- `net.minecraft.world.level.levelgen.feature.OreFeature#place`、`#doPlace`
- `net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration`
- `OreConfiguration.Predicates#NATURAL_STONE`
- `net.minecraft.world.level.levelgen.WorldgenRandom#setDecorationSeed`、`#setFeatureSeed`
- `net.minecraft.world.level.biome.Biome#generate`
- `net.minecraft.util.Mth#sin`、`#floor`、`#ceil`、`#lerp`

## 確定した設定

| stable order / salt | Feature | material | vein size | attempts/chunk | origin Y |
|---:|---|---|---:|---:|---|
| 0 | DIRT | DIRT | 33 | 10 | 0以上256未満 |
| 1 | GRAVEL | GRAVEL | 33 | 8 | 0以上256未満 |
| 2 | GRANITE | GRANITE | 33 | 10 | 0以上80未満 |
| 3 | DIORITE | DIORITE | 33 | 10 | 0以上80未満 |
| 4 | ANDESITE | ANDESITE | 33 | 10 | 0以上80未満 |

feature追加順は表の順であり、1 source chunkあたりの合計試行数は48である。各試行の原点は、source chunk最小X/Zに`Random#nextInt(16)`で選んだlocal X/Zを加え、その後`Random#nextInt(maxYExclusive)`でYを選ぶ。

当初のPhase 2A期待表はDIRTとGRAVELもY=0以上80未満としていたが、公式1.16.5 server JARでは両者に`range(256)`が適用されている。公式一次資料を優先し、コードとテストでは上表の256を採用した。Phase 2BではY=70より上がAIRであるため、その範囲の候補はreplacement判定で書き込まれない。

## seedと安定順序

`LegacyDecorationSeed`は1.16.5の式を次のように再実装する。すべてJava `long`の2の補数overflowを仕様として利用する。

1. `new Random(worldSeed)`から2個の`nextLong()`を取り、それぞれ最下位bitを1にする。
2. source chunkのblock原点`sourceChunkX << 4`、`sourceChunkZ << 4`と2係数を積和し、world seedとXORしてdecoration seedを得る。
3. `featureSeed = decorationSeed + stableSalt + 10000 * 6`とする。6は`GenerationStep.Decoration.UNDERGROUND_ORES`のordinalである。
4. featureごとに新しい`Random(featureSeed)`を作り、attempt順にlocal X、local Z、origin Y、鉱脈形状の順で消費する。

stable saltはenum ordinalから導出せず0～4を明示する。この値は標準1.16.5 biomeで最初に追加される5 featureのfeature indexにも対応する。source chunkはZ昇順を外側、X昇順を内側とし、その中をfeature stable order、attempt index、vein sequenceの順に処理する。リスト、`HashMap`、`HashSet`のiteration orderへは依存しない。

## 旧式鉱脈形状

`LegacyVeinGenerator`は`OreFeature`の処理順を純粋Javaで再現する。

- `nextFloat() * PI`で角度を決め、`size / 8`だけ離したX/Z両端を作る。
- 両端Yはそれぞれ`originY + nextInt(3) - 2`とする。
- index 0以上size未満を分母sizeで補間し、各節で`nextDouble() * size / 16`を消費する。
- 1.16.5の65,536要素sine tableと同じindex式を用いたsin曲線で各楕円体の半径を決める。
- 中心距離と半径差から完全に包含される楕円体を除外する。
- 公式の保守的bounding box内でX、Y、Zの順にblock中心の正規化距離を判定する。
- bounding box相対indexの`BitSet`で同一鉱脈内の重複候補を抑制する。

長さ33では端点の最大水平偏位が`33 / 8 = 4.125`、楕円体半径の上限が`(2 * (33 / 16) + 1) / 2 = 2.5625`であり、原点からの水平到達距離は6.6875未満である。2 chunk離れたsourceの最寄り原点とtarget端block中心の距離16.5より小さいため、source探索はtargetのX/Zそれぞれ±1、合計3×3で十分である。

## target chunk ownership

`LegacyGeologyPlanner`はtarget chunkの絶対範囲だけを所有する。targetの周囲3×3 source chunksについて全48 attemptをseedから再構築し、鉱脈候補のうちtargetの`[chunkX * 16, chunkX * 16 + 16)`かつ同様のZ範囲に入るものだけをsinkへ渡す。隣のtargetを処理すると同じsource veinを同じseedから再生成するため、隣chunkへの直接書き込みや共有cacheなしで境界両側を復元できる。

固定world seed `11652021`、target chunk `(0,0)`のPhase 2A golden planは5,564 placements、FNV-1a系テストchecksumは`-4572519745665027215`である。

## replacement targetとPhase 2B対応

公式`NATURAL_STONE`はserver JAR内`data/minecraft/tags/blocks/base_stone_overworld.json`で次の4 blockに限定される。

- `STONE`
- `GRANITE`
- `DIORITE`
- `ANDESITE`

`BEDROCK`、`AIR`、`GRASS_BLOCK`、`DIRT`、`GRAVEL`、水、溶岩、鉱石、`DEEPSLATE`は置換しない。Phase 2BのPaper adapterはBukkit `Material.STONE`、`GRANITE`、`DIORITE`、`ANDESITE`を同名の`LegacyBlockKind`へ対応させ、他のMaterialは置換不可として扱う。これにより地表のDIRT/GRASS_BLOCK、岩盤床、Y<0や地表上のAIRを壊さない。

Phase 2Bでは`BlockPopulator#getDefaultPopulators`から`LimitedRegion`内の現在blockを読み、plannerのstream順にreplacement predicateを評価してMaterialを書き込む。target外へは書き込まず、1 chunk終了後にplacementを保持しない。

## 再現範囲と意図的な非再現

Phase 2Aが再現するのは、5 featureの公式設定、Java `Random`系列、decoration/feature seed式、decorator由来の原点乱数順、旧式楕円体形状、feature順、target ownershipと境界再構築である。同じplugin version・world seed・chunk座標では、thread数やtarget処理順に関係なく同じplanを生成する。

一方、Minecraft 1.16.5の同一seedとblock座標単位の完全一致は保証しない。純粋generatorはVanilla `OreFeature#place`のheightmap事前判定や実blockに対するtarget predicateを実行せず、Vanilla biome decoration pipeline全体、他feature、洞窟・水・溶岩、Vanilla noise地形を再現しないためである。特に高いDIRT/GRAVEL原点をVanillaがheightmapで早期終了する場合とPhase 2Aの乱数消費は異なり得る。Phase 2BでもLegacyMiningWorld固有の水平地形に適用するため、旧Vanilla worldとの座標一致ではなく、決定性と1.16.5型設定・形状を保証対象とする。

Phase 2AではPaper runtimeへ接続せず、`BlockPopulator`登録、`LimitedRegion`アクセス、Multiverse-Core使用は行わない。
