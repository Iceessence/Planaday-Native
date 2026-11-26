# Planaday Native — Greenfield Separation & Verification Checks (`GREENFIELD_CHECKS.md`)

**Purpose:** Enforce and document clean-room separation between `Planaday-Native` and the read-only legacy archive (`Planaday_Legacy_Reference_ONLY.zip`). Automated checks are necessary guardrails and **do not** prove originality alone; independent human code review is mandatory before release acceptance.

---

## 1. Automated CI Enforcement (`scripts/verify_greenfield_and_traceability.py`)

Executed against the complete `Planaday-Native` working tree:
1. **Zero Legacy Paths & Files:** Rejects legacy archive paths, `package.json`, `vite.config.ts`, `capacitor.config.json`, `desktop/main.cjs`, `public/sw.js`, `AppContext.tsx`, and any `.sqlite` database files.
2. **Zero Legacy Web-Shell Dependencies:** Rejects `electron`, `capacitor`, `dexie`, `fastify`, `vite`, `react-dom` in all Gradle/TOML/Dockerfile build files.
3. **Zero Legacy Storage/Identity Reuse (O-004) & Zero Private IP Leaks:** Rejects `dayplana-v2`, `dayplana.sqlite`, `dayplana_db`, `com.dayplana`, `dayplana-backup-v2`, and private LAN IPs across all code and configuration.
4. **Zero Exact File Duplicates (SHA-256):** Compares SHA-256 of every file in `Planaday-Native` against all 152 reference file hashes in `docs/LEGACY_REFERENCE_LEDGER.csv`.
5. **Verbatim Master Spec & Real Build Identity:** Enforces preservation of `docs/spec/PLANADAY_FRESH_START_MASTER_REV4_FULL.md` (`>= 130 KB`) and rejects placeholder `"greenfield-dev"` in `BuildIdentity.kt`.
6. **Exact Traceability & Strict 10-Status Taxonomy:** Verifies `652` unique requirement IDs, `56` unique PF IDs, `50` unique N IDs, `152` unique ledger entries, `28` historical regression IDs, valid 10-status values across all 3 CSVs, and `0` self-certified `INDEPENDENTLY_ACCEPTED` claims.

---

## 2. Limitations & Manual Independent Review Protocol

Automated hash and pattern scans cannot prove absence of disguised line-by-line translations. An independent reviewer separate from the authoring agent must audit:
- Architectural divergence (typed Room KMP v2 relational schema, route-scoped reactive `Flow` controllers, Ktor + PostgreSQL server, native Jetpack/Desktop Compose vs legacy Dexie/AppContext/React/Electron).
- Representative core engines (`core:math`, `core:persistence`, `core:security`, `core:sync-client`, `core:design-tokens/studios`, `server:app`, `apps:android`, `apps:desktop`) and verification tests.
- Git history starting from empty root commit `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`.
- Built APK (`android-debug.apk`), desktop MSI/EXE distributions, rendered PNG screenshots, and server artifacts.

---

## 3. Check Execution Log

| Date (UTC) | Scope Scanned | Automated Script Result | Independent Manual Review |
|---|---|---|---|
| 2026-11-25 | 35 files (Milestone 1 docs/scaffold) | **PASS** (`0` legacy duplicates, `0` O-004 violations, 652/56/50/152/28 verified) | `PENDING_INDEPENDENT_REVIEW` |
| 2026-11-25 | 118 files (Full KMP core, Room v1, Ktor server, Android, Desktop, E2E suite) | **PASS** (`0` legacy duplicates, `0` O-004 violations, 652/56/50/152/28 verified) | `PENDING_INDEPENDENT_REVIEW` |
| 2026-11-25 | 126 files (Update 1 full-scope engines, studio UIs, 88-record/100k-soak/64MB-stream/Compose-UI suites, Windows MSI/EXE + Android APK + Ktor dist) | **PASS** (`0` legacy duplicates, `0` O-004 violations, 652/56/50/152/28 verified) | `PENDING_INDEPENDENT_REVIEW` |
| 2026-11-26 | Full Update 2 repo (verbatim Rev 4.0 spec, Room v2 `2.json`, OS credential vaults, 8 modular commercial studios, 8 verified PNG screenshots, honest 10-status CSVs, clean-SHA `0d00bb7a936a` MSI/EXE/APK/server) | **PASS** (`0` legacy duplicates, `0` O-004 violations, verbatim spec intact, real Git SHA enforced, 10-status taxonomy valid) | `PENDING_INDEPENDENT_REVIEW` |
