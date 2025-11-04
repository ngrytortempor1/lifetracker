# SQLite / JSONL デュアルラン運用ガイド

本ドキュメントは、Room（SQLite）ストレージと既存の JSONL ストレージを併用する際の運用ルールと移行手順をまとめたものです。`SqliteStoragePlugin` が導入されたことにより、アプリは内部的に SQLite をソース・オブ・トゥルースとしつつ、JSONL を継続的に更新します。

---

## 1. 起動時の初期化フロー

1. `SqliteStorage` が初回アクセス時に JSONL からデータをシードします。
  - 対象: TaskList、Task、Habit、QuickLogTag、Event、MoodEntry、SleepSession。
  - `Event` はメタデータ未設定の場合でも `ensureMetadata()` でタグ／詳細を補完してから書き込む。
   - JSONL が空の場合は何もコピーされません。
2. シード完了後は SQLite が一次データソースとなり、JSONL は追従更新されます。
3. `StoragePluginLogger` により、シードや同期失敗時のログが `StoragePlugin` タグで出力されます。

---

## 2. アウトボックスと WorkManager

| テーブル | 役割 |
|----------|------|
| `json_outbox` | JSONL へ反映すべきイベントを一時保管。 |

- `appendEvent` 呼び出し時に SQLite に保存 → `json_outbox` に格納 → WorkManager (`JsonlOutboxSyncWorker`) が JSONL へ追記。
- WorkRequest は `ExistingWorkPolicy.APPEND_OR_REPLACE` で enque され、連続実行にも耐える構成。
- 同期に失敗した場合は `Result.retry()` となり、再試行は WorkManager のバックオフポリシーに従います。

---

## 3. 既存プロファイルの移行手順

1. 新バージョンのアプリをインストール（ユーザーデータは保持したまま）。
2. 初回起動時に JSONL から SQLite へ自動でシードされる。
3. 以降、SQLite → JSONL 同期が継続。JSONL をバックアップとして活用可能。
4. JSONL をサードパーティと共有するワークフローが残っている場合は、従来通り `getExportFiles()` を利用する。

開発者が手動でシードを再実行したい場合は、アプリのデータを消去してから再度起動する（ただしユーザーデータも消える点に注意）。

---

## 4. バックアップ／復元ポリシー

| 項目 | 推奨 |
|------|------|
| DB バックアップ | `life_tracker.db` を ZIP 化して保管。`LifeTrackerDatabase.databaseFile()` でパス取得可能。 |
| JSONL バックアップ | 従来通り `events.jsonl` などをコピー。SQLite 不具合時の復旧用。 |
| 復元手順 | DB と JSONL を両方復元 → 最初の起動で JSONL が上書きされないよう注意。必要なら JSONL をリネームしてから起動し、同期完了後に戻す。 |

---

## 5. 監視とデバッグ

- ログ出力：
  - `StoragePlugin` タグの INFO/WARN/ERROR を確認。
  - `JsonlOutboxSyncWorker` のリトライ数が多い場合はストレージ権限やファイル破損を疑う。
- DB 点検：
  ```shell
  adb shell run-as com.lifetracker.app \
    cp /data/data/com.lifetracker.app/databases/life_tracker.db /sdcard/life_tracker.db
  adb pull /sdcard/life_tracker.db .
  ```
  `sqlitebrowser` 等でテーブルを確認可能。
- JSONL 点検：`adb pull /sdcard/Android/data/com.lifetracker.app/files/events.jsonl .`

---

## 6. 今後の拡張ポイント

- Outbox バッチサイズや WorkManager のバックオフ設定をプロダクト要件に合わせて調整。
- JSONL の書き込み先をユーザー選択ディレクトリへ固定する場合は、`StorageLocationManager` で SQLite バックアップの取り扱いを別途検討。
- 将来的に JSONL を廃止する場合は、`json_outbox` と `JsonlOutboxSyncWorker` を無効化し、エクスポート手段を Room ベースへ移行する（このドキュメントを更新すること）。

---

## 7. JSONL → SQLite 移行プラン（破壊的マイグレーション廃止時）

### 7.1 ゴール

- 既存ユーザーの JSONL データを欠損なく Room DB へ移行し、以降は破壊的マイグレーション無しでバージョンアップを行う。
- トラブル発生時に JSONL バックアップへロールバックできる運用手順を保持する。

### 7.2 ステージング

| ステージ | 目的 | 実施内容 |
|----------|------|----------|
| Stage 0 | 現状把握 | v1.0 時点の JSONL/SQLite デュアルランで、イベント・タスク・ウェルネスのシード結果を収集。ログにユーザー環境差異が無いか確認。 |
| Stage 1 | 移行準備リリース | アプリ起動時に「JSONL → SQLite の整合性チェック」を実施。差分ありの場合は `StoragePluginLogger.warn` で通知し、`life_tracker_migration_report.json` を生成（SD カードに出力）してユーザーサポートが参照できるようにする。|
| Stage 2 | 本移行 | `fallbackToDestructiveMigration()` を撤廃し、`Migration(4, 5)` を追加。起動時に JSONL が存在すればマイグレーション後も引き続き JSONL へ出力。DB バージョンアップに失敗した場合は JSONL から再シードを試み、失敗時にはユーザーへ通知。|
| Stage 3 | 移行完了後の最適化 | 連続起動で JSONL を参照しない場合は outbox をスキップするオプションを追加検討。JSONL を完全アーカイブ用途とし、将来的な廃止に備えてメトリクスを収集。|

### 7.3 フォールバック手順

1. `life_tracker.db` のバックアップを取得しておく（起動直後に WorkManager が走る前が望ましい）。
2. JSONL を最新状態に同期後、起動時のマイグレーションが失敗した場合は、アプリを終了 → `life_tracker.db` を削除 → 再起動して JSONL から再シード。
3. それでも失敗する場合は、`life_tracker_migration_report.json` を開発者へ送付し、JSONL コンテンツを直接解析する。

### 7.4 リリースチェックリスト

- [ ] `Migration(4, 5)` のユニットテスト／端末実機テストを作成する。
- [ ] JSONL シード実行中の UI ブロッキングが発生しないことを検証する。
- [ ] `StoragePluginLogger` の WARN/ERROR を Crashlytics などへ転送するフックを実装し、移行エラーが即座に検知できるようにする。
- [ ] Release ノートに「初回起動時にデータ移行処理が走る」旨を記載し、バックアップ取得を推奨する。

### 7.5 今後の検討事項

- JSONL をユーザーデータ保持目的に限定し、エクスポートは Room ベースの `ExportRepository` に統一する案を検討。
- WorkManager 経由の JSONL 同期が不要になるタイミングで outbox 系処理を削除できるよう、使用状況のメトリクス取得を開始する。

---

このガイドは運用中に得られた知見を随時更新してください。特にバックアップ戦略や同期失敗時の復旧手順は変更があれば即座に反映することを推奨します。
