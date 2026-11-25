# Planaday Native — Verification & Test Plan (`TEST_PLAN.md`)

**Core Rule:** No feature, requirement, or historical regression passes on screenshots, compilation, or historical developer claims alone. Every requirement maps to concrete automated tests across the pyramid below.

---

## 1. Verification Pyramid

1. **Greenfield & Traceability Gate (`scripts/verify_greenfield_and_traceability.py`):**
   - Verifies 652 unique requirement IDs, 56 PF IDs, 50 N IDs, 152 legacy ledger entries, 28 historical regression IDs.
   - Verifies zero legacy file SHA-256 duplicates, zero legacy paths, zero legacy runtime dependencies (`electron`, `capacitor`, `dexie`, `vite`, `fastify`), zero legacy storage/app identifiers (`O-004`), and zero private LAN IP leaks.
2. **Deterministic Domain & Engine Contract Tests (`core:*:test`):**
   - `core:math`: 120+ independent mathematical, calculus, pole-safe root, complex, matrix, 23-category invertible unit conversion, lab analytical (`C1V1=C2V2`, `%` recovery, `%RSD`, Wet Chem vs NIR OLS/r/R²/SEP/RMSE), and sparse DAG spreadsheet formula (`#REF!`, cycle detection, composite formulas, zero-length `LEFT`/`RIGHT`) assertions.
   - `core:domain`: IANA timezone/DST gap & fold transitions, leap year Feb 29 policies, RFC 5545 recurrence expansion, completion-driven chore recalculation, room health replay, constraint-aware task scheduler, cycle-safe hierarchy, ACL policy.
   - `core:documents`: Blurless cross-record draft isolation (`PD-016`), 100k-word document editing & O(n) counters (`PD-010`), WikiLink/backlink graph, line-diff revisions.
   - `core:graphics`: 10,000-object spatial index (`SpatialIndex2D`), zoom-independent hit tests, SVG export.
   - `core:interchange`: RFC 4180 CSV round-trip + formula injection neutralization (`PD-006`), RFC 5545 ICS empty envelope & RRULE/EXDATE/TZID round-trip (`C-0060`), typed `ExportArtifact` (`PD-008`), Presentation PPTX import + HTML fidelity export (`R-0455`, `PD-018`), Book Markdown/HTML/EPUB export, Planaday Native v1 backup ZIP + SHA-256 manifest + tamper/traversal rejection (`PF-42`).
   - `core:security`: SSRF outbound policy (`PD-014`), AES-256-GCM encrypted backup round-trip + wrong-key rejection (`N-048`), Ed25519 offline entitlement verification + data survival (`N-045`), code execution isolation policy (`PD-005`).
   - `core:automation` & `core:notifications`: Idempotent trigger/condition/dry-run/action engine (no replay duplication), cycle/rate caps, wall-time alarms, monotonic timers, quiet hours, snooze.
3. **Real Persistence & Migration Tests (`core:persistence:jvmTest`):**
   - Real Room KMP + Bundled SQLite (WAL + FTS5 + FK) fresh empty database initialization (`O-004`), all typed entity CRUD round-trips, 1,000 concurrent disjoint updates (`PD-001`), rollback on failure, 30-day trash & ref-counted blobs, FTS5 workspace-scoped search, and future schema migration (`v1 → v2` test fixture).
4. **Live Server & Two-Client End-to-End Sync Tests (`server:*:test`, `tests:integration:test`):**
   - **Fresh-Start Acceptance Gate:** Two clean SQLite clients + empty isolated Ktor server; offline synthetic creation on both clients; authenticated pairing; bidirectional record + SHA-256 PNG/JPEG/WAV blob sync (`PD-002`, `CONT-003`); 401/403 workspace ACL & cross-workspace blob probing rejection (`PD-004`); SHA-256 mismatch HTTP 400 (`PD-015`); simulated HTTP 503 blob failure forcing `ERROR` status (`CONT-004`); backup restore onto empty replacement Server B triggering epoch reconciliation (`PD-003`, `PF-03`).
   - All **15 Cross-Feature Integration Journeys (`INT-001`..`INT-015`)**.
5. **Native UI, Responsive & Build Gates (`apps:desktop`, `apps:android`):**
   - Android debug APK compilation (`:apps:android:assembleDebug`, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 29`).
   - Compose Desktop headless UI & navigation/accessibility/keyboard tests (`:apps:desktop:test`).
   - Responsive `320dp` Android & desktop multi-pane layout tests (`PD-011`, `PD-012`, `PD-013`, `PD-019`).
6. **Measured Performance Benchmarks (`tests:integration:test`):**
   - Real wall-clock p50/p95/p99 measurements for 100k-word note, 10k sparse spreadsheet cells, 10k canvas objects, 10k–100k FTS5 records, 5,000 planner bookmarks (`R-0072`), 64-level deep note tree (`R-0233`), and 1,000 concurrent SQLite edits.
