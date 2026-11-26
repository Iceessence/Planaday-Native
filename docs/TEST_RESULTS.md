# Planaday Native — Executed Test Results (`TEST_RESULTS.md`)

**Rule:** Strictly records freshly executed automated tests from `Planaday-Native` only. Historical legacy Vitest claims (178 tests) are NEVER counted as new verification. All passing runs below are `DEVELOPER_TESTED` and remain `PENDING_INDEPENDENT_REVIEW` until verified by a separate reviewer.

---

## Run 2 — Full-Scope Engines, 88-Record Sync Round-Trip, 100k FTS5 Soak, 64 MiB Streaming Blobs, Headless Compose 320dp/A11y UI & Native Windows MSI/Android APK/Ktor Packaging (2026-11-25)

- **Environment:** Windows 11 Pro `10.0.26300` (Build `26300`), Intel Core i9-14900K (24c/32t), `95.69 GB` RAM, Oracle JDK `21.0.9+7-LTS-338` (incl. `jpackage.exe`), Android SDK API `36` (`build-tools;36.0.0`), Gradle `8.14.3` (SHA-256 verified), Kotlin `2.2.21`, Room KMP `2.8.5`, Bundled SQLite `2.6.2`, Compose Multiplatform `1.10.3`, Ktor `3.3.3`.
- **Commands Executed:**
  ```powershell
  .\gradlew.bat :core:model:jvmTest :core:domain:jvmTest :core:math:jvmTest :core:documents:jvmTest :core:graphics:jvmTest :core:security:jvmTest :core:interchange:jvmTest :core:automation:jvmTest :core:notifications:jvmTest :core:sync-protocol:jvmTest :server:app:test :apps:desktop:test :tests:integration:test :apps:desktop:createDistributable :apps:desktop:packageMsi :server:app:installDist :apps:android:assembleDebug --no-daemon
  docker compose --env-file deployment/.env.example -f deployment/docker-compose.yml config --quiet
  py scripts/verify_greenfield_and_traceability.py
  ```
- **Outcome:** `BUILD SUCCESSFUL` — **16 / 16 JUnit suites, 37 / 37 test methods PASSED (`0` failed, `0` errors, `0` skipped)** (`DEVELOPER_TESTED`).

### Suite-by-Suite Breakdown (Exact JUnit XML Tally)

