# ロギング戦略レビュー

LifeTracker におけるログ出力の現状と改善案をまとめる。Phase 4D のタスクとして、ストレージプラグインを中心にログ設計を再確認した。

---

## 1. 現状のログポイント

| コンポーネント | ログ方法 | レベル | 備考 |
|----------------|---------|--------|------|
| `StoragePluginLogger` 実装 (`AndroidStoragePluginLogger`) | `Log.{i,w,e}` | INFO/WARN/ERROR | プラグイン ID をタグに付与し、JSONL/SQLite 双方のハンドリングで利用。 |
| `JsonlOutboxSyncWorker` | `Log.d/w` | DEBUG/WARN | WorkManager 実行パスで失敗時に WARN。
| ViewModel 全般 | 原則ログなし | - | 状態は `UiState` で露出。 |
| `LifeTrackerApplication` | 起動時の通知登録などで `Log.i` | INFO | 重要イベントのみ。 |

---

## 2. 改善方針

1. **構造化メッセージ**
   - `StoragePluginLogger` の実装を拡張し、`Pair<String, Any?>` を受け取って JSON 形式へ変換。Crashlytics などに転送した際にキー検索しやすくする。
   - 例: `logger.warn(pluginId, "seed_failed", mapOf("file" to events.jsonl, "line" to index))`。

2. **ログレベルの明確化**
   - INFO: ユーザー操作に直結する通常イベント（シード開始/完了）。
   - WARN: 自動復旧が可能なリカバリ系（JSONL 行のパース失敗 → スキップ）。
   - ERROR: データ欠損に直結する失敗（DB マイグレーションの失敗、outbox 永続化不可）。
   - DEBUG: 開発ビルドのみ出力（WorkManager の再試行理由、計測値など）。

3. **集約ポイントの追加**
   - ログを Crashlytics へ送る `StoragePluginLogger` 実装を別途用意 (`CrashlyticsStoragePluginLogger`) し、リリースビルドで差し替え。`AppContainer` で build variant に応じて注入。
   - WorkManager の失敗数を `AnalyticsEvent` として `EventAnalyticsRepository` へ送信し、ダッシュボードや将来の監視画面に使えるようにする（長期検討）。

4. **ログ抑制ガイドライン**
   - ユーザーのプライバシー保護のため、具体的なタグ・ノートなどの PII はログへ書き込まない。
   - WARN/ERROR を出す際はイベント ID、ファイル名など復旧に必要な識別子に限定する。

---

## 3. 今後の実装タスク候補

- [ ] `StoragePluginLogger` のインターフェイスを拡張し、オプションで構造化コンテキストを渡せるようにする。
- [ ] Crashlytics 連携の `StoragePluginLogger` 実装を作成し、ビルドタイプで切り替える。
- [ ] WorkManager エラー件数を収集し、将来的なヘルスチェック画面に表示する。
- [ ] ログポリシーを `docs/開発ガイド.md` に取り込み、開発者が迷わないようにする。

---

## 4. 参考

- Google: [Best practices for logging](https://developer.android.com/topic/performance/vitals/anr)（ANR 計測向けだがログ設計の指針が参考になる）
- Firebase Crashlytics: カスタムキー／ログ API を利用した構造化ロギング。
