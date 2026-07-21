# LegacyMiningWorld 運用ガイド

## 日常運用とbackup

serverと対象worldを定期backupし、world名、seed、generator id、導入JARのversion/SHA-256を一緒に記録してください。Multiverseを使う場合は`plugins/Multiverse-Core/worlds.yml`もbackup対象です。このplugin自身はruntime data、database、configを作成しません。

状態確認にはMultiverse-Core 5.7.2の`mv info <world>`、`mv list`、`mv generators list`を使えます。production pluginには独自commandやpermissionはありません。通常ログのload、enable、generator request、disable行を監視し、`SEVERE`、class loading、generator not foundを確認してください。

world削除は不可逆で危険なため、この文書では自動削除commandを推奨しません。停止、複数backup、対象pathとMultiverse登録の照合を行い、運用者の手順で実施してください。CoreProtect等の変更記録・復元pluginは別責務であり、本generatorのbackup代替ではありません。

## 更新手順

1. test worldで候補JARと同じPaper/Java構成を確認します。
2. server、world、現在のplugin JAR、Multiverse `worlds.yml`をbackupします。
3. serverを完全停止します。
4. 古いLegacyMiningWorld JARを退避し、新しいproduction JAR 1本へ置換します。
5. test worldを先に読み込み、既存chunkと新規chunk、seed、spawn、generator表示、ログを確認します。
6. 問題がなければ本番worldを読み込みます。

既存chunkは更新されません。更新後に生成する新規chunkは新JARの実装で生成されるため、versionをまたぐ境界が生じる可能性があります。性能値はCPU、storage、heap、view distance、他pluginに依存し、このリポジトリの測定値を絶対閾値にしないでください。

## rollback

serverを停止し、更新後のworldとログを保全してから旧JARを戻します。更新後に新規生成したchunkを旧版でそのまま使うかは事前に判断し、確実な復元が必要なら更新前world backupと`worlds.yml`を対で戻します。generatorを外した状態やpluginが見つからない状態で対象worldを読み込まないでください。

## troubleshooting

- generator not found: JAR名の重複、plugin load失敗、`mv generators list`、generator idを確認します。
- wrong Java: `java -version`がJava 25であることを確認します。
- wrong Paper: Paper 26.1.2 build 69を使用し、異なるAPI版で起動しないでください。
- Multiverse syntax: 5.7.2では一覧が`mv generators list`、作成が`mv create ... --generator LegacyMiningWorld --no-adjust-spawn`です。
- old JAR duplicate: `plugins/`にLegacyMiningWorld production JARが1本だけあることを確認します。
- verifier accidental installation: `LegacyMiningWorld-MultiverseVerifier-*`を削除して再起動します。これは配布・運用用ではありません。
- world already exists: 上書きせず、新しい空world名を使うか、既存登録とfilesystemを慎重に調査します。
- wrong seed: 作成時のseedと`mv info`、保存した運用記録を照合します。
- spawn adjustment: 固定spawnの評価には作成時に`--no-adjust-spawn`を付けます。
