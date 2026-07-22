# ライセンス

LegacyMiningWorld `1.0.1`以降はMIT Licenseで提供します。

- SPDX identifier: `MIT`
- Copyright (c) 2026 nobu0707
- 正式なライセンス本文: [root LICENSE](../LICENSE)

MIT Licenseの条件に従い、ソフトウェアの利用、複製、変更、結合、公開、配布、サブライセンス、販売が可能です。配布するcopyまたは重要な部分にはcopyright noticeとpermission noticeを保持してください。本ソフトウェアは無保証です。正確な条件はLICENSE本文を確認してください。

ライセンス本文は次にも同梱します。

- production JAR: `META-INF/LICENSE`
- test-only verifier JAR: `META-INF/LICENSE`
- release package: `LICENSE`

Paper APIは`compileOnly` dependencyであり、JARへ同梱しません。Multiverse-Coreもproduction dependencyではなく、JAR/packageへ同梱しません。PaperとMultiverseはそれぞれのprojectが定めるライセンスに従います。

`1.0.0`以前の成果物は当時ライセンスが選択されていませんでした。MIT条件で公開配布可能な対象は`1.0.1`以降です。`1.0.1`はpublic-distribution-readyですが、Git tag、push、GitHub Release、Maven publish、外部upload等の実際の外部公開は実施していません。
