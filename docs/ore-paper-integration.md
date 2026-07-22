# Phase 3B Paper鉱石統合

## APIと統合順

Paper 26.1.2 build 69のBlockPopulator APIを依存JARで確認し、非推奨でない次のsignatureだけをoverrideする。

    public void populate(
            WorldInfo worldInfo,
            Random random,
            int chunkX,
            int chunkZ,
            LimitedRegion limitedRegion)

LegacyMiningChunkGenerator#getDefaultPopulators(World)は変更不能な1要素listを返し、statelessなLegacyUndergroundPopulatorを共有する。geology populatorとore populatorを分離登録せず、同じcallback内でLegacyGeologyApplicatorを先、LegacyOreApplicatorを後に同期適用する。

この順序により鉱石は地質適用後のSTONE、GRANITE、DIORITE、ANDESITEを読み、DIRT/GRAVELは保護する。populator間の外部順序やWorldInitEvent、手動登録には依存しない。

## seedとLimitedRegion

Paperが渡すRandomは意図的に消費しない。Phase 2A/3Aのseed契約はworld seedとsource chunkから再構築するため、population scheduling、thread割当、callback順から分離する必要がある。seedは各callbackでWorldInfo#getSeed()から取得する。

LimitedRegionUndergroundBlockAccessはcallback中だけregion参照を保持し、絶対座標でisInRegion、getType、setTypeだけを呼ぶ。LimitedRegion#getWorld、World/Chunk/Block access、scheduler、async task、cache、pending write、ThreadLocalは使わない。callback後にregionを保存しない。

## Material mappingとreplacement

LegacyOreMaterialAdapterは6 pure materialを明示的に同名Bukkit Materialへ変換する。

| pure | Bukkit |
|---|---|
| COAL_ORE | COAL_ORE |
| IRON_ORE | IRON_ORE |
| GOLD_ORE | GOLD_ORE |
| REDSTONE_ORE | REDSTONE_ORE |
| DIAMOND_ORE | DIAMOND_ORE |
| LAPIS_ORE | LAPIS_ORE |

nullは拒否し、文字列変換、matchMaterial、valueOf、STONE fallbackは使わない。現在Materialの分類はLegacyGeologyMaterialAdapterを再利用する。

置換対象はSTONE、GRANITE、DIORITE、ANDESITEだけである。DIRT、GRAVEL、BEDROCK、3種AIR、GRASS_BLOCK、水、溶岩、既存鉱石、emerald、copper、DEEPSLATE/TUFF/CALCITE、全深層岩鉱石、未知Materialは置換しない。

plannerのsource Z、source X、feature、attempt、vein sequence順をそのまま逐次適用する。各候補で現在blockを1回読み、最初の候補が自然石を鉱石へ変えた後は後続候補をnot-replaceableとしてskipする。sort、group、HashMap上書き、parallel streamは使わない。

## 高度とownership

Paper world heightに加え、LegacyMiningWorld固有の鉱石適用範囲を0以上68未満へ固定する。

- Y<0のcandidateは旧式world底面外なのでskip
- Y=0は適用範囲内だが全面BEDROCKなので不変
- Y=1～4はSTONE部分だけ置換可能
- Y=5～67は自然石4種を置換可能
- Y>=68は地表層保護のため、STONEであってもskip

判定順はtarget chunk、Paper world height、legacy Y、region、現在block、replacement、writeである。target外とregion外ではget/setしない。各targetは周囲3×3 source chunksを再構築し、自chunk内だけを書き込む。

## fixed seed combined model

seedは11652021、chunksは(-1,-1)、(0,-1)、(-1,0)、(0,0)。初期terrainへgeology、次にoreを適用したgoldenは次のとおり。

| Material | count |
|---|---:|
| AIR | 320512 |
| BEDROCK | 3073 |
| STONE | 53542 |
| DIRT | 3132 |
| GRAVEL | 960 |
| GRANITE | 3098 |
| DIORITE | 3017 |
| ANDESITE | 3358 |
| GRASS_BLOCK | 1024 |
| COAL_ORE | 867 |
| IRON_ORE | 443 |
| GOLD_ORE | 48 |
| REDSTONE_ORE | 106 |
| DIAMOND_ORE | 17 |
| LAPIS_ORE | 19 |

