# LegacyMiningWorld 1.0.1 導入手順

## 必要環境

- Paper 26.1.2 build 69
- Java 25
- 任意: Multiverse-Core 5.7.2

`1.0.1`はMIT License（SPDX: `MIT`）のtechnical stable版です。Copyright (c) 2026 nobu0707。MIT条件に従ってpublic distributionできますが、実際の外部公開は実施していません。

## production JARの配置

1. server全体、対象world、Multiverse利用時は`plugins/Multiverse-Core/worlds.yml`をbackupします。
2. serverを完全停止します。
3. 旧LegacyMiningWorld JARを退避し、production JAR `LegacyMiningWorld-1.0.1.jar`だけを`plugins/`へ配置します。
4. `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`は配置しません。これはtest-only成果物です。
5. 起動後、load/enable成功とversion `1.0.1`をログで確認します。

release packageは`LegacyMiningWorld-1.0.1-release.tar.gz`です。package内`SHA256SUMS.txt`は`sha256sum -c SHA256SUMS.txt`で検証できます。ライセンスは[root LICENSE](../LICENSE)と[licensing.md](licensing.md)を参照してください。

## 1.0.0からの更新

`LegacyMiningWorld-1.0.0.jar`を`LegacyMiningWorld-1.0.1.jar`へ置換します。両JARを同時に配置しないでください。production Javaとclass payloadは同一で、変更はversion/license/packageだけです。config、database、runtime data migrationとworld再作成は不要です。既存chunkは変更されません。

## Bukkit generator設定

新しいworldを作る場合、server停止中に`bukkit.yml`へ指定できます。

```yaml
worlds:
  legacy_mining:
    generator: LegacyMiningWorld
```

generator idは`LegacyMiningWorld`または`LegacyMiningWorld:default`です。world seedは明示して運用記録へ残してください。固定spawnはgenerator API上 `(0.5, 71.0, 0.5)`、永続block座標は通常 `(0, 71, 0)`です。

## Multiverse-Core 5.7.2

Multiverseは任意で、production dependencyではありません。

```text
mv generators list
mv create legacy_mining normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

## 制約

- 初回導入で既存worldへ後付けせず、新しい空worldを使ってください。
- 既存chunkへのretro-generationはありません。
- 同一seedでも旧Vanilla 1.16.5とのblock座標完全一致は保証しません。
- pluginを削除する前にgenerator依存worldとbackupを確認してください。
- [ユーザー受入チェックリスト](user-acceptance-checklist.md)は人間が実運用環境で実施してください。

Git tag、push、release作成、外部uploadは実施していません。
