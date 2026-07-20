# Phase 2B/3B Paper地下生成統合

## API境界

Paper 26.1.2 build 69の非推奨でない次の`BlockPopulator` APIを使用する。

```java
public void populate(
        WorldInfo worldInfo,
        Random random,
        int chunkX,
        int chunkZ,
        LimitedRegion limitedRegion)
```

Phase 2Bでは`LegacyGeologyPopulator`を使用したが、Phase 3Bでstandalone populatorを削除し、`LegacyMiningChunkGenerator#getDefaultPopulators(World)`は変更不能な単一要素listからstateless `LegacyUndergroundPopulator`を共有する。地質applicator自体は変更せず、同じcallbackでgeologyの直後にoreを適用する。`World#getPopulators().add`や`WorldInitEvent`による二重登録は行わない。Worldとworld seedはfieldへ保存しない。

Paperが渡す`Random`は意図的に使用しない。Phase 2Aの決定性契約はworld seedとsource chunk由来であり、populationのthread割り当てや実行順に依存させないためである。seedは各呼び出しで`WorldInfo#getSeed()`から取得する。

## LimitedRegion制約

Phase 3Bの`LimitedRegionUndergroundBlockAccess`はpopulate呼び出し中だけ作成し、地質と鉱石で共有する。絶対座標で次だけを呼ぶ。

- `isInRegion(int, int, int)`
- `getType(int, int, int)`
- `setType(int, int, int, Material)`

Yを`[WorldInfo#getMinHeight(), WorldInfo#getMaxHeight())`で確認し、次にtarget chunk ownership、region範囲、現在Material、replacement predicateの順で判定する。`LimitedRegion#getWorld`、World/Chunk block access、scheduler、block/fluid updateは使用しない。LimitedRegion参照はstatic、cache、task、`ThreadLocal`へ保存しない。

plannerのstream順をsort、group、parallel化せず、その場で適用する。target範囲外placementは防御的にskipし、LimitedRegionのbufferへ隣chunk分を書き出さない。各targetは周囲3×3 source chunksから同じveinを再構築する。

## Materialとreplacement

生成側mappingは次のとおり。

| pure material | Bukkit Material |
|---|---|
| DIRT | DIRT |
| GRAVEL | GRAVEL |
| GRANITE | GRANITE |
| DIORITE | DIORITE |
| ANDESITE | ANDESITE |

Bukkit側で置換可能と分類するのはSTONE、GRANITE、DIORITE、ANDESITEだけである。BEDROCK、3種AIR、GRASS_BLOCK、DIRT系、GRAVEL、液体、DEEPSLATE/TUFF/CALCITE、通常/深層岩鉱石、その他は置換不可。未知MaterialをSTONEへfallbackしない。

順序はDIRT、GRAVEL、GRANITE、DIORITE、ANDESITEである。後続stone variantは先行variantを置換できる。DIRT/GRAVELへ到達した後はstone variantも置換できず、DIRTへGRAVELが来ても置換しない。Y=1～4でBEDROCKにならなかったSTONEは置換され得るが、Y=0の全面BEDROCKと地表DIRT/GRASS_BLOCKは不変である。

## 固定anchor

seedは`11652021`、追跡ファイルは`src/test/resources/geology-smoke-anchors.tsv`である。TSVはUTF-8、`#`開始行をcommentとし、runtimeで探索・更新しない。release JARには含めない。

| id | 座標 | final | source chunk | feature / attempt | purpose |
|---|---|---|---|---|---|
| DIRT_1 | (-2,34,-1) | DIRT | (-1,-1) | DIRT / 0 | material |
| DIRT_2 | (-2,34,0) | DIRT | (-1,-1) | DIRT / 0 | material |
| GRAVEL_X_WEST | (-1,65,-12) | GRAVEL | (-1,-1) | GRAVEL / 3 | X pair |
| GRAVEL_X_EAST | (0,66,-12) | GRAVEL | (-1,-1) | GRAVEL / 3 | X pair |
| GRANITE_Z_NORTH | (-11,19,-1) | GRANITE | (-1,-1) | GRANITE / 6 | Z pair |
| GRANITE_Z_SOUTH | (-11,19,0) | GRANITE | (-1,-1) | GRANITE / 6 | Z pair |
| DIORITE_1 | (-16,15,-13) | DIORITE | (-1,-1) | DIORITE / 5 | material |
| DIORITE_2 | (-16,16,-13) | DIORITE | (-1,-1) | DIORITE / 5 | material |
| ANDESITE_1 | (-10,5,-1) | ANDESITE | (-1,-1) | ANDESITE / 3 | material |
| ANDESITE_2 | (-10,5,0) | ANDESITE | (-1,-1) | ANDESITE / 3 | material |

X境界はX=-1/0を同じsource GRAVEL veinの両側で、Z境界はZ=-1/0を同じsource GRANITE veinの両側で確認する。target chunksはX/Zとも-1..0に収まる。

## pure model distribution

初期modelはY=0 BEDROCK、Y=1～4にtest用STONE/BEDROCK混在、Y=5～67 STONE、Y=68～69 DIRT、Y=70 GRASS_BLOCK、外側AIRである。全高度-64..319をchecksum対象とした。

| target chunk | DIRT | GRAVEL | GRANITE | DIORITE | ANDESITE | STONE | BEDROCK | checksum |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| (-1,-1) | 796 | 411 | 718 | 807 | 702 | 13,718 | 768 | 7636051648872243184 |
| (0,-1) | 766 | 207 | 701 | 650 | 888 | 13,939 | 769 | -556462915633053921 |
| (-1,0) | 687 | 283 | 908 | 821 | 897 | 13,554 | 770 | -3889388701578752134 |
| (0,0) | 883 | 59 | 860 | 802 | 983 | 13,567 | 766 | 8312497771964804421 |

4 chunk合計はDIRT 3,132、GRAVEL 960、GRANITE 3,187、DIORITE 3,080、ANDESITE 3,470、STONE 54,778、BEDROCK 3,073、GRASS_BLOCK 1,024、AIR 320,512。combined checksumは`-8052018879515985261`。正順と逆順でchunk別結果が一致する。

## Paper smoke結果

`build/paper-smoke/`を毎回再作成し、source Paper/EULAを変更せず、`LegacyMiningWorld-0.3.0.jar`だけをpluginsへコピーした。seed `11652021`で4 target chunksをforce-load後、10/10 anchor、X/Z pair、negative chunk、Y=0 BEDROCK、surface DIRT/GRASS_BLOCK、Y=-1/71 AIR、PLAINSがPASSした。fatal error/unknown command/class loading scanもPASS。Paper JAR SHA-256は`d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b`。

populator/applicator/plannerはimmutable参照とmethod-local stateだけを持つ。同じapplicatorを複数threadから独立regionへ適用するJUnitでsummary/count/checksum一致を確認した。productionではchunk/material/candidateごとのlogを出さない。

## Phase 3B回帰とPhase境界

Phase 3B/version 0.4.0で単一underground populator内のgeology→ore順へ移行した。geology-only planner 5,564/checksum `-4572519745665027215`と4chunk checksum `-8052018879515985261`は不変で、固定geology anchor 10件もcombined final worldで10/10残る。oreがstone variantsを置換した最終worldは別のcombined checksum `-7165395187979696007`で管理する。詳細は[Phase 3B Paper鉱石統合](ore-paper-integration.md)を参照。

Multiverse-Core統合・大量chunk性能試験・禁止block完全走査はPhase 4で扱い、現在の依存、plugin descriptor、Paper smoke、release JARにMultiverse-Coreは含まれない。
