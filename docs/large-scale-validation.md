# Phase 4B1 大規模検証

## 目的と範囲

Phase 4B1/version 0.6.0-alpha.1は、Phase 4AのMultiverse-Core 5.7.2統合を33×33、合計1,089チャンクへ拡張する。1runあたりY=-64..319の107,053,056 blockを読み、生成順、保存・再読込、world UUID、server directoryからblock結果が独立していることを検証する。Phase 4B2のrelease candidate化、最終監査、配布packageは含まない。

追跡specは`src/test/resources/large-scale-grid.properties`で、world名`legacy_mining_scale`、seed `11652021`、chunk X/Z `-16..16`、Y `-64..319`を固定する。parserはmissing/unknown/duplicate key、数値不正、逆転range、1,089と一致しない再計算値、安全でないworld名を拒否する。specはtest-only verifier JARには含め、release JARには含めない。

## A1/A2/B1

Multiverse commandは次を使う。`bukkit.yml`のgenerator指定は使わない。

    mv generators list
    mv create legacy_mining_scale normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn

- A1: `build/large-scale-smoke-a/`をclean作成し、Z昇順・X昇順で生成する。
- A2: A1 directoryを保持して再起動し、Multiverse auto-load後に全1,089chunkが既存であることを先に確認する。新規生成へfallbackしない。
- B1: `build/large-scale-smoke-b/`を別にclean作成し、同じworld名・seedでZ降順・X降順に生成する。

検証run例ではA1/A2 UUIDが`0c3251c0-6c50-4617-9acc-6147320f65d7`、B1 UUIDが`103efa49-e790-4bd7-9820-a7f556b1a874`だった。clean再実行ごとに値自体は変わるが、A1=A2かつA1!=B1を必須とする。

## incremental verifier

test-only verifierは`lmwit grid`、`grid-status`、`grid-cancel`、`verify-vanilla-world`を追加する。同時jobは1件だけで、consoleかつmain threadから開始する。schedulerはverifierだけが`runTaskTimer`で使用し、production sourceへは追加しない。

jobは2段階で処理する。第1段階は指定されたforward/reverse順で1tickに1chunkを生成または既存確認する。第2段階はchecksumの正規順を生成順から独立させるため、常にZ昇順・X昇順で1tickに1chunkの`ChunkSnapshot`を取得・走査する。snapshotは1個だけ保持し、保存するのは1,089件の小さなchunk集計である。全snapshotや全block配列は保持しない。

本環境では`World#unloadChunk`が各処理直後にfalseを返したため、Paperのticket lifecycle観測値`immediateUnloadRejected`として2段階合計2,178件を記録した。これはproduction generatorの失敗件数ではない。一方、次tick開始時には対象chunkが残らず、取得直後を含むmaximum loaded chunksは1だった。この観測値とloaded chunk上限を性能reportへ残す。

## checksumとpure model

FNV-1a 64bit系のmix、Y→local Z→local Xのblock順、absolute X/Y/Zと`Material.name()`をPhase 4Aから維持する。chunk reportは生成順にかかわらずZ昇順・X昇順で書く。

- Y=5..67 checksum: `-7581040318536063180`
- full Y=-64..319 checksum: `-56844145234233245`
- A1 chunk report SHA-256: `e5e26ff1bdac20270d314098728c418ed5faf50501d08e913cf39bacc1b14e27`
- A1 Y=0..67 ore histogram SHA-256: `961eebd1e9d4443e1a30134fa43c4a706bd29d5dff8667953aab1da85bd9e55c`

A1/A2/B1はper-chunk Y=5..67 checksum、full checksum、全Material count、Y=0..67 ore histogramをbyte単位で一致確認した。pure in-memory modelはPaperから渡るbedrock Randomに依存しないY=5..67だけを対象とし、全1,089chunkのper-chunk checksum、Material count、combined checksum、Y=5..67 ore histogramをliveとexact比較した。Y=1..4のpure/live一致や全高pure一致は主張しない。

## 分布と構造

Y=5..67のaggregateは次のとおり。

