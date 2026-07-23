# スターターハウス生成のワールド単位トグル 設計

- 日付: 2026-07-21
- 対象: スターターハウス生成のみ(Village Mode は既にコマンドでワールド単位オプトイン済みのため対象外)

## 背景 / 目的

現状 `StarterHouseGenerator.tryGenerate()` は `ServerStartedEvent` で全ワールド・毎回呼ばれ、
`StarterHouseData.isGenerated()` により各ワールドで1回だけ家を生成する。結果として
**新規作成した全ワールドで自動的にスターターハウスが生成される**。

ユーザーから「特定のワールドだけで使いたい(全新規ワールドで生成させたくない)」という
要望があった(例: Overworld 全体が Pale Garden のワールドでは羊毛入手のため家が欲しいが、
他の新規ワールドでは不要)。要望者自身が代替案として「ワールド作成前にゲームルールで
生成可否を切り替えられるようにする」を挙げていた。

### 決定事項(ブレインストーミングで確定)

1. 対象は **スターターハウスのみ**。
2. デフォルト挙動は **従来通り自動生成(オプトアウト)** とし、既存ユーザーの体験を変えない。
3. 方式は **カスタムゲームルール(ワールド作成画面で切替)+ そのデフォルト値を設定ファイルで変更可能**。

## 全体モデル(セマンティクス)

- **真の切替スイッチ = カスタム boolean ゲームルール `beginnersDelightGenerateStarterHouse`(ワールド単位)**
  - ワールド作成画面の「ゲームルール」で ON/OFF 可能。
  - ゲーム内でも `/gamerule beginnersDelightGenerateStarterHouse <true|false>` で変更可能。
  - ゲームルールはワールドごとに永続化されるため、要望の「特定ワールドだけ」を満たす。
- **設定ファイルは「ゲームルールの登録時デフォルト値」を決めるだけ**
  - `beginnersdelight.toml` に `[starter_house] auto_generate = true` を追加。
  - Mod 初期化時に一度読み、ゲームルールの登録デフォルト値として使用する。
  - → 新規ワールドは作成画面で最初からこの値になり、その場で個別に上書き可能。

MC の仕様上、ゲームルールの既定値は**登録時(Mod 初期化時)に確定**するため、設定値は
「登録デフォルト」に反映する形が唯一クリーンな方法である。デフォルトを `true` に保つことで、
既存ユーザー・設定を触らないユーザーは従来通り全新規ワールドで生成される。

### なぜコマンド/設定単独では不十分か

デフォルトが「自動生成 ON」の場合、家は**新規ワールドの初回サーバー起動時**に生成される。
コマンドや SavedData トグルはワールドがロードされた後にしか実行できず、初回生成を事前に
止められない。したがって「特定ワールドだけ生成させない」を実現するには、**ワールド生成
時点で決まる仕組み = ゲームルール(作成画面で設定)** が必要になる。

## 命名

- ゲームルール名: `beginnersDelightGenerateStarterHouse`
  - vanilla ゲームルールは単一のグローバル名前空間・camelCase(`doDaylightCycle` 等)で、
    レジストリのような mod ID 名前空間分離がない。衝突回避のため mod 名相当のプレフィックス
    + 説明的な名前を camelCase にするのがコミュニティ慣例であり、それに従う。
- 設定: `[starter_house]` セクション / `auto_generate`(boolean)

## 変更コンポーネント

| 箇所 | 変更 |
|---|---|
| common(各 version)に共通ホルダ `ModGameRules` 相当 | ゲームルール Key を保持する共通 static フィールド `Key<BooleanValue> GENERATE_STARTER_HOUSE`。tryGenerate から参照する |
| fabric/base, neoforge/base, forge/base | Mod 初期化で config デフォルトを読み、ローダー別 API でゲームルールを登録し、返却された Key を共通ホルダへ格納する<br>・Fabric: `GameRuleRegistry.register(name, Category, GameRuleFactory.createBooleanRule(default))`(fabric-game-rule-api-v1)<br>・Forge/NeoForge: `GameRules.register(name, Category, BooleanValue.create(default))` |
| `StarterHouseGenerator.tryGenerate` | **未生成パスの先頭にのみ**ゲートを追加(下記) |
| `VillageConfig` / `VillageConfigLoader` / `VillageConfigDefaults` / bundled `beginnersdelight-default-config.toml` | `starter_house.auto_generate` キー追加。`schema_version` を 2 へ。既存の per-field フォールバック方針を踏襲(無効値/欠損時は既定 `true`) |

