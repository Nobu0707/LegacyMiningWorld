# LegacyMiningWorld 1.0.0 stable release

## status

| 項目 | 値 |
|---|---|
| version | `1.0.0` |
| stable | YES |
| release candidate | NO |
| distribution | private/internal deployment only |
| license selected | NO |
| public publication | NO |

`1.0.0`は技術検証を完了したstable版です。technical stableはpublicly releasedを意味しません。ライセンス未選択のため、現時点のJAR/packageは組織内・個人内のprivate/internal deploymentだけを対象とし、open-source licenseや公開再配布許可を付与しません。

## 対応環境

- PaperMC 26.1.2 build 69
- Java 25
- Gradle 9.2.0
- 任意統合: Multiverse-Core 5.7.2
- Paper JAR SHA-256: `d30fae0c74092b10855f0412ca6b265c60301a013d34bc28a2a41bf5682dd80b`
- Multiverse-Core JAR SHA-256: `574862aa3062af53957fe845de110a386f886445366836c8c63712e11d697400`

production pluginはPaper/Bukkit公開APIだけを使用し、Multiverse API、NMS、CraftBukkit内部、reflectionへ依存しません。Multiverse-Coreは任意の利用側であり、production dependencyではありません。

## stable成果物

- production JAR: `LegacyMiningWorld-1.0.0.jar`
- production JAR SHA-256: `95fc4798970d28a8b095cc5fd276348dbb893c831d697e7efb8c9960d62e419f`
- test-only verifier JAR: `LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`
- verifier JAR SHA-256: `e797a5e9ffc3bf496c20f88ea6da65468af78c291d8129e8515f9100cc8ae7e7`
- release package: `LegacyMiningWorld-1.0.0-release.tar.gz`
- release package SHA-256: `dd469700f986f8f2aa41ac8f846608a331245e3a95b181c54adcafb7823a29f4`

verifier JARは開発検証専用であり、production serverへ導入せず、release packageにも含めません。

package内容は次の5ファイルだけです。

```text
LegacyMiningWorld-1.0.0/
  LegacyMiningWorld-1.0.0.jar
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
  LICENSE-NOT-SELECTED.txt
```

test、source、anchors、large-scale spec、Paper/Multiverse JAR、server、world、verifier、review logs、Git metadata、credentialsは含みません。package内`SHA256SUMS.txt`の自己検査と、packaged JAR/build JARのbyte一致を確認しています。

## RC baselineとproduction payload同一性

- baseline commit: `3c30291b5c570d1c53a261ef8f5d9715b42512ff`
- baseline subject: `chore: prepare 1.0.0 release candidate`
- baseline version: `1.0.0-rc.1`
- stable commit subject: `chore: promote LegacyMiningWorld 1.0.0`

baselineのdetached worktreeでRC JARを再buildし、stable worktreeでstable JARをclean buildして比較しました。

- `src/main/java/**`: byte-for-byte同一
- production `src/main/resources/plugin.yml` template: byte-for-byte同一
- Paper API dependency、production dependency定義: 同一
- feature設定、seed設定、salts、anchors、large-scale spec: 同一
- production functional source changes: 0
- production class entry名集合: PASS
- production classごとのSHA-256: PASS
- production functional class payload: IDENTICAL
- production JARへのintegration verifier class混入: 0
- manifestのversion非依存差: 0

JAR内`plugin.yml`の差は次のversionだけです。name、main、api-version、load、author、description、command/dependency absenceは同一です。

```diff
-version: 1.0.0-rc.1
+version: 1.0.0
```

## automated validation matrix

| 検証 | 結果 |
|---|---|
| stable source equivalence to `3c30291` | PASS |
| RC/stable production class payload | IDENTICAL |
| JAR内`plugin.yml` version-only difference | PASS |
| `clean test` | PASS |
| `geologyEngineTest` / `geologyAdapterTest` | PASS |
| `oreEngineTest` / `oreAdapterTest` | PASS |
| `multiverseVerifierTest` | PASS |
| `largeScaleVerifierTest` / `largeScaleModelTest` | PASS |
| `build` / `multiverseVerifierJar` | PASS |
| region header tool unit test | PASS |
| `-Xlint:all -Werror` compiler audit | PASS |
| production dependency audit | PASS |
| normal Paper 4chunk smoke | PASS |
| package JAR Paper 4chunk smoke | PASS |
| Multiverse create / save / restart / auto-load | PASS |
| 1,089chunk A1 forward | PASS |
| 1,089chunk A2 existing/restart | PASS |
| 1,089chunk B1 reverse/clean | PASS |
| reproducible production JAR | PASS |
| reproducible verifier JAR | PASS |
| reproducible stable package | PASS |
| stable package self-check | PASS |
| stable version scan | PASS |
| committed tracked source clean-room | PASS |

