# Dashboard Export TODO

Roadmap for adding a dashboard export feature that produces CSV and PDF summaries consumable by third-party AI tooling.

---

## Goal
- Allow the user to export the current dashboard metrics as shareable files (CSV and PDF).
- Keep the export pipeline lightweight so it works fully offline and reuses existing `DashboardUiState.Ready` data.

## Scope
- Compose UI action to trigger the export workflow.
- Formatting logic that turns dashboard metrics into CSV rows and a simple PDF layout.
- File persistence and sharing through `FileProvider` so other apps can ingest the output.
- Minimal instrumentation covering both export formats.

Out of scope for this iteration:
- Scheduled/automatic exports (hand off to WorkManager later if needed).
- Direct integration with specific AI SaaS products.

## Technical Notes
- Introduce a `DashboardSnapshot` DTO that captures `DashboardUiState.Ready` plus export metadata (timestamp, timezone).
- CSV formatter emits UTF-8 with LF newlines and header `metric,value,description`.
- PDF generation can start with `PdfDocument` and direct text drawing; later revisions may replace it with a Compose-rendered page if richer layout becomes necessary.
- Store files under `context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` and expose them via `FileProvider` for sharing.

## Task Checklist

### 1. Data pipeline
- [x] Add a mapper in `DashboardViewModel` (or dedicated use case) that produces `DashboardSnapshot` from `DashboardUiState.Ready`.
- [x] Decide on canonical ordering for cards and include localized titles/descriptions in the snapshot.
- [x] Capture export metadata (generatedAt, timezoneId, locale).

### 2. Formatting
- [x] Implement `DashboardCsvFormatter` with unit tests verifying header and ordering.
- [x] Implement `DashboardPdfFormatter` based on `PdfDocument`; include unit test covering the document metadata and page count (Robolectric friendly).
- [x] Ensure both formatters accept `DashboardSnapshot` to keep business logic isolated from Android UI concerns.

### 3. File handling and sharing
- [x] Create an `ExportFileRepository` that writes CSV/PDF bytes to the app-specific documents directory and returns a `Uri` via `FileProvider`.
- [x] Extend `AndroidManifest.xml` and add `res/xml/export_file_paths.xml` for the provider configuration.
- [x] Add instrumentation test (or Espresso flow) that triggers an export and asserts the share sheet intent contains the expected MIME type and filename.

### 4. UI integration
- [x] Add an export action to the dashboard screen (e.g. top-right menu or FAB) with a format selector dialog (CSV, PDF).
- [x] Bridge the UI action to a new `DashboardExportViewModel` method that orchestrates snapshot creation, formatting, saving, and share-intent launch on a background dispatcher.
- [x] Display progress and success/failure feedback (e.g. snackbar, toast, or dialog) so the user knows the export status.

### 5. Documentation & QA
- [ ] Update user-facing docs/help tips explaining how to export and hand the file to external AI tools.
- [ ] Add a regression checklist item to the dashboard instrumentation suite to verify the export entry point remains visible.

## Open Questions
- Should exported CSV values be localized (e.g. `3 / 5`) or normalized (`3,5`) for machine ingestion? (Default: reuse display strings and revisit if AI tooling struggles.)
- Do we need to redact any personally identifiable notes before export? (Investigate mood/sleep notes when enabling notes in export.)