### `tryGenerate` の変更(最小差分)

```java
if (data.isGenerated()) {
    // ...既存の spawn 復元処理...
    return;                       // 既存のまま
}
if (!overworld.getGameRules().getBoolean(ModGameRules.GENERATE_STARTER_HOUSE)) {
    return;                       // NEW: 未生成 && ルール false なら生成しない
}
// ...以降、既存の生成処理...
```

- 既に家がある既存ワールドは `isGenerated=true` のため従来通り(spawn 復元含む)動作し、
  一切影響を受けない。ゲートは「まだ生成していないワールド」の初回生成のみを対象とする。

## データフロー

1. **Mod 初期化**: 設定ファイル読込(なければ bundled デフォルトを生成)→ `auto_generate` の値を
   ゲームルールの登録デフォルトとして `register`。Key を共通ホルダへ格納。
2. **ワールド作成画面**: ゲームルールが登録デフォルト値で表示され、ユーザーが任意で切替。
3. **初回サーバー起動(`ServerStartedEvent`)**: `tryGenerate` がそのワールドのゲームルール値を
   参照し、生成/スキップを決定。

## 挙動の細部 / エッジケース

- **既存ワールド(本機能追加前に生成済み)**: 家は既に生成済みのためスキップ、影響なし。
- **作成時に false**: 初回起動で生成されない。
- **後から `/gamerule ... true` にした場合**: **次回サーバー起動時**に生成される
  (mid-session の即時生成フックは付けずスコープを絞る)。この挙動はドキュメント/CHANGELOG に明記する。
- **設定ファイルで `auto_generate = false`**: 以降に作成する新規ワールドのゲームルール
  デフォルトが false になる(要望者はこの状態で、家が欲しいワールドだけ作成画面で true にできる)。
- 設定変更は再起動後に作成するワールドから反映(ゲームルールのデフォルトは登録時=Mod 初期化時
  に確定するため)。`/beginnersdelight config reload` は既存ワールドのゲームルール値は変えない。

## クロスバージョン / ローダーのリスク

- **【最重要・要検証】** カスタム boolean ゲームルールが、Fabric / Forge / NeoForge それぞれ・
  全バージョンの**ワールド作成画面**に表示・編集可能であること。
  まず default 版 26.2 の Fabric / NeoForge で spike し、表示可否を確認してから全バージョンへ横展開する。
- 1.21.11 で `GameRules` のパッケージ移動(`net.minecraft.world.level.gamerules.GameRules`)・
  API 変更(CLAUDE.md 記載)→ version 別のインポート/シムが必要。
- 1.16.5 等の古い API 差異、Fabric API の game-rule モジュール(fabric-game-rule-api-v1)の
  各バージョンでの可用性。
- `common/shared` はビルド未接続で各 version に実コピーが存在するため、変更は
  **全 version の common モジュール + 各ローダーの base** に横展開する必要がある。

## 実装方針(段階的)

1. 26.2 の Fabric / NeoForge で共通ホルダ + 登録 + ゲート + 設定キーを実装し、
   作成画面表示・生成/スキップ・`/gamerule` 反映を実機確認(spike 兼本実装)。
2. 確認が取れたら残り全バージョンへ横展開(API 差異はバージョンごとに吸収)。
3. CHANGELOG / README / ストア説明(curseforge, modrinth)に新オプションを反映。

## テスト / 検証

単体テストでは確認しづらいため実機確認を中心とする:

- (a) 作成画面でゲームルール ON → 初回起動で家が生成される。
- (b) 作成画面で OFF → 家が生成されない。
- (c) `/gamerule beginnersDelightGenerateStarterHouse <値>` の設定/取得が機能する。
- (d) 設定ファイルで `auto_generate = false` → 新規作成時のゲームルールデフォルトが OFF。
- (e) 既存(生成済み)ワールドで挙動が変わらない(spawn 復元含む)。
- ビルド: 対象版で `./gradlew build`(横展開後は必要に応じて対象版を `buildAll`)。

## スコープ外

- Village Mode のトグル方式変更(既にコマンドでワールド単位オプトイン済み)。
- mid-session でのゲームルール true 化に対する即時生成。
- 既存の config 読込パス(`getServerDirectory()/config`)の見直し。新規のデフォルト読込は
  ローダー提供の config ディレクトリを用いる前提で、既存挙動と一致するか実装時に確認する。
