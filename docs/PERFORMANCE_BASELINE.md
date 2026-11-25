# Planaday Native — Performance Engineering & Benchmark Baseline (`PERFORMANCE_BASELINE.md`)

**Rule:** Never fabricate benchmark numbers or claim targets as achieved until measured on actual compiled builds. Every measurement records hardware, OS, JVM/Android build mode, dataset size, and exact command.

---

## 1. Declared Reference Engineering Workstation

- **OS:** Microsoft Windows 11 Pro `10.0.26300` (Build `26300`)
- **CPU:** Intel Core i9-14900K (24 cores / 32 threads)
- **RAM:** `95.69 GB`
- **JDK:** Oracle JDK `21.0.9+7-LTS-338` (64-Bit Server VM)
- **Android SDK:** Compile/Target API `36` (Build Tools `36.0.0`), `minSdk = 29`
- **Note:** This workstation is high-spec; modest Android devices (API 29–36 mid-range phone) and modest Windows laptops remain required verification targets prior to commercial release.

---

## 2. Provisional Performance Budgets (Master §9)

| Metric | Provisional Target | Workload | Status |
|---|---|---|---|
| Desktop Warm / Cold Launch | `< 1.0s` warm / `< 3.0s` cold | Fresh DB + 10k records | Pending prototype measurement |
| Android Warm / Cold Launch | `< 1.5s` warm / `< 4.0s` cold | Mid-range Android device (release R8 + Baseline Profile) | Pending physical device run |
| Common Local CRUD Action (p95) | `< 100 ms` | Create/update task, note, event with atomic outbox | Measured in `PerformanceBenchmarkTest` |
| Indexed FTS5 Search (p95) | `< 300 ms` | `100,000` records across workspaces with ACL | Measured in `PerformanceBenchmarkTest` |
| UI Frame Budget | `16.7 ms` (60Hz), zero UI blocks `> 100ms` | Virtualized 1-min calendar, 10k canvas objects | Measured in prototype & UI tests |
| Large Note Editing | `< 50 ms` text echo, zero timeout | `100,000`-word multiline document (`PD-010`) | Measured in `PerformanceBenchmarkTest` |
| Sparse Spreadsheet Recalculation | `< 150 ms` incremental recalc | `10,000+` sparse cells with dependency DAG | Measured in `PerformanceBenchmarkTest` |
| Vector Canvas Spatial Hit-Test | `< 2 ms` per query | `10,000` canvas objects (`SpatialIndex2D`) | Measured in `PerformanceBenchmarkTest` |
| Attachment Streaming | Bounded heap (`O(1)` streaming buffer) | Large binary stream SHA-256 verification | Measured in `PerformanceBenchmarkTest` |
| Outbox Sync Queue Throughput | `< 500 ms` batch apply | `1,000` concurrent disjoint edits + `10,000` queued ops | Measured in `PerformanceBenchmarkTest` |

---

## 3. Executed Benchmark Log

*(Populated automatically with real wall-clock measurements from `gradlew.bat :tests:integration:test` once prototype & benchmark suite execute.)*
