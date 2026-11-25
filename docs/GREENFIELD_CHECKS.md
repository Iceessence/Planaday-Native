# Planaday Native — Greenfield Separation & Verification Checks (`GREENFIELD_CHECKS.md`)

**Purpose:** Enforce and document clean-room separation between `Planaday-Native` and the read-only legacy archive (`Planaday_Legacy_Reference_ONLY.zip`). Automated checks are necessary guardrails and **do not** prove originality alone; independent human code review is mandatory before release acceptance.

---

## 1. Automated CI Enforcement (`scripts/verify_greenfield_and_traceability.py`)

Every CI run and local verification gate executes `scripts/verify_greenfield_and_traceability.py`, which fails the build on any violation of:

1. **Zero Legacy Paths & Files:**
   - Rejects presence of `Planaday-legacy`, `dayplana-data`, `capacitor.config.json`, `vite.config.ts`, `package.json`, `package-lock.json`, `desktop/main.cjs`, `public/sw.js`, `src/core/AppContext.tsx`, or any `.sqlite`/`.sqlite-wal`/`.sqlite-shm` database file committed to the repository.
2. **Zero Legacy Web-Shell Dependencies:**
   - Rejects any build file referencing `electron`, `capacitor`, `dexie`, `fastify`, `vite`, `react-dom`, or legacy npm/Node runtime shells.
3. **Zero Legacy Storage/Identity Reuse (Owner Decision O-004):**
   - Scans all production source, build, config, and deployment files (excluding explicit provenance exclusion tables in `docs/GREENFIELD_PROVENANCE.md` and `docs/GREENFIELD_CHECKS.md`) for `dayplana-v2`, `dayplana.sqlite`, `dayplana_db`, `com.dayplana`, `dayplana-backup-v2`, and private LAN IPs (`192.168.88.*`).
4. **Exact File Duplicate Rejection (SHA-256 Check):**
   - Computes SHA-256 of every file inside `Planaday-Native` and checks against all 152 SHA-256 hashes recorded in `docs/LEGACY_REFERENCE_LEDGER.csv`. Any match immediately fails CI.
5. **Traceability Completeness Gate:**
   - Verifies exact unique counts:
     - `docs/REQUIREMENT_TRACEABILITY.csv`: exactly **652** unique IDs (`R-0001`..`R-0494`, `C-0001`..`C-0158`).
     - `docs/EXPANSION_TRACEABILITY.csv`: exactly **56** unique IDs (`PF-01`..`PF-56`).
     - `docs/NEW_FEATURE_REGISTRY.csv`: exactly **50** unique IDs (`N-001`..`N-050`).
     - `docs/LEGACY_REFERENCE_LEDGER.csv`: exactly **152** unique baseline reference entries.
     - `docs/KNOWN_ISSUES.md`: all **28** historical issue IDs (`PD-001`..`PD-020`, `CONT-001`, `CONT-003`..`CONT-009`).

---

## 2. Limitations of Automated Checks & Manual Review Protocol

Automated hash, filename, and dependency scans cannot detect paraphrased or line-by-line translations from TypeScript/React into Kotlin. Therefore:

- **Architectural Divergence Check:** Independent reviewer must verify that `Planaday-Native` uses typed relational Room entities, scoped Kotlin `StateFlow`/`Flow` repositories, Ktor + PostgreSQL server architecture, and independent Jetpack/Desktop Compose screens rather than replicating legacy global `AppContext` or untyped JSON blobs.
- **Representative Code & Test Audit:** Independent reviewer inspects representative core engines (`core:math`, `core:persistence`, `core:sync-client`, `server:app`, `apps:android`, `apps:desktop`) and test suites against behavioral contracts.
- **Git History & Packaged Artifact Audit:** Independent reviewer confirms `git log --reverse` begins at empty commit `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`, and inspects built Android APK/AAB, Windows installer, and Docker image layers for any unintended legacy files or assets.
- **Self-Certification Prohibition:** Authoring agent marks automated checks as `DEVELOPER_VERIFIED`; independent reviewer status remains `PENDING_INDEPENDENT_REVIEW` until a separate reviewer signs off.

---

## 3. Check Execution Log

| Date (UTC) | Commit | Automated Script Result | Manual Independent Review Status |
|---|---|---|---|
| 2026-11-25 | Pending initial commit | Scheduled to run post-scaffold | `PENDING_INDEPENDENT_REVIEW` |
