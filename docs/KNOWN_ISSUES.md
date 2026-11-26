# Planaday Native — Known Issues & Historical Regression Register (`KNOWN_ISSUES.md`)

**Rule:** All **28** historical audit issues (`PD-001`–`PD-020`, `CONT-001`, `CONT-003`–`CONT-009`) are permanently tracked as mandatory regression targets for `Planaday-Native`. Statuses strictly follow the 10-status taxonomy in [TRACEABILITY_DEFINITIONS.md](TRACEABILITY_DEFINITIONS.md) (`0` self-certified `INDEPENDENTLY_ACCEPTED`). Private LAN host details remain redacted (`[REDACTED_LAN_HOST]`).

---

## 1. Historical Issue Regression Matrix (28 / 28 Tracked)

| ID | Severity | Historical Defect | Greenfield Prevention & Evidence (Update 2) | Native Status |
|---|---|---|---|---|
| **PD-001** | P0 | Concurrent updates lose disjoint edits | Per-record lock + Room SQLite tx + optimistic `rev` (`1,000` concurrent updates verified → `alpha=500, beta=500, rev=1001`) + reactive query isolation (`telemetry={TASK=1}`). | `DEVELOPER_TESTED` |
| **PD-002** | P1 | Clean second client lacks synced attachment bytes | Bidirectional blob sync + client SHA-256 verification verified across 2 clean SQLite clients in `FreshStartAndSyncE2ETest`. | `DEVELOPER_TESTED` |
| **PD-003** | P1 | Restored client falsely reports synced against empty replacement server | Persistent `serverId` + `syncEpoch` + restore reconciliation mode verified against empty Replacement Server B in `FreshStartAndSyncE2ETest`. | `DEVELOPER_TESTED` |
| **PD-004** | P0 | Self-hosted server lacks application-layer ACL | Deny-by-default Bearer auth, PBKDF2, 10-min single-use pairing codes, workspace ACL, token revocation (`401`/`403`), OS credential vaults & HTTPS policy (`SyncTransportSecurityPolicy`). | `DEVELOPER_TESTED` |
| **PD-005** | P0 | Untrusted code runs without security boundary | Zero in-process `eval()`; `SafeLearningInterpreter` + breakpoint debugger verified; **Update 2 §23 disabled unrestricted host OS subprocess execution by default (`SecurityException`)**. | `DEVELOPER_TESTED` |
| **PD-006** | P1 | Quoted CSV round-trip corrupts cell boundaries | Full RFC 4180 state-machine `Rfc4180CsvCodec` + formula neutralization verified in `CoreInterchangeContractTest` and wired to Spreadsheet & Lab CSV export. | `DEVELOPER_TESTED` |
| **PD-007** | P1 | Root finder mistakes pole for root & misses tangency | Continuity pole rejection (`1/(x-0.123) -> []`) & golden-section tangent root (`(x-0.123)^2 -> [0.123]`) verified in `CoreMathContractTest`. | `DEVELOPER_TESTED` |
| **PD-008** | P1 | Export callers swap filename and content | Typed `ExportArtifact(filename, mimeType, bytes)` wired to native desktop file save dialogs (`JvmNativeFileDialogBridge`) across CSV, ICS, HTML deck, PPTX, XLSX, DOCX, EPUB, Markdown, SVG, and Backup ZIP. | `DEVELOPER_TESTED` |
| **PD-009** | P1 | Lazy chunks unavailable offline on unvisited routes | Native APK (`43.4 MB`) & Windows MSI (`74.8 MB`) bundle all 54 modules offline with zero web chunks. | `DEVELOPER_TESTED` |
| **PD-010** | P1 | Large multiline note sluggish / timed out at 50k–100k words | Chunked `LargeDocumentBuffer` + O(n) single-pass stats (`7 ms` stats, `0 ms` edit) **AND live 100,000-word note Compose UI open & render verified in `380 ms`** (`ComposeDesktopUiAndA11yTest`). | `DEVELOPER_TESTED` |
| **PD-011** | P2 | File Hub horizontally overflows on 320dp phone | Verified in `ComposeDesktopUiAndA11yTest` (`UNIVERSAL_FILES` rendered within `320.dp` with zero clip). | `DEVELOPER_TESTED` |
| **PD-012** | P2 | Projects & Records overflows on 320dp phone | Verified in `ComposeDesktopUiAndA11yTest` (`PROJECTS` rendered within `320.dp` with zero clip). | `DEVELOPER_TESTED` |
| **PD-013** | P2 | Planner, Diary & Calendar lack accessible controls | Automated semantics tree audit in `ComposeDesktopUiAndA11yTest` verified **0 unnamed clickable controls** across `DesktopShellRoot` and **30 studios** (TalkBack physical pass pending Android device). | `DEVELOPER_TESTED` |
| **PD-014** | P1 | Remote integrations lack SSRF outbound policy | Centralized `OutboundUrlPolicy` + server `/api/v1/proxy/check` verified blocking loopback, RFC1918, metadata (`169.254.169.254`), CGNAT, and plain HTTP (`403`). | `DEVELOPER_TESTED` |
| **PD-015** | P1 | Server blob upload skips SHA-256 verification & staging | `ServerBlobStore.storeStagedVerified()` verified rejecting spoofed digest (`400`) + atomic `.tmp` rename; client 64 MiB streaming SHA-256 ingest (`62 ms`) & corruption detection verified. | `DEVELOPER_TESTED` |
| **PD-016** | P0 | Blurless note switch corrupts next selected note | `DraftSessionManager` strictly bound by `(workspaceId, recordId, sessionId)` + SQLite `drafts` verified in `CoreDocumentsContractTest` & `CrossFeatureIntegrationAndRegressionsTest` and wired to Notes Studio. | `DEVELOPER_TESTED` |
| **PD-017** | P2 | Release metadata & CI gates inconsistent | **Update 2 §36:** `generatePlanadayBuildMeta` Gradle task injects live Git commit SHA (`0d00bb7a936a`, `dirty=false`) & UTC timestamp into `BuildIdentity` (`1.0.0-alpha.2`, Schema `v2`); CI gate rejects `"greenfield-dev"`. | `DEVELOPER_TESTED` |
| **PD-018** | P2 | Presentation HTML export loses images/tables/transforms | `RichDocumentExporters.exportPresentationHtmlDeck` verified preserving `4:3`, `data-transition`, inline base64 PNG, `rotate(15deg)`, `<table>`, `<code>`, speaker notes; wired to `RealPresentationsStudio`. | `DEVELOPER_TESTED` |
| **PD-019** | P2 | Photo Studio overflows 320dp phone | Verified in `ComposeDesktopUiAndA11yTest` (`PHOTO_STUDIO` rendered within `320.dp` with zero clip). | `DEVELOPER_TESTED` |
| **PD-020** | P1 | EOL Electron packaging vulnerability | Electron completely eliminated; real native Windows MSI (`Planaday Native-1.0.0.msi`, `74.8 MB`) & standalone EXE built via JDK 21 `jpackage` + WiX; enforced by CI gate. | `DEVELOPER_TESTED` |
| **CONT-001** | P1 | Live production deployment diverged from audited source (`[REDACTED_LAN_HOST]`) | Compiled real-SHA `BuildIdentity` verified on `/api/v1/health`; live host intentionally untouched without owner authorization. | `DEVELOPER_TESTED` (source) / `BLOCKED` (live owner host) |
| **CONT-003** | P1 | PNG/JPEG/WAV blob PUT returned HTTP 415 | Live Ktor blob route verified accepting `image/png`, `image/jpeg`, `audio/wav` (`200`) with exact byte round-trip. | `DEVELOPER_TESTED` |
| **CONT-004** | P1 | Failed blob transfer falsely reported `synced` with 0 pending | Simulated HTTP `503` verified forcing `SyncHealthStatus.ERROR`, `failedBlobTransfers >= 1`, `totalPendingCount >= 1`. | `DEVELOPER_TESTED` |
| **CONT-005** | P1 | Android 10 WebView 74 syntax error | WebView completely eliminated; native Android Jetpack Compose APK (`minSdk 29`, `targetSdk 36`, `43.4 MB`) built cleanly; physical Android 10 device boot pending hardware. | `BLOCKED` (device) / `DEVELOPER_TESTED` (build) |
| **CONT-006** | P1 | Missing-sheet formula returned active sheet data | `SpreadsheetFormulaEngine` verified returning `#REF!` for `=Missing!A1+1` in `CoreMathContractTest`. | `DEVELOPER_TESTED` |
| **CONT-007** | P2 | Composite formulas & zero-length `LEFT`/`RIGHT` failed | `SpreadsheetFormulaEngine` verified `=SUM(A1:A3)+1 -> 13`, `LEFT("abc",0) -> ""`, `RIGHT("abc",0) -> ""`. | `DEVELOPER_TESTED` |
| **CONT-008** | P1 | Startup blocked indefinitely awaiting storage permission | Non-blocking local SQLite startup verified cleanly (`DesktopShellTest`, `ComposeDesktopUiAndA11yTest`, `FreshStartAndSyncE2ETest`). | `DEVELOPER_TESTED` |
| **CONT-009** | P1 | Unverified server backup coverage | Server `/api/v1/admin/backup` SHA-256 manifest + `deployment/backup-restore.sh` verified in test; live TrueNAS host untouched by policy. | `DEVELOPER_TESTED` (source) / `BLOCKED` (live owner host) |

