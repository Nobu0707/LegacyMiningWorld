# LegacyMiningWorld 1.0.1 ユーザー受入チェックリスト

この文書は、ユーザーが実際のゲームクライアントと運用環境でMIT License（SPDX: `MIT`）のtechnical stable `1.0.1`を確認するための未記入チェックリストです。Copyright (c) 2026 nobu0707。Codexは以下の項目を実施・確認・記入していません。実施者が事実に基づいてチェックし、FAILや懸念をnotesへ記録してください。

この文書はrepository docsとして提供し、stable release packageには含めません。

## A. 事前準備

- [ ] server全体、対象world、plugin JAR、設定をbackupした
- [ ] Multiverseを使う場合、`plugins/Multiverse-Core/worlds.yml`をbackupした
- [ ] serverを完全停止した
- [ ] Paper 26.1.2 build 69であることを確認した
- [ ] `java -version`でJava 25であることを確認した
- [ ] `plugins/`から旧LegacyMiningWorld JARと重複JARを除去した
- [ ] production JAR `LegacyMiningWorld-1.0.1.jar`を配置した
- [ ] `LegacyMiningWorld-MultiverseVerifier-1.0.1.jar`を配置していない
- [ ] Multiverse-Coreを使う場合、version 5.7.2であることを確認した
- [ ] 起動ログでLegacyMiningWorld version `1.0.1`のload/enable成功を確認した
- [ ] 起動ログに`SEVERE`、class loading error、`generator not found`がないことを確認した

## B. world作成

consoleまたは必要なpermissionを持つ管理者として、次の正確なcommandを実行します。

```text
mv generators list
mv create legacy_mining_acceptance normal --seed 11652021 --generator LegacyMiningWorld --no-adjust-spawn
```

- [ ] `mv generators list`に`LegacyMiningWorld`が表示された
- [ ] create commandが成功した
- [ ] world名が`legacy_mining_acceptance`である
- [ ] environmentがNORMALである
- [ ] seedが`11652021`である
- [ ] generatorが`LegacyMiningWorld`である
- [ ] Multiverseの安全地点調整を無効にした状態で作成した

## C. 目視・プレイ確認

### 地表とspawn

- [ ] playerのspawn位置がY=71である
- [ ] 地表がY=70である
- [ ] 地表が平坦である
- [ ] 地表下に意図しない洞窟がない
- [ ] 水が自然生成されていない
- [ ] 溶岩が自然生成されていない

### Y=11での採掘

十分な範囲を採掘し、存在するmaterialを目視確認します。短い探索で特定鉱石が見つからない場合は、それだけで直ちに分布FAILとせず、探索範囲と座標をnotesへ記録してください。

- [ ] STONEを確認した
- [ ] COAL_ORE（石炭）を確認した
- [ ] IRON_ORE（鉄）を確認した
- [ ] GOLD_ORE（金）を確認した
- [ ] REDSTONE_ORE（レッドストーン）を確認した
- [ ] DIAMOND_ORE（ダイヤモンド）を確認した
- [ ] LAPIS_ORE（ラピスラズリ）を確認した
- [ ] GRANITE（花崗岩）を確認した
- [ ] DIORITE（閃緑岩）を確認した
- [ ] ANDESITE（安山岩）を確認した
- [ ] DIRT（土）を確認した
- [ ] GRAVEL（砂利）を確認した

### 生成されないmaterial

- [ ] DEEPSLATE（深層岩）がない
- [ ] COPPER_ORE（銅鉱石）がない
- [ ] EMERALD_ORE（エメラルド鉱石）がない
- [ ] 深層岩系鉱石がない

## D. 再起動

- [ ] worldとserverをbackupした
- [ ] `save-all`後にserverを正常停止した
- [ ] 同じJAR/config/worldを保持してserverを再起動した
- [ ] `legacy_mining_acceptance`が自動loadされた
- [ ] seed `11652021`が維持された
- [ ] generator `LegacyMiningWorld`が維持された
- [ ] spawn Y=71が維持された
- [ ] 採掘済みchunkとplayerによる変更が維持された
- [ ] 未生成方向へ移動し、新規chunkが正常に生成された
- [ ] restart後のログに`generator not found`がない
- [ ] restart後のログに`SEVERE`やclass loading errorがない

## E. 運用確認

- [ ] 通常時と新規chunk生成時のTPSを記録した
- [ ] 通常時と新規chunk生成時のMSPTを記録した
- [ ] heapおよびprocess memoryを確認した
- [ ] watchdog、timeout、OutOfMemoryErrorがない
- [ ] LegacyMiningWorld、Paper、Multiverse関連logを確認した
- [ ] world backupを作成し、復元手順を確認した
- [ ] Multiverse commandとworld accessのpermissionを確認した
- [ ] 想定するplayerがworldへ移動・退出できることを確認した
- [ ] 運用記録にworld名、seed、generator、JAR version/SHA-256を保存した

## F. 判定欄

| 項目 | 人間による記入欄 |
|---|---|
| 実施日 | |
| 実施者 | |
| server build | |
| Java version | |
| Multiverse version（未使用ならN/A） | |
| LegacyMiningWorld JAR SHA-256 | |
| world backup location/reference | |

最終判定:

- [ ] PASS
- [ ] FAIL

notes:

```text



```

FAILの場合はproduction worldへの適用を止め、server、world、log、使用JARを保全してください。機能欠陥が疑われる場合、同じ変更でstableを修正せず、新しいRCを検討します。
