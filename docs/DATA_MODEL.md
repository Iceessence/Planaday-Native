# Planaday Native — Canonical Data Model (`DATA_MODEL.md`)

**Schema Version:** `2` (Fresh greenfield v1 → additive Room KMP `MIGRATION_1_2` — Owner Decision O-004: zero legacy Planaday DB import)  
**Client Storage Engine:** AndroidX Room KMP (`2.8.5`) + AndroidX SQLite Bundled (`2.6.2`) in WAL mode with `PRAGMA foreign_keys = ON` and FTS5.  
**Exported Room Schemas:** `core/persistence/schemas/com.planaday.nativeapp.core.persistence.PlanadayDatabase/1.json` & `2.json`.  
**Server Storage Engine:** PostgreSQL 17 (isolated internal Docker network) via `SyncServerRepository` abstraction.

---

## 1. Architectural Invariants

1. **Single Canonical Identity:** Every user record has one stable UUIDv4 (`id`) stored in `record_index` with strongly typed serialized payloads and indexed query columns. Showing a task across Today, Flow Calendar, Kanban, Projects, Physical Planner, and Reports observes the **same** canonical `id` — never duplicating data across screens.
2. **Bounded Reactive & Keyset Queries (Update 2 §6):** Every feature subscribes to route-scoped Room `Flow`s (`observeByKind`, `observeScheduleWindow`, `observeTrash`, `observeDirtyDrafts`, `observeAutomationRuns`, `observeWorkspaceAttachments`). No screen triggers global workspace reloads. Large collections paginate in `O(log N)` using composite `(updated_at_utc, id)` keyset cursors rather than `OFFSET` scans.
3. **Exact Precision & Wall-Clock Time Safety:** Money (`DecimalAmount` with ISO-4217 currency) and analytical lab measurements avoid lossy binary float money math. Temporal records store UTC ISO-8601 instants (`created_at_utc`, `updated_at_utc`) AND explicit local date/time + IANA timezone (`time_zone`) + indexed `schedule_start_iso` / `schedule_end_iso` columns for DST-safe indexed calendar range queries.
4. **Optimistic Concurrency & Atomic Outbox:** Every mutation runs inside one Room immediate SQLite write transaction that checks `rev = expectedRev`, increments `rev`, updates `record_index` + FTS5 triggers, writes optional revision snapshots, and coalesces `outbox_ops` atomically.
5. **Durable Record-Bound Drafts, Preferences & Encrypted Secrets:** Active editor sessions persist to `drafts` keyed by `(workspace_id, record_id, session_id)` (`PD-016` eliminated). User appearance/workspace preferences and stable `deviceId` persist in `app_preferences`. Sync bearer tokens are **never** stored in plaintext SQLite — they are encrypted via OS-protected vaults (`JvmProtectedCredentialVault` NTFS-ACL/0600 AES-256-GCM on desktop; `AndroidKeystoreCredentialVault` hardware/TEE AES-256-GCM on Android).
6. **Immutable Evidence Records:** Laboratory QC, calibration measurements, and evidence snapshots preserve raw values immutably and store authorized corrections as append-only linked revisions.

---

## 2. Client SQLite Tables & Schema v2 Indexes

| Table | Purpose | Key Indexes (Schema v2) |
|---|---|---|
| `workspaces` | Workspace scopes (`PERSONAL`, `LABORATORY`, `OFFICE`, `HOUSEHOLD`, `CREATIVE`, `PRIVATE_DIARY`, `CUSTOM`) | `PRIMARY KEY (id)` |
| `record_index` | Authoritative canonical records (`98` `RecordKind`s) with indexed `schedule_start_iso`, `schedule_end_iso` | `(workspace_id, kind, deleted_at_utc, updated_at_utc, id)` (O(log N) keyset), `(workspace_id, kind, deleted_at_utc, schedule_start_iso, schedule_end_iso)` (calendar window), `(workspace_id, deleted_at_utc)`, `(workspace_id, parent_id, sort_order)` |
| `record_fts` (FTS5) | Full-text search virtual table kept synchronized via SQLite triggers with `workspace_id` filtering | FTS5 inverted index on `title`, `summary_text`, `tags_text` |
| `relations` | Typed bidirectional cross-feature record links (`SCHEDULED_AS`, `LINKED_TO`, `DERIVED_FROM`, `PART_OF`, etc.) | `(workspace_id, source_id)`, `(workspace_id, target_id)` |
| `drafts` | Record-bound editor draft recovery (`PD-016`, `PF-55`) | `PRIMARY KEY (workspace_id, record_id, session_id)` |
| `revisions` | Point-in-time record & note revision snapshots with one-click restore | `(workspace_id, record_id, rev DESC)` |
| `evidence_snapshots` | Immutable SHA-256 provenance snapshots (`N-020`) | `(workspace_id, source_record_id)` |
| `planner_bookmarks` | Indexed relational planner bookmarks tested to `5,000+` rows (`R-0072`) | `(workspace_id, book_id, sort_order)` |
| `attachments` & `attachment_refs` | Content-addressed SHA-256 blob metadata & reference-counted workspace ACL links (`PD-002`, `PD-015`) | `UNIQUE (sha256)`, `(workspace_id, record_id, sha256)` |
| `outbox_ops`, `sync_conflicts`, `blob_transfer_receipts`, `sync_cursors` | Offline-first atomic sync queue, conflict preservation (`N-042`), and explicit per-blob receipts (`CONT-004`) | `(workspace_id, created_at_utc)`, `(workspace_id, resolved)` |
| `automation_runs` | Idempotent automation execution audit ledger (`PF-34`, `N-035`) | `PRIMARY KEY (idempotency_key)` |
| `app_preferences` | Persistent user settings (`UserWorkspacePreferences`), `CalendarViewConfig`, stable `deviceId`, and OS-vault-encrypted secret ciphertexts | `PRIMARY KEY (key)` |

---

## 3. Verified Schema Evolution (`v1 → v2` Real Room Migration)

- **Schema v1 (`1.json`):** Initial greenfield schema for fresh installs.
- **Schema v2 (`2.json`, `PlanadayMigrations.MIGRATION_1_2`):** Additive migration adding `schedule_start_iso` and `schedule_end_iso` columns to `record_index` plus composite keyset and calendar-range indexes, wired into BOTH `JvmPlanadayDatabaseFactory` and `AndroidPlanadayDatabaseFactory`.
- **Automated Migration Proof:** `Update2ScalabilitySecurityAndFeaturesTest#syncTransportSecurityPolicyAndRealRoomMigration1To2PreserveData` builds a genuine `user_version = 1` SQLite database directly from `1.json`, inserts user data, opens it with Room v2 + `MIGRATION_1_2`, and verifies 100% lossless record survival and index availability.
- **Owner Decision O-004:** Zero legacy Planaday / Dayplana database importers or compatibility bridges exist.
