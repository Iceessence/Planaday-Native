# Planaday Native — Performance Engineering & Benchmark Baseline (`PERFORMANCE_BASELINE.md`)

**Rule:** Never fabricate benchmark numbers or claim targets as achieved until measured on actual compiled builds. All measurements below were captured directly from `PerformanceAndCapacityBenchmarkTest` and `ScaleSoakConflictAndStreamingTest` on real compiled Kotlin + Room KMP + Bundled SQLite.

---

## 1. Declared Reference Engineering Workstation

- **OS:** Microsoft Windows 11 Pro `10.0.26300` (Build `26300`)
- **CPU:** Intel Core i9-14900K (24 cores / 32 threads)
- **RAM:** `95.69 GB`
- **JDK:** Oracle JDK `21.0.9+7-LTS-338` (64-Bit Server VM)
- **Persistence Engine:** Room KMP `2.8.5` + Bundled SQLite `2.6.2` (WAL + FTS5 + FK enabled)
- **Caveat:** High-spec workstation; modest Android hardware (API 29–36 mid-range phone) and modest Windows laptops remain required release verification targets.

---

## 2. Executed Benchmark Results (2026-11-25 — Run 2)

| Benchmark Workload | Requirement / Defect | Provisional Budget | Measured Result (JVM Workstation) | Status |
|---|---|---|---|---|
| `100,000`-word multiline document O(n) word/paragraph stats | `PD-010`, `PF-07` | `< 250 ms` | **`6 ms`** | `DEVELOPER_TESTED` |
| `100,000`-word chunked buffer localized insert | `PD-010`, `PF-07` | `< 50 ms` | **`< 1 ms` (`0 ms`)** | `DEVELOPER_TESTED` |
| `10,000` sparse spreadsheet cells DAG eval + `SUM(A1:A2000)` | `R-0436`, `PF-08` | `< 150 ms` | **`30 ms`** | `DEVELOPER_TESTED` |
| `10,000` vector canvas objects `SpatialIndex2D` build | `PF-31`, `R-0485` | `< 200 ms` | **`19 ms`** | `DEVELOPER_TESTED` |
| `100` zoom-independent hit tests over `10,000` canvas objects | `PF-31`, `R-0073` | `< 2 ms`/query | **`< 1 ms` total (`0 ms`)** | `DEVELOPER_TESTED` |
| `64`-depth / `10,000`-node hierarchy breadcrumb + cycle check + subtree BFS | `R-0233`, `N-008` | `< 500 ms` | **`4 ms`** | `DEVELOPER_TESTED` |
| `5,000` relational SQLite planner bookmarks insert | `R-0072` | `< 500 ms` | **`11 ms`** | `DEVELOPER_TESTED` |
| `5,000` relational SQLite planner bookmarks indexed query | `R-0072` | `< 100 ms` | **`2 ms`** | `DEVELOPER_TESTED` |
| **`100,000`-record Room KMP + Bundled SQLite FTS5 soak bulk ingest** | **Master §9, `PF-10`** | `< 15,000 ms` | **`2,269 ms` (`~44k rec/s`)** | `DEVELOPER_TESTED` |
| **Workspace-scoped SQLite FTS5 query across `100,000` records (20 trials)** | **Master §9, `PF-10`, `N-006`** | **`< 300 ms` (p95)** | **`p50 = 0 ms`, `p95 = 1 ms`** | `DEVELOPER_TESTED` |
| **`64 MiB` (`67,108,864` B) bounded-memory O(1) streaming SHA-256 ingest** | **Master §9, `PF-02`, `PD-015`** | `< 2,000 ms` | **`62 ms` (`> 1.0 GB/s`)** | `DEVELOPER_TESTED` |
| **`64 MiB` on-disk streaming SHA-256 integrity verification** | **Master §9, `PF-02`** | `< 2,000 ms` | **`38 ms` (`> 1.6 GB/s`)** | `DEVELOPER_TESTED` |
| `1,000` overlapping concurrent Room SQLite disjoint updates | `PD-001`, `PF-01` | `< 2,000 ms` | **`< 676 ms`** (incl. all 15 INT journeys) | `DEVELOPER_TESTED` |
| All `98` `RecordKind`s create→update→trash→restore→two-device server sync | `C-0070`..`C-0158` | `< 5,000 ms` | **`1,444 ms`** (entire suite) | `DEVELOPER_TESTED` |
| Ktor sync server app boot in test host | `C-0061` | `< 1,000 ms` | **`53 ms` (cold) / `1–2 ms` (warm)** | `DEVELOPER_TESTED` |
| Android R8 release cold start & mid-range phone `320dp` frame pacing | Master §9 | `< 4.0 s` / 60 fps | Pending physical Android 10 & 16 hardware | `ENVIRONMENT_BLOCKED` |
