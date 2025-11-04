# Pomodoro Linking Plugin – Discussion Draft

## 1. 背景と狙い
- LifeTracker を「軽量なハブ」として拡張する文脈で、ポモドーロタイマーをタスク/習慣と結びつけたい。
- 将来のプラグイン構想を見据え、タイマーをコアとは疎結合に保ち、イベント経由で他機能と連携させる。

## 2. ユースケース
1. ユーザーが「ポモドーロ開始」→対象タスクを選択→25分集中→5分休憩→完了ログがタスクに記録される。
2. 習慣にも紐付けられ、例: “英語学習”習慣に対してポモドーロ回数を自動カウント。
3. タイマーはホームやクイック通知から即起動でき、バックグラウンドでも継続。

## 3. アーキテクチャ案（プラグイン視点）
```
TimerPlugin (独立モジュール想定)
 ├─ PomodoroTimerController
 ├─ TimerState (Idle / Focus / Break / Paused)
 ├─ SessionRepository (JSONL: pomodoro_sessions.json)
 └─ TimerNotificationManager

core/event
 ├─ EventType.POMODORO_COMPLETED
 └─ EventPayload.PomodoroCompleted(targetType, targetId, focusDuration, breakDuration, interrupted, notes)

既存タスク/Habit UI
 ├─ 「タイマー開始」ボタン (intent to TimerPlugin)
 ├─ 直近のポモ回数表示 (Events フィルタリング)
```

## 4. 主要仕様 (ドラフト)
- **タイマー設定**: 25/5 / カスタム (開始時に選択、カスタムは記憶。プリセット群の最右に「カスタム」ボタンを配置し、押下時に任意分数を入力できるダイアログを開く)
- **状態遷移**: Idle → Focus → Break → 次Focus ... / Cancel → Idle
- **イベント記録**:
  - `POMODORO_COMPLETED`：`targetType` (`task`|`habit`|`none`)、`targetId`、`startedAt`、`endedAt`、`focusDuration`、`breakDuration`、`interrupted`
  - キャンセルの場合はイベント記録無し（要議論）
- **データ保存**: 即座にイベント追記＋セッション履歴 (JSONL or events.jsonl)
- **UI**:
  - タイマー画面: 現在の状態、残り時間、開始/停止ボタン、ターゲット変更、集中/休憩チップ（横スクロール対応）
  - クイックアクセス: ホームカード・常駐通知・Quick Settings Tile
- **集計表示** (Phase 2以降):
  - タスク詳細: 当日/週の実施回数、累積時間
  - ダッシュボード: 今日のポモドーロ実行数、連続実行日数

## 5. 未決定事項
- セッション中にアプリが終了した場合の復帰方法（Foreground Serviceを使うか？）
- 休憩スキップ・延長機能の有無
- イベント保存を単一ファイル（events.jsonl）に統合するか、専用ファイルにするか
- デフォルトのポモ/ブレイク長さの扱い、ユーザー設定の保存先

## 6. 次のステップ
1. タイマー状態遷移図とUXフローの詳細化
2. EventPayload の確定と JSON スキーマ例の作成
3. PoC：単機能 Compose UI + カウントダウンロジックを試作し、イベント保存まで繋げる
4. タスク/Habit UI側での最小連携（ボタンと実績表示）の検討

---

*このドキュメントで設計・議論を深めた後、必要に応じて仕様書や実装タスクに展開する。*

---

## 実装メモ（2025-02-06）
- `PomodoroCompleted` イベントを EventType/ペイロードに追加し、JSONL に追記できる状態を作成。
- `PomodoroTimerViewModel` / `PomodoroTimerScreen` を追加し、Focus → Break の基本サイクルとイベント記録を実装。
- タスク/習慣カードにポモドーロ開始ボタンを追加し、メイン画面の新規「タイマー」タブへ遷移して対象を選択済み状態で表示。
- 今後の課題: セッション履歴の読み出しとダッシュボード連携、バックグラウンド継続（Foreground Service）検討、休憩スキップ後のイベント整備など。