combined checksumは-7165395187979696007。chunk別checksumは(-1,-1) 8200217911935443408、(0,-1) 834795681032079842、(-1,0) -5789077494783116012、(0,0) -7748694558319417456。

Y=11の4chunk合計はSTONE 733、DIRT 47、GRAVEL 19、GRANITE 76、DIORITE 34、ANDESITE 85、COAL 6、IRON 5、GOLD 8、REDSTONE 7、DIAMOND 4。LAPISはこの固定modelのY=11にないため別高度anchorを使う。

## fixed anchors

ore anchorはsrc/test/resources/ore-smoke-anchors.tsvに14件固定する。内訳はCOAL 3、IRON 3、GOLD 2、REDSTONE 2、DIAMOND 2、LAPIS 2。

Y=11:

- COAL (-9,11,-10)
- IRON (8,11,0)
- GOLD (-9,11,14)、(2,11,13)
- REDSTONE (-16,11,1)、(13,11,1)
- DIAMOND (14,11,-4)、(13,11,-4)

boundary:

- X_COAL: source (-1,0)、attempt 0。(-1,22,3) sequence 6と(0,22,3) sequence 0
- Z_IRON: source (0,0)、attempt 7。(0,34,-1) sequence 2と(1,34,0) sequence 0

LAPISは(-4,16,-10) sequence 0と(-4,17,-10) sequence 1。全anchorは既存4chunk内かつY>=5で、planner source metadataとcombined final Materialをテストする。runtime探索や自動更新はしない。

既存geology anchor 10件はore統合後も全件同じMaterialで残り、X_GRAVELとZ_GRANITEのpairも維持する。geology-only planner 5564/checksum -4572519745665027215、geology-only 4chunk checksum -8052018879515985261は変更しない。

## thread safetyと禁止block

applicatorとpopulatorが共有するのはimmutable planner/applicator参照だけで、summary、region wrapper、candidate stateはmethod-local。同じinstanceを複数threadから独立worldへ適用し、summary、count、checksum一致をテストする。

combined pure modelではCOPPER_ORE、EMERALD_ORE、DEEPSLATE、TUFF、CALCITE、全DEEPSLATE ore、WATER、LAVAを4chunk全blockで0確認する。Paper smokeは固定anchorと代表保護点を確認するが、Phase 3BではMCA/NBTによるPaper 4chunk完全走査を行わない。完全走査はPhase 4へ引き継ぐ。

## Paper smoke結果

Paper SHA-256 d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b、plugin version 0.4.0、seed 11652021で使い捨てworldを起動した。LegacyMiningWorld JARだけをpluginsへコピーし、Multiverse-Coreはコピーしていない。

geology 10/10、ore 14/14、Y=11 COAL/IRON/GOLD/REDSTONE/DIAMOND、X_COAL両側、Z_IRON両側、地表・Y=0 BEDROCK・Y<0 AIR、PLAINS、fatal error scan、正常stop、source Paper/EULA hash不変がPASSした。禁止materialはPaper代表点でも確認したが、全4chunkの厳密な0判定はcombined pure modelによるものである。

## 保証範囲とPhase 4

同じplugin version内のworld seed/chunk決定性、Java 1.16.5の設定、seed式、decorator乱数順、高度分布、旧式形状、ownershipを保証する。Vanilla heightmap早期終了、biome decoration pipeline全体、noise地形を再現しないため、旧Vanilla同一seedとのblock座標完全一致は保証しない。

Phase 3BはMultiverse-Coreへ依存せず、server/plugins/multiverse-core-5.7.2.jarを通常Paper smokeへコピーしなかった。後続のPhase 4A/4B1/4B2でMultiverse generator列挙/world作成、大量chunk、禁止block完全走査、分布、性能、再生成一致、release candidate化を完了した。

Phase 5/version `1.0.0`では、6鉱石設定、salts 5～10、decorator乱数順、旧式形状、replacement、14 anchors、combined/Y=11 goldenを`1.0.0-rc.1`から変更していない。RC/stable production class payloadは同一で、stable JAR、package JAR、clean-roomの回帰はPASSした。最終結果は[stable release情報](stable-release.md)を参照する。
