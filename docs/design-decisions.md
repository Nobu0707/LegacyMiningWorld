# 設計判断

## 確定仕様

- 再現基準は Java Edition 1.16.5。
- 対象は PaperMC 26.1.2 build 69、Java 25。NMS、CraftBukkit内部実装、リフレクションは使わず、公開Paper/Bukkit APIだけを使う。
- 地表は常にY=70。Y=70は`GRASS_BLOCK`、Y=68～69は`DIRT`、Y=5～67は`STONE`、Y=0は`BEDROCK`。
- Y=1～4は旧式バニラ床相当の`BEDROCK`と`STONE`の混在。
- 水、溶岩、洞窟、空洞、ダンジョン、構造物、`DEEPSLATE`と深層岩系鉱石は生成しない。

## Phase 1で確定した実装

- `ChunkGenerator`の分割生成APIを使用し、`generateNoise`でY=0～67、`generateSurface`でY=68～70、`generateBedrock`でY=0～4を確定する。`setRegion`の上端は排他的として指定する。
- 現行通常ワールドの高度範囲は変更せず、Y<0へ書き込まない。Paper 26.1.2実機でY=-1が`AIR`のままになることを確認した。
- 岩盤床はY=0～4の各ブロックで、Paperから渡された`Random`に対して`y <= random.nextInt(5)`を1回だけ評価する。Y=0でも乱数呼び出しを省略しない。
- `getBaseHeight`はsurface適用前のnoise層の最高ブロックY=67を返す。WorldやChunkの参照は行わない。
- 固定スポーンは `(0.5, 71.0, 0.5)` とする。
- `PlainsBiomeProvider`は全座標で`Biome.PLAINS`を返し、使用バイオームは変更不能なPLAINS単独リストとする。
- `shouldGenerateNoise`、`shouldGenerateSurface`、`shouldGenerateCaves`、`shouldGenerateDecorations`、`shouldGenerateStructures`の引数なし版を`false`へ明示的に上書きする。Paper APIの座標別版は引数なし版へ委譲するため二重実装しない。
- `shouldGenerateMobs`は上書きせず、Paper APIの既定動作を維持する。ランタイムMobスポーン制御は追加しない。
- generator idはnull、blank、大小文字を問わない`default`を同じ既定構成として受理し、それ以外は警告してnullを返す。
- 生成クラスは可変共有状態、キャッシュ、共有`Random`、logger、plugin参照を持たず、Paperの並列生成を前提とする。

Paper 26.1.2の`Biome`定数はregistry-backedであり、サーバー外の素のJUnit VMではregistry bootstrapなしに値へアクセスできない。そのためproviderの型・不変性は単体テスト、実際のPLAINS戻り値は必須Paperスモークで検証する。

## Phase 2Aで確定した実装

- Phase 2は、複雑な形状・seed・境界処理をPaper書き込みから独立して検証するため2A/2Bへ分割した。2Aは純粋engine、2BはPaper adapterと実機配置を担当する。
- Mojang公式1.16.5 server JARと公式mappingを根拠とし、DIRT/GRAVELはsize 33、attempt 10/8、origin Y=0以上256未満、GRANITE/DIORITE/ANDESITEはsize 33、attempt各10、origin Y=0以上80未満とする。追加順もこの順とする。
- 乱数は1.16.5と同じ系列の`java.util.Random`を使う。world seedとsource chunk block原点から公式decoration seed式を再現し、`UNDERGROUND_ORES` step 6と明示的なstable feature salt 0～4でfeature seedを作る。enum ordinal、hashCode、時刻、thread IDへ依存しない。
- `OreFeature`型の楕円体補間、旧式sine table相当、包含関係除去、bounding box、block中心判定、BitSet重複抑制を再実装する。単純球、random walk、noiseへ置き換えない。
- target chunkだけが自範囲のplacementを所有する。source chunkは絶対Z昇順、X昇順、feature、attempt、vein内部の順で再構築し、隣chunkへ直接書かない。
- size 33の理論上の最大水平到達距離は6.6875未満のため、source neighborhoodはX/Z各±1の3×3とする。
- replacement targetは公式`base_stone_overworld`と同じ`STONE`、`GRANITE`、`DIORITE`、`ANDESITE`のみとする。DIRT/GRAVEL、BEDROCK、AIR、地表、液体、鉱石、DEEPSLATEは置換しない。
- plannerとgeneratorは共有可変状態を持たず、Randomを呼び出し内だけに閉じ込める。chunk cacheや全world placement保存は採用しない。immutable設定は全threadで共有できる。
- 同一plugin version・world seed・target chunkのplanは決定的だが、Vanilla heightmap事前判定、全biome decoration pipeline、地形や他featureを再現しないため、Minecraft 1.16.5の同一seedとのblock座標完全一致は保証しない。
- Phase 2Aでは既存Paper runtime behaviorを変えない。`BlockPopulator`を登録せず、`LimitedRegion`を参照せず、Multiverse-Coreへ依存しない。

詳細な一次資料と式は`docs/vanilla-1.16.5-geology.md`に記録する。

## Phase 2Bで確定した実装

