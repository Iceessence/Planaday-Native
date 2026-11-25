# Planaday Native — Release & Fresh-Start Acceptance Gates (`RELEASE_GATES.md`)

No commercial release is permitted until every gate below achieves `INDEPENDENTLY_ACCEPTED` from a reviewer separate from the authoring engineer, with zero open P0/P1 defects.

## Mandatory Gates

- [ ] **Gate 0 — Greenfield Provenance & Fresh-Start Gate (Owner Decision O-004):**
  - Empty initial commit `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`, zero legacy files/hashes/runtime deps, fresh independent package/DB/volume identities, empty DB initialization on Android/Windows/Server, offline create → pair → sync verified, zero old files read/copied/mounted.
- [ ] **Gate A — Data Integrity & Durability:** Atomic SQLite transactions (`PD-001`), record-bound drafts (`PD-016`), 30-day recoverable trash, immutable lab QC/evidence revisions, future `v1 → vN` migration tests.
- [ ] **Gate B — Security & Privacy:** Deny-by-default auth & per-workspace record/blob ACL (`PD-004`), SHA-256 staged blobs (`PD-015`), SSRF outbound guard (`PD-014`), isolated code execution (`PD-005`), ZIP/CSV/XXE import guards, AES-256-GCM encrypted backup option.
- [ ] **Gate C — Backup & Restorability:** Versioned `planaday-native-backup-v1` ZIP + SHA-256 manifest, staged verified restore drill, tamper rejection, replacement-server reconciliation (`PD-003`, `PF-42`), Docker PostgreSQL restore drill.
- [ ] **Gate D — Synchronization:** Two-client offline → reconnect sync, ordinary media types (`CONT-003`), bidirectional verified blob delivery (`PD-002`), explicit failed/pending receipts (`CONT-004`), conflict review preservation, selective sync.
- [ ] **Gate E — Native Platform Builds & Compatibility:** Android AAB/APK (`minSdk 29`, `targetSdk 36`) verified on emulator + physical Android 10 & Android 16 devices; signed Windows EXE/MSI verified on clean Windows VM with data-preserving uninstall.
- [ ] **Gate F — UX, Accessibility & Responsive Design:** Zero overflow at `320dp` Android (`PD-011`, `PD-012`, `PD-019`), `48dp` touch targets & semantic labels (`PD-013`), keyboard-only desktop workflows, high contrast AA/AAA, RTL, reduced motion.
- [ ] **Gate G — Mathematical, Lab & File Interchange Correctness:** Independent math/unit/lab/spreadsheet reference suites (`PD-007`, `CONT-006`, `CONT-007`), RFC 4180 CSV (`PD-006`), RFC 5545 ICS (`C-0060`), typed exports (`PD-008`), PPTX/XLSX/HTML fidelity (`R-0455`, `PD-018`).
- [ ] **Gate H — Performance & Capacity Budgets:** Measured benchmarks passing or reconciled with owner across 100k-word notes (`PD-010`), 100k records FTS5, 10k sparse cells, 10k canvas objects, 5,000 bookmarks (`R-0072`), 64-depth notes (`R-0233`).
- [ ] **Gate I — Complete Full-Scope Traceability:** All 652 requirements, 56 PF packages, and 50 N-series targets verified or covered by owner-approved exception; all 15 cross-feature integration journeys verified (`INT-001`..`INT-015`).
- [ ] **Gate J — Release Identity, Licensing & Commercial Policy:** Consistent `BuildIdentity` (`C-0061`, `PD-017`), Play Data Safety / Billing / Executable-code compliance, SBOM/license audit, offline desktop entitlement data survival, owner release authorization.
