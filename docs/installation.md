# LegacyMiningWorld 1.0.0-rc.1 導入手順

## 必要環境

- Paper 26.1.2 build 69
- Java 25
- 任意: Multiverse-Core 5.7.2

これはrelease candidateでありstable版ではありません。導入前にserver全体とworldをbackupし、新しい空world名で試験してください。

## production JARの配置

serverを停止し、`LegacyMiningWorld-1.0.0-rc.1.jar`だけを`plugins/`へ配置します。古い版との重複を避け、`LegacyMiningWorld-MultiverseVerifier-1.0.0-rc.1.jar`は導入しないでください。verifierは開発時のtest-only成果物です。

初回起動後、ログにpluginのload/enable完了と正しいversionが出ることを確認します。production pluginには設定ファイルもcommandもありません。

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
mv create legacy_mining normal --seed <seed> --generator LegacyMiningWorld --no-adjust-spawn
```

`mv generators list`に`LegacyMiningWorld`が表示されることを先に確認します。`--no-adjust-spawn`はMultiverseの安全地点調整で固定spawnを変更させないための指定です。

## 重要な制約

- 既存worldへ後付けでgeneratorを適用しないでください。新しい空world名を使ってください。
- 既存chunkは変更されません。導入後に初めて生成されるchunkだけが対象です。
- pluginを削除するとgenerator依存worldを安全に読み込めない可能性があります。削除・rollback前に依存関係とbackupを確認してください。
- 同一seedでも旧Vanilla 1.16.5とのblock座標完全一致は保証しません。
