# 設計判断

## 確定仕様

- 再現基準は Java Edition 1.16.5。
- 対象は PaperMC 26.1.2 build 69、Java 25。NMS、CraftBukkit内部実装、リフレクションは使わず、公開Paper/Bukkit APIだけを使う。
- 地表は常にY=70。Y=70は`GRASS_BLOCK`、Y=68～69は`DIRT`、Y=5～67は`STONE`、Y=0は`BEDROCK`。
- Y=1～4は旧式バニラ床相当の`BEDROCK`と`STONE`の混在。
- 水、溶岩、洞窟、空洞、ダンジョン、構造物、`DEEPSLATE`と深層岩系鉱石は生成しない。

## 設計候補

- 現行通常ディメンションの`minY=-64`は変更せず、Y=-64～-1を`AIR`のまま残し、Y=0を旧式ワールドの底面として扱う。Phase 1の公開API実装と実機検証で確定させる。
- 固定PLAINS系`BiomeProvider`、`BlockPopulator`と`LimitedRegion`を使う構成は後続PhaseでAPI適合性と決定性を検証する。

## 未決事項

- Y=1～4の岩盤混在確率と乱数列をJava 1.16.5へどこまで一致させるか。
- 地中岩石と各鉱石の正確な試行回数、サイズ、高度選択、鉱脈形状。
- 固定スポーン座標の最終値。
