# 設計判断

## 確定仕様

- 再現基準は Java Edition 1.16.5。
- 対象は PaperMC 26.1.2 build 69、Java 25。NMS、CraftBukkit内部実装、リフレクションは使わず、公開Paper/Bukkit APIだけを使う。
- 地表は常にY=70。Y=70は`GRASS_BLOCK`、Y=68～69は`DIRT`、Y=5～67は`STONE`、Y=0は`BEDROCK`。
- Y=1～4は旧式バニラ床相当の`BEDROCK`と`STONE`の混在。
- 水、溶岩、洞窟、空洞、ダンジョン、構造物、`DEEPSLATE`と深層岩系鉱石は生成しない。

## Phase 1で確定した実装

- `ChunkGenerator`の分割生成APIを使用し、`generateNoise`でY=0～67、`generateSurface`でY=68～70、`generateBedrock`でY=0～4を確定する。`setRegion`の上端は排他的として指定する。
- 現行通常ワールドの高度範囲は変更せず、Y<0へ書き込まない。Paper 26.1.2実機でY=-1が`AIR`のままになることを確認した。
- 岩盤床はY=0～4の各ブロックで、Paperから渡された`Random`に対して`y <= random.nextInt(5)`を1回だけ評価する。Y=0でも乱数呼び出しを省略しない。
- `getBaseHeight`はsurface適用前のnoise層の最高ブロックY=67を返す。WorldやChunkの参照は行わない。
- 固定スポーンは `(0.5, 71.0, 0.5)` とする。
- `PlainsBiomeProvider`は全座標で`Biome.PLAINS`を返し、使用バイオームは変更不能なPLAINS単独リストとする。
- `shouldGenerateNoise`、`shouldGenerateSurface`、`shouldGenerateCaves`、`shouldGenerateDecorations`、`shouldGenerateStructures`の引数なし版を`false`へ明示的に上書きする。Paper APIの座標別版は引数なし版へ委譲するため二重実装しない。
- `shouldGenerateMobs`は上書きせず、Paper APIの既定動作を維持する。ランタイムMobスポーン制御は追加しない。
- generator idはnull、blank、大小文字を問わない`default`を同じ既定構成として受理し、それ以外は警告してnullを返す。
- 生成クラスは可変共有状態、キャッシュ、共有`Random`、logger、plugin参照を持たず、Paperの並列生成を前提とする。

Paper 26.1.2の`Biome`定数はregistry-backedであり、サーバー外の素のJUnit VMではregistry bootstrapなしに値へアクセスできない。そのためproviderの型・不変性は単体テスト、実際のPLAINS戻り値は必須Paperスモークで検証する。

## 設計候補

- `BlockPopulator`と`LimitedRegion`を使う地中岩石生成はPhase 2でAPI適合性と決定性を検証する。

## 未決事項

- 地中岩石と各鉱石の正確な試行回数、サイズ、高度選択、鉱脈形状。
- Phase 2以降のChunk境界をまたぐ生成に使うseed導出と置換規則。