---

## 2. Update 1 Audit Findings Repaired in Update 2

1. **Global `refreshWorkspace()` Fan-Out Eliminated (§6):** Removed global multi-collection refresh and unbounded `listByKind()`; replaced with route-scoped Room `Flow`s, Schema v2 keyset/date-range indexes, and verified `{TASK=1}` query isolation + flat `250k`-record scaling.
2. **Hardcoded Demo Stubs Replaced with Real Persisted Studios (§4, §8–§26):** Replaced 4-cell spreadsheet demo, simulated-time button, hardcoded Book/Game/Photo/Project/Automation examples, and plain-text calendar/planner/presentation views with interactive SQLite-persisted commercial studios.
3. **Engineering Jargon Purged from Customer UI (§7):** Removed `"Canonical"`, `"FTS5"`, `"O-004"`, and internal tags from primary customer views; moved technical diagnostics cleanly to Settings → Diagnostics.
4. **Placeholder `"greenfield-dev"` Eliminated (§36):** Replaced with live Gradle-generated Git commit SHA (`0d00bb7a936a`).
5. **Unrestricted Host OS Code Execution Disabled & Credentials Vaulted (§23, §28):** Disabled host subprocess execution by default (`SecurityException`); added NTFS-ACL / `AndroidKeyStore` AES-256-GCM vaults and HTTPS sync transport policy.
6. **Overstated Update 1 Traceability Downgraded (§5, §33):** Downgraded **123 rows** (`74` requirements, `24` PF packages, `25` N-series proposals) from Update 1's `DEVELOPER_TESTED` to `FUNCTIONALLY_IMPLEMENTED` / `UI_IMPLEMENTED` / `ENGINE_IMPLEMENTED` / `BLOCKED` / `SPECIFIED` under the strict 20-point rubric.

