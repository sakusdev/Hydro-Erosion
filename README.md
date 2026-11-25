# Hydro-Erosion (`hydroerosion`)

Minecraft **1.20.1 / Forge (`47.3.0`)** 向けの土木・水力工業Modです。  
有限水・雨による底面からの貯水・水頭圧に基づく構造物のひび割れ＆ダム決壊メカニクスに加えて、`Create` および `Mekanism` との安全なソフト依存連携（単体起動対応）を備えています。

---

## 1. 実装済みシステム一覧

### コア水理学システム
1. **無限水源化の制限 (`FluidPhysicsEventHandler`)**
   - `BlockEvent.CreateFluidSourceEvent` により、バニラの水の `2x2` 無限水源化を無効化します（設定 `disableInfiniteWaterSource` でオン／オフ切替可能）。
2. **セルラーオートマトン型 有限水物理 (`FiniteWaterPhysicsManager` / `FlowingFluidMixin`)**
   - バニラの水ティックをフックし、水ブロックの体積（`1〜8` クォンタ、満水 `8/8` がバニラ水源 `Blocks.WATER`）を保存しながら移動・平準化します。
   - **処理優先順位:** ①垂直落下（重力） → ②斜め下への流下 → ③水平方向の平準化・連通管均等化 → ④孤立した最小水量（`1/8`）の蒸発。
   - **TPS最適化**: 変化地点と隣接座標のみを重複排除付きアクティブキュー（`LongLinkedOpenHashSet`）へ登録し、1Tickあたりの最大処理数（既定 `600` ブロック/tick）で上限制御します。水面が平らになると自動でキューから除外され、静水時のCPU負荷はゼロになります。
2. **底面積み上げ式の雨貯水システム (`RainAccumulationManager`)**
   - 降雨時（`Level.isRaining()`）、ロード済みチャンクをサンプリングし、空が見えていて周囲が固体ブロックで囲まれた低地（盆地・ダム湖）に雨水（`Blocks.WATER`）を蓄積します。
   - 空中に水源が生成されて滝だらけになるのを防ぐため、**必ず底面（固体ブロックまたは既存の水源ブロックの直上）かつ水平スライス全体が底面支持されている位置から下層優先で積み上げる**設計になっています。
3. **パスカルの原理に基づく水頭圧計算・決壊イベント (`WaterPressureCalculator` / `BreachManager` / `ErosionSavedData`)**
   - 連続した水塊の最大水面高を探索し、水頭圧 $P = 9.81 \times h\text{ (kPa)}$ を算出します。
   - ブロックごとの耐圧限界を超過すると、ひび割れオーバーレイ（ステージ `0〜9`）と漏水パーティクルが発生し、疲労が `100%` に達するとブロックが破壊されて水が噴出する**ダム決壊イベント**が発生します。
   - **主な耐圧限界目安:**
     - 土・砂・砂利・泥など: `29.4 kPa`（水深 約3m）
     - 丸石・木材・砂岩・ガラスなど: `68.7 kPa`（水深 約7m）
     - 石・石レンガ・通常コンクリート: `157.0 kPa`（水深 約16m）
     - **鉄筋ダムコンクリート (`hydroerosion:reinforced_dam_concrete`)**: `2500.0 kPa`（水深 約255m対応）

---

## 2. 追加アイテム・ブロック一覧

| 種類 | レジストリID | 名称 | 機能・用途 |
| :--- | :--- | :--- | :--- |
| アイテム | `hydroerosion:pressure_gauge` | 水圧計 | 右クリックしたブロック／水域の水深（m）・水頭圧（kPa）・耐圧限界・ひび割れ率をチャット表示 |
| アイテム | `hydroerosion:high_pressure_upgrade` | 高水圧アップグレード | `深層高圧取水口` または Mekanism 機械へ装着し、水深40m以上での出力を2倍化 |
| ブロック | `hydroerosion:reinforced_dam_concrete` | 鉄筋ダムコンクリート | 耐圧 `2500 kPa`・高耐爆のダム建設用メイン建材 |
| ブロック | `hydroerosion:water_pressure_sensor` | 水位・水圧センサー | 隣接する水頭圧に応じて `0〜15` のレッドストーン信号を出力（Create のピストン・滑車による水門自動開閉に最適） |
| ブロック | `hydroerosion:deep_pressure_intake` | 深層高圧取水口 | 隣接配管へ冷却水を供給。**水深40ブロック以上**で供給量を大幅ブーストし、Mekanism連携時は重水を生成 |
| ブロック *(Create導入時)* | `hydroerosion:high_pressure_turbine` | 高水圧タービン | Create の `DirectionalKineticBlock` / `GeneratingKineticBlockEntity` を継承し、水深に応じた高RPM・高SUを出力 |

---

## 3. 外部Mod連携仕様（ソフト依存）

`META-INF/mods.toml` 上では `create` および `mekanism` ともに `mandatory = false` として定義しており、コアパッケージから外部Modのクラスを直接 `import` しない分離アーキテクチャを採用しています。単体環境でも `NoClassDefFoundError` なく起動します。