通常Paper smokeではproduction JARだけを使用し、Multiverse/verifierをコピーしていません。package JAR smokeではpackageを展開し、`SHA256SUMS.txt`を検証したJARが`build/libs`のproduction JARとbyte一致することを確認しました。

Multiverse-Core 5.7.2では次の構文でgenerator列挙とworld作成を行い、停止後のrestart/auto-loadまで確認しました。

```text
mv generators list
mv create legacy_mining_mv_smoke normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

## 1,089chunk golden

- world: `legacy_mining_scale`
- seed: `11652021`
- chunks: 1,089（X/Z `-16..16`）
- blocks/run: 107,053,056
- full checksum: `-56844145234233245`
- Y=5..67 checksum: `-7581040318536063180`
- deterministic chunk report SHA-256: `e5e26ff1bdac20270d314098728c418ed5faf50501d08e913cf39bacc1b14e27`
- ore histogram SHA-256: `961eebd1e9d4443e1a30134fa43c4a706bd29d5dff8667953aab1da85bd9e55c`
- pure expected chunks SHA-256: `5facaf32cf760ce463b4bcde25529cae5febd672f507e5d41df6c4de6daac575`
- forbidden block: 0
- unknown non-AIR: 0
- biome: 1,115,136点すべてPLAINS
- target region missing: 0

A1 forward、同じ保存worldを読むA2 existing/restart、別clean worldのB1 reverseでper-chunk report、Material count、鉱石histogram、checksumが一致しました。pure/liveの完全一致主張はPaperのbedrock Randomに影響されないY=5..67だけを対象とし、Y=1..4や全高pure一致は主張しません。

Y=5..67のaggregate count:

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

Y=11の鉱石countはCOAL 3,757、IRON 1,984、GOLD 383、REDSTONE 2,959、DIAMOND 360、LAPIS 294です。

## 再現可能build/packageとclean-room

production/verifier JARはtimestampを保存せず、再現可能entry順で2回の独立clean buildを行い、それぞれbyte一致しました。

stable packageはcanonical file order、固定mtime、uid/gid 0、numeric owner、`gzip -n`で2回生成し、archive SHA-256と展開内容が一致しました。`SHA256SUMS.txt`、packaged/build JAR同一性、`LICENSE-NOT-SELECTED.txt`、verifier/禁止物の非収録もPASSしました。

clean-roomはcommit済みstable HEADのtracked sourceだけをdetached worktreeへ展開し、外部入力のPaper JAR、EULA、Multiverse JARだけを明示的にコピーして全回帰を再実行しました。main/cleanのproduction JAR、verifier JAR、stable package、world checksums、必須logは一致しました。

## 1.0.0-rc.1からのupgrade

1. server、world、production JAR、Multiverse `worlds.yml`をbackupします。
2. serverを完全停止します。
3. `LegacyMiningWorld-1.0.0-rc.1.jar`を退避し、`LegacyMiningWorld-1.0.0.jar`だけを配置します。
4. test worldでversion、seed、generator、spawn、既存chunk、新規chunk、ログを確認してから本番へ適用します。

RCからの設定変更はありません。config、database、runtime dataのmigrationとworld再作成は不要です。production class payloadが同一のため、既存generated chunksは変化せず、新規chunkの生成仕様も変わりません。

## rollback

serverを停止し、stable適用後のworld/logを保全してからstable JARを退避し、backup済みのRC JARだけを戻します。RC/stable JARを同時に配置しないでください。config/data migrationはありませんが、復元後のversionとgenerator loadをログで確認します。確実なworld復元が必要な場合は、JARだけでなく更新前worldとMultiverse `worlds.yml`を対で戻します。

## ユーザー手動受入

[ユーザー受入チェックリスト](user-acceptance-checklist.md)は作成済みです。これは人間が実際のゲームクライアントと運用環境で記入する文書であり、Codexは実施・記入していません。チェックリストはrepository docsとして提供し、stable release packageには含めません。

## known limitations

- 水、溶岩、洞窟、構造物、DEEPSLATE、copper、emerald、badlands追加goldは生成しません。
- 同一seedでも旧Vanilla 1.16.5とblock座標単位の完全一致は保証しません。
- 既存chunkを遡及生成・再生成しません。初回導入では新しい空worldを使用してください。
- 性能は環境依存です。24時間耐久試験と広範な利用者実機試験は未実施です。
- ライセンスは未選択で、public redistribution termsは確定していません。

## publication statusとfuture Phase 6

- Git tag: not created
- push: not performed
- GitHub Release: not created
- Maven publishing: not performed
- external upload: not performed

将来public releaseを行うoptional Phase 6では、ライセンス選択、public distribution terms、Git tag、GitHub Release、公開checksums、必要に応じた署名、外部publicationを別途実施します。これらにはユーザーの明示承認が必要です。
