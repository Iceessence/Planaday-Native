# Planaday Native — Architecture Decision Records (`ARCHITECTURE_DECISIONS.md`)

All foundational architecture decisions are documented below and remain subject to measured prototype verification (`Step 2`) and independent reviewer sign-off (`PENDING_INDEPENDENT_REVIEW` — author cannot self-accept).

---

## ADR-001: Multiplatform Architecture — Kotlin Multiplatform + Jetpack Compose (Android) + Compose Desktop (JVM/Windows)

- **Status:** `PROVISIONAL — PENDING PROTOTYPE BENCHMARKS & INDEPENDENT REVIEW`
- **Context:** Planaday must ship as one integrated commercial product for Android (phones/tablets/foldables) and Windows Desktop without an Electron/WebView shell, sharing business logic, schemas, math/document/graphics/sync engines while keeping platform-optimized UIs.
- **Decision:**
  - Use **Kotlin Multiplatform (`2.2.21`)** targeting `androidTarget` and `jvm` (Windows Desktop + shared server protocol) with **Compose Multiplatform** for shared design tokens/components and dedicated native shell modules (`apps:android`, `apps:desktop`).
  - Reject Electron, Capacitor, WebView shells, and premature microservices.
  - Isolate any platform-sensitive component (large-doc text input, printing, OS notifications, camera, keystore, external desktop toolchain) behind explicit interfaces in `core:*` so a single weak component can be swapped without rebuilding the project.
- **Tradeoffs:** Compose Desktop packages a minimal jlinked JVM runtime (~45–80 MB installer) rather than a C++ binary; startup, RAM, IME composition, and printing are benchmarked in `tests:integration` / `PERFORMANCE_BASELINE.md`.

---

## ADR-002: Client Local Persistence — Room KMP + Bundled SQLite (WAL + FTS5)

- **Status:** `PROVISIONAL — PENDING PROTOTYPE BENCHMARKS & INDEPENDENT REVIEW`
- **Context:** Android devices ship heterogeneous OEM SQLite builds; Windows JVM lacks a standard system SQLite library. We need identical SQL semantics, WAL concurrency, FTS5 full-text search, foreign keys, and deterministic future schema migrations (`v1 → vN`) across both platforms.
- **Decision:**
  - Use **AndroidX Room KMP (`2.8.5`)** + **AndroidX SQLite Bundled (`2.6.2`)** (`BundledSQLiteDriver`) as the unified client database engine.
  - Enable `PRAGMA foreign_keys = ON`, `PRAGMA journal_mode = WAL`, `PRAGMA busy_timeout = 5000`, and `PRAGMA synchronous = NORMAL` (or `FULL` during backup/restore checkpoints).
  - Never open a live SQLite file over a network filesystem (SMB/NFS).
  - Fallback alternative (isolated behind repository interfaces): SQLDelight or direct bundled SQLite wrapper if Room KMP hits any measured limitation.

---

## ADR-003: Binding Fresh-Start Policy & Independent Product Identities (Owner Decision O-004)