- Paper adapterはpure engineから分離し、`LegacyGeologyMaterialAdapter`、`LegacyGeologyApplicator`、`LegacyGeologyBlockAccess`、`LimitedRegionGeologyBlockAccess`、`LegacyGeologyPopulator`の薄い責務に限定する。
- 非推奨でない`BlockPopulator#populate(WorldInfo, Random, int, int, LimitedRegion)`だけをoverrideする。旧`populate(World, Random, Chunk)`はoverrideしない。
- `getDefaultPopulators(World)`はstatic finalの`List.of`を返す。stateless populatorを正確に1個共有し、Worldを保存せず、別APIで二重登録しない。
- 読み書きはpopulate呼び出し中の`LimitedRegion#isInRegion/getType/setType`だけを使う。`LimitedRegion#getWorld`、World/Chunk block access、scheduler、block/fluid updateは使わない。
- Paperから渡された`Random`は使わず、各呼び出しの`WorldInfo#getSeed()`をplannerへ渡す。これによりthread割り当てやpopulation順からseed契約を分離する。
- plannerのstream順をそのまま逐次適用する。target chunk外、world Y範囲外、region外をread前にskipし、現在Materialを1回読んだ後だけreplacement判定してwriteする。
- Bukkit Materialは明示switchで分類する。STONE/GRANITE/DIORITE/ANDESITEだけが置換可能で、未知Materialは`OTHER`として安全側に倒す。STONEへのfallbackはしない。
- 後続stone variantは先行variantを置換できるが、DIRT/GRAVELは後続featureから保護する。Y=1～4のSTONEは置換可能、Y=0 BEDROCKとsurfaceは保護する。
- fixed anchorはseed `11652021`専用TSVとして追跡し、runtime探索や自動更新をしない。5 material各2点、X=-1/0とZ=-1/0の同一source vein pairを固定した。
- distribution sanityはtarget chunks `(-1,-1)`, `(0,-1)`, `(-1,0)`, `(0,0)`を全高度modelへ適用し、per-chunk counts/checksumとcombined checksum `-8052018879515985261`をgolden化する。
- populator、applicator、plannerは共有可変状態を持たず、独立fake regionへの並行適用で同一checksumを確認する。global lockや`ThreadLocal`は採用しない。
- Multiverse-Core依存と実機利用はPhase 4へ延期する。Phase 2BのPaper smokeへ`server/plugins`のJARをコピーしない。

## Phase 3Aで確定した実装

- Phase 3は鉱石のseed・高度分布・境界処理をPaper block書き込みと独立して固定するため3A/3Bへ分割する。3Aはpure engineだけを実装し、3Bでruntimeへ接続する。
- Mojang公式1.16.5 server JAR/mappingを根拠とし、COAL/IRON/GOLD/REDSTONE/DIAMOND/LAPISのsizeは17/9/9/8/8/7、attemptは20/20/2/8/1/1とする。1 source chunk合計52 attempt。
- PLAINSの`UNDERGROUND_ORES` stepでは地質5 featureの後に6鉱石が追加される。地質golden互換を守るためsalt 0～4を変更せず、鉱石はexplicit salt 5～10を使う。enum ordinalはseed契約へ使わない。
- COAL/IRON/GOLD/REDSTONE/DIAMONDのorigin Yはそれぞれ上端128/64/32/16/16のuniform range。lapisはbaseline 16/spread 16のdepth-averageとし、`nextInt(16)`を2回使う0～30の三角分布をuniformへ近似しない。
- decorator乱数順は固定count、local X、local Z、origin Y、`OreFeature`形状。lapisのcount(1)は暗黙で、Yだけ2回乱数を消費する。
- 鉱脈形状はPhase 2Aの`LegacyVeinGenerator`と同じ公式`OreFeature`なので複製せず再利用する。size 33 shape goldenとgeology planner goldenは変更しない。
- max size 17の理論最大水平到達距離は3.6875で、source neighborhoodはtarget X/Z各±1の3×3とする。source Z、X、feature、attempt、vein sequence順に再構築し、target範囲内だけをstreamする。
- target ownership方式を維持し、隣chunk直接書き込み、cache、pending map、共有Random、parallel streamを使わない。大座標はint block座標へのexact変換でoverflowを拒否する。
- 鉱石replacement targetも`base_stone_overworld`のSTONE/GRANITE/DIORITE/ANDESITEだけとする。既存鉱石、DIRT/GRAVEL、BEDROCK、AIR、液体、地表、DEEPSLATE系は置換しない。
- pure plannerは重複候補をstable順ですべてstreamする。Phase 3Bの逐次read-before-writeで、最初に配置された鉱石を後続鉱石のreplacement対象外にして先行featureを優先する。
- Phase 3Aでは`BlockPopulator`、`getDefaultPopulators`、`LegacyGeologyPopulator`、`LimitedRegion`、Bukkit ore Material mappingを変更しない。Paper runtimeは引き続き基本地形と5地質だけを生成する。
- Phase 3Bは複数populatorの順序へ依存せず、単一stateless underground populator内でgeology applicatorの後にore applicatorを同期実行する案を優先する。
- 同一plugin version内のworld seed/chunk plan決定性は保証するが、Vanilla heightmap早期終了、全decoration pipeline、実地形、置換成功/失敗を再現しないため、1.16.5同一seedのblock座標完全一致は保証しない。
- emerald、badlands追加gold、copper、DEEPSLATE系鉱石は固定PLAINS/1.16.5対象外として追加しない。Multiverse-Coreは引き続きPhase 4へ延期する。

## 後続で確定する事項

- Phase 3B: pure ore engineのLimitedRegion/Bukkit接続、geology→ore適用順、固定ore anchor、実world分布とgeology回帰。
- Phase 4: Multiverse-Core統合、大量chunk走査、性能・決定性、release candidate。
