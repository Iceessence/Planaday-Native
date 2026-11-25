# Planaday Native — Known Issues & Historical Regression Register (`KNOWN_ISSUES.md`)

**Rule:** All **28** historical audit issues (`PD-001`–`PD-020`, `CONT-001`, `CONT-003`–`CONT-009`) are permanently tracked as mandatory regression targets for the new greenfield native architecture. Historical developer-verified claims do **NOT** transfer — each issue remains `REGRESSION_PENDING` until verified by freshly authored Kotlin tests in `Planaday-Native` and independently reviewed. Private LAN host addresses from historical records are intentionally redacted (`[REDACTED_LAN_HOST]`).

---

## 1. Historical Issue Regression Matrix (28 / 28 Tracked)

| ID | Severity | Historical Defect | Greenfield Architectural Prevention | Native Regression Test | Native Status |
|---|---|---|---|---|---|
| **PD-001** | P0 | Concurrent updates lose disjoint edits | Room SQLite write transaction + optimistic `rev` check + per-record lock + atomic outbox | `ConcurrencyAndDraftRegressionTest#pd001ConcurrentUpdates` | `PENDING_EXECUTION` |
| **PD-002** | P1 | Clean second client lacks synced attachment bytes | Bidirectional recursive blob ref extraction + verified SHA-256 download in `SyncEngine` | `SyncAndBlobRegressionTest#pd002TwoClientBlobDelivery` | `PENDING_EXECUTION` |
| **PD-003** | P1 | Restored client falsely reports synced against empty replacement server | Persistent `serverId` + `syncEpoch` binding + backup-restore reconciliation mode | `SyncAndBlobRegressionTest#pd003ReplacementServerReconcile` | `PENDING_EXECUTION` |
| **PD-004** | P0 | Self-hosted server lacks application-layer ACL | Deny-by-default Bearer auth, PBKDF2 owner auth, 10-min pairing codes, workspace ACL | `SyncServerSecurityTest#pd004AuthAndWorkspaceAcl` | `PENDING_EXECUTION` |
| **PD-005** | P0 | Untrusted code runs without security boundary | Zero in-process `eval`; isolated child process (desktop) / safe interpreter (Android) | `CodeStudioSecurityTest#pd005IsolatedExecution` | `PENDING_EXECUTION` |
| **PD-006** | P1 | Quoted CSV round-trip corrupts cell boundaries | Full RFC 4180 state-machine CSV parser/writer + formula neutralization | `InterchangeContractTest#pd006Rfc4180CsvRoundTrip` | `PENDING_EXECUTION` |
| **PD-007** | P1 | Root finder mistakes pole for root & misses tangency | Continuity/residual pole filter + golden-section minimum tangent root finder | `MathEngineContractTest#pd007PoleRejectionAndTangentRoot` | `PENDING_EXECUTION` |
| **PD-008** | P1 | Export callers swap filename and content | Typed `ExportArtifact(filename, mimeType, bytes)` value object + filename sanitizer | `InterchangeContractTest#pd008TypedExportArtifact` | `PENDING_EXECUTION` |
| **PD-009** | P1 | Lazy chunks unavailable offline on unvisited routes | Native APK/Desktop binary bundles all modules locally — 100% offline cold-boot | `OfflineNativeBootTest#pd009AllRoutesWorkOffline` | `PENDING_EXECUTION` |
| **PD-010** | P1 | Large multiline note sluggish / times out at 50k–100k words | Chunked document store + O(n) single-pass deferred stats + virtualized rendering | `PerformanceBenchmarkTest#pd010HundredThousandWordNote` | `PENDING_EXECUTION` |
| **PD-011** | P2 | File Hub horizontally overflows on 320dp phone | Responsive Compose `BoxWithConstraints` stacking cleanly at `320dp` | `ResponsiveUiTest#pd011FileHub320dp` | `PENDING_EXECUTION` |
| **PD-012** | P2 | Projects & Records overflows on 320dp phone | Adaptive single-column layout below `600dp` width | `ResponsiveUiTest#pd012Projects320dp` | `PENDING_EXECUTION` |
| **PD-013** | P2 | Planner, Diary & Calendar lack accessible controls | Mandatory `contentDescription` + `48dp` Android touch targets across all screens | `AccessibilityUiTest#pd013AllControlsLabeled` | `PENDING_EXECUTION` |
| **PD-014** | P1 | Remote integrations lack SSRF outbound policy | Centralized `OutboundUrlPolicy` blocking loopback/RFC1918/metadata/CGNAT/redirects | `SecurityPolicyTest#pd014SsrfOutboundGuard` | `PENDING_EXECUTION` |
| **PD-015** | P1 | Server blob upload skips SHA-256 verification & staging | Independent server SHA-256 verification + atomic `.tmp` staging rename | `SyncServerSecurityTest#pd015ServerBlobSha256Staging` | `PENDING_EXECUTION` |
| **PD-016** | P0 | Blurless note switch corrupts next selected note | `DraftSessionManager` strictly bound by `(workspaceId, recordId, sessionId)` + SQLite `drafts` | `ConcurrencyAndDraftRegressionTest#pd016BlurlessDraftIsolation` | `PENDING_EXECUTION` |
| **PD-017** | P2 | Release metadata & CI gates inconsistent | Single compiled `BuildIdentity` + CI greenfield/traceability/test gate | `ReleaseIdentityGateTest#pd017ConsistentBuildIdentity` | `PENDING_EXECUTION` |
| **PD-018** | P2 | Presentation HTML export loses images/tables/transforms | Pure `PresentationHtmlExporter` preserving aspect ratio, base64 images, tables, code, transforms, speaker notes | `InterchangeContractTest#pd018PresentationHtmlFidelity` | `PENDING_EXECUTION` |
| **PD-019** | P2 | Photo Studio overflows 320dp phone | Adaptive Compose grid (`GridCells.Adaptive`) with zero horizontal overflow at `320dp` | `ResponsiveUiTest#pd019PhotoStudio320dp` | `PENDING_EXECUTION` |
| **PD-020** | P1 | EOL Electron packaging vulnerability | Electron completely retired; native JVM Compose Desktop + jpackage MSI/EXE | `GreenfieldCheck#pd020NoElectronRuntime` | `PENDING_EXECUTION` |
| **CONT-001** | P1 | Live production deployment diverged from audited source (`[REDACTED_LAN_HOST]`) | Single `BuildIdentity` surfaced on `/api/v1/health` + immutable digest Docker Compose; live host untouched | `ReleaseIdentityGateTest#cont001HealthIdentity` | `PENDING_EXECUTION` (live host untouched by policy) |
| **CONT-003** | P1 | PNG/JPEG/WAV blob PUT returned HTTP 415 | Ktor streaming binary blob route accepts all declared ordinary media types cleanly | `SyncAndBlobRegressionTest#cont003OrdinaryMediaMimeTypes` | `PENDING_EXECUTION` |
| **CONT-004** | P1 | Failed blob transfer falsely reported `synced` with 0 pending | Explicit `BlobTransferReceipt` state machine forces `SyncStatus.ERROR` & `pendingCount >= failedCount` | `SyncAndBlobRegressionTest#cont004FailedBlobNeverReportsSynced` | `PENDING_EXECUTION` |
| **CONT-005** | P1 | Android 10 WebView 74 syntax error | WebView completely retired; native Android Jetpack Compose (`minSdk = 29`, `targetSdk = 36`) | `AndroidBuildGate#cont005NativeMinSdk29Build` | `PENDING_EXECUTION` |
| **CONT-006** | P1 | Missing-sheet formula returned active sheet data | Explicit `SheetRefResolver` returns `#REF!` error value propagated through formula DAG | `SpreadsheetEngineContractTest#cont006MissingSheetRefError` | `PENDING_EXECUTION` |
| **CONT-007** | P2 | Composite formulas & zero-length `LEFT`/`RIGHT` failed | Full recursive AST spreadsheet parser supporting `=SUM(A1:A3)+1` and `LEFT("abc",0)==""` | `SpreadsheetEngineContractTest#cont007CompositeAndZeroLength` | `PENDING_EXECUTION` |
| **CONT-008** | P1 | Startup blocked indefinitely awaiting storage permission | Non-blocking async initialization; local SQLite opens immediately with bounded timeout | `StartupReliabilityTest#cont008NonBlockingStartup` | `PENDING_EXECUTION` |
| **CONT-009** | P1 | Unverified server backup coverage | Server `/api/v1/admin/backup` + `deployment/backup-restore.sh` verified with SHA-256 manifest; live TrueNAS untouched | `BackupRecoveryTest#cont009VerifiedServerBackupManifest` | `PENDING_EXECUTION` (live host untouched by policy) |

---

## 2. Active Environment & Process Blockers

- **Docker Daemon Offline Locally:** Docker CLI (`29.1.3`) is installed on this workstation, but the Docker Desktop Linux engine daemon is not currently started; live container execution (`docker compose up`) is therefore marked `ENVIRONMENT_BLOCKED_LOCAL` until Docker Desktop is running (while `docker compose config` syntax validation and full live Ktor HTTP server tests run directly on JVM!).
- **Physical Android Hardware:** No physical USB Android device attached in this session; Android APK builds cleanly against SDK API 36, and JVM/Compose/unit tests run locally, while physical Android 10 & Android 16 hardware tests remain required prior to commercial Play release.
- **Independent Reviewer Sign-Off:** All work completed in this session is `DEVELOPER_TESTED` pending independent second-party review.
