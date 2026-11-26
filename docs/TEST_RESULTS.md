# Planaday Native — Executed Test Results (`TEST_RESULTS.md`)

**Rule:** Strictly records freshly executed automated tests from `Planaday-Native` only. Historical legacy Vitest claims (178 tests) are NEVER counted as new verification. All passing runs below are `DEVELOPER_TESTED` and remain `PENDING_INDEPENDENT_REVIEW` until verified by a separate reviewer (`0` self-certified `INDEPENDENTLY_ACCEPTED`).

---

## Run 3 — Update 2 Reactive Persistence, 250k Progressive Scaling, Room v1→v2 Migration, Security Hardening, Commercial Studios, 100k-Word Live UI, 8 Rendered Screenshots & Clean-SHA Packaging (`1.0.0-alpha.2`, 2026-11-26)

- **Git Commit Built Into Binaries:** `0d00bb7a936a` (`0d00bb7a936a3496481149b5bf838737caf139aa`, `GIT_WORKTREE_DIRTY = false`, `BUILD_TIMESTAMP_UTC = 2026-11-26T02:03:16.115621300Z`).
- **Environment:** Windows 11 Pro `10.0.26300` (Build `26300`), Intel Core i9-14900K (24c/32t), `95.69 GB` RAM, Oracle JDK `21.0.9+7-LTS-338` (incl. `jpackage.exe`), Android SDK API `36` (`build-tools;36.0.0`), Gradle `8.14.3`, Kotlin `2.2.21`, Room KMP `2.8.5` (Schema `v2`), Bundled SQLite `2.6.2`, Compose Multiplatform `1.10.3`, Ktor `3.3.3`.
- **Commands Executed:**
  ```powershell
  .\gradlew.bat :core:model:jvmTest :core:domain:jvmTest :core:math:jvmTest :core:documents:jvmTest :core:graphics:jvmTest :core:security:jvmTest :core:interchange:jvmTest :core:automation:jvmTest :core:notifications:jvmTest :core:sync-protocol:jvmTest :server:app:test :apps:desktop:test :tests:integration:test --no-daemon --rerun-tasks
  .\gradlew.bat :core:model:generatePlanadayBuildMeta :apps:desktop:createDistributable :apps:desktop:packageMsi :server:app:installDist :apps:android:assembleDebug --no-daemon --rerun-tasks
  docker compose --env-file deployment/.env.example -f deployment/docker-compose.yml config --quiet
  py scripts/verify_greenfield_and_traceability.py
  ```
- **Outcome:** `BUILD SUCCESSFUL` — **17 / 17 JUnit suites, 42 / 42 test methods PASSED (`0` failed, `0` errors, `0` skipped)** (`DEVELOPER_TESTED`).

### Suite-by-Suite Breakdown (Exact Run 3 JUnit XML Tally)

