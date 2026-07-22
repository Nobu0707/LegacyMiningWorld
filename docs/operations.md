# LegacyMiningWorld 1.0.0 運用ガイド

## stable運用とbackup

production JARは`LegacyMiningWorld-1.0.0.jar`です。`plugins/`にはこのproduction JARを1本だけ配置し、`1.0.0-rc.1`や他の旧JARと同時に置かないでください。test-onlyの`LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`も運用serverへ導入しません。

serverと対象worldを定期backupし、world名、seed、generator id、導入JARのversion/SHA-256を一緒に記録してください。Multiverseを使う場合は`plugins/Multiverse-Core/worlds.yml`もbackup対象です。このplugin自身はruntime data、database、configを作成しません。

状態確認にはMultiverse-Core 5.7.2の`mv info legacy_mining`、`mv list`、`mv generators list`を使えます。world名を変更した環境では、運用記録にある実際のworld名で`mv info`を実行してください。production pluginには独自commandやpermissionはありません。通常ログのload、enable、generator request、disable行を監視し、version `1.0.0`、`SEVERE`、class loading error、`generator not found`の有無を確認してください。

world削除は不可逆で危険なため、この文書では自動削除commandを推奨しません。停止、複数backup、対象pathとMultiverse登録の照合を行い、運用者の手順で実施してください。CoreProtect等の変更記録・復元pluginは別責務であり、本generatorのbackup代替ではありません。

## 1.0.0-rc.1からの更新

1. test worldでstable JARと同じPaper/Java構成を確認します。
2. server、world、現在のRC JAR、Multiverse `worlds.yml`をbackupします。
3. serverを完全停止します。
4. `LegacyMiningWorld-1.0.0-rc.1.jar`を退避し、`LegacyMiningWorld-1.0.0.jar`だけへ置換します。
5. test worldを先に読み込み、既存chunkと新規chunk、seed、spawn、generator表示、stable version、ログを確認します。
6. 問題がなければ本番worldを読み込みます。

RCとstableのproduction class payloadは同一で、設定変更はありません。config、database、runtime dataのmigrationやworld再作成は不要です。既存chunkは更新されず、stableで生成する新規chunkもRCと同じproduction生成payloadを使用します。

将来、production生成payloadが異なる別versionへ更新する場合は、新旧versionをまたぐchunk境界が生じる可能性があります。これはpayload同一性を確認済みの`1.0.0-rc.1`→`1.0.0`には該当しません。

性能値はCPU、storage、heap、view distance、他pluginに依存し、このリポジトリの測定値を絶対閾値にしないでください。TPS、MSPT、memory、server log、backup成否、permission、player accessを実運用で監視してください。

## rollback

serverを停止し、更新後のworldとログを保全してからstable JARを退避し、backup済みのRC JARを1本だけ戻します。stableとRCのproduction class payloadは同一で、config/data migrationはありませんが、JAR重複を避け、復元後のversionとgenerator loadをログで確認してください。

別の旧versionへrollbackする場合、更新後に新規生成したchunkをそのまま使うかを事前に判断し、確実な復元が必要なら更新前world backupと`worlds.yml`を対で戻します。generatorを外した状態やpluginが見つからない状態で対象worldを読み込まないでください。

## ユーザー受入

[ユーザー受入チェックリスト](user-acceptance-checklist.md)を、ゲームクライアントと実際の運用環境で人間が実施・記入してください。Codexはチェック項目を実行済みにしていません。結果と実施日、server build、Java、Multiverse version、notesを運用記録と一緒に保管してください。

## troubleshooting

- generator not found: JAR名の重複、plugin load失敗、`mv generators list`、generator idを確認します。
- wrong Java: `java -version`がJava 25であることを確認します。
- wrong Paper: Paper 26.1.2 build 69を使用し、異なるAPI版で起動しないでください。
- wrong plugin version: 起動ログが`LegacyMiningWorld 1.0.0`であることと、RC JARが残っていないことを確認します。
- Multiverse syntax: 5.7.2では一覧が`mv generators list`、作成例が`mv create legacy_mining normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn`です。
- old JAR duplicate: `plugins/`にLegacyMiningWorld production JARが1本だけあることを確認します。
- verifier accidental installation: `LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`を退避して再起動します。これは配布・運用用ではありません。
- world already exists: 上書きせず、新しい空world名を使うか、既存登録とfilesystemを慎重に調査します。
- wrong seed: 作成時のseedと`mv info`、保存した運用記録を照合します。
- spawn adjustment: 固定spawnの評価には作成時に`--no-adjust-spawn`を付けます。

## 配布上の注意

`1.0.0`はtechnical stableですが、ライセンスは未選択です。stable JAR/packageはprivate/internal deploymentだけを対象とし、open-source licenseやpublic redistribution permissionを付与しません。public distributionには別途ライセンス決定とユーザーの明示承認が必要です。tag、push、GitHub Release、Maven publish、外部uploadはPhase 5では実施していません。