- **Create 連携 (`com.hydroerosion.compat.create`)**
  - Modコンストラクタ内で `ModList.get().isLoaded("create")` を判定し、Forge登録タイミングを逃さず `high_pressure_turbine` を登録。
  - 水頭圧に応じて回転速度（`16〜128 RPM`）および応力容量（`32 + 12 * 水深` SU/RPM）を動的に出力。
  - クライアント側では `CreateClientCompat` (`PartialModel`) と `HighPressureTurbineRenderer` (`KineticBlockEntityRenderer`) により、フレーム（`high_pressure_turbine_base.json`）内部で8枚羽根車（`high_pressure_turbine_rotor.json`）が発生RPMに応じて回転描画されます。
  - エンジニアゴーグル着用時には水頭圧（m / kPa）をツールチップ表示。
  - `water_pressure_sensor` と Create のレッドストーンリンク／メカニカルピストン／ローププーリーを組み合わせることで、水位上昇時の自動放水ゲートを構築できます。

- **Mekanism 連携 (`com.hydroerosion.compat.mekanism`)**
  - **水深40ブロック以上**の高水圧環境下で `deep_pressure_intake` または Mekanism の `electric_pump` を稼働させた場合、隣接する流体ハンドラ（メカニカルパイプ、動的タンク、核分裂炉、熱電ボイラー等）へ高圧冷却水および **重水 (`mekanism:heavy_water`)** をボーナス供給します。
  - `electric_pump` に Mekanism のフィルターアップグレード（`Upgrade.FILTER`）が入っている場合は重水ボーナスが、通常時は高圧冷却水ボーナスが発動し、`high_pressure_upgrade` の併用でさらに出力が倍増します。

---

## 4. 設定ファイル (`config/hydroerosion-common.toml`)

- `finite_water.disableInfiniteWaterSource` (既定: `true`): `2x2` 無限水源化の無効化
- `finite_water.enableFiniteWaterPhysics` (既定: `true`): 体積保存型セルラーオートマトン有限水シミュレーションの有効化
- `finite_water.finiteWaterMaxUpdatesPerTick` (既定: `600`): 1Tickあたりの最大水セル処理数（TPS保護用）
- `finite_water.finiteWaterEvaporateChance` (既定: `0.15`): 孤立した最小水量（`1/8`）水膜の蒸発確率
- `rain_accumulation.enableRainAccumulation` (既定: `true`): 底面積み上げ式の雨貯水
- `rain_accumulation.rainSamplesPerChunk` (既定: `3`): チャンクあたりサンプリング数
- `rain_accumulation.rainAccumulationChance` (既定: `0.15`): 1回のサンプリングあたりの貯水確率
- `hydrostatic_breach.enablePressureBreach` (既定: `true`): 水圧超過によるひび割れ＆決壊
- `hydrostatic_breach.erosionSpeedMultiplier` (既定: `1.0`): 決壊進行速度倍率
- `automation.sensorMaxDepthBlocks` (既定: `30`): 水圧センサーが信号強さ `15` を出力する水深

---

## 5. 3Dモデル・Blockbench編集ファイル & ビルド方法

- `scripts/generate_3d_models.py` により、Create風のマルチエレメント立体モデル、`32x32` 陰影付きプロシージャルPNGテクスチャ、および Base64 テクスチャ埋め込み済みの Blockbench プロジェクト（`blockbench_models/*.bbmodel`）を生成しています。
- ビルド時、`build.gradle` の `generateModAssets` タスクは `src/main/resources` に配置済みの立体モデル・テクスチャを上書きしないよう保護されています。

Java 17 環境を用意し、プロジェクトルートで以下を実行します。

```powershell
.\gradlew.bat build --no-daemon
```

ビルド成功後、`build/libs/hydroerosion-1.20.1-1.0.0.jar` が生成されます。

---

## 6. ゲーム内での動作確認手順

1. 生成された `build/libs/hydroerosion-1.20.1-1.0.0.jar` を Minecraft 1.20.1 Forge の `mods` フォルダに導入してワールドへ入ります。
2. **`/hydroerosion testdam`** を実行します。
   - プレイヤー前方に **水深10m（底部 98.1 kPa）** の `鉄筋ダムコンクリート` 製テストダム、右壁の `水位・水圧センサー`（＋レッドストーンランプ点灯）、左壁の `深層高圧取水口`、前面の **土＋丸石のテスト栓** と放水受け皿が自動生成されます。
3. **`水圧計 (Pressure Gauge)`** を手に持ち、各所のブロックや水面を右クリックして水深・水頭圧・耐圧限界・ひび割れ率を確認します。
4. **`/hydroerosion inspect`** を実行し、足元および視線先ブロックの計算済み水頭圧・降雨条件・40m深層ボーナス状態を確認します。
5. 数秒待つと、耐圧限界を超えた土・丸石のテスト栓にひび割れエフェクトと漏水パーティクルが進行し、最終的に**決壊して水が受け皿へ流れ出す**様子を確認できます。
6. 有限水・雨貯水を確認する場合は、平地に `2x2` の窪みを作ってバケツ2杯で無限水源化しないことを確認し、囲われた窪みの下で `/weather rain` を実行すると底面から順に水位が溜まる様子を確認できます。

---

## 7. ライセンス (License)

本プロジェクトはソースコードとアートアセット（3Dモデル・テクスチャ）で分離ライセンスを採用しています。

- **ソースコード・ビルドスクリプト・データ定義**: [MIT License](LICENSE)
- **3Dモデル・Blockbenchファイル (`blockbench_models/*.bbmodel`)・テクスチャ**: [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)](LICENSE-ASSETS)（クレジット表記 `sakusdev`・非営利・継承）

