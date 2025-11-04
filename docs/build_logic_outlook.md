# Build Logic / Convention Plugin 検討メモ

LifeTracker プロジェクトでプラグイン構成が増えていくことを想定し、Gradle の共通設定をどのレイヤーで管理するかを整理したメモ。

---

## 1. 現状整理

- `app` モジュールと `kernel` モジュールの 2 つ構成。
- プラグイン実装は `app` 内にサブパッケージとして存在し、追加の Gradle モジュールは未作成。
- 共通バージョン管理は `gradle/libs.versions.toml` で実施。
- Kotlin/Compose/Room などの共通プラグイン設定が `app/build.gradle.kts` に集中している。

---

## 2. Convention Plugin 導入案

| 案 | 概要 | メリット | デメリット / 懸念 |
|----|------|-----------|--------------------|
| A. `build-logic` モジュールを作成 | `build-logic` 直下に `convention` プラグインを定義。`com.lifetracker.android-app` などカスタム ID を作る。 | - Jetpack Compose/Room 設定を共通化<br>- 今後プラグインを別モジュールへ切り出す際に設定の再利用が容易 | - 初期セットアップの学習コスト<br>- Gradle Sync がやや遅くなる可能性 |
| B. `settings.gradle.kts` のバージョンカタログにプリセット | `plugins` ブロックで `alias(libs.plugins.android.application)` のように lib catalog を利用し続ける。 | - 既存と同等のシンプルさを維持<br>- Convention plugin を覚える必要なし | - モジュールが増えると `build.gradle` の記述重複が増える |
| C. Kotlin DSL の `apply(from = ...)` で共通スクリプト | `gradle/common-android-config.gradle.kts` を作成し `apply` する。 | - 実装が容易 | - IDE の補完が弱く、保守が難しい |

現状のモジュール数を鑑みると、短期的には案 B で十分。ただし Phase 5 以降にストレージプラグインや Worker を別モジュールとして切り出す計画があるため、案 A のプロトタイピングを早めに行う価値がある。

---

## 3. 推奨アクション

1. Phase 5 のモジュール増加が確定するタイミングで `build-logic` ディレクトリを作成し、`com.android.library + compose` の Convention プラグインを用意する。
2. 現状はバージョンカタログによる共通管理を継続し、二重管理を避ける。
3. Convention プラグイン導入時は以下をガイド化する：
   - プラグイン ID 命名規則 (`com.lifetracker.android-app` など)
   - Compose 有効化、Java/Kotlin バージョン、Lint 設定の共通化
   - テスト依存関係（Room testing、Compose testing など）のデフォルト登録

---

## 4. 今後のチェックポイント

- [ ] Kotlin Multiplatform など他プラットフォーム追加予定が出た際、Convention プラグインを使った構成を早期に試す。
- [ ] 既存モジュールでビルド時間が増加した場合、Gradle 分割（`configuration cache` 対応）を並行検討。
- [ ] Convention プラグイン導入後は `docs/開発ガイド.md` に設定手順を追記する。
