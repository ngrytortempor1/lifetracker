# Test Data Fixtures

The `docs/testdata` directory contains JSONL event streams used to exercise analytics features without manual data entry.

## Available Fixtures

- `events_sample.jsonl` – mixed task/log activity used for general event ingestion smoke tests.
- `routine_transition_sequences.jsonl` – deterministic task-completion chains that mirror the debug seed bundled at `app/src/debug/res/raw/routine_transition_sample.jsonl`.
- `events_transition_sequences.jsonl` – minimal A → B → C task chains across multiple days. Useful when you want a lightweight dataset to copy into a simulator or to inspect transitions manually.

## Debug Importer

When running a debug build, you can load the routine transitions into the active storage plugin via `RoutinePredictabilitySampleImporterDebug`. It reads the bundled JSONL resource and appends any missing events, letting the routine transition graph render without hand-entering data.

```kotlin
val importer = provideDebugRoutinePredictabilitySampleImporter(context, storage)
if (importer.isEnabled) {
    scope.launch { importer.importSample() }
}
```

This helper is already wired for manual invocation from tooling or a temporary developer button. It compares event IDs, so repeated imports remain idempotent.
