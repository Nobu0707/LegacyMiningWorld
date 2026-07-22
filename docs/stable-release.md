# LegacyMiningWorld 1.0.1 stable release

## status

| 項目 | 値 |
|---|---|
| version | `1.0.1` |
| stable | YES |
| release candidate | NO |
| license | MIT License |
| SPDX | `MIT` |
| public-distribution-ready | YES |
| externally published | NO |

Copyright (c) 2026 nobu0707。ライセンスの正本は[root LICENSE](../LICENSE)、説明は[licensing.md](licensing.md)です。MIT条件に従うpublic distributionが可能ですが、tag、push、release作成、外部uploadは実施していません。

## 対応環境とdependency

- PaperMC 26.1.2 build 69
- Java 25
- Gradle 9.2.0
- 任意統合: Multiverse-Core 5.7.2

production pluginはPaper/Bukkit公開APIだけを使用します。Paper APIは`compileOnly`で、Multiverse-Coreはproduction dependencyではありません。Paper/Multiverse/external libraryを配布JARへ同梱しません。

## 成果物

- production JAR: `LegacyMiningWorld-1.0.1.jar`
- SHA-256: `cb63c1d31fbc95fe9da262e900a3072687c9b4d66c28d11cdcf01cfa4cba65f4`
- test-only verifier: `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`
- SHA-256: `90641e05b211c3e90eb10152463e6afa0da866ad52f0a766b7436141ad16fea1`
- release package: `LegacyMiningWorld-1.0.1-release.tar.gz`
- SHA-256: `e17ba6ee75f6e3bdcc0a114b1f641956f7925597dc5ae9f4cb0f85900ce02c8b`
- root LICENSE SHA-256: `8b7be028c1e6da4e34647010a229cc8b85c715c13cbe03959ccf8bbbd5e4e2d8`

package内容は次の5ファイルだけです。

```text
LegacyMiningWorld-1.0.1/
  LegacyMiningWorld-1.0.1.jar
  README.txt
  RELEASE_NOTES.md
  SHA256SUMS.txt
  LICENSE
```

production JARとverifier JARは`META-INF/LICENSE`を1件含み、root/package LICENSEとbyte一致します。release packageにverifier、test、source、anchors、Paper/Multiverse JAR、server、world、review logsは含めません。固定mtime、uid/gid 0、sort済みtar、`gzip -n`で二重生成し、archive SHA-256一致を確認します。

## 1.0.0からの変更

baselineは`2c487560b0d862df0af0c452c3686a7ca72fade3`（`chore: promote LegacyMiningWorld 1.0.0`）です。

- version `1.0.0` → `1.0.1`
- MIT License適用
- JAR/packageへのLICENSE同梱
- README、CHANGELOG、docs、release/review scripts更新
- production Java変更: 0
- production `plugin.yml`テンプレート変更: 0
- Paper dependency、generator設定、seed、salt、placement、anchors、grid spec変更: 0
- production class entry名・各class bytes: IDENTICAL
- JAR内`plugin.yml`: version-only

したがってworld-generation結果は不変です。config、database、runtime data migrationとworld再作成は不要で、既存chunkへの遡及生成もありません。同一seedでも旧Vanilla 1.16.5とのblock座標完全一致は保証しません。

## Phase 6 lightweight validation

| 検証 | 結果 |
|---|---|
| `clean build -x test` | PASS |
| `multiverseVerifierJar -x test` | PASS |
| production/verifier JAR content | PASS |
| 1.0.0/1.0.1 class payload | IDENTICAL |
| `plugin.yml` version-only | PASS |
| root/JAR/package LICENSE equality | PASS |
| MIT License audit | PASS |
| release package self-check | PASS |
| release package reproducibility | PASS |
| current public-license status scan | PASS |

production Javaと生成ロジックに変更がないため、unit/integration test、Paper smoke、Multiverse create/restart、1,089chunk large-scale validation、clean-room full regressionは今回実行していません。Phase 5の過去のPASSログを1.0.1で再実行した結果として更新・複製しません。

## 1.0.0からのupgrade

1. server、world、production JAR、Multiverse利用時は`worlds.yml`をbackupします。
2. serverを完全停止します。
3. `LegacyMiningWorld-1.0.0.jar`を退避します。
4. `LegacyMiningWorld-1.0.1.jar`だけを配置します。旧JARと重複させません。
5. 起動ログのversion、generator load、既存chunk、新規chunkを確認します。

production class payloadは同一です。config/data migrationやworld再作成は不要です。rollback時もserver停止・backup・JAR 1本の置換を守ってください。

## publication status

- public-distribution-ready: YES
- externally published: NO
- Git tag: not created
- push: not performed
- GitHub Release: not created
- Maven/Modrinth/Hangar等へのpublish: not performed
- external upload: not performed