| Material | count |
|---|---:|
| STONE | 14,092,844 |
| DIRT | 294,241 |
| GRAVEL | 233,506 |
| GRANITE | 815,647 |
| DIORITE | 854,559 |
| ANDESITE | 869,347 |
| COAL_ORE | 230,273 |
| IRON_ORE | 126,309 |
| GOLD_ORE | 11,175 |
| REDSTONE_ORE | 27,327 |
| DIAMOND_ORE | 3,554 |
| LAPIS_ORE | 4,610 |

Y=11はCOAL 3,757、IRON 1,984、GOLD 383、REDSTONE 2,959、DIAMOND 360、LAPIS 294だった。per-chunk mean、population standard deviation、min/max、median、p95、zero-count chunk数と、鉱石密度/1,000,000 blocksは`large-scale-distribution.txt`へ固定小数・Locale.ROOTで出力する。

全高ではY<0 AIR 17,842,176、Y=0 BEDROCK 278,784、Y=1..4 volume 1,115,136、Y=68..69 DIRT 557,568、Y=70 GRASS_BLOCK 278,784、Y=71..319 AIR 69,417,216を確認した。禁止block 0、許可以外non-AIR 0、Y=0/11/70/100のPLAINS 1,115,136件だった。

## region header

`scripts/inspect-region-headers.py`はPython標準ライブラリだけでMCA先頭4,096 byteを読む。本文NBTやbinary SHAは比較しない。必須regionは`r.-1.-1.mca`、`r.0.-1.mca`、`r.-1.0.mca`、`r.0.0.mca`の4つで、target 1,089chunkのmissingは0だった。

Multiverse/Paperのspawn準備により同じ4region内には`-27..27`の55×55、合計3,025chunkが保存され、target外extraは1,936件だった。この集合はA1/A2/B1で同一である。extraを隠さず記録し、target missingだけは必ずFAILとする。

## 性能

測定環境はJava 25.0.3、Paper 26.1.2 build 69、WSL2、`-Xms512M -Xmx2G`。値はこの実行環境固有で、他環境の閾値にはしない。

| run | grid秒 | chunks/s | blocks/s | process wall | max RSS KiB | world bytes | region bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| A1 | 108.931 | 9.997 | 982,764.187 | 2:20.62 | 1,206,200 | 12,441,780 | 12,439,552 |
| A2 | 109.010 | 9.990 | 982,049.852 | 2:15.62 | 1,207,252 | 12,441,780 | 12,439,552 |
| B1 | 108.929 | 9.997 | 982,779.562 | 2:21.21 | 1,281,088 | 12,441,785 | 12,439,552 |

startupはA1/A2/B1で13.932/8.019/12.028秒、Multiverse createは1.004/対象外/1.003秒、save+stopは1.668/1.652/1.640秒だった。

timeout、watchdog停止、OutOfMemoryErrorはなく、reportはPASS前にatomic moveでflushした。

## world分離と回帰

各runの公開API検査でdefault `world`はgenerator `null`、biome provider `null`、folder `overworld`だった。Multiverse entryのgeneratorは空で、対象entryだけが`LegacyMiningWorld`だった。対象worldのgeneratorとbiome providerはproduction classで、default worldとfolder/keyを共有しない。

通常Paper 4chunk smokeとPhase 4A Multiverse 4chunk create/restart smokeはPhase 4B1 review checksでも必須とする。production Java、production dependency、production plugin metadataはversion以外変更せず、Multiverse API、NMS、reflectionを追加しない。

## Phase 4B2とPhase 5での利用

Phase 4B2ではこのgoldenを変更せず、version `1.0.0-rc.1`の最終コード監査、再現可能JAR/package、package JAR smoke、commit済みtracked sourceのclean-roomでA1/A2/B1を再実行した。

Phase 5/version `1.0.0`でもproduction sourceとclass payloadをRCから変更せず、通常worktreeとcommitted stable HEADのclean-roomでA1/A2/B1を再実行した。full checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、deterministic chunk report、ore histogram、Material count、forbidden 0、unknown non-AIR 0、PLAINS 1,115,136件、target missing 0はすべて維持した。stable package JARを使うPaper smokeもPASSした。

technical stableの成果物、再現可能性、RC payload同一性、ライセンスと公開状態は[stable release情報](stable-release.md)を参照する。ライセンスは未選択で、公開配布はPhase 5に含めない。
