# Planaday Native — Executed Test Results (`TEST_RESULTS.md`)

**Rule:** Strictly records freshly executed automated tests from `Planaday-Native` only. Historical legacy Vitest claims (178 tests) are NEVER counted as new verification. All passing runs below are `DEVELOPER_TESTED` and remain `PENDING_INDEPENDENT_REVIEW` until verified by a separate reviewer.

---

## Run 1 — Full Multiplatform Foundation, Room KMP, Ktor Sync Server, Android APK & Benchmarks (2026-11-25)

- **Environment:** Windows 11 Pro `10.0.26300` (Build `26300`), Intel Core i9-14900K (24c/32t), `95.69 GB` RAM, Oracle JDK `21.0.9+7-LTS-338`, Android SDK API `36` (`build-tools;36.0.0`), Gradle `8.14.3` (SHA-256 verified), Kotlin `2.2.21`, Room KMP `2.8.5`, Bundled SQLite `2.6.2`, Compose Multiplatform `1.10.3`, Ktor `3.3.3`.
- **Command Executed:**
  ```powershell
  .\gradlew.bat :core:model:jvmTest :core:domain:jvmTest :core:math:jvmTest :core:documents:jvmTest :core:graphics:jvmTest :core:security:jvmTest :core:interchange:jvmTest :core:automation:jvmTest :core:notifications:jvmTest :core:sync-protocol:jvmTest :server:app:test :apps:desktop:test :apps:android:assembleDebug :tests:integration:test
  ```
- **Outcome:** `BUILD SUCCESSFUL in 28s` — **13 / 13 JUnit suites, 29 / 29 test methods PASSED (`0` failed, `0` errors, `0` skipped)** (`DEVELOPER_TESTED`).

### Suite-by-Suite Breakdown

| Test Suite | Tests | Passed | Failed | Duration | Verified Scope |
|---|---|---|---|---|---|
| `CoreMathContractTest` | 5 | 5 | 0 | `0.158s` | 55+ AST math/calculus/pole-safe root (`PD-007`)/complex/matrix assertions, all 23 invertible unit categories, Lab `C1V1=C2V2`/`%` recovery/`%RSD`/Wet vs NIR (`LA-001`..`LA-002`), 36+ spreadsheet formulas (`CONT-006` `#REF!`, `CONT-007` composite & zero-length `LEFT`/`RIGHT`, `#DIV/0!`, `#CIRC!`). |
| `CoreDomainContractTest` | 3 | 3 | 0 | `0.168s` | Leap year Feb 29 policies, IANA DST spring-forward (`America/Los_Angeles`), RFC 5545 recurrence + EXDATE, completion-driven chore recalculation & room health, all 7 calendar overlap policies, task carryover preserving original `createdAt` & reschedule count, intelligent task placement around events, 70-depth hierarchy & cycle rejection (`R-0233`), exact decimal money math, deny-by-default workspace ACL. |
| `CoreDocumentsContractTest` | 2 | 2 | 0 | `0.214s` | Blurless cross-record draft switch isolation (`PD-016` + `PF-55`), 100,000-word O(n) single-pass stats & chunked edit (`PD-010`), `[[WikiLink]]` extraction, line diffs. |
| `CoreGraphicsContractTest` | 2 | 2 | 0 | `0.128s` | 10,000-object `SpatialIndex2D` viewport cull & zoom-independent hit tests, SVG whiteboard export (`C-0066`), 50 US states, dynamic Left/Right side tabs (`R-0068`), 5,000 paginated planner bookmarks (`R-0072`). |
| `CoreSecurityContractTest` | 3 | 3 | 0 | `0.228s` | SSRF outbound URL policy (`PD-014`), SHA-256 + PBKDF2 + AES-256-GCM round-trip & wrong-key rejection, Ed25519 offline entitlement + data survival (`N-045`), Safe Learning Interpreter + breakpoint debugger (`PD-005`, `R-0325`). |
| `CoreInterchangeContractTest` | 3 | 3 | 0 | `0.057s` | RFC 4180 CSV quoted/multiline round-trip + formula neutralization (`PD-006`), RFC 5545 empty ICS envelope (`C-0060`) & RRULE/EXDATE/all-day round-trip, Presentation HTML fidelity export (`PD-018`), Book Markdown & HTML-DOC export (`C-0065`). |
| `ArchiveInterchangeJvmTest` | 2 | 2 | 0 | `0.268s` | Planaday Native v1 verified ZIP + AES-256-GCM encrypted backup (`PF-42`, `N-048`), tamper & wrong-key rejection, OOXML `.pptx` round-trip import with Fidelity Report (`R-0455`). |
| `AutomationContractTest` | 1 | 1 | 0 | `0.043s` | Dry-run preview, human approval gate, idempotent sync-replay dedup, cycle depth guard (`PF-34`, `N-035`). |
| `NotificationContractTest` | 1 | 1 | 0 | `0.042s` | Quiet hours deferral, snooze rollover, monotonic elapsed timer unaffected by wall-clock shifts (`PF-46`). |
| `DesktopShellTest` | 1 | 1 | 0 | `0.414s` | All 54 named workspaces (`C-0001`..`C-0054`) verified in route catalog, fresh empty desktop DB (`O-004`), task creation, theme switch, route switch, FTS5 search. |
| `FreshStartAndSyncE2ETest` | 2 | 2 | 0 | `2.082s` | **Fresh-Start Acceptance Gate (`O-004`):** Empty Device A + Empty Device B + Empty Ktor Server; offline creation + PNG/JPEG/WAV attachments (`CONT-003`); `401`/`403` auth, single-use pairing code, cross-workspace blob probing `403`, SSRF `403`, SHA-256 mismatch `400` (`PD-015`), simulated `503` blob failure forcing `ERROR` (`CONT-004`); verified two-client sync (`PD-002`); `C-0059` idempotency; `C-0061` BuildIdentity; `CONT-009` server backup manifest; token revocation `401`; AES-256-GCM backup restore onto empty Replacement Server B reconciliation (`PD-003`, `PF-42`). |
| `CrossFeatureIntegrationAndRegressionsTest` | 3 | 3 | 0 | `0.992s` | All **15 cross-feature integration journeys (`INT-001`..`INT-015`)** on real Room KMP + Bundled SQLite (WAL + FTS5); **1,000 overlapping concurrent disjoint updates** (`PD-001`, exact `alpha=500, beta=500, rev=1001`, coalesced outbox, rollback on throw); SQLite-backed blurless draft switch (`PD-016`); Desktop isolated child-process runner (`R-0322`); future `v1 → v2` additive SQLite migration drill. |
| `PerformanceAndCapacityBenchmarkTest` | 1 | 1 | 0 | `0.477s` | Real measured timings across 100k-word note, 10k sparse cells, 10k canvas objects, 64-depth/10k tree, 5k SQLite bookmarks, 1k FTS5 records (recorded in [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md)). |

