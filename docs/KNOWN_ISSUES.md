# Planaday Native — Known Issues & Historical Regression Register (`KNOWN_ISSUES.md`)

**Rule:** All **28** historical audit issues (`PD-001`–`PD-020`, `CONT-001`, `CONT-003`–`CONT-009`) are permanently tracked as mandatory regression targets for the new greenfield native architecture. Statuses strictly distinguish `DEVELOPER_TESTED` (backed by executed tests in `Planaday-Native`) from `PENDING_INDEPENDENT_REVIEW` and `ENVIRONMENT_BLOCKED`. Private LAN host details remain redacted (`[REDACTED_LAN_HOST]`).

---

## 1. Historical Issue Regression Matrix (28 / 28 Tracked)

| ID | Severity | Historical Defect | Greenfield Prevention & Evidence | Native Status |
|---|---|---|---|---|
| **PD-001** | P0 | Concurrent updates lose disjoint edits | Per-record lock + Room SQLite tx + optimistic `rev` (`1,000` concurrent updates verified -> `alpha=500, beta=500, rev=1001`, rollback verified). | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-002** | P1 | Clean second client lacks synced attachment bytes | Bidirectional blob sync + client SHA-256 verification verified across 2 clean SQLite clients in `FreshStartAndSyncE2ETest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-003** | P1 | Restored client falsely reports synced against empty replacement server | Persistent `serverId` + `syncEpoch` + restore reconciliation mode verified against empty Replacement Server B in `FreshStartAndSyncE2ETest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-004** | P0 | Self-hosted server lacks application-layer ACL | Deny-by-default Bearer auth, PBKDF2, 10-min single-use pairing codes, workspace ACL & token revocation verified (`401`/`403`) in `FreshStartAndSyncE2ETest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-005** | P0 | Untrusted code runs without security boundary | Zero in-process `eval()`; `SafeLearningInterpreter` + Desktop isolated child process runner (`DesktopExternalToolchainAdapter`) verified. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-006** | P1 | Quoted CSV round-trip corrupts cell boundaries | Full RFC 4180 state-machine `Rfc4180CsvCodec` + formula neutralization verified in `CoreInterchangeContractTest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-007** | P1 | Root finder mistakes pole for root & misses tangency | Continuity pole rejection (`1/(x-0.123) -> []`) & golden-section tangent root (`(x-0.123)^2 -> [0.123]`) verified in `CoreMathContractTest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-008** | P1 | Export callers swap filename and content | Typed `ExportArtifact(filename, mimeType, bytes)` verified across CSV, ICS, HTML deck, PPTX, Markdown, DOC, and Backup ZIP exporters. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-009** | P1 | Lazy chunks unavailable offline on unvisited routes | Native APK & JVM Desktop bundles all 54 modules offline with zero web chunks (`apps:android:assembleDebug` + `DesktopShellTest`). | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-010** | P1 | Large multiline note sluggish / timed out at 50k–100k words | Chunked `LargeDocumentBuffer` + O(n) single-pass stats measured at `8 ms` stats & `<1 ms` edit for `100,000` words. | `DEVELOPER_TESTED` (Android IME benchmark pending device) |
| **PD-011** | P2 | File Hub horizontally overflows on 320dp phone | Responsive single-column Compose layouts in `PlanadayWorkspaceViews.kt` + `AndroidAdaptiveShell`. | `IMPLEMENTED` (320dp emulator/device visual pass pending) |
| **PD-012** | P2 | Projects & Records overflows on 320dp phone | Adaptive Compose layout below `600dp` width in `AndroidAdaptiveShell`. | `IMPLEMENTED` (320dp emulator/device visual pass pending) |
| **PD-013** | P2 | Planner, Diary & Calendar lack accessible controls | Explicit `contentDescription` semantics & `48dp` minimum touch targets across shared & Android controls. | `IMPLEMENTED` (TalkBack / Compose UI accessibility test pending) |
| **PD-014** | P1 | Remote integrations lack SSRF outbound policy | Centralized `OutboundUrlPolicy` + server `/api/v1/proxy/check` verified blocking loopback, RFC1918, metadata (`169.254.169.254`), CGNAT, and plain HTTP (`403`). | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-015** | P1 | Server blob upload skips SHA-256 verification & staging | `ServerBlobStore.storeStagedVerified()` verified rejecting spoofed digest with HTTP `400` + atomic `.tmp` rename. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-016** | P0 | Blurless note switch corrupts next selected note | `DraftSessionManager` strictly bound by `(workspaceId, recordId, sessionId)` + SQLite `drafts` verified in `CoreDocumentsContractTest` & `CrossFeatureIntegrationAndRegressionsTest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-017** | P2 | Release metadata & CI gates inconsistent | Unified `BuildIdentity` (`1.0.0-alpha.1`, protocol v1) + CI workflow `.github/workflows/ci.yml` + `verify_greenfield_and_traceability.py`. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-018** | P2 | Presentation HTML export loses images/tables/transforms | `RichDocumentExporters.exportPresentationHtmlDeck` verified preserving `4:3`, `data-transition`, inline base64 PNG, `rotate(15deg)`, `<table>`, `<code>`, speaker notes. | `DEVELOPER_TESTED` (Pending independent review) |
| **PD-019** | P2 | Photo Studio overflows 320dp phone | Adaptive Compose single-column compact layout in `PlanadayWorkspaceViews.kt`. | `IMPLEMENTED` (320dp emulator/device visual pass pending) |
| **PD-020** | P1 | EOL Electron packaging vulnerability | Electron completely eliminated; native JVM Compose Desktop + Windows MSI/EXE jpackage configured; enforced by CI greenfield gate. | `DEVELOPER_TESTED` (Clean Windows VM MSI smoke test pending) |
| **CONT-001** | P1 | Live production deployment diverged from audited source (`[REDACTED_LAN_HOST]`) | Compiled `BuildIdentity` verified on `/api/v1/health`; live host intentionally untouched without owner authorization. | `DEVELOPER_TESTED_SOURCE / BLOCKED_OWNER_ACTION_LIVE` |
| **CONT-003** | P1 | PNG/JPEG/WAV blob PUT returned HTTP 415 | Live Ktor blob route verified accepting `image/png`, `image/jpeg`, `audio/wav` with HTTP `200` and exact byte round-trip. | `DEVELOPER_TESTED` (Pending independent review) |
| **CONT-004** | P1 | Failed blob transfer falsely reported `synced` with 0 pending | Simulated HTTP `503` verified forcing `SyncHealthStatus.ERROR`, `failedBlobTransfers >= 1`, `totalPendingCount >= 1`. | `DEVELOPER_TESTED` (Pending independent review) |
| **CONT-005** | P1 | Android 10 WebView 74 syntax error | WebView completely eliminated; native Android Jetpack Compose APK (`minSdk 29`, `targetSdk 36`) compiled cleanly. | `DEVELOPER_TESTED_BUILD` (Physical Android 10 boot pending) |
| **CONT-006** | P1 | Missing-sheet formula returned active sheet data | `SpreadsheetFormulaEngine` verified returning `#REF!` for `=Missing!A1+1` in `CoreMathContractTest`. | `DEVELOPER_TESTED` (Pending independent review) |
| **CONT-007** | P2 | Composite formulas & zero-length `LEFT`/`RIGHT` failed | `SpreadsheetFormulaEngine` verified `=SUM(A1:A3)+1 -> 13`, `LEFT("abc",0) -> ""`, `RIGHT("abc",0) -> ""`. | `DEVELOPER_TESTED` (Pending independent review) |
| **CONT-008** | P1 | Startup blocked indefinitely awaiting storage permission | Non-blocking local SQLite startup verified cleanly (`DesktopShellTest`, `FreshStartAndSyncE2ETest`). | `DEVELOPER_TESTED` (Pending independent review) |
| **CONT-009** | P1 | Unverified server backup coverage | Server `/api/v1/admin/backup` SHA-256 manifest + `deployment/backup-restore.sh` verified in test; live TrueNAS host untouched by policy. | `DEVELOPER_TESTED_SOURCE / BLOCKED_OWNER_ACTION_LIVE` |