---

## 3. Remaining Open Gaps & Environment Blockers

1. **Docker Desktop Daemon Offline (`C-0056`, `C-0057`, `PF-42` live container):** `docker compose config --quiet` passes, but live PostgreSQL 17 container execution & restore drill require starting Docker Desktop.
2. **No Physical Android Device or AVD Emulator (`C-0055`, `PF-37`, `PF-53`, `N-010`, `N-015`, `N-023`, `R-0095`..`R-0111`):** Debug APK (`43.4 MB`) builds cleanly; physical Android 10/16 verification (Gboard IME, TalkBack, Doze exact-alarm reboot recovery, CameraX/barcode scan, Glance AppWidget) remains `BLOCKED`.
3. **Unsigned Windows MSI & Clean-VM Install/Uninstall:** Clean-SHA MSI (`74.8 MB`) and standalone EXE built cleanly; Authenticode signing and clean Windows VM install/upgrade/uninstall smoke test await owner signing key/VM.
4. **Remaining `49` `SPECIFIED` & `23` `UI_IMPLEMENTED` Granular Requirements:** Sidebar tab grouping/reordering, drag-resizable dashboard widgets, automatic schedule-based workspace switching, configurable favorite limits, stylus pressure brush dynamics, native PDF rasterizer/OCR UI, spreadsheet auto-filters/freeze-panes, and per-studio E2E UI test coverage for all `265` `FUNCTIONALLY_IMPLEMENTED` requirements.
5. **Independent Second-Party Review:** Required before moving any item to `INDEPENDENTLY_ACCEPTED`.
