# Planaday Native — Canonical Data Model (`DATA_MODEL.md`)

**Schema Version:** `1` (Fresh greenfield v1 schema — Owner Decision O-004: zero legacy Planaday DB import)  
**Client Storage Engine:** AndroidX Room KMP (`2.8.5`) + AndroidX SQLite Bundled (`2.6.2`) in WAL mode with `PRAGMA foreign_keys = ON` and FTS5.  
**Server Storage Engine:** PostgreSQL 17 (isolated internal Docker network) via `SyncServerRepository` abstraction.

---

## 1. Architectural Invariants

1. **Single Canonical Identity:** Every user record has one stable UUIDv4 (`id`) stored in `record_index` plus its authoritative typed domain table. Showing a task across Today, Task Calendar, Kanban, Project, and Reports observes the **same** canonical `id` — never duplicating data across screens.
2. **Typed Relational Core + Versioned Custom Extension:** Core high-query domains use strongly typed columns and foreign keys. User-defined fields and forward-compatible unknown fields use versioned `custom_schemas` / `custom_values` and explicit `schema_version` + `extra_json`.
3. **Exact Precision:** Money (`amount_minor_units` or decimal text with explicit ISO-4217 currency) and analytical lab measurements never use lossy binary float money math. Temporal records store UTC ISO-8601 instants (`created_at`, `updated_at`) AND explicit wall-clock local date (`YYYY-MM-DD`), local time (`HH:MM`), floating flag, and IANA timezone (`time_zone`) for DST-safe calendar semantics.
4. **Optimistic Concurrency & Atomic Outbox:** Every mutation runs inside one SQLite write transaction that checks `rev = expectedRev`, increments `rev`, updates typed table + `record_index` + FTS5 index, and appends an `outbox_ops` entry atomically.
5. **Durable Record-Bound Drafts & Recoverable Trash:** Active editor sessions persist to `drafts` keyed by `(workspace_id, record_id, session_id)` (`PD-016` eliminated). Soft-deletes set `deleted_at` (30-day default retention in `trash_items`) with reference-counted attachment garbage collection.
6. **Immutable Evidence Records:** Laboratory QC, calibration measurements, and evidence snapshots preserve raw values immutably and store authorized corrections as append-only linked revisions.

---

## 2. Client SQLite Tables (Schema v1)

| Group | Tables | Primary Indexes |
|---|---|---|
| **Identity & Access** | `workspaces`, `memberships`, `devices`, `family_profiles`, `app_settings` | `(workspace_id, user_id)` |
| **Canonical Index & Graph** | `record_index`, `relations`, `search_fts` (FTS5 virtual table), `saved_views`, `dashboard_layouts`, `templates` | `(workspace_id, kind, updated_at)`, `(source_id, relation_kind)`, `(target_id, relation_kind)`, `(workspace_id, deleted_at)` |
| **Drafts, History & Trash** | `drafts`, `revisions`, `evidence_snapshots`, `trash_items` | `(workspace_id, record_id, session_id)`, `(record_id, created_at DESC)` |
| **Planning & Time** | `calendars`, `events`, `recurrence_rules`, `event_exceptions`, `tasks`, `task_dependencies`, `chores`, `chore_completions`, `habits`, `habit_logs`, `goals`, `goal_milestones`, `alarms`, `timers`, `time_ledger`, `notification_occurrences` | `(workspace_id, start_date, end_date)`, `(workspace_id, status, do_date, due_date)`, `(workspace_id, room, next_due_date)` |
| **Knowledge & Diary** | `notebooks`, `notes`, `thoughts`, `diary_entries`, `life_events`, `countdowns` | `(workspace_id, notebook_id, parent_id, sort_order)`, `(workspace_id, entry_date)` |
| **Physical Planner & Canvas** | `planner_books`, `planner_pages`, `planner_bookmarks`, `decor_items`, `whiteboards`, `canvas_objects` | `(book_id, page_number)`, `(book_id, sort_order)`, `(canvas_id, z_index)` |
| **Kitchen & Personal Life** | `recipes`, `pantry_lots`, `meal_plans`, `food_logs`, `grocery_items`, `budget_envelopes`, `expenses`, `bills`, `medications`, `workouts`, `trips`, `people`, `home_assets`, `review_cards`, `media_items` | `(workspace_id, canonical_name, expiry_date)`, `(workspace_id, entry_date)`, `(workspace_id, envelope_id)` |
| **Laboratory Operations** | `lab_samples`, `lab_custody_events`, `lab_subcontracts`, `lab_inventory`, `lab_orders`, `lab_budgets`, `lab_price_entries`, `lab_calibrations`, `lab_wet_nir_pairs`, `lab_meetings`, `lab_work_hours`, `lab_qms_items` | `(workspace_id, sample_code)`, `(workspace_id, due_date)`, `(workspace_id, analysis, commodity)` |
| **Office, Projects & Creative** | `calc_saved`, `spreadsheets`, `presentations`, `projects`, `kanban_boards`, `kanban_cards`, `game_projects`, `game_entities`, `book_projects`, `book_chapters`, `book_entities`, `code_projects`, `code_snippets` | `(workspace_id, project_id)`, `(board_id, column_id, sort_order)` |
| **Custom Schemas & Automation** | `custom_schemas`, `custom_records`, `automation_rules`, `automation_runs`, `decision_journal` | `(workspace_id, schema_id)`, `(rule_id, idempotency_key)` |
| **Attachments & Files** | `attachments`, `attachment_refs`, `file_folders`, `photo_assets` | `UNIQUE(sha256)`, `(workspace_id, record_id, sha256)`, `(workspace_id, folder_id)` |
| **Sync & Recovery** | `outbox_ops`, `applied_ops`, `conflicts`, `sync_cursors`, `backup_receipts`, `blob_transfer_receipts` | `(workspace_id, created_at)`, `UNIQUE(op_id)`, `(workspace_id, status)` |

---

## 3. Future Schema Evolution (New Product Only)

- Version `1` initializes fresh empty databases on Android and Windows.
- Every future change (`v1 → v2 → ...`) uses explicit deterministic Room `Migration` classes tested with exported JSON schemas and staged pre-upgrade backup snapshots.
- No migration path exists or is permitted for legacy Planaday / Dayplana databases (Owner Decision O-004).
