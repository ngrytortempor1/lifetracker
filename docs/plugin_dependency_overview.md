# Storage Plugin Dependency Overview

LifeTracker splits domain logic into the shared `:kernel` module while persistence is provided by pluggable storage implementations. This document lists the key contracts and where they live so new plugins can reuse existing utilities instead of re‑inventing them.

---

## 1. Toolchain & Build

- **Gradle:** 8.7.3 (`gradle/wrapper/gradle-wrapper.properties`)
- **Kotlin:** 2.0.21 (`gradle/libs.versions.toml`)
- **Java:** 17 (`jvmToolchain`, `compileOptions`, `kotlinOptions` are all set to 17)
- **Plugins in app module:** `com.android.application`, `org.jetbrains.kotlin.android`, Compose, Serialization, and `org.jetbrains.kotlin.kapt`

When creating a new plugin module, stick to Java 17 / Kotlin 2.0.21 so the code remains cross compatible.

---

## 2. Core Contracts

| Role | Interface / Utility | Location |
|------|---------------------|----------|
| Storage core | `LifeTrackerStorage` | `app/src/main/java/com/lifetracker/app/core/storage/LifeTrackerStorage.kt` |
| Storage location | `StorageLocationManager` / `JsonlStorageLocation` | `app/src/main/java/com/lifetracker/app/core/storage/StorageLocationManager.kt` |
| Plugin registry | `StoragePlugin` / `StoragePluginRegistry` | `app/src/main/java/com/lifetracker/app/core/storage/` |
| Plugin logging | `StoragePluginLogger` / `AndroidStoragePluginLogger` | `app/src/main/java/com/lifetracker/app/core/storage/` |
| Analytics API | `EventAnalyticsRepository` | `kernel/src/main/kotlin/com/lifetracker/core/analytics/` |
| Event metadata | `EventMetadata`, `Event.ensureMetadata()` | `kernel/src/main/kotlin/com/lifetracker/core/model/` |
| Analytics implementations | `JsonEventAnalyticsRepository`, `SqliteEventAnalyticsRepository` | `app/src/main/java/com/lifetracker/app/data/analytics/`, `app/src/main/java/com/lifetracker/app/plugins/storage/sqlite/analytics/` |
| JSONL plugin | `JsonStoragePlugin` / `JsonlStorage` | `app/src/main/java/com/lifetracker/app/plugins/storage/json/` |
| SQLite plugin | `SqliteStoragePlugin` / `SqliteStorage` | `app/src/main/java/com/lifetracker/app/plugins/storage/sqlite/` |
| Repositories | `TaskRepository`, `EventRepository` | `kernel/src/main/kotlin/com/lifetracker/core/repository/` |
| Wellness repository | `WellnessRepository` / `JsonWellnessRepository` | `kernel/src/main/kotlin/com/lifetracker/core/repository/` / `app/src/main/java/com/lifetracker/app/data/local/` |
| Domain models | `Task`, `TaskList`, `Event`, `Habit`, `MoodEntry`, `SleepSession`, … | `kernel/src/main/kotlin/com/lifetracker/core/model/` |

New storage plugins must at least provide a `LifeTrackerStorage` implementation. Use the JSONL and SQLite plugins as references for JSON/Room specific details.

---

## 3. Dependency Checklist

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":kernel"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
}
```

For Android-facing plugins you can switch to `com.android.library` / `org.jetbrains.kotlin.android` so you can use Android platform APIs.

---

## 4. Reference Implementations

- `app/plugins/storage/json/JsonlStorage.kt`  
  Uses `kotlinx.serialization.Json` + file IO and demonstrates how to harden against corrupt lines.
- `app/plugins/storage/json/JsonStoragePlugin.kt`  
  Entry point that implements `StoragePlugin` for JSONL.
- `app/plugins/storage/sqlite/SqliteStorage.kt`  
  Room-backed production storage that seeds data from JSONL and updates an outbox.
- `app/plugins/storage/sqlite/sync/JsonlOutboxSyncWorker.kt`  
  WorkManager job that drains SQLite outbox items into JSONL.

---

## 5. Things to Double-check

- **Serialization settings:** reuse `Json { ignoreUnknownKeys = true; encodeDefaults = true }`.
- **Coroutines:** run blocking IO inside `Dispatchers.IO`.
- **Testing:** follow `docs/testing_strategy.md` before adding or updating tests.
- **DI wiring:** confirm `AppContainer` swaps your plugin in without leaking implementation details.
- **File deletion UX:** any plugin that deletes/moves user files must show an explicit confirmation dialog (see `docs/開発ガイド.md`).
- **Dual-run operations:** consult `docs/sqlite_jsonl_dual_run.md` for the SQLite + JSONL coexistence workflow.

---

## 6. Future extensions

- Extract common utility helpers for analytics once requirements solidify.
- Consider a gradle convention plugin (`build-logic`) if multiple storage modules emerge.
- Keep this doc in sync with new contracts or utilities.