### Build, Packaging & CI Gate Verification

- **Android Debug APK (`:apps:android:assembleDebug`):** **PASS** (`compileSdk 36`, `targetSdk 36`, `minSdk 29`, `applicationId = "com.planaday.nativeapp"`).
  - Artifact: `apps/android/build/outputs/apk/debug/android-debug.apk` (`40,676,129` bytes)
  - SHA-256: `382034F1CDA99D4354E1362F4584D153C736D36966329BB256AB3C9F494DBA1A`
- **Room KMP Schema Export:** **PASS** — `core/persistence/schemas/com.planaday.nativeapp.core.persistence.PlanadayDatabase/1.json` (`38,162` bytes).
- **Docker Compose Config (`docker compose -f deployment/docker-compose.yml config --quiet`):** **PASS** (exit code `0`).
- **Greenfield & Traceability CI Gate (`py scripts/verify_greenfield_and_traceability.py`):** **PASS** (`118` files scanned, `0` legacy SHA-256 duplicates, `0` O-004 identity violations, `652/652` requirements, `56/56` PF, `50/50` N, `152/152` ledger, `28/28` historical issues).

### Environment-Blocked Checks (Explicitly NOT Claimed as Passed)
- **Live Docker Container Run (`docker compose up` + PostgreSQL 17 container restore drill):** Docker CLI `29.1.3` installed, but Docker Desktop Linux engine daemon is offline in this environment.
- **Android Emulator & Physical Android 10 / 16 Hardware UI & Alarm Reboot Checks:** No running AVD or USB physical Android device attached in this session.
- **Packaged Signed Windows MSI/EXE Clean-VM Install/Uninstall Check:** Not executed in this session (requires clean Windows VM + authorized Authenticode test/release signing).
- **Independent Reviewer Sign-Off:** Pending second-party review across all architecture, security, durability, and release gates.