| Test Suite | Tests | Passed | Failed | Duration | Verified Scope |
|---|---|---|---|---|---|
| `CoreMathContractTest` | 5 | 5 | 0 | `0.160s` | 55+ AST math/calculus/pole-safe root (`PD-007`)/complex/matrix assertions, all 23 invertible unit categories, Lab `C1V1=C2V2`/`%` recovery/`%RSD`/Wet vs NIR, 36+ spreadsheet formulas (`CONT-006` `#REF!`, `CONT-007` composite & zero-length `LEFT`/`RIGHT`, `#DIV/0!`, `#CIRC!`). |
| `CoreDomainContractTest` | 3 | 3 | 0 | `0.120s` | Leap year Feb 29 policies, IANA DST spring-forward (`America/Los_Angeles`), RFC 5545 recurrence + EXDATE, chore recalculation, all 7 calendar overlap policies, task carryover & smart placement, 70-depth hierarchy & cycle rejection (`R-0233`), exact decimal money math, workspace ACL. |
| `CoreDocumentsContractTest` | 2 | 2 | 0 | `0.174s` | Blurless cross-record draft switch isolation (`PD-016`, `PF-55`), 100,000-word O(n) single-pass stats & chunked edit (`PD-010`), `[[WikiLink]]` extraction, line diffs. |
| `CoreGraphicsContractTest` | 2 | 2 | 0 | `0.149s` | 10,000-object `SpatialIndex2D` viewport cull & zoom-independent hit tests, SVG whiteboard export (`C-0066`), 50 US states, dynamic Left/Right side tabs (`R-0068`), 5,000 paginated planner bookmarks (`R-0072`). |
| `CoreSecurityContractTest` | 3 | 3 | 0 | `0.178s` | SSRF outbound URL policy (`PD-014`), SHA-256 + PBKDF2 + AES-256-GCM round-trip & wrong-key rejection, Ed25519 offline entitlement + data survival (`N-045`), Safe Learning Interpreter + breakpoint debugger (`PD-005`, `R-0325`). |
| `CoreInterchangeContractTest` | 3 | 3 | 0 | `0.057s` | RFC 4180 CSV quoted/multiline round-trip + formula neutralization (`PD-006`), RFC 5545 empty ICS envelope (`C-0060`) & RRULE/EXDATE/all-day round-trip, Presentation HTML fidelity export (`PD-018`), Book Markdown & HTML-DOC export (`C-0065`). |
| `ArchiveInterchangeJvmTest` | 2 | 2 | 0 | `0.241s` | Planaday Native v1 verified ZIP + AES-256-GCM encrypted backup (`PF-42`, `N-048`), tamper & wrong-key rejection, OOXML `.pptx` round-trip import with Fidelity Report (`R-0455`). |
| `AutomationContractTest` | 1 | 1 | 0 | `0.032s` | Dry-run preview, human approval gate, idempotent sync-replay dedup, cycle depth guard (`PF-34`, `N-035`). |
| `NotificationContractTest` | 1 | 1 | 0 | `0.045s` | Quiet hours deferral, snooze rollover, monotonic elapsed timer unaffected by wall-clock shifts (`PF-46`). |
| `DesktopShellTest` | 1 | 1 | 0 | `0.060s` | All 54 named routes (`C-0001`..`C-0054`), real Git SHA in `BuildIdentity` (rejects `"greenfield-dev"`), fresh empty DB (`O-004`), **live multi-window Room Flow synchronization**, full restart persistence of `deviceId`, theme/accent/fontScale preferences, and OS-vault-encrypted sync bearer token (with explicit proof of **zero plaintext bearer token in SQLite**). |
| `ComposeDesktopUiAndA11yTest` | 4 | 4 | 0 | `5.787s` | (1) Zero-jargon interactive desktop shell + Command Palette click (`0` `Canonical`/`FTS5`/`O-004` matches); (2) **Live 100,000-word note UI open & render in `380 ms`** (`100003 words`); (3) `320.dp` compact viewport layout & `PD-013` accessibility audit across **30 studios** (`0` unnamed clickable controls, `0` horizontal overflow); (4) **8 real Compose Skia PNG screenshots** captured and visually inspected in `docs/screenshots/*.png`. |
| `FreshStartAndSyncE2ETest` | 2 | 2 | 0 | `0.628s` | Empty Device A + Empty Device B + Empty Ktor Server; offline creation + PNG/JPEG/WAV attachments (`CONT-003`); `401`/`403` auth, single-use pairing code, cross-workspace blob `403`, SSRF `403`, SHA-256 mismatch `400` (`PD-015`), simulated `503` forcing `ERROR` (`CONT-004`); verified two-client sync (`PD-002`); `C-0059` idempotency; `C-0061` BuildIdentity; `CONT-009` server backup manifest; token revocation `401`; AES-256-GCM backup restore onto empty Replacement Server B (`PD-003`, `PF-42`). |
| `CrossFeatureIntegrationAndRegressionsTest` | 3 | 3 | 0 | `0.717s` | All **15 cross-feature integration journeys (`INT-001`..`INT-015`)**; **1,000 overlapping concurrent disjoint updates** (`PD-001`, exact `alpha=500, beta=500, rev=1001`); SQLite-backed blurless draft switch (`PD-016`); **security proof that unrestricted host OS execution throws `SecurityException` by default (Update 2 §23)** + offline diagnostics & portable project ZIP export. |
| `All88RecordTypesAndFullScopeTest` | 3 | 3 | 0 | `1.612s` | Persistence & two-device sync across all 98 `RecordKind`s (`C-0070`..`C-0158`), plus domain/interchange/graphics engine contract verification across Gantt, Kitchen, Finance, Wellness, Lab, Game Balance, Custom Schemas, `.xlsx`/`.docx`/`.epub`/Markdown+YAML, PDF models, Secure Share Bundles, 260 stickers, and photo recipes. *(Under Update 2 §5, generic persistence here no longer confers `DEVELOPER_TESTED` on specialized `R-*` UI features.)* |
| `ScaleSoakConflictAndStreamingTest` | 3 | 3 | 0 | `2.884s` | Multi-device optimistic sync conflict preservation (`N-042`, `resolveConflictKeepBoth`) + `SelectiveSyncPolicy` zero-deletion proof (`N-041`); **`100,000`-record Room KMP + Bundled SQLite FTS5 soak (`2,536 ms` ingest, `p50=0 ms`, `p95=1 ms`)**; **`64 MiB` streaming SHA-256 blob ingest (`62 ms`) & verification (`36 ms`)** + 1-byte corruption detection. |
| `PerformanceAndCapacityBenchmarkTest` | 1 | 1 | 0 | `0.375s` | Core engine benchmarks (`100k` word stats `7 ms`/edit `0 ms`, `10k` sparse sheet `33 ms`, `10k` canvas `18 ms`/`1 ms` hit tests, `64`-depth/`10k`-node tree `4 ms`, `5k` SQLite bookmarks `13 ms` insert/`2 ms` query). |
| `Update2ScalabilitySecurityAndFeaturesTest` | 3 | 3 | 0 | `7.734s` | (1) **Reactive Query-Isolation Proof (§6):** editing 1 task triggers `telemetry_after_task_edit={TASK=1}` and **0 reloads** of Notes, Expenses, Lab, Food, Chores, or other unrelated kinds; (2) **Progressive `10k → 100k → 250k` SQLite Scaling Benchmark:** routine task edit (`3 ms → 21 ms → 48 ms`), indexed calendar window (`1 ms → 0 ms → 0 ms`), keyset pagination (`1 ms → 1 ms → 1 ms`), FTS5 search (`1 ms → 1 ms → 0 ms`); (3) **TLS Transport Policy (`SyncTransportSecurityPolicy`) + Real Room KMP `MIGRATION_1_2` Drill** migrating an authentic `1.json` v1 SQLite database to Schema v2 with existing user records intact! |

