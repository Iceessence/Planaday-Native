# Planaday Native — Performance Engineering & Benchmark Baseline (`PERFORMANCE_BASELINE.md`)

**Rule:** Never fabricate benchmark numbers or claim targets as achieved until measured on actual compiled builds. All measurements below were captured directly from executed JUnit tests on real compiled Kotlin + Room KMP `2.8.5` (Schema `v2`) + Bundled SQLite `2.6.2` + Compose Multiplatform `1.10.3`.

---

## 1. Declared Reference Engineering Workstation

- **OS:** Microsoft Windows 11 Pro `10.0.26300` (Build `26300`)
- **CPU:** Intel Core i9-14900K (24 cores / 32 threads)
- **RAM:** `95.69 GB`
- **JDK:** Oracle JDK `21.0.9+7-LTS-338` (64-Bit Server VM)
- **Persistence Engine:** Room KMP `2.8.5` (Schema `v2` with composite keyset & schedule-range indexes) + Bundled SQLite `2.6.2` (WAL + FTS5 + FK enabled)
- **Caveat:** High-spec workstation; modest Android hardware (API 29–36 mid-range phone) and modest Windows laptops remain required pre-release verification targets (`PLATFORM_TESTED` pending).

---

## 2. Update 2 Architectural Performance Repair — Reactive Query Isolation (§6)

Update 1's `PlanadayWorkspaceController.refreshWorkspace()` reloaded multiple unrelated collections after ordinary edits and used unbounded `listByKind()` queries. Update 2 **completely eliminated `refreshWorkspace()`** and replaced it with:
- Route-scoped reactive Room `Flow`s (`observeByKind(..., limit)` and `observeScheduleWindow(...)`).
- Composite SQLite Schema v2 indexes for `O(log N)` keyset pagination (`(workspace_id, kind, deleted_at_utc, updated_at_utc, id)`) and indexed calendar windows (`(workspace_id, kind, deleted_at_utc, schedule_start_iso, schedule_end_iso)`).
- Live `RepositoryQueryTelemetry` instrumentation verified in `Update2ScalabilitySecurityAndFeaturesTest#reactiveQueryIsolationEditingOneTaskReloadsZeroUnrelatedCollections`:
  - **Measured Telemetry After Editing 1 Task:** `telemetry_after_task_edit = {TASK=1}`
  - **Unrelated Collection Reloads (Notes, Expenses, Laboratory, Food, Chores, Habits, Goals, Events):** **`0`**!

---

## 3. Progressive Dataset Scaling Benchmark (`10,000 → 100,000 → 250,000` SQLite Records — Run 3)

Captured in `Update2ScalabilitySecurityAndFeaturesTest#progressiveScaling10kTo100kTo250kRecordsRoutineOpsStayFlat`:

| Total Workspace Records | Single-Task Atomic Edit | Indexed Calendar Window Query | O(log N) Keyset Page Fetch (`pageSize=50`) | Scoped SQLite FTS5 Search | Status |
|---:|---:|---:|---:|---:|---|
| **`10,000`** | **`3 ms`** | **`1 ms`** | **`1 ms`** | **`1 ms`** | `DEVELOPER_TESTED` |
| **`100,000`** | **`21 ms`** | **`0 ms`** | **`1 ms`** | **`1 ms`** | `DEVELOPER_TESTED` |
| **`250,000`** | **`48 ms`** | **`0 ms`** | **`1 ms`** | **`0 ms`** | `DEVELOPER_TESTED` |

*(Dataset growth of `25×` produced flat `0–1 ms` calendar window, keyset pagination, and FTS5 search latency, proving zero global table-scan degradation.)*

---

## 4. Full Run 3 Engine, UI & Storage Benchmark Matrix (2026-11-26)

| Benchmark Workload | Requirement / Defect | Provisional Budget | Measured Result (JVM Workstation) | Status |
|---|---|---|---|---|
| **`100,000`-word note REAL Compose Notes Studio UI open & live render** | **`PD-010`, `PF-07`, Update 2 §11** | `< 5,000 ms` | **`380 ms`** (`ComposeDesktopUiAndA11yTest`) | `DEVELOPER_TESTED` |
| `100,000`-word multiline document O(n) word/paragraph stats | `PD-010`, `PF-07` | `< 250 ms` | **`7 ms`** | `DEVELOPER_TESTED` |
| `100,000`-word chunked buffer localized insert | `PD-010`, `PF-07` | `< 50 ms` | **`< 1 ms` (`0 ms`)** | `DEVELOPER_TESTED` |
| `10,000` sparse spreadsheet cells DAG eval + `SUM(A1:A2000)` | `R-0436`, `PF-08` | `< 150 ms` | **`33 ms`** | `DEVELOPER_TESTED` |
| `10,000` vector canvas objects `SpatialIndex2D` build | `PF-31`, `R-0485` | `< 200 ms` | **`18 ms`** | `DEVELOPER_TESTED` |
| `100` zoom-independent hit tests over `10,000` canvas objects | `PF-31`, `R-0073` | `< 2 ms`/query | **`1 ms` total** | `DEVELOPER_TESTED` |
| `64`-depth / `10,000`-node hierarchy breadcrumb + cycle check + subtree BFS | `R-0233`, `N-008` | `< 500 ms` | **`4 ms`** | `DEVELOPER_TESTED` |
| `5,000` relational SQLite planner bookmarks insert | `R-0072` | `< 500 ms` | **`13 ms`** | `DEVELOPER_TESTED` |
| `5,000` relational SQLite planner bookmarks indexed query | `R-0072` | `< 100 ms` | **`2 ms`** | `DEVELOPER_TESTED` |
| `100,000`-record Room KMP + Bundled SQLite FTS5 soak bulk ingest | Master §9, `PF-10` | `< 15,000 ms` | **`2,536 ms` (`~39.4k rec/s`)** | `DEVELOPER_TESTED` |
| Workspace-scoped SQLite FTS5 query across `100,000` records (20 trials) | Master §9, `PF-10`, `N-006` | `< 300 ms` (p95) | **`p50 = 0 ms`, `p95 = 1 ms`** | `DEVELOPER_TESTED` |
| `64 MiB` (`67,108,864` B) bounded-memory O(1) streaming SHA-256 ingest | Master §9, `PF-02`, `PD-015` | `< 2,000 ms` | **`62 ms` (`> 1.0 GB/s`)** | `DEVELOPER_TESTED` |
| `64 MiB` on-disk streaming SHA-256 integrity verification | Master §9, `PF-02` | `< 2,000 ms` | **`36 ms` (`> 1.7 GB/s`)** | `DEVELOPER_TESTED` |
| `1,000` overlapping concurrent Room SQLite disjoint updates | `PD-001`, `PF-01` | `< 2,000 ms` | **`< 717 ms`** (incl. all 15 INT journeys) | `DEVELOPER_TESTED` |
| Real Room KMP `MIGRATION_1_2` (`1.json` v1 DB → Schema v2) | Update 2 §27 | Zero data loss | **PASS (`100%` record preservation)** | `DEVELOPER_TESTED` |
| Android R8 release cold start & mid-range phone `320dp` frame pacing | Master §9 | `< 4.0 s` / 60 fps | Pending physical Android 10 & 16 hardware | `BLOCKED` |
