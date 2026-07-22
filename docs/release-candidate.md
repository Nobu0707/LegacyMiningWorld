# LegacyMiningWorld 1.0.0-rc.1 release candidate

> この文書は`1.0.0-rc.1`の履歴です。LegacyMiningWorldの現行版はMIT Licenseのtechnical stable `1.0.1`です。現行成果物と検証結果は[stable release情報](stable-release.md)を参照してください。

## 範囲と対応環境

この候補は固定採掘地形、旧式岩盤床、5地質、6鉱石をPaper 26.1.2 build 69 / Java 25で提供します。Multiverse-Core 5.7.2は任意で、production側にMultiverse依存はありません。stable版ではなく、ライセンスも未選択です。

release artifactは`LegacyMiningWorld-1.0.0-rc.1.jar`、再現可能JAR SHA-256は`abead261a33ef1415c27f9a9832a51d7383c33bb712286a0e92942fce65b6161`です。`LegacyMiningWorld-1.0.0-rc.1-release.tar.gz`のSHA-256は`c9029ce0587a116c71bc13e368be771be810ea1c03024f4002e6a6a99bab17a3`です。

## 検証matrix

| 検証 | 結果 |
|---|---|
| unit / geology / ore / strict compiler | PASS |
| 通常Paper固定4chunk smoke | PASS |
| Multiverse create / save / restart / auto-load | PASS |
| 1,089chunk A1 forward / A2 existing / B1 reverse | PASS |
| production/verifier JAR二重clean build | PASS |
| release package二重生成 | PASS |
| committed tracked source clean-room | PASS（最終summaryにHEADを記録） |

大規模検証はfull checksum `-56844145234233245`、Y=5..67 checksum `-7581040318536063180`、chunk report SHA-256 `e5e26ff1bdac20270d314098728c418ed5faf50501d08e913cf39bacc1b14e27`、ore histogram SHA-256 `961eebd1e9d4443e1a30134fa43c4a706bd29d5dff8667953aab1da85bd9e55c`を固定しました。禁止blockと許可以外non-AIRは0、Y=0/11/70/100のbiome 1,115,136件はすべてPLAINSです。

性能例は1,089chunkのgrid処理が約108.9秒、約10 chunks/s、約983,000 blocks/s、maximum RSS約1.15～1.22 GiBでした。これは検証環境固有であり保証値ではありません。

## RC時点の制約、rollback、stable昇格

- 水、溶岩、洞窟、構造物、DEEPSLATE、copper、emeraldは生成しません。
- 同一seedの旧Vanilla 1.16.5とblock座標単位で完全一致する保証はありません。
- 既存chunkは遡及生成されません。新しい空worldで利用してください。
- rollback時はserverを停止し、旧JARと更新前world/Multiverse設定backupを対で復元してください。

RC作成時点では、1.0.0への昇格を別作業としていました。当時のproduction JAR/package SHA、検証matrix、goldenはこの履歴文書にそのまま保持します。

その後のPhase 5でproduction機能を変更せずtechnical stable `1.0.0`へ昇格し、Phase 6でMIT Licenseの`1.0.1`配布物へ更新しました。このRC節のライセンス記述は当時の履歴です。ユーザー受入チェックリストは作成済みですがCodexによる手動プレイは未実施で、24時間耐久試験も未実施です。tag、push、GitHub Release、Maven publishing、外部uploadは実施していません。
