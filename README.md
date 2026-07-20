# LegacyMiningWorld

LegacyMiningWorld 0.3.0-alpha.1 は、深層岩が追加される前の Java Edition 1.16.5 相当の採掘環境を Paper 上のカスタムワールドとして再現するためのプラグインです。対象環境は PaperMC 26.1.2 build 69 と Java 25、ビルド方式は Gradle Kotlin DSL です。

## 現在の状態

Phase 0（開発基盤）、Phase 1（基本地形）、Phase 2A（地中岩石の純粋生成エンジン）が完了しています。標準 Bukkit generator `LegacyMiningWorld`または`LegacyMiningWorld:default`としてPhase 1の水平地形をruntimeで利用できます。

基本層はY=0～4の旧式岩盤床、Y=5～67の`STONE`、Y=68～69の`DIRT`、Y=70の`GRASS_BLOCK`です。Y<0とY>70は空気のままで、水・溶岩・洞窟・装飾・構造物・Vanilla鉱石・深層岩は生成しません。固定スポーンは`(0.5, 71.0, 0.5)`です。

Phase 2は、旧式鉱脈形状・seed・chunk境界をPaper書き込みと分けて安全に検証するため2Aと2Bへ分割しました。Phase 2Aでは次を実装済みです。

- DIRT、GRAVEL、GRANITE、DIORITE、ANDESITEの5種類
- Mojang公式1.16.5 server JAR/mappingに基づく設定と旧式楕円体鉱脈
- world seed/source chunkからの決定論的decoration/feature seed
- target chunk ownershipと周囲3×3 source chunkからの境界再構築
- natural stone置換規則、golden値、negative chunk、境界、並行性のJUnitテスト

公式1.16.5設定は全featureがvein size 33、attemptは順に10/8/10/10/10です。origin YはDIRTとGRAVELが0以上256未満、GRANITE・DIORITE・ANDESITEが0以上80未満です。DIRT/GRAVELも80未満とする当初の期待表とは公式実装が異なったため、一次資料を優先しました。詳細は[Java Edition 1.16.5 地中岩石調査](docs/vanilla-1.16.5-geology.md)に記録しています。

地中岩石engineはまだPaper実ワールドへ接続されていません。`BlockPopulator`、`LimitedRegion`、`getDefaultPopulators`はPhase 2Bで実装し、バージョン0.3.0で実ワールド配置を検証します。Minecraft 1.16.5の同一seedとblock座標単位の完全一致は保証せず、同一plugin version・world seed・chunk座標で同じplacement planになることを保証範囲とします。

鉱石はPhase 3、Multiverse-Core統合はPhase 4の対象であり、まだ未実施です。ローカルの`server/plugins/multiverse-core-5.7.2.jar`は後続統合試験用で、依存にも追加せずGit追跡しません。

## ビルドと検証

Java 25を有効にして、リポジトリ直下で実行します。

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon test --tests 'net.nobu0707.legacyminingworld.geology.*'
./gradlew --no-daemon build
./scripts/run-review-checks.sh
```

`run-review-checks.sh`は全単体テスト、Phase 2A engine専用テストログ、ビルド、JAR検査に加え、使い捨ての`build/paper-smoke/`でPhase 1 runtimeを検査します。Phase 2Aはruntime behaviorを変更しないため、スモークは従来どおり実block層、PLAINS、plugin lifecycleと正常停止を確認し、地中岩石の実配置は成功条件にしません。

スモークには次の2ファイルが必要です。`server/`はローカル専用であり、Git追跡やレビューアーカイブへの収録は禁止です。

```text
server/paper-26.1.2-69.jar
server/eula.txt              # eula=true
```

## レビューアーカイブ

Phaseのコミット後に、期待するHEAD件名の一部を引数で検証しながら作成できます。どちらのスクリプトも先に最新のレビュー検証を実行します。

```bash
./scripts/make-review-archive.sh "feat: add deterministic geology engine"
./scripts/make-full-review-archive.sh "feat: add deterministic geology engine"
```

差分レビュー版とHEAD時点の安全な追跡ファイル一式を含む全体レビュー版が作成されます。Paper本体、Multiverse-Core、`server/`、Mojang調査物、ビルド成果物、ログ、秘密情報候補は除外されます。

## 最終仕様上の注意

現行通常dimensionの`minY=-64`は変更せず、Y=-64～-1を空気のまま残し、Y=0を旧式worldの底面として扱います。Phase 1 runtimeはPaper実機スモークで確認済みです。Phase分割は[開発Phase](docs/development-phases.md)、設計上の保証範囲は[設計判断](docs/design-decisions.md)を参照してください。

標準Bukkit generator公開は実装済みですが、Multiverse-Core本体を使った統合試験はPhase 4で行います。

```text
/mv create legacy_mining normal --generator LegacyMiningWorld
```

## ライセンス

License not yet selected.