- **Status:** `ACCEPTED — BINDING OWNER DECISION O-004`
- **Decision:**
  - Initialize completely empty v1 SQLite databases on Android/Windows and an empty optional server database.
  - Zero legacy Planaday/Dayplana database readers, old backup readers, ID mappers, or migration bridges.
  - Use independent identities (`com.planaday.nativeapp`, `planaday_native_v1.db`, `%LOCALAPPDATA%\PlanadayNative\`, port `8420`, Docker volumes `planaday_native_*`) for safe side-by-side coexistence with any legacy installation.
  - Preserve ordinary standards-based file imports/exports (CSV, ICS, XLSX, PPTX, Markdown, PDF, images), new-product versioned backups (`planaday-native-backup-v1`), and future `v1 → vN` migrations for data created by Planaday Native.

---

## ADR-004: Canonical Hybrid Data Model (Typed Relational Core + Versioned Custom Schemas)

- **Status:** `PROVISIONAL — PENDING INDEPENDENT REVIEW`
- **Context:** Legacy Planaday stored all 88 record types inside one untyped JSON column and subscribed to full tables in a monolithic global context (`AppContext`), causing unbounded memory scans, lost concurrent edits, and weak relational integrity.
- **Decision:**
  - Introduce a canonical `record_index` table (`id`, `workspace_id`, `kind`, `title`, `status`, `rev`, `created_at`, `updated_at`, `deleted_at`, `owner_profile_id`, `shared_scope`) paired with **first-class typed relational tables** for core high-query domains (`workspaces`, `memberships`, `tasks`, `task_dependencies`, `events`, `recurrence_rules`, `event_exceptions`, `calendars`, `chores`, `chore_completions`, `habits`, `goals`, `notebooks`, `notes`, `drafts`, `revisions`, `diary_entries`, `alarms`, `timers`, `time_ledger`, `recipes`, `pantry_lots`, `meal_plans`, `food_logs`, `grocery_items`, `expenses`, `budget_envelopes`, `lab_samples`, `lab_calibrations`, `lab_wet_nir_pairs`, `lab_inventory`, `lab_orders`, `spreadsheets`, `presentations`, `projects`, `kanban_boards`, `whiteboards`, `planner_books`, `planner_pages`, `decor_items`, `attachments`, `attachment_refs`, `relations`, `custom_schemas`, `custom_records`, `automation_rules`, `automation_runs`, `outbox_ops`, `applied_ops`, `conflicts`, `sync_cursors`, `search_fts`).
  - Maintain strict foreign keys, optimistic revision checks (`WHERE id = ? AND rev = ?`), atomic outbox writes in the same SQLite transaction, and versioned `extra_json` extension bag strictly for forward-compatible unknown-field preservation in future schema versions.

---

## ADR-005: Optional Self-Hosted Sync Server — Ktor 3 + PostgreSQL (with Isolated SQLite Test Driver)

- **Status:** `PROVISIONAL — PENDING PROTOTYPE BENCHMARKS & INDEPENDENT REVIEW`
- **Decision:**
  - Build the optional self-hosted sync server in **Kotlin + Ktor 3** with **PostgreSQL 17** for production Docker Compose / TrueNAS deployment (strictly internal Docker network, non-root container, pinned image digests, least-privilege DB role, staged SHA-256 blob volume).
  - Abstract server persistence behind `SyncServerRepository` so automated CI/local integration tests can run both against an isolated embedded SQLite/H2 test store AND against real PostgreSQL in Docker.
  - Sync Protocol `v1` is explicitly versioned and decoupled from app version, enforcing: owner auth + 10-min single-use pairing codes, revocable workspace-scoped SHA-256 Bearer tokens, idempotent `opId`, optimistic `baseRev` conflict detection, monotonic `serverCursor`, persistent `serverId` + `syncEpoch` (reconciling replacement servers), and server-verified SHA-256 staged blob transfer with per-workspace ACL.

---

## ADR-006: Security Threat Model, SSRF Egress Policy & Isolated Code Execution

- **Status:** `PROVISIONAL — PENDING INDEPENDENT REVIEW`
- **Decision:**
  - Deny-by-default authorization on every server route including indirect blob references (knowing a SHA-256 hash NEVER grants download permission without a matching authorized workspace attachment reference).
  - Centralized SSRF egress policy blocking loopback, RFC1918, link-local/metadata (`169.254.169.254`), CGNAT (`100.64.0.0/10`), non-HTTPS (unless explicitly opted in for local test), >3 redirects, and >2 MB responses.
  - Zero `eval()` or untrusted in-process execution. Desktop Code Studio executes user code strictly in external OS child processes with explicit user opt-in, restricted working directory, stripped environment variables, hard timeout, and output cap; Android uses a pure-Kotlin deterministic safe learning interpreter (Google Play compliant).
  - Optional encrypted backups use authenticated **AES-256-GCM** with **PBKDF2-HMAC-SHA256** (600,000 iterations) and user-held recovery key.

---

## ADR-007: Large-Document Editor & Spatial-Indexed Canvas Architecture

- **Status:** `PROVISIONAL — PENDING PROTOTYPE BENCHMARKS`
- **Decision:**
  - `core:documents` implements record-bound draft sessions keyed by `(workspaceId, recordId, sessionId)` persisted atomically to `drafts` table so switching records without blur can NEVER overwrite another record (`PD-016` eliminated by construction).
  - Large documents (`100k+` words) use chunked/piece-table storage, deferred O(n) single-pass statistics, and virtualized paragraph layout rather than synchronous full-document regex or layout reflow on every keystroke.
  - `core:graphics` uses a uniform-grid spatial index (`SpatialIndex2D`) for 10,000+ objects on Physical Planner and Infinite Whiteboard with zoom-independent hit testing and shared vector/SVG export.

---

## ADR-008: Commercial Distribution, Packaging & Offline Licensing Boundary

- **Status:** `PROVISIONAL — PENDING OWNER COMMERCIAL APPROVAL`
- **Decision:**
  - **Android:** Target API 36 (Android 16), `minSdk = 29` (Android 10), Play App Bundle (AAB), Play App Signing, Data Safety & exact-alarm/notification justifications, never bypassing Play Billing policy.
  - **Windows Desktop:** Signed native MSI/EXE packaging via Compose jpackage with bundled minimal JRE, per-user default install (`%LOCALAPPDATA%\Programs\PlanadayNative`), uninstall preserves user data in `%LOCALAPPDATA%\PlanadayNative` by default.
  - **Offline Entitlement:** Ed25519 public-key-only signed offline entitlement token verifier in `core:security` (zero private keys in repo/binary). **Data Survival Guarantee:** Local data ALWAYS remains readable and exportable even if any license server is unreachable or entitlement expires.
