# Planaday Native — Greenfield Provenance Record (`GREENFIELD_PROVENANCE.md`)

**Status:** Active — Revision 4.0 Binding Greenfield Record  
**Date Established:** 2026-11-25  
**Owner Decision O-004:** Fresh data only — zero legacy Planaday data migration, database readers, old backup readers, compatibility bridges, or identity reuse.

---

## 1. Empty Initial Repository Evidence

- **Repository Path:** `C:\Planaday\Planaday-Native`
- **Default Branch:** `main`
- **Initial Empty Root Commit SHA:** `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`
- **Commit Message:** `chore: initialize empty greenfield Planaday-Native repository (Rev 4.0)`
- **Parent Commits:** None (independent root commit; NOT forked or cloned from any legacy repository).

---

## 2. Read-Only Legacy Reference Archive Manifest

The legacy reference archive is stored strictly **outside** the `Planaday-Native` repository, outside all Gradle build inputs, dependency graphs, and packaged release artifacts.

- **Archive Filename:** `Planaday_Legacy_Reference_ONLY.zip` (cleaned reference subset)
- **Archive Size:** `758,549` bytes
- **Archive SHA-256:** `55C88DFF72754C054C611654B374005417BEE8DF880D1A37FABF3D9E6EAC15AD`
- **Verified Entry Count:** `152` files (`0` SQLite databases, `0` `.git/` entries, `0` `dist/` or `dist-server/` compiled bundles)
- **Extracted Read-Only Inspection Workspace:** Isolated scratch directory outside repository with filesystem read-only attributes set on all 152 files.
- **Local Secret / Private Info Scan Results:**
  - `0` hardcoded private keys or API secrets found.
  - `24` private LAN IP address occurrences and `1` lockfile email detected in legacy reference files; **all private LAN network details are strictly excluded and redacted** (`[REDACTED_LAN_HOST]`) from `Planaday-Native` documentation and source code.
- **File-Level Provenance:** Full SHA-256 hashes, line counts, exported symbols, import graph findings, and extracted behavioral contracts for all 152 reference files are recorded in [LEGACY_REFERENCE_LEDGER.csv](LEGACY_REFERENCE_LEDGER.csv).

---

## 3. Independent New Product Identities (Side-by-Side Coexistence)

To guarantee zero interference with any existing Planaday / Dayplana installation (Owner Decision O-004):

| Identity Dimension | New Planaday Native Identity | Prohibited Legacy Identifiers |
|---|---|---|
| Android Application ID | `com.planaday.nativeapp` | `com.dayplana.planner`, `com.dayplana.app` |
| Kotlin Root Namespace | `com.planaday.nativeapp` | `dayplana` |
| Client Local SQLite DB | `planaday_native_v1.db` | `dayplana-v2`, `dayplana.sqlite` |
| Client Local Blob Store | `<appData>/PlanadayNative/blobs_v1/` | `/data/blobs`, legacy IndexedDB blobs |
| Windows Data Directory | `%LOCALAPPDATA%\PlanadayNative\` | Legacy Electron/Dayplana paths |
| Optional Server Default Port | `8420` (configurable) | `3000` (legacy default) |
| Docker Named Volumes | `planaday_native_pgdata`, `planaday_native_blobs`, `planaday_native_backups` | `dayplana_db`, `dayplana_blobs` |
| Release Version Line | `1.0.0-alpha.1` (new product line) | `4.1.0` (legacy web line) |
| Backup Format Identifier | `planaday-native-backup-v1` | `dayplana-backup-v2` |

---

## 4. Independently Selected Third-Party Dependencies & Licenses

All runtime and build dependencies are independently selected, standard, open-source Kotlin/JVM/Android ecosystem libraries pinned in [gradle/libs.versions.toml](../gradle/libs.versions.toml). Zero legacy web-shell runtimes (`electron`, `capacitor`, `vite`, `react`, `dexie`, `fastify`) exist in this repository.

| Dependency / Toolchain | Purpose | License |
|---|---|---|
| Gradle Wrapper (`8.14.3`) | Verified reproducible build orchestration (SHA-256 verified) | Apache-2.0 |
| Kotlin Multiplatform & Coroutines & Serialization & DateTime | Shared Android/JVM/Server domain, async, serialization, temporal types | Apache-2.0 |
| JetBrains Compose Multiplatform & Android Jetpack Compose (Material 3) | Native Android & JVM Windows Desktop declarative UI | Apache-2.0 |
| AndroidX Room KMP & AndroidX SQLite Bundled | Typed relational local persistence with bundled SQLite (WAL, FTS5) | Apache-2.0 |
| Ktor Server & Client | Optional self-hosted sync server and multiplatform HTTP sync client | Apache-2.0 |
| PostgreSQL JDBC & HikariCP & SQLite JDBC (server test harness) | Optional server PostgreSQL storage and disposable test harnesses | BSD-2-Clause / Apache-2.0 |
| JUnit 5 / Kotlin Test / Compose UI Test | Independent contract, integration, UI, and performance verification | EPL-2.0 / Apache-2.0 |

---

## 5. New Module Ownership & Contract-to-Code Traceability

Every source file, schema table, UI screen, server route, and test in `Planaday-Native` is newly authored in Kotlin from behavioral contracts documented in:
- [REQUIREMENT_TRACEABILITY.csv](REQUIREMENT_TRACEABILITY.csv) (652 original requirement IDs)
- [EXPANSION_TRACEABILITY.csv](EXPANSION_TRACEABILITY.csv) (56 PF packages)
- [NEW_FEATURE_REGISTRY.csv](NEW_FEATURE_REGISTRY.csv) (50 N-series proposals)
- [FEATURE_INTEGRATION_MATRIX.csv](FEATURE_INTEGRATION_MATRIX.csv) (15 mandatory cross-feature journeys)

---

## 6. Approved Exceptions

| Exception ID | Scope | Rationale | Approval Status |
|---|---|---|---|
| **O-004** | Legacy Planaday data migration, DB readers, legacy backup importers, ID bridges | Owner binding directive (Rev 4.0 §0): fresh empty databases only; preserve ordinary standards-based interchange (CSV, ICS, XLSX, PPTX, Markdown, new-product backups) and future v1→vN new-product migrations. | **OWNER_APPROVED (Binding)** |
| **EXC-PLAT-001** | Browser-only Firefox startup check (`C-0064`) & legacy iOS Safari checks | Owner binding scope (Rev 4.0 §1): Android + Windows Desktop native + optional server; no iOS or mandatory browser shell. Original IDs retained in traceability CSV. | **OWNER_APPROVED (Master §1/§11)** |
| **EXC-ASSET-001** | Newly authored vector icons, stickers, themes, and synthetic fixtures | Zero legacy CSS/SVG/UI assets copied. All icons, themes, and opt-in synthetic demo records are independently authored in Kotlin/Compose. | **GREENFIELD_POLICY** |
