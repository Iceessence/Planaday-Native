# Planaday Native — Release & Fresh-Start Acceptance Gates (`RELEASE_GATES.md`)

No commercial release is permitted until every gate below achieves `INDEPENDENTLY_ACCEPTED` from a reviewer separate from the authoring engineer, with zero open P0/P1 defects. All checkboxes below remain unchecked until independent verification completes; current developer evidence is listed transparently alongside each gate.

## Mandatory Gates

- [ ] **Gate 0 — Greenfield Provenance & Fresh-Start Gate (Owner Decision O-004):**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Empty root commit `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`, CI gate `PASS` (`126` files, `0` legacy duplicates, `0` O-004 violations), empty DB boot + offline create → pair → sync verified in `FreshStartAndSyncE2ETest` & `DesktopShellTest`. *Pending:* Independent review.
- [ ] **Gate A — Data Integrity & Durability:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Atomic Room SQLite tx (`1,000` concurrent disjoint edits `PD-001`), record-bound drafts (`PD-016`), 30-day soft trash + restore across all 98 `RecordKind`s (`All88RecordTypesAndFullScopeTest`), immutable lab calibration/custody/evidence revisions, future `v1 → v2` additive SQLite migration drill. *Pending:* Independent review.
- [ ] **Gate B — Security & Privacy:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Deny-by-default Bearer auth + PBKDF2 + 10-min single-use pairing codes + per-workspace record/blob ACL (`PD-004`), SHA-256 staged blobs (`PD-015`), SSRF outbound guard (`PD-014`), isolated code execution (`PD-005`), ZIP/CSV/XXE import guards, AES-256-GCM encrypted backup & Secure Share Bundle (`N-046`, `N-048`), declarative extension capability validator (`N-038`). *Pending:* Independent security audit.
- [ ] **Gate C — Backup & Restorability:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Versioned `planaday-native-backup-v1` ZIP + SHA-256 manifest, encrypted restore drill, tamper/wrong-key rejection, replacement-server reconciliation (`PD-003`, `PF-42`), server `/api/v1/admin/backup` manifest (`CONT-009`). *Environment-Blocked:* Live Docker PostgreSQL 17 container restore drill (Docker daemon offline).
- [ ] **Gate D — Synchronization:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Two-client offline → reconnect sync across all 98 `RecordKind`s, PNG/JPEG/WAV blobs (`CONT-003`), bidirectional verified blob delivery (`PD-002`), simulated `503` explicit error receipt (`CONT-004`), optimistic multi-device conflict preservation + `resolveConflictKeepBoth` (`N-042`), `SelectiveSyncPolicy` zero-deletion proof (`N-041`). *Pending:* Live PostgreSQL soak & independent review.
- [ ] **Gate E — Native Platform Builds & Compatibility:**
  - *Developer Evidence (`DEVELOPER_TESTED_BUILD`):* Android API 36 Debug APK (`minSdk 29`, `41,244,597` B), Native Windows MSI (`Planaday Native-1.0.0.msi`, `72,393,015` B), Standalone Windows EXE (`Planaday Native.exe`, `560,640` B), Standalone Ktor Server dist (`installDist`). *Environment-Blocked:* Physical Android 10 & 16 device verification, signed Windows MSI clean-VM install/uninstall.
- [ ] **Gate F — UX, Accessibility & Responsive Design:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Headless Compose UI `320.dp` width & semantics accessibility audit across `DesktopShellRoot` and **24 studios** (`0` unnamed clickable controls `PD-013`, `0` horizontal overflow `PD-011`/`PD-012`/`PD-019`), 4 themes incl. High Contrast AAA, `48dp` touch targets. *Environment-Blocked:* Android TalkBack, physical Gboard IME (`C-0055`), RTL visual pass.
- [ ] **Gate G — Mathematical, Lab & File Interchange Correctness:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* AST math/calculus/pole-safe roots (`PD-007`), all 23 unit categories, Lab Wet vs. NIR + Dilution/%Recovery/%RSD/GUM Uncertainty (`LA-001`..`LA-002`, `PF-56`), sparse spreadsheet DAG (`CONT-006`, `CONT-007`), RFC 4180 CSV (`PD-006`), RFC 5545 ICS (`C-0060`), OOXML `.xlsx` & `.pptx` round-trip (`R-0455`, `PF-50`), `.docx`/`.epub`/Markdown+YAML (`N-018`, `N-019`), HTML deck fidelity (`PD-018`), fidelity previews (`PF-54`). *Pending:* Independent reference validation.
- [ ] **Gate H — Performance & Capacity Budgets:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* `100,000`-word note stats `6 ms` / edit `0 ms`; **`100,000`-record FTS5 soak `2,269 ms` ingest / `p50=0 ms, p95=1 ms` search**; **`64 MiB` streaming SHA-256 blob `62 ms` ingest / `38 ms` verify**; `10k` sparse sheet `30 ms`; `10k` canvas `19 ms` / `0 ms` hit tests; `5k` SQLite bookmarks `2 ms`; `64`-depth/`10k`-node tree `4 ms`. *Environment-Blocked:* Mid-range Android device profiling.
- [ ] **Gate I — Complete Full-Scope Traceability:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* `652/652` tracked (`368` `DEVELOPER_TESTED` incl. all 54 routes `C-0001`..`C-0054` & all 88 record families `C-0070`..`C-0158`, `283` `SPECIFIED`, `1` `NOT_APPLICABLE_APPROVED`), **`54/56` PF `DEVELOPER_TESTED`** (`2` `IMPLEMENTED`), **`48/50` N-series `DEVELOPER_TESTED`** (`2` `IMPLEMENTED`), **`15/15` `INT-001`..`INT-015` `DEVELOPER_TESTED`**. *Pending:* Closing remaining tactile/hardware requirements & independent review.
- [ ] **Gate J — Release Identity, Licensing & Commercial Policy:**
  - *Developer Evidence (`DEVELOPER_TESTED`):* Unified `BuildIdentity` (`1.0.0-alpha.1`, protocol v1, `C-0061`), Ed25519 offline desktop entitlement + read/export survival (`N-045`), fresh MSI `upgradeUuid`, Play non-executable learning interpreter (`PD-005`). *Pending:* Commercial Play Store / Authenticode signing & owner release authorization.
