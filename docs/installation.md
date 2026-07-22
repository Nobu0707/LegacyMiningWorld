# LegacyMiningWorld 1.0.0 導入手順

## 必要環境

- Paper 26.1.2 build 69
- Java 25
- 任意: Multiverse-Core 5.7.2

`1.0.0`はtechnical stable版です。ライセンスは未選択であり、現時点の成果物はprivate/internal deploymentだけを対象とします。open-source licenseや公開再配布許可は付与されていません。

導入前にserver全体、対象world、Multiverseを使う場合は`plugins/Multiverse-Core/worlds.yml`をbackupしてください。

## production JARの配置

serverを完全停止し、production JAR `LegacyMiningWorld-1.0.0.jar`だけを`plugins/`へ配置します。`plugins/`にはLegacyMiningWorld production JARを1本だけ置き、古いalpha/RC/stable JARとの重複を避けてください。

`LegacyMiningWorld-MultiverseVerifier-1.0.0.jar`は導入しないでください。verifierは開発時のtest-only成果物であり、stable release packageにも含まれません。

初回起動後、ログにpluginのload/enable完了とversion `1.0.0`が出ることを確認します。production pluginには設定ファイル、command、database、runtime data、外部runtime libraryはありません。

stable release packageは`LegacyMiningWorld-1.0.0-release.tar.gz`です。production JARとpackageのSHA-256および正確な内容は[stable release情報](stable-release.md)を参照してください。

## 1.0.0-rc.1からの更新

1. server、world、現在のplugin JAR、Multiverse `worlds.yml`をbackupします。
2. serverを完全停止します。
3. `plugins/LegacyMiningWorld-1.0.0-rc.1.jar`を退避または削除し、同じdirectoryに残っていないことを確認します。
4. `LegacyMiningWorld-1.0.0.jar`を`plugins/`へ配置します。
5. test worldを先に読み込み、version、seed、generator、spawn、既存chunk、新規chunk、ログを確認します。

RCとstableのproduction class payloadは同一で、JAR内`plugin.yml`の差はversionだけです。config migration、database migration、runtime data migration、world再作成は不要です。既存の生成済みchunkは変更されません。rollbackに備えてRC JARと更新前backupは検証完了まで保管してください。

## standard Bukkit generator設定

新しいworldをBukkit設定から作る場合は、server停止中に`bukkit.yml`へ指定します。

```yaml
worlds:
  legacy_mining:
    generator: LegacyMiningWorld
```

generator idは`LegacyMiningWorld`または`LegacyMiningWorld:default`です。未指定、blank、大小文字を問わない`default`もplugin API上で受理します。world seedはserver側で明示して記録してください。固定spawnはgenerator API上 `(0.5, 71.0, 0.5)`、永続block座標は通常 `(0, 71, 0)`です。

## Multiverse-Core 5.7.2の例

Multiverseは任意であり、このpluginのproduction依存ではありません。Multiverse側のpermissionを持つ利用者が次を実行します。

```text
mv generators list
mv create legacy_mining normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

`mv generators list`に`LegacyMiningWorld`が表示されることを先に確認します。この例のseedは`11652021`です。別のseedを使用する場合は作成前に値を決め、commandと運用記録へ同じ値を記載してください。`--no-adjust-spawn`はMultiverseの安全地点調整で固定spawnを変更させないための指定です。

## 導入後の受入

実際のゲームクライアントと運用環境で[ユーザー受入チェックリスト](user-acceptance-checklist.md)を実施してください。このチェックリストは未記入で提供しており、Codexは手動プレイ結果を実施済みとしていません。

## 重要な制約

- 初回導入時に既存worldへ後付けでgeneratorを適用しないでください。新しい空world名を使ってください。RCからstableへの更新では既存worldの再作成は不要です。
- 既存chunkは変更されません。初回導入後に生成される新規chunkだけがgeneratorの対象です。
- pluginを削除するとgenerator依存worldを安全に読み込めない可能性があります。削除・rollback前に依存関係とbackupを確認してください。
- 同一seedでも旧Vanilla 1.16.5とのblock座標完全一致は保証しません。
- ライセンス決定前にstable packageを公開配布・再配布しないでください。
