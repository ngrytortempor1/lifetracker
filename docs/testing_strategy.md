# テスト戦略ガイド

## 目的

LifeTracker のドメインコアとストレージプラグインを安全に拡張するため、最低限のテスト方針をここにまとめる。開発中に疑問点が出た場合はこのドキュメントを更新しつつ、`docs/開発ガイド.md` と合わせて参照すること。

---

## 1. テストの基本方針

- コアモデル／リポジトリ (`:kernel`)  
  - Kotlin/JVM のユニットテストで純粋関数やエンティティ変換ロジックを検証する。  
  - テスト名は `should...` 形式、期待する振る舞いを一文で表す。
- プラグイン層（JSONL / SQLite）  
  - プラグイン単体でユニットテスト（JSON の encode/decode、DAO の SQL）を追加する。  
  - SQLite については Room の `inMemoryDatabaseBuilder` を活用し、テスト用 DB をメモリに構築する。
- UI 層  
  - Compose の画面は `androidTest` で最低限のスナップショット／状態テストを行う。  
  - ViewModel は `runTest` + `TestDispatcher` でコルーチンを制御する。

---

## 2. プラグイン別のテスト例

### JSONL プラグイン
- `JsonlStorage`：  
  - イベント追記後にファイルへ追記されること。  
  - 壊れた JSON 行があっても読み込みが継続すること（ログ出力の確認）。  
  - ✅ テスト: `JsonlStorageResilienceTest` が劣化行スキップとメタデータ補完を検証。

### SQLite プラグイン
- `LifeTrackerDao`：  
  - `getTaskLists` などの DAO が想定通りに CRUD できるか。  
  - `json_outbox` の取得・更新が正しく動作するか。  
  - ✅ テスト: `LifeTrackerDaoTest` がインメモリ Room で CRUD / outbox / 集計をカバー。
- `SqliteStorage`：  
  - シード処理が JSONL のテストダブルから読み込めるか。  
  - Outbox 側にイベントが積まれると WorkManager が enqueue されるか（`TestListenableWorkerBuilder` で検証）。

### UI 層
- Compose の画面は `androidTest` で最低限のスナップショット／状態テストを行う。  
- ViewModel は `runTest` + `TestDispatcher` でコルーチンを制御する。  
- ✅ ダッシュボードの集計 UI は `DashboardAnalyticsSnapshotTest` でセマンティクススナップショット比較を実施。
- ✅ 分析ダッシュボードは `AnalyticsGraphSemanticsTest` でチャート／テキスト表示のアクセシビリティを検証し、`AnalyticsRepositoryPerformanceTest` で JSONL / SQLite 両実装の集計レイテンシを計測。
- ✅ ビジネスロジックは `AnalyticsGraphViewModelTest` が TTL キャッシュとフィルター切り替えを保証。

---

## 3. 実行コマンドの例

```bash
# kernel のユニットテスト
./gradlew :kernel:test

# app モジュール（ユニット＋Androidテスト）
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

---

## 4. CI / レポート

- 各ジョブの結果は GitHub Actions（もしくは社内 CI）に成果物として保存する。  
- 失敗したテストの `logcat` や HTML レポートは最低 14 日間保持する。

---

## 5. 今後のTODO

- [x] SQLite プラグインのインメモリテスト雛形を `app/src/test/` に追加する。  
- [x] Compose UI のゴールデンテスト導入可否を調査する（`DashboardAnalyticsSnapshotTest` にてセマンティクスベースのスナップショット検証を導入）。  
- [ ] CI 上での `connectedAndroidTest` の安定化（エミュレータ／仮想デバイス管理）。
- ⚠️ 補足: API 34 以降のエミュレータでは、`DashboardAnalyticsSnapshotTest` 実行時に Espresso が `android.hardware.input.InputManager.getInstance()` を呼び出してクラッシュする既知の不具合がある。UI 表示自体には影響しないため、実用上は問題なし。必要に応じて API 33 以下で実行するか、Robolectric Compose テストへ移行する。

ドキュメントに追記した方が良い知見が出た場合は、本ファイルに直接加筆すること。