---

### Real Visual Screenshots Captured & Inspected (`docs/screenshots/`)

Captured from the compiled Compose Multiplatform UI via `ImageComposeScene` (Skia) and visually audited for contrast, layout balance, zero jargon, and zero text overlap:
- `docs/screenshots/windows_desktop_today_light.png` (`79,376` B) — Desktop `1280×800` Warm Paper Light Today dashboard
- `docs/screenshots/windows_desktop_flow_calendar.png` (`85,928` B) — Desktop `1280×800` Interactive Flow Calendar with side-by-side non-overlapping events, layers, zoom & Tasks-to-Schedule sidebar
- `docs/screenshots/windows_desktop_spreadsheet_dark.png` (`75,539` B) — Desktop `1280×800` Deep Slate Dark Multi-Sheet Spreadsheet with live `=B2-C2` / `=SUM(...)` evaluation & crisp Formula Bar
- `docs/screenshots/windows_desktop_laboratory.png` (`66,426` B) — Desktop `1280×800` 15-Tab Laboratory Suite with non-LIMS disclaimer, active samples & Wet/NIR pair metrics
- `docs/screenshots/windows_desktop_notes_studio.png` (`68,876` B) — Desktop `1280×800` Master-Detail Notes Studio with formatting toolbar, Undo/Redo, and word/paragraph counter
- `docs/screenshots/windows_desktop_physical_planner.png` (`86,188` B) — Desktop `1280×800` Physical Digital Planner with spiral rings, dot grid, sticker, rotated sticky note & side tab
- `docs/screenshots/compact_phone_390dp_today.png` (`48,231` B) — Compact `390×844` phone Today view
- `docs/screenshots/compact_phone_390dp_flow_calendar.png` (`31,273` B) — Compact `390×844` phone Flow Calendar view