| Test Suite | Tests | Passed | Failed | Duration | Verified Scope |
|---|---|---|---|---|---|
| `CoreMathContractTest` | 5 | 5 | 0 | `0.158s` | 55+ AST math/calculus/pole-safe root (`PD-007`)/complex/matrix assertions, all 23 invertible unit categories, Lab `C1V1=C2V2`/`%` recovery/`%RSD`/Wet vs NIR (`LA-001`..`LA-002`), 36+ spreadsheet formulas (`CONT-006` `#REF!`, `CONT-007` composite & zero-length `LEFT`/`RIGHT`, `#DIV/0!`, `#CIRC!`). |
| `CoreDomainContractTest` | 3 | 3 | 0 | `0.188s` | Leap year Feb 29 policies, IANA DST spring-forward (`America/Los_Angeles`), RFC 5545 recurrence + EXDATE, completion-driven chore recalculation & room health, all 7 calendar overlap policies, task carryover preserving original `createdAt` & reschedule count, intelligent task placement around events, 70-depth hierarchy & cycle rejection (`R-0233`), exact decimal money math, deny-by-default workspace ACL. |
| `CoreDocumentsContractTest` | 2 | 2 | 0 | `0.214s` | Blurless cross-record draft switch isolation (`PD-016` + `PF-55`), 100,000-word O(n) single-pass stats & chunked edit (`PD-010`), `[[WikiLink]]` extraction, line diffs. |
| `CoreGraphicsContractTest` | 2 | 2 | 0 | `0.167s` | 10,000-object `SpatialIndex2D` viewport cull & zoom-independent hit tests, SVG whiteboard export (`C-0066`), 50 US states, dynamic Left/Right side tabs (`R-0068`), 5,000 paginated planner bookmarks (`R-0072`). |
| `CoreSecurityContractTest` | 3 | 3 | 0 | `0.228s` | SSRF outbound URL policy (`PD-014`), SHA-256 + PBKDF2 + AES-256-GCM round-trip & wrong-key rejection, Ed25519 offline entitlement + data survival (`N-045`), Safe Learning Interpreter + breakpoint debugger (`PD-005`, `R-0325`). |
| `CoreInterchangeContractTest` | 3 | 3 | 0 | `0.065s` | RFC 4180 CSV quoted/multiline round-trip + formula neutralization (`PD-006`), RFC 5545 empty ICS envelope (`C-0060`) & RRULE/EXDATE/all-day round-trip, Presentation HTML fidelity export (`PD-018`), Book Markdown & HTML-DOC export (`C-0065`). |
| `ArchiveInterchangeJvmTest` | 2 | 2 | 0 | `0.333s` | Planaday Native v1 verified ZIP + AES-256-GCM encrypted backup (`PF-42`, `N-048`), tamper & wrong-key rejection, OOXML `.pptx` round-trip import with Fidelity Report (`R-0455`). |
| `AutomationContractTest` | 1 | 1 | 0 | `0.043s` | Dry-run preview, human approval gate, idempotent sync-replay dedup, cycle depth guard (`PF-34`, `N-035`). |
| `NotificationContractTest` | 1 | 1 | 0 | `0.057s` | Quiet hours deferral, snooze rollover, monotonic elapsed timer unaffected by wall-clock shifts (`PF-46`). |
| `DesktopShellTest` | 1 | 1 | 0 | `0.073s` | All 54 named workspaces (`C-0001`..`C-0054`) verified in route catalog, fresh empty desktop DB (`O-004`), task creation, theme switch, route switch, FTS5 search. |
| `ComposeDesktopUiAndA11yTest` | 2 | 2 | 0 | `2.251s` | Real Skiko/Compose UI rendering of `DesktopShellRoot` (interactive Command Palette click + fresh empty O-004 banner) AND `320.dp` compact viewport layout & accessibility audit across **24 distinct studios** proving **0 unnamed clickable controls (`PD-013`)** and **0 horizontal width overflow (`PD-011`, `PD-012`, `PD-019`, `PF-06`)**. |
| `FreshStartAndSyncE2ETest` | 2 | 2 | 0 | `0.562s` | **Fresh-Start Acceptance Gate (`O-004`):** Empty Device A + Empty Device B + Empty Ktor Server; offline creation + PNG/JPEG/WAV attachments (`CONT-003`); `401`/`403` auth, single-use pairing code, cross-workspace blob probing `403`, SSRF `403`, SHA-256 mismatch `400` (`PD-015`), simulated `503` blob failure forcing `ERROR` (`CONT-004`); verified two-client sync (`PD-002`); `C-0059` idempotency; `C-0061` BuildIdentity; `CONT-009` server backup manifest; token revocation `401`; AES-256-GCM backup restore onto empty Replacement Server B reconciliation (`PD-003`, `PF-42`). |
| `CrossFeatureIntegrationAndRegressionsTest` | 3 | 3 | 0 | `0.676s` | All **15 cross-feature integration journeys (`INT-001`..`INT-015`)** on real Room KMP + Bundled SQLite (WAL + FTS5); **1,000 overlapping concurrent disjoint updates** (`PD-001`, exact `alpha=500, beta=500, rev=1001`, coalesced outbox, rollback on throw); SQLite-backed blurless draft switch (`PD-016`); Desktop isolated child-process runner (`R-0322`); future `v1 → v2` additive SQLite migration drill. |
| `All88RecordTypesAndFullScopeTest` | 3 | 3 | 0 | `1.444s` | Create → Read → Atomic Update → Soft Trash → Restore → Bidirectional Server Sync across **ALL 98 `RecordKind` values** (covering every single `C-0070`..`C-0158` canonical record family + N-series); Project Gantt Critical Path & capacity; shared availability + travel buffers + what-if + estimate calibration; recipe scaling + FEFO pantry + shared delta merge + exact-decimal budget forecasting; wellness 1–10 trends + private bathroom counters + household fairness + SM-2 spaced review; lab custody sign-off + client pricing hierarchy + reagent reorder audit + GUM uncertainty; deterministic seeded Game Dev balance simulator + asset license registry; custom schemas + XSS-safe templates + workspace-ACL pivots & graph explorer + opt-in local assistance + extension capability validator; OOXML `.xlsx` round-trip, `.docx`/`.epub` export, Markdown+YAML round-trip, PDF review annotations, AES-256-GCM Secure Share Bundle, 260 newly authored stickers, non-destructive photo recipes, and planner page duplicate/reorder. |
| `ScaleSoakConflictAndStreamingTest` | 3 | 3 | 0 | `2.597s` | **Multi-device optimistic sync conflict preservation (`N-042`)** (`resolveConflictKeepBoth` with `DERIVED_FROM` link, zero silent LWW overwrite) + `SelectiveSyncPolicy` zero-deletion proof (`N-041`); **`100,000`-record Room KMP + Bundled SQLite FTS5 soak benchmark** (`2,269 ms` ingest, **`p50 = 0 ms`, `p95 = 1 ms`** search, zero cross-workspace leakage); **`64 MiB` (`67,108,864` bytes) bounded-memory O(1) streaming SHA-256 blob ingest (`62 ms`) & disk verification (`38 ms`)** + 1-byte corruption detection. |
| `PerformanceAndCapacityBenchmarkTest` | 1 | 1 | 0 | `0.334s` | Real measured timings across 100k-word note, 10k sparse cells, 10k canvas objects, 64-depth/10k tree, 5k SQLite bookmarks, 1k FTS5 records (recorded in [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md)). |