---

## 2. Active Blockers & Remaining Risks

1. **Docker Daemon Offline Locally:** `docker compose -f deployment/docker-compose.yml config` passed, but live PostgreSQL 17 container execution & restore drill requires starting Docker Desktop (not started automatically).
2. **Android Emulator & Physical Hardware:** Debug APK (`android-debug.apk`, API 36, `minSdk 29`) builds cleanly, but interactive emulator / physical Android 10 & Android 16 testing (IME composition, 320dp visual pixel audit, Doze alarm reboot recovery, camera/photo picker) remains pending device availability.
3. **Windows Packaged MSI/EXE & Commercial Signing:** Compose `nativeDistributions` is configured for Windows MSI/EXE with a fresh `upgradeUuid`, but clean Windows VM install/upgrade/uninstall testing and Authenticode signing require owner-authorized keys/runner.
4. **Full Vertical UI Depth Across Remaining Secondary Modules:** All 54 routes (`C-0001`..`C-0054`) are wired to real Room SQLite persistence and FTS5 search, with dedicated rich screens for Today, Flow/Task Calendar, Tasks/Kanban, Chores, Notes/Drafts, Laboratory 15-Tab Suite + Wet/NIR, Calculator/23 Units, Spreadsheets, Physical Planner, Code Studio Debugger, Trash, and Settings/Sync/Backup; remaining specialized studios (Game Dev visual skill tree, Book manuscript multi-pane editor, Whiteboard interactive touch drawing, Presentation visual slide canvas, Kitchen recipe clipper/pantry FEFO UI, Finance envelope charts, full 15 individual Lab forms) need dedicated rich UI slices atop the already-built shared core engines.
5. **Independent Second-Party Review:** Required before changing any gate or requirement from `DEVELOPER_TESTED` to `INDEPENDENTLY_ACCEPTED`.
