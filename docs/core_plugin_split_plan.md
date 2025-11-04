# Core / Plugin Split Todo

## Background

- UI → ViewModel → Repository が JSON 実装に直結していたため、段階的にドメインコアとストレージプラグインに切り分ける。
- 将来的に SQLite をソース・オブ・トゥルースにするため、既存 JSONL 実装はプラグインとして残しつつ移行コストを下げる。

## Phase 1: Domain Core Definition

- [x] Move Task/TaskList/TaskStep/TaskCreationParams to `:kernel` (`com.lifetracker.core.model`).
- [x] Move Event/Mood/Sleep models to `:kernel` (Mood/Sleep now in `WellnessModels.kt`).
- [x] Relocate `TaskRepository` under `com.lifetracker.core.repository` and update UI references.
- [x] Ensure UI (ViewModel/Screen) depends only on core abstractions.
- [x] Make JSON repositories comply with new interfaces (`JsonTaskRepository` etc.).
- [x] Adjust DI (`AppContainer`) so concrete storage can be swapped easily.

## Phase 2: Plugin Layer Refinement

- [x] Extract JSONL implementation into `JsonStoragePlugin` (`com.lifetracker.app.plugins.storage.json`). 
- [x] Wire Task/Event repositories through the plugin abstraction.
- [x] Introduce `StoragePluginLogger` for unified logging/error handling across plugins.

## Phase 3: New Storage Preparation

- [x] Implement Room-backed `SqliteStoragePlugin` (entities/DAO/Room DB + plugin).
- [x] Add outbox + WorkManager bridge to keep JSONL in sync (`json_outbox` + `JsonlOutboxSyncWorker`).
- [x] Document the SQLite + JSONL dual-run strategy (`docs/sqlite_jsonl_dual_run.md`).

## Phase 4: Additional Work

### A. Wellness Data Support
- [x] Add Mood/Sleep tables (entities & DAO).
- [x] Persist mood entries (`LifeTrackerStorage` extension, migration/seeding).
- [x] Persist sleep sessions.
- [x] Expose mood/sleep read APIs to ViewModels/UI (plug into analytics once defined).
- [x] Extend SQLite schema and `Event` payloads to include tags/details; update JSONL seeding strategy accordingly.

### B. Event Analytics
- [x] Define `EventAnalyticsRepository` contract in `:kernel`.
- [x] Provide JSONL-backed implementation (basic counts/aggregates).
- [x] Provide SQLite-backed implementation leveraging SQL/Room.
- [x] Build “routine predictability” scatter dataset (time vs transition probability).
  - [x] Compute transition matrix (task → next task probability) from events/tasks.
  - [x] Produce scatter DTOs (timestamp, probability, source/destination IDs).
  - [x] Add API so UI/graph modules can consume the dataset.

### C. Testing & Tooling
- [x] Document testing approach (`docs/testing_strategy.md`).
- [x] Create in-memory Room tests covering dao operations/outbox behavior.
- [x] Add JSONL storage resilience tests (corrupt line handling, seeding).
- [x] Explore Compose golden/snapshot tests for analytics views (optional).

### D. Migration / Cleanup
- [x] Define migration plan for existing JSONL-only users to SQLite (if destructive migration removal is required later).
- [x] Consider build-logic or convention plugins for shared Gradle config if more plugins are added.
- [x] Review logging strategy (log levels, structured logging for analytics).

### E. Routine Transition Graph Follow-up
- [x] Verify routine predictability pipeline end-to-end:
  - [x] Confirm `EventAnalyticsRepository.routinePredictability` returns data for both JSONL and SQLite plugins with the current fixtures (`JsonEventAnalyticsRepositoryTest`, `SqliteEventAnalyticsRepositoryTest`).
  - [x] Add integration tests that cover the ViewModel path (`AnalyticsGraphViewModel`) to ensure `routinePredictabilityPoints` is non-empty when consecutive `TASK_COMPLETED` events exist (`AnalyticsGraphViewModelTest`).
- [x] Create reliable seed data:
  - [x] Extend `docs/testdata` with `events_transition_sequences.jsonl` containing deterministic task chains for transition validation.
  - [x] Add a debug-only importer/command that can inject this sequence so the graph can be validated without manual entry (`app/src/debug/java/com/lifetracker/app/debug/RoutinePredictabilitySampleImporterDebug.kt`).
- [x] Instrument the UI rendering path:
  - [x] Add debug logging inside `RoutineTransitionGraphCard` to report node/edge counts when rendering.
  - [x] Extend Compose tests to cover the new card rendering with sample nodes/edges.
- [x] Guard empty states and user feedback:
  - [x] Display an inline hint explaining the minimum data requirement when no edges can be rendered.
  - [x] Surface the latest ingestion timestamp so users know when analytics were last recomputed.
- [x] Separate visualization module for easier iteration:
  - [x] Scaffold `:analytics-visualizer` with Compose dependencies and namespace.
  - [x] Move the Canvas renderer and data transformer into the module, exposing a reusable `RoutineTransitionGraph` API.
  - [ ] Add a standalone preview/debug screen in the module that renders seeded data without the app shell.
- [ ] UX polish once data flows:
  - [ ] Add edge legends (thickness -> probability, color -> recency) and consider interaction affordances (tap to highlight successor chain). _Design note: evaluate using a legend row beneath the canvas and highlight interactions via `MutableState`-driven alpha adjustments._
  - [ ] Evaluate performance with >200 transitions; fall back to textual summaries if Canvas rendering drops frames. _Action: profile with synthetic data from the new importer once batch seeding for large datasets is available._

---

Track progress here and mirror major milestones in `docs/更新履歴.md`. Update subtasks whenever implementation details become clearer.
