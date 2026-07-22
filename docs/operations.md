# LegacyMiningWorld 1.0.1 運用ガイド

## stable運用とbackup

production JARは`LegacyMiningWorld-1.0.1.jar`です。`plugins/`にはこのJARを1本だけ配置し、`1.0.0`や他の旧JARと重複させません。test-onlyの`LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`も運用serverへ導入しません。

server、対象world、world名、seed、generator id、JAR version/SHA-256を定期backupします。Multiverse利用時は`plugins/Multiverse-Core/worlds.yml`も対象です。このplugin自身はruntime data、database、configを作成しません。

起動ログでversion `1.0.1`、load/enable、`SEVERE`、class loading error、`generator not found`を確認します。Multiverse-Core 5.7.2では`mv info <world>`、`mv list`、`mv generators list`を使えます。

## 1.0.0からの更新

1. server/world/JAR/`worlds.yml`をbackupします。
2. serverを完全停止します。
3. `LegacyMiningWorld-1.0.0.jar`を退避し、`LegacyMiningWorld-1.0.1.jar`だけへ置換します。
4. test worldでversion、seed、generator、spawn、既存chunk、新規chunk、ログを確認します。

production Javaとclass payloadは同一です。変更はversion、MIT License、文書、packageだけで、config/data migrationとworld再作成は不要です。既存chunkへの遡及生成はなく、新規chunkの生成結果も1.0.0と同じです。

## rollback

serverを停止し、更新後world/logを保全してから1.0.1 JARを退避し、backup済みJARを1本だけ戻します。確実な復元が必要ならworldと`worlds.yml`も更新前backupへ戻します。generatorが見つからない状態で対象worldを読み込まないでください。

## troubleshooting

- generator not found: JAR重複、plugin load失敗、`mv generators list`、generator idを確認します。
- wrong Java/Paper: Java 25 / Paper 26.1.2 build 69を確認します。
- wrong plugin version: 起動ログが`LegacyMiningWorld 1.0.1`で、旧JARが残っていないことを確認します。
- verifier installed: `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`を退避して再起動します。
- wrong seed: 作成command、`mv info`、運用記録を照合します。
- spawn adjustment: 固定spawn確認時はMultiverse作成commandへ`--no-adjust-spawn`を付けます。

## ライセンスとpublication

`1.0.1`はMIT License（SPDX: `MIT`）です。Copyright (c) 2026 nobu0707。詳細は[licensing.md](licensing.md)を参照してください。MIT条件に従うpublic distributionは可能ですが、Git tag、push、GitHub Release、Maven publish、外部uploadは実施していません。