---

### Clean-SHA Native Builds, Packaging & CI Gate Verification (Commit `0d00bb7a936a`)

- **Native Windows MSI Installer (`:apps:desktop:packageMsi`, JDK 21 `jpackage` + WiX, `upgradeUuid = 9B7E4E52-3C8A-4D7E-A156-7F41C9E2B604`, zero Electron):** **PASS**
  - Artifact: `apps/desktop/build/compose/binaries/main/msi/Planaday Native-1.0.0.msi` (`74,817,847` bytes)
  - SHA-256: `C0907507012CDCD9152934D67F27E246132CE040AC1DA9AAE3068F6E78D9ADEE`
- **Native Windows Standalone Executable (`:apps:desktop:createDistributable`):** **PASS**
  - Artifact: `apps/desktop/build/compose/binaries/main/app/Planaday Native/Planaday Native.exe` (`560,640` bytes)
  - SHA-256: `6B1943EDB0B6B9267412A2F4CB005FFBDAF9D6C168C4AE809F0361972117D5`
- **Native Android Debug APK (`:apps:android:assembleDebug`, `compileSdk 36`, `targetSdk 36`, `minSdk 29`, `applicationId = "com.planaday.nativeapp"`, zero WebView/Capacitor):** **PASS**
  - Artifact: `apps/android/build/outputs/apk/debug/android-debug.apk` (`43,355,183` bytes)
  - SHA-256: `BDE58905C31C5BFC2E17356C834FDAD0243CAAF1BD6FF0B422C5075EA4F68BE0`
- **Standalone Ktor 3 Self-Hosted Sync Server Dist (`:server:app:installDist`):** **PASS**
  - Launcher: `server/app/build/install/app/bin/app.bat` (`5,353` bytes, SHA-256 `34E9E5234736CC05E59E9DED28FEEE905D1AEBE38CF40182A10B31EF28C28DB7`)
  - Server JAR: `server/app/build/install/app/lib/app-1.0.0-alpha.1.jar` (`128,732` bytes, SHA-256 `1BA2EA3CB1AD573D49AC4708B9451FCD4DF160D62940B7415C41A6E94F3CD660`)
- **Room KMP Schema Exports:** **PASS** — `1.json` (`38,180` bytes) AND `2.json` (`39,646` bytes) committed under `core/persistence/schemas/`.
- **Docker Compose Validation:** **PASS** (`docker compose --env-file deployment/.env.example -f deployment/docker-compose.yml config --quiet` exited `0`).
- **Greenfield & Traceability CI Gate (`py scripts/verify_greenfield_and_traceability.py`):** **PASS** (0 legacy duplicates, 0 O-004 violations, verbatim Rev 4.0 spec preserved, zero `"greenfield-dev"` placeholder, strict 10-status CSV validation).

### Environment-Blocked Checks (Explicitly NOT Claimed as Passed)

- **Live Docker Container Run (`docker compose up` + PostgreSQL 17 container restore drill):** Docker CLI `29.1.3` installed, but Docker Desktop Linux engine daemon is offline on this workstation.
- **Android Emulator & Physical Android 10 / 16 Hardware UI, Gboard IME (`C-0055`), TalkBack, Camera & Doze Exact-Alarm Reboot Checks:** No AVD emulator or USB Android device is attached in this session.
- **Authenticode-Signed Windows MSI Clean-VM Install/Upgrade/Uninstall & Interactive User Smoke Run:** Unsigned MSI and standalone EXE built cleanly; clean Windows VM install/upgrade/uninstall and Authenticode signing await owner signing certificate/VM.
- **Independent Reviewer Sign-Off:** Pending second-party review across all gates (`0` self-certified `INDEPENDENTLY_ACCEPTED`, `0` `PLATFORM_TESTED`).