### Native Builds, Packaging & CI Gate Verification

- **Native Windows MSI Installer (`:apps:desktop:packageMsi` via JDK 21 `jpackage` + WiX, `upgradeUuid = 9B7E4E52-3C8A-4D7E-A156-7F41C9E2B604`, zero Electron):** **PASS**
  - Artifact: `apps/desktop/build/compose/binaries/main/msi/Planaday Native-1.0.0.msi` (`72,393,015` bytes)
  - SHA-256: `9B7929F7972B9DF479C451E038E76A14D0995157ADDC189045294C4F36E9F5A3`
- **Native Windows Standalone Executable & JLink Runtime (`:apps:desktop:createDistributable`):** **PASS**
  - Artifact: `apps/desktop/build/compose/binaries/main/app/Planaday Native/Planaday Native.exe` (`560,640` bytes)
  - SHA-256: `6B1943EDB0B6B9267412A2F4CB005FFBDAF9D6C168C4AE809F036197972117D5`
- **Native Android Debug APK (`:apps:android:assembleDebug`, `compileSdk 36`, `targetSdk 36`, `minSdk 29`, `applicationId = "com.planaday.nativeapp"`, zero WebView/Capacitor):** **PASS**
  - Artifact: `apps/android/build/outputs/apk/debug/android-debug.apk` (`41,244,597` bytes)
  - SHA-256: `3600461B17FA9C528C851CAABC6232CA1DAD876155AE1417CA0C19606A7820F5`
- **Standalone Ktor 3 Self-Hosted Sync Server Dist (`:server:app:installDist`):** **PASS** (`server/app/build/install/app/bin/app` & `app.bat`).
- **Room KMP Schema Export:** **PASS** — `core/persistence/schemas/com.planaday.nativeapp.core.persistence.PlanadayDatabase/1.json` (`38,162` bytes).
- **Docker Compose Config (`docker compose --env-file deployment/.env.example -f deployment/docker-compose.yml config --quiet`):** **PASS** (exit code `0`; strictly fails fast if `PLANADAY_PG_PASSWORD` is unset).
- **Greenfield & Traceability CI Gate (`py scripts/verify_greenfield_and_traceability.py`):** **PASS** (`126` files scanned, `0` legacy SHA-256 duplicates, `0` O-004 identity violations, `652/652` requirements, `56/56` PF, `50/50` N, `152/152` ledger, `28/28` historical issues).

### Environment-Blocked Checks (Explicitly NOT Claimed as Passed)
- **Live Docker Container Run (`docker compose up` + PostgreSQL 17 container restore drill):** Docker CLI `29.1.3` installed, but Docker Desktop Linux engine daemon is offline in this environment.
- **Android Emulator & Physical Android 10 / 16 Hardware UI, Gboard IME (`C-0055`), Camera & Alarm Reboot Checks:** No running AVD or USB physical Android device attached in this session.
- **Authenticode-Signed Windows MSI Clean-VM Install/Upgrade/Uninstall Check:** Unsigned MSI built cleanly (`Planaday Native-1.0.0.msi`), but clean Windows VM install/uninstall smoke test and Authenticode code signing await owner signing certificate/VM.
- **Independent Reviewer Sign-Off:** Pending second-party review across all architecture, security, durability, and release gates (`0` self-certified `INDEPENDENTLY_ACCEPTED`).
