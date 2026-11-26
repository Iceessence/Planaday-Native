# Planaday — Complete Greenfield Product Master Specification
**Revision 4.0 | 25 November 2026 | Full-scope greenfield development directive | Fresh data only**

## 0. Binding greenfield construction contract — highest priority
**Owner decision: build Planaday completely from scratch, with no old data migration.** This revision supersedes every earlier instruction to update, port, translate, refactor, consolidate or reuse an old implementation. The complete product vision survives; the old implementation does not become the new application's foundation. All 652 requirement IDs, 56 expansion packages, 28 historical issue investigations, 152 file reference entries and 50 additional proposal entries remain in scope as specified below. All full-release feature families must be designed into the new architecture from day one; development order is a dependency order, not an authorization to omit advanced features or ship a basic MVP.

### Allowed and prohibited inputs
- **Allowed:** study the legacy source and its documentation to extract expected behavior, rules, calculations, supported formats, data semantics, defects, workflows and acceptance criteria. Retain requirement text and identifiers, independently confirmed behavior descriptions and factual interface/data compatibility specifications in new documentation. Use independently chosen, licensed, established third-party libraries, frameworks, SDKs, algorithms and standard tools. Creating new code from scratch does not require reinventing databases, cryptography, operating systems, or established language toolchains.
- **Prohibited:** fork or clone the old repository as the new project; edit or rename old source files to become new production files; translate React/TypeScript into Kotlin line by line or function by function; copy old source, functions, components, CSS, templates, scripts, manifests, build configurations, tests, assets or binaries into any new production or test module; wrap the old web application in a new native shell; replicate the old module/file structure, schemas or implementation solely for convenience; use legacy runtime dependencies as a compatibility shortcut. Do not use an LLM to paraphrase old implementation code as a disguised port. Preserve functionality, not implementation.
- **Fresh data only — binding owner decision:** No legacy Planaday data migration, conversion, recovery, transfer, import adapter, database reader, old backup reader, account migration, old-to-new ID mapping, or compatibility bridge is required or permitted in the new project. Do not read old records, attach the old database, restore old backups, or reuse the old server, storage paths, credentials or installation identities. Start with empty new SQLite databases on each client and a new empty optional server database. Create new IDs when users create records. Use independently selected package, installer, application data and server volume identities so the old installation remains untouched. This decision supersedes all earlier migration instructions and any conflicting historical requirement. Normal new-product backups, standard user-requested file imports (CSV, ICS, XLSX, PPTX, images, documents, etc.), new-product data portability, and future schema/protocol upgrades remain required; none authorize importing the old Planaday database or backup format.

### Mandatory execution procedure
1. Keep the legacy reference ZIP and extracted inspection workspace outside the new product repository and outside all build inputs, dependency graphs and packaged artifacts. Record SHA-256 manifests; preserve original bytes. Never run historical code except in a separately authorized isolated verification sandbox with synthetic data. Keep any old test results separate from new evidence.
2. Before coding, extract each requirement into implementation-neutral behavioral contracts, state transitions, failure cases, integration dependencies and independent test vectors. Review all 152 files to discover features and pitfalls. The file ledger maps evidence to requirement IDs and proposed new module ownership; it is NOT a list of old files to convert. A many-to-many mapping is expected. Where an old feature is incomplete or defective, use the owner requirements and verified expected behavior rather than reproducing its defect.
3. Initialize an empty private Git repository. Scaffold the new Gradle/toolchain setup, new module boundaries, new database schemas/migrations, new domain models, new UI system and new server API from the contracts and the validated architecture. Do not import old commits or source directories. Write new tests against the behavioral contracts, independent mathematical references and synthetic fixtures. Existing tests can identify missing scenarios, but their code and fixtures must not be copied. All new product assets must be newly authored or independently licensed; obtain owner approval for any deliberate reuse of legacy branding artwork.
4. Keep `GREENFIELD_PROVENANCE.md` with initial commit, reference archive hashes, independently selected dependencies/licenses, new source/module ownership, contract-to-code links and any approved exceptions. Add a CI check rejecting legacy paths, archived source or binaries in build inputs, old web-shell toolchains as runtime dependencies, and accidental exact duplicates of substantial legacy implementation files. Use a local review for near-copy/translation risks; automated similarity alone cannot prove originality. A reviewer must examine representative new implementations, test provenance, Git history and packaged contents. Preserve a written report of checks, limitations and exceptions. No self-certification.
5. Begin actual full-scope implementation once relevant architecture gates pass. Create feature contracts for every domain upfront, including office, lab, creative and advanced functionality. Implement complete integrated vertical slices across Android, Windows, server and tests, in parallel where safe. No placeholder-only completion, no basic-feature-only release and no indefinite architecture-only phase. Independent review and meaningful tests still gate release. If the session ends before completion, leave a reproducible new repository, exact evidence and a handoff for continuation; do not restart from zero in later sessions.

**Meaning of terms throughout this document:** “preserve” refers to feature requirements, behavior, historical evidence; “migration” refers only to future schema evolution within the new product, never old data or old code; “replacement”, “redesign”, “split” and “retire” in Appendix D identify independent new responsibilities and do not authorize modifying or translating the old files. This chapter controls any apparent conflict in historical material.

## 1. Binding product objective and scope
Build a new, commercially distributable, offline-first Planaday for Android and Windows, with optional self-hosted synchronization. It is a personal and professional productivity operating system, not a basic planner. The complete existing functionality, all documented requirements including previously missing features, every expansion package, and the new owner-requested cross-platform capabilities belong in the first complete product release. The source archive is a read-only behavioral reference only; all new application code and implementation assets must be authored independently under chapter 0. This specification supersedes earlier minimal-first or basic-planner-first interpretations. The detailed source requirement register in Appendix A and expansion register in Appendix B are binding coverage inputs. New ideas in chapter 13 are proposed additional first-release targets under the owner’s broad request for more features; incorporate their architectural extension points from day one and implement them alongside the appropriate workstreams where feasible. Any scope exclusion or unsafe/platform-dependent equivalent needs an explicit owner decision. External actions, spending, publishing and sensitive data access still require specific authorization. Do not call a partial prototype a complete product.

Design all domains, record types, permission boundaries, editor contracts, sync contracts, import/export contracts, accessibility and feature routes at inception. Implement in dependency-ordered vertical slices, with several independent feature teams working concurrently when their contracts are frozen and they do not edit the same files. The first development activation must create the repository, complete scope map, prototype the critical stack, and begin real integrated implementation where gates pass; it must not terminate solely with a report if tools and verification permit further work. No final release until all applicable requirements and approved expansions have evidence, or explicit owner-approved exception. No infinite scope: freeze the first complete release scope while maintaining a versioned future backlog.

Offline means an installed application can create, view, edit, link, search, print, export and back up all local records without a server, account or internet. Live weather, exchange rates, external calendar feeds, remote collaboration and external AI cannot deliver fresh remote data offline; they must provide clearly dated caches, manual entry, queued actions or honest unavailable states. The Android and Windows editions offer equivalent semantic capability, not identical screens; system-level restrictions must be recorded and resolved with safe platform alternatives. No iOS or required web/PWA edition. Keep optional web administration limited to server operations if needed. Preserve ordinary file interchange capabilities; do not implement a legacy Planaday database or backup importer.

## 2. Evidence, archive handling and scope baseline
The current supplied file is `Planaday Old No Audits No Nodes.zip`. It has 976 file entries: 152 source/configuration/documentation/test files, 794 Git files, 19 client build artifacts, 8 server build artifacts and 3 database-related files. ZIP integrity check returned no error. package.json declares 4.1.0; the legacy master calls its authoritative revision 4.1.0-dev. The source is TypeScript/React/Vite, Dexie/IndexedDB, Fastify and SQLite, with Capacitor configuration and Electron desktop packaging. It is not yet the proposed native application. Source text and specifications, not generated bundles, determine intended behavior. Test reports are historical claims, not fresh execution results. The old audit folders are absent from this ZIP although referenced by the specifications. The developer must mark unavailable evidence, obtain an authorized copy if needed, and create fresh evidence rather than pretend to have audited it.

The matrix contains 652 distinct identifiers: 4 verified complete, 53 mostly complete, 31 developer-verified pending independent verification, 143 partial, 414 not verified, 3 blocked by undefined requirements, 3 not implemented, 1 broken. The 56 expansion table rows classify 13 developer-verified, 36 partial, 7 planned; its heading summary uses a different treatment of PF-42 live verification. Reconcile by preserving both original labels and evidence. PF-42 still needs owner-authorized production verification. No old classification automatically transfers to a new implementation.

IMPORTANT: the supplied original archive includes `dayplana-data/dayplana.sqlite`, `dayplana.sqlite-shm`, and `dayplana.sqlite-wal`, plus .git history. Do not upload these to external AI, cloud builds or a public repo. A cleaned reference archive accompanies this specification; it excludes database, Git history and generated distributions. It is not a guarantee that every retained source file is free of credentials or personal references. Run local secret scanning and review before sharing. Do not open real records, use them for tests or publish historical local network details. Preserve the original ZIP locally, read-only, with SHA-256 manifest. Never copy Git history or compiled outputs into the new repository. Do not deploy to the live server, register accounts, purchase services, access credentials or change signing identity without owner approval.

## 3. Architecture decision and validation
Preferred stack: Kotlin Multiplatform shared core (Android and JVM desktop), Android Jetpack Compose, Windows-first Compose Desktop on JVM, Room KMP with bundled SQLite driver if stable and benchmarked, Kotlin coroutines/Flow, explicit dependency injection at composition roots, Ktor server with PostgreSQL, Docker Compose and persistent volumes. Compose Desktop is a JVM application packaged with its runtime, not a C++ native binary. Its viability depends on actual rendering, accessibility, editing, printing and packaging benchmarks. Share domain rules and persistence where practical; separate UI layouts and platform adapters. Use stable, mutually compatible pinned toolchain versions in a Gradle version catalog and wrapper, no speculative latest dependencies. Ktor/PostgreSQL is a provisional multi-user choice; benchmark a single-node SQLite server alternative if it substantially simplifies self-hosting without sacrificing correctness. Prefer one supported server persistence engine for first release. Reject unnecessary microservices, Electron/WebView as the primary shell, unrestricted plugin execution and one giant application state object.

Validate in isolated prototypes before locking dependency versions: startup on modest Android and Windows devices, native IME composition and selection, 100k-word editing, virtualized calendar at one-minute scale, 10k+ sparse spreadsheet cells, vector canvas with 10k objects, camera/attachments, accessibility semantics, database migrations and concurrent writers, notifications across reboot and permission denial, signed-package smoke test using test keys, sync retries/conflicts and Docker restore. Compare alternatives when a hard requirement fails (e.g. platform-native text editor component, Windows-specific editor or rendering adapter, SQLDelight/explicit SQLite). Do not rebuild the whole project for one weak component; isolate it behind an interface. Record measured results and tradeoffs in ADRs. If a gate cannot run, report exact dependency/environment blocker, leave affected decision provisional, and continue only independent work.

### Repository and development setup
Use a new private repository, e.g. `Planaday-Native`, initialized from an empty directory. Keep the separate read-only reference checkout outside the new repository entirely; only independently extracted specifications and audit records belong in new documentation. Root: Gradle wrapper, settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, README, LICENSE/NOTICE, .gitignore, CI, scripts and docs. Suggested module map (adjust only via ADR):
- `apps/android` — activity, navigation, Compose screens, Android storage/notifications/camera/widgets/permissions, Play billing adapter if approved.
- `apps/desktop` — Windows Compose screens, windowing, keyboard/mouse, printing, file dialogs, installer and entitlement adapter.
- `core/model`, `core/domain`, `core/usecases` — identifiers, value types, rules and typed commands; no Android or UI imports.
- `core/persistence`, `core/search`, `core/documents`, `core/math`, `core/graphics`, `core/automation`, `core/notifications`, `core/interchange` — common engines with explicit interfaces and isolated tests.
- `core/sync-protocol`, `core/sync-client`, `core/security`, `core/platform`, `core/design-tokens`, `core/testing` — versioned transport models, local queues, crypto adapters, platform contracts, tokens and deterministic fixtures.
- `features/planning`, `features/tasks`, `features/knowledge`, `features/time`, `features/planner`, `features/kitchen`, `features/life`, `features/lab`, `features/office`, `features/projects`, `features/creative`, `features/files`, `features/customization`, `features/reports` — feature-owned use cases, data adapters and presentation contracts; add modules only where boundaries are meaningful.
- `server/app`, `server/storage`, `server/auth`, `server/sync`, `server/operations`, `deployment/` — API, authorization, PostgreSQL migrations, blobs, maintenance, Docker health/backup scripts. Avoid linking the server to desktop UI or Android classes.
- `tools/fixtures`, `tests/contract`, `tests/e2e`, `tests/performance`, `docs/`, `distribution/` — repeatable synthetic test data, verification and release assets.

Install Git, current stable Android Studio, compatible JDK and Android SDK/emulator, Windows build tools for installer signing/package creation, Docker Desktop or isolated Linux Docker host, and a supported PostgreSQL image. Choose hardware virtualization, use physical Android USB debugging only with permission, and create a disposable test server with synthetic users and fixtures. Do not put SDKs, runtime database, build caches, credentials or signing material in Git. Run Gradle via wrapper; enable dependency verification, lockfiles where supported and reproducible CI. Initial commands should be written and actually executed after project creation (e.g. `gradlew.bat projects`, `gradlew.bat check`, `gradlew.bat :apps:android:assembleDebug`, `gradlew.bat :apps:desktop:run`, `docker compose config`, `docker compose up -d` only in the isolated test environment); adapt to real project task names. Pin versions after compatibility checks. Document exact clean-machine setup, environment variables, expected outputs and shutdown/cleanup commands. No production secrets in local examples.

## 4. Canonical data and cross-feature engine
Use one canonical identity per entity, not a duplicate for each screen. Prefer typed relational tables for stable domains plus a versioned custom-schema mechanism for user-defined types. A shared record index can hold id, workspace, kind, revision, created/updated timestamps, lifecycle state and searchable label; typed tables hold authoritative domain fields. Relations hold source/target typed IDs, relationship kind, workspace, ordering and metadata with referential checks. Model permissions independently from UI navigation. Avoid a universal unvalidated JSON table as the only source of truth; preserve unknown fields in future new-product formats when safe and explicitly versioned. Use stable UUIDs, UTC instants plus local dates/time zones for calendar semantics, decimal values for money and lab precision, explicit units, enumerations and typed validation. No lossy floating-point money arithmetic. Use optimistic concurrency and per-record revisions, immutable result snapshots where appropriate, and deterministic clock injection.

Suggested tables/entities: workspace, membership, device, record_index, relation, task, task_dependency, event, recurrence_rule, event_exception, calendar, notebook, note, document_block, draft, revision, goal, habit, completion_log, time_entry, alarm, notification_occurrence, project, custom_schema, custom_field, custom_value, attachment, attachment_ref, blob_manifest, trash, outbox_op, applied_op, conflict, sync_cursor, import_run, backup_receipt, automation_rule, automation_run, search_index, saved_view, dashboard_layout, template, settings. Add specific typed kitchen, finance, lab, creative, spreadsheet and presentation tables through schema review. Define key/index coverage from real queries; use foreign keys, migrations, integrity checks and transaction boundaries. Index workspace+type+date, hierarchy parent+order, links in both directions, due/completion state and full text. Use keyset paging where appropriate. SQLite WAL only when supported and tested with chosen driver; measure busy timeouts, recovery, checkpoint and backup behavior. Never allow another device to open the same live SQLite file through a network share.

Shared engines: typed relationship graph and deep-link resolver; record-scoped change streams; text editing, draft recovery and revision history; FTS and saved searches; canonical attachment store and thumbnails; recurrence/timezone service; scheduling and constraints solver; formula parser/typed evaluator with unit and error semantics; reporting and incremental aggregates; template/custom field builder; command registry, undo/redo and transactional automation; permission checking; import/export adapters; backup/restore; diagnostics; offline licensing boundary. One feature may expose its own domain-specific operations via typed APIs; it must not directly mutate another feature's database tables. Domain events emitted after commit and outbox in same transaction. Use asynchronous scoped observation, structured concurrency and cancellation, no global load-all query, no synchronous expensive work on UI thread.

Examples of mandatory integrated journeys: capture a note → linked task → scheduled calendar event → completion → goal/dashboard/report, preserving one task ID; recurring chore → completion → next due date → household fairness; recipe servings → unit conversion → pantry lot shortage → grocery list → purchase → inventory/expense with preview; sample receipt → client → test method → instrument/QC → raw results → calculation → report snapshot → deadline and invoice estimate; book scene → character/location → research note → revision → project milestone; game item → balance formula → playtest evidence → task; photo → one blob → links to diary, lab and project with independent permissions; spreadsheet live data pull → explicitly versioned snapshot → presentation chart; search → deep link → permission check → restore archived result. Prevent duplicate automation on sync replay, cascading delete surprise and relationship cycles. Maintain an integration matrix of producer, consumer, command, permissions, transaction, events, failure/retry and tests. No module is accepted on screenshots alone.

## 5. Fresh initialization, local reliability and recovery
Every edit uses explicit save state (editing, locally saved, failed, sync pending, synced). Draft keyed by workspace/record/editor session; persist safely on focus changes, app pause and crash; do not rebind dirty drafts to another record. Protect simultaneous edits with revision checks and field merge where proven; preserve both versions otherwise. Undo/redo emits compensating commands and is not a raw DB rollback across devices. Trash retention defaults to 30 days; support restore, configurable retention within policy, and confirmed purge, with shared links and attachment reference counting. For laboratory calibration/QC and other evidence records preserve raw measurements and append corrections, never silently rewrite historic accepted results. Encrypt sensitive exports on request using standard vetted authenticated encryption; local DB encryption choice must be benchmarked and key recovery documented. OS secure stores protect keys, not guarantee protection from a compromised device.

Fresh initialization is mandatory. Independently define a version 1 schema, generate a new installation and device identity, create an empty database and new app-specific file/blob directories, and present first-run workspace setup. Do not seed real or legacy records. Optional demonstration records must be synthetic, explicitly opted into, and independently removable. The optional server must initialize a new PostgreSQL database and new volumes; it must not discover, read, mount or reuse old Planaday storage. The new product must never overwrite or uninstall the old product. Test a clean install, a side-by-side install, first-run interrupted initialization, permission failure, empty workspace, new-device pairing and first sync. Future upgrades from version 1 onward must preserve newly created Planaday data with versioned schema migrations, staged validation, backups and recovery tests. New-product restore must validate records, attachment hashes, references, bounds and format versions before committing; handle interrupted restore, wrong keys, malformed archives and path traversal. Retain ordinary standards-based file importing and exporting, not old Planaday-specific formats. The old product and its records remain untouched; no conversion is needed for first release.

Backups: portable versioned manifest, DB consistent snapshot, attachment list, hashes, schema and app versions, settings and allowed drafts, optional authenticated encryption and user-held recovery key. Use SHA-256 or stronger validated standard for the new manifest, distinguish corruption detection from authentication. Stage before publication, verify on creation and restore, use test restores not just successful writes. Schedule backups with storage quotas and retention, keep at least one independent copy, document RPO/RTO and restore drills. Filesystem and DB are separate resources: use staged object publication, recovery journal and orphan collection after grace period; never claim a multi-resource transaction is atomic without a recovery protocol.

## 6. Synchronization and optional server
Each app owns its local DB. Server is optional authority for shared workspace authorization and accepted operations, not a prerequisite for offline use. Protocol version and negotiated capabilities are independent of app versions. HTTPS transport (or explicitly authorized isolated dev loopback), user/device enrollment via short-lived pairing token, scoped revocable device credentials in OS secure storage. Server identity and epoch distinguish replacement, reset and restore. Each outbox operation includes idempotency key, workspace, entity ID/type, operation kind, base revision, schema version, payload/patch, attachment dependencies and device id. Client writes entity and outbox atomically. Server validates authentication, permission, limits, schema and revision, commits event + new revision + operation receipt in one transaction, and returns durable acknowledgement. Clients pull changes by monotonic server cursor, apply idempotently and transactionally, acknowledge cursor only after commit. Offline creates use stable IDs; retries exponential backoff with jitter, never rely on wall-clock order alone. Batch size, payload quotas and resumable attachment transfer are explicit. Attachment hashes are verified after upload and download; authorization checked per workspace and reference, never infer permission from knowing hash. A synced record with required missing attachment must show pending/error, not healthy.

Conflict policies by type: safe disjoint-field merge only with common ancestor and explicit schema semantics; task status and recurrence completion use deterministic occurrence IDs and conflict history; notes preserve competing revisions and three-way merge when proven; collaborative rich editing can use a tested CRDT if needed, not default all data; money, lab results and destructive operations require explicit conflict review. Delete-vs-edit preserves recovery copy; tombstones retained until supported devices acknowledge or explicitly rebootstrap. Workspace ACL revocation denies future pulls; an already offline device may retain previously downloaded bytes until next authorized policy action, so do not promise remote wipe. Selective sync uses membership/filter snapshots and explicit local retention semantics; do not confuse filtered-out with deletion. First pair supports join existing workspace, upload new workspace, or explicit merge preview. Never silently combine unrelated datasets. Server replacement enters reconciliation mode, shows divergence and requires safe rebind or authorized restore. Protocol compatibility tests cover at least current and supported previous versions.

Deploy a single non-root Ktor service, PostgreSQL on internal network, staged blob volume, health/readiness checks, resource limits, pinned image digests and migrations, reverse proxy/TLS or LAN/VPN access. No public PostgreSQL port. Document Docker Compose on TrueNAS/Dockge with persistent host dataset mapping, permissions, backup schedule, restore into disposable instance, upgrade rollback and schema compatibility. Use least-privilege DB role, secret injection, rotation, rate limits, request limits, SSRF protection including redirects/IP resolution, no anonymous record/blob access. Admin diagnostics show version/build SHA/protocol/server ID/epoch/storage, last verified backup and queue errors without logging personal content. API examples: capabilities, pairing, token revoke, pull, push, attachment init/chunk/finalize/get, health, diagnostics, backup admin; choose real routes via ADR. No live cutover without owner approval and independently verified export/restore.

## 7. User interface and platform behavior
One design language, two interaction models. Android: bottom bar with 4 configurable slots, more/search drawer, quick capture, compact/expanded layouts for 320dp through tablet/foldable, safe keyboard insets, native IME, accessible labels, at least 48dp interactive targets where practical, swipe alternatives, state restoration after process death, split-pane tablets, screen readers and scalable type. Windows: sidebar and optional multi-pane inspector, searchable command palette, drag/drop, multiple independent windows, shortcuts, dense tables with adjustable columns, context menus, print preview, DPI scaling and keyboard-only navigation. Honor reduced motion, high contrast, dark/light/custom themes, density, focus order, localization-ready strings and RTL layout testing. Use real platform dialogs, clipboard, file picker, share and camera via adapters; no mandatory web shell. Same record opened in different windows remains consistent. Preserve all native functionality under offline conditions; optional platform integrations display reason when unavailable.

Navigation organized by user-selected workspaces (daily, personal, lab, creative, office, custom); user can pin, hide, reorder, rename, restore and schedule contexts. Every feature discoverable by search, help, command registry and customizable navigation. Do not overwhelm new users: simple default workspace is a view configuration, not a reduced-feature edition. Every included feature exists and works; no placeholder navigation routes. Provide empty states, synthetic onboarding examples, error repair actions, consistent save indicators, undo, search filters, accessible status messages and contextual help. Test actual workflows with phone emulators and Windows input, not screenshots alone.

## 8. Full feature families and expected behavior
Appendix A is the detailed binding register. The following describes feature integration and functional acceptance, not a replacement for any individual row.

### 8.1 Daily planning and scheduling
Today Spread, Home Dashboard, Quick Glance, Quick Capture Inbox, All-in-One Scroll, custom tabs and workspace schedule: configurable sections, free-time finder, daily notes with scheduled sections, priorities, family notices, mood/water/sleep, meals, photos, active timers, weather/daily facts; triage into linked records. Week/Month/Year and Flow Calendar: 1,2,5,10,15,20,30,45 minute and 1,2,3,4 hour zoom plus custom intervals, grid styling, adaptive text, 7 overlap policies, optional prevention, calendar layers, drag, resize, recurring exceptions, all-day/multiday, timezone/DST, ICS feed import/export, stickers/ink. Store wall time and recurrence rules, test DST gaps/folds and exception IDs, drag conflict preview, keyboard accessible editing, calendar virtualization at minute zoom. Task Calendar and intelligent scheduler: Do Date versus Hard Due Date, carryover with original creation age and reschedule count, Favorites, dependencies, priority, estimates, manual locks, preferred end time, Work/Home/Lab/Relationship/custom contexts, distracted/undistracted time tracking, learned averages, schedule suggestions with decision history, resources and travel buffers. Explain infeasible deadlines, never auto-delete conflicting user plans. Chores by room, completion-driven recurring due dates, room health/punctuality, assignment fairness. Goals/habits/routines with history, progress and links. Test long history and completed occurrence duplication across devices.

### 8.2 Mind, documents and time
Recursive notebooks/sections/subsections/sub-notes; practical unbounded depth subject to tested safety limits, cycle-safe moving, breadcrumbs, subtree duplication, sorting, wiki links, backlinks, graph, rich text/typography/paper, pagination and 2-page spreads, searchable attachments, voice memos/sketches, durable draft inbox, revision diffs. Use incremental document model, block/rope/piece-table or platform-native editor after benchmark; preserve cursor/selection/IME composition, undo boundaries, search and export. Test 100k words, cross-note focus without blur, restart and simultaneous edits. Thought Inbox triage; Diary custom categories and 1–10/custom scales, photos, private optional bathroom counters, trend charts; life events and countdowns. Clocks 6+ styles, world clock, alarms/ringtone/volume/chime, timers, Pomodoro, intervals, stopwatch and time ledger, Zen/focus overlay. Use monotonic elapsed time for timers and wall time/timezone for alarms; restore on reboot/time change; Android AlarmManager exact alarm permission only where allowed, notifications permission, WorkManager for deferrable tasks, desktop scheduler/tray, clearly disclose OS restrictions and missed alarm diagnostics. Do not promise guaranteed alarms when phone off or force-stopped.

### 8.3 Physical planner and visual assets
Notebook-style front/back covers, spiral/ring/book/none binding, single/double page spreads, dynamic left/right tabs and top bookmarks, custom page layouts/lines, special pages including interactive 50-state US map, 260+ source stickers and custom art subject to license review, pixel-anchored decorations above/below text, ink/shape drawing, layers, optional live data binding, printing/export. Implement scalable coordinate system, zoom-independent hit tests, spatial index, vector/bitmap renderer, nondestructive originals, unlimited-feeling bookmarks with tested capacity, template serialization. Same document renderer for preview and export where practical; verify layout across DPI and page sizes. Whiteboard: infinite-feeling canvas, pan/zoom, grid/snap, minimap, bookmarks, sticky notes, frames, cards, connectors bound to objects, drawing, SVG/PDF export. Avoid literal infinite storage or unbounded resource usage.

### 8.4 Kitchen and personal life
Recipes and clipper with source provenance and manual offline fallback, scalable portions and unit conversion, lot-level pantry (expiry, zones, quantities, FIFO/FEFO suggestions), Meal Planner, searchable unified Food History, aisle-sorted Groceries, cost/receipt integration, shortages and purchase reconciliation. Household fairness/family profiles with item-level sharing, people/gifts, personal wellness and medications (schedule, skip, refills), workouts, finance/bills/budgets/expense/subscriptions/warranties/wishlists, spaced review/learning, media/hobbies/collections, travel/packing/hosting with offline travel packs, home assets/vehicles/pets/plants/storage bins, reports/digests. Sensitive health/finance/private diary scope isolated by permission and selective sync. Wellness records are organizational aids, not diagnosis or treatment. Financial arithmetic uses currency-aware decimals, explicit FX rate source/time/manual value, no automatic bank transfers. Test one expense referenced by grocery and budget without duplicate totals.

### 8.5 Laboratory workspace
Preserve all 15 original submodules: overview; samples and TAT/custody; subcontract tracking; inventory; purchasing/reorders; budgets; client-specific pricing and templates; method research; email drafts; Lab tasks; meetings creating tasks; calibration/QC; Wet Chemistry vs NIR; work hours/breaks; QMS/SOP/CAPA/PT/training (the source groups budget with purchasing; verify exact navigation count rather than using count as completion proof). Add PF-23–25 and PF-56 full workflows: sample identifiers and custody events, methods/version, instrument/reagent/lot, QC and raw data, replicates, units, authorized corrections, immutable snapshots, statistical references, purchasing approval preview, workload and capacity. Wet/NIR calculations: bias, SD/SEP, RMSE, Pearson r, OLS slope/intercept, R², scatter/residual/trend, documented outlier exclusion, unit mismatch warnings; test paired sample set, missing values, degenerate fit, n limits and precision against independent reference datasets. Validation/uncertainty worksheets, dilution C1V1=C2V2, recovery, %RSD, reference workbook, calibration history. Define any unspecified R-0373 requirements with owner and lab subject-matter review; do not invent accreditation requirements or claim ISO/IEC 17025 or 21 CFR Part 11 compliance. No automatic instrument control or unsafe clinical inference. Protect employer data, scope permissions and export authorizations. Offline research references distinguish cached and live.

### 8.6 Mathematics, office and files
Calculator standard/scientific/lab, symbolic features only if verified, 2D/3D graphing with discontinuity/pole rejection and tangent roots, 23-category unit converter, live/cached/manual currency rates. Shared parser with AST, units and errors, no eval. Test domains, overflow, precision, zero length functions, nested calls and circular references. Spreadsheet workbooks/sheets, ranges/named ranges, formulas and cross-sheet refs (#REF! for missing sheet), dependency DAG, incremental recalculation, sparse/virtual grid, charts, templates, live data pulls with permissions, snapshots, CSV/TSV RFC4180, SpreadsheetML .xls, XLSX import/export with supported-feature fidelity report. Presentation designer, themes/aspect ratio, draggable/rotatable layered objects, images/tables/code, transitions, speaker notes, presenter timer, PPTX import/export, PDF/HTML export with fidelity preview and explicit unsupported features; round-trip fixture files. Structured document/PDF review, annotations, bookmarks, search, text extraction when available, native printing. Universal files nested folders, favorites/archive, Personal/Lab split, global fuzzy search, import/export and file reuse. Photo library capture/import/paste/drop, albums, hash dedup, nondestructive edit recipes and original preservation. Handle large binary files via streaming, thumbnails and bounded caches. Protect against ZIP bombs, malformed formats, external links/macros/scripts and formula injection on CSV export. Do not imply full Microsoft Office compatibility until independently tested; publish exact feature-level interoperability limits.

### 8.7 Projects, creative and programming
Nested projects, dependencies, milestones, timeline/capacity, templates, custom record schema fields and Table/List/Gallery/Kanban/Calendar views, linked resources. Kanban live Tasks/Chores view without duplication, unlimited-feeling custom boards, WIP limits, comments/checklists and board/list/table. Game Dev Studio multi-game GDD, systems, characters/enemies/bosses, skill trees, items/loot, world/lore, logic snippets, XP curve and scenario balancing, asset registry, playtest evidence/release checklist. Book Studio series/books/chapters/scenes, focus manuscript editor, character appearances/arcs, locations/lore/research, continuity checker with evidence, editorial handoff, Markdown/HTML/DOC/PDF and proposed DOCX/EPUB adapters. Code Studio multifile editor, snippets, language grammar, diagnostics, tests, compiler and debugger workflow (R-0322, R-0325): design real trusted external toolchain adapter on desktop, opt-in isolated execution with explicit filesystem/network/process limits, bounded output and timeout; never run arbitrary source in main process. Android safe learning sandbox for supported interpreted languages if security review passes; remote or external toolchain only with consent, honest capability matrix for compiled languages. Google Play executable code restrictions may limit compiler behavior; verify official rules and owner-approved safe alternative before release. No pretending syntax highlighting is compilation/debugging.

### 8.8 Application-wide personalization and intelligence
Custom tabs/spaces, declarative schemas, templates, workspace schedules, pinned preferences, simple/standard/advanced modes, rituals/contexts, themes/density, history/reviews, insights/heatmaps, global search, reports, trash, backup, settings, help, release/health diagnostics. Automation trigger → condition → preview → action with typed permissions, idempotency, rate/cycle limits, human approval for sensitive or irreversible effects. Decision journal links choices to outcomes, historical timelines with permissions, living documents and evidence snapshots, relationship explorer, open template exchange with versioned safe imports and attribution, owner-controlled local assistance with explicit opt-in, deterministic/manual alternatives and no mandatory AI. No external personal information or credentials sent without approval; local suggestions must be explainable and never self-approve consequential writes. All PF packages in Appendix B are first complete-release scope, including historically planned ones; owners approve concrete interpretation where undefined.

## 9. Performance engineering and measurable budgets
Baseline old app on synthetic data if dependencies and build permit; record environment and distinguish historical reports. Profile release builds, not just debug. Provisional performance goals on declared reference hardware: warm launch <1s desktop/<1.5s Android, cold <3s desktop/<4s Android, p95 common local action <100ms, p95 indexed search <300ms on 100k records, smooth 60Hz interaction target (16.7ms frame budget), no blocked UI >100ms on ordinary workflows, visible text echo within 50ms where feasible; measure p50/p95/p99, frame jank, startup, CPU/RAM, battery and disk. These are targets to validate, not guarantees. Declare hardware/OS/build/data/thermal/network details; revise with owner-visible evidence rather than hiding failures. Test baseline 10k records, stress 100k records and 1m where sensible, 100k-word notes, 1GB attachment import streaming, sparse 100k-cell sheets, 10k canvas objects, 10k queued operations and long-running sessions. Use quotas and cancellation; performance degraded gracefully with explicit feedback. No fixed RAM cap without benchmark; budget per engine and measure native/JVM heaps, GC, allocations and background idle consumption. Use lazy module init, paginated query flows, incremental indexes/recalculation, bounded thumbnails, batch writes, structured background jobs, deferred analytics, debounce search (not durable saves), and trace regression. Build Android baseline profiles/R8 as appropriate, desktop bundled minimal runtime and profiler-driven optimizations; no premature C++ rewrite.

## 10. Security, privacy and commercial viability
Threat model local device theft, compromised client, malicious server/peer, unauthorized cross-workspace blob access, injection, traversal, SSRF, malicious document/import/template, oversized payload, executable snippets, key theft, backup tampering, unsafe updates and licensing bypass. Authenticated server routes enforce every object authorization including indirect refs and blobs. Deny default, audit without sensitive content, scoped tokens, rate limits, credential rotation, TLS, encrypted backup options and established cryptographic libraries. No secrets in logs; no forced telemetry; opt-in crash reports scrub data. Explicit workspace sharing, redaction/export previews, data deletion and user-controlled portability. Sensitive professional/health/financial records require stronger boundaries; no diagnosis, financial advice or regulatory claims. Dependency SBOM, license/asset provenance, vulnerability review, SAST and adversarial tests. Sign artifacts, verify update signatures and version compatibility; preserve rollback and database backups. Avoid proprietary or incompatible libraries/assets without license review. No release or production deployment without owner authorization.

Android: Google Play AAB, stable package identity, Play App Signing and secure upload key, monotonic versionCode, release builds, store listing/screenshots, accessibility, privacy policy, Data safety disclosures and permission justification, internal/closed tests and production application where applicable. As checked 25 Nov 2026, official target API page reports Android 16 (API 36) for new general apps and updates; recheck on release. minSdk is separate: choose after compatibility tests, initially evaluate Android 10/API 29 and newer because legacy docs prioritize it; do not promise unsupported old devices. Test recent Android and lowest supported version, background alarm rules, photo picker/scoped storage, 16KB page size where required for native dependencies, notification permission, backup policy and native architecture ABI. Check latest Play policy on digital goods/billing, executable code, privacy, health features and account deletion if applicable. No bypass of Play billing or policy. Website desktop licensing separate from Play; do not assume cross-store purchase rules. Use signed Windows EXE/MSI on clean Windows VM, bundled runtime, Authenticode and timestamping, installation per-user or approved all-user, stable paths, version checks, uninstall preserves user data by default, optional user-authorized purge. Offer signed offline entitlement file (public-key verification only in app) or one-time purchase with offline grace/activation as owner-approved, keep data readable/exportable regardless of license outage. Private signing keys stay out of client/repo. Choose pricing, payment vendor, support/refund policy and cross-device licensing only with owner approval. Server deployment separate, versioned images and license terms. Website downloads with checksum/signature, release notes and verified support docs; no public hosting setup or purchases without authorization.

## 11. Test strategy, acceptance and release gates
Every requirement ID and PF package maps to concrete acceptance criteria, code paths, Android/desktop UI routes, storage, sync semantics, permissions, failure/boundary cases, test IDs, actual results and independent verdict. Use statuses DISCOVERED, SPECIFIED, IMPLEMENTED, DEVELOPER_TESTED, INDEPENDENTLY_ACCEPTED, BLOCKED, NOT_APPLICABLE_APPROVED. Do not mark historical evidence as new. Each supported user workflow exercises create/read/update/delete/recovery where applicable, restart, offline, cross-device sync, integration, permission denied, invalid inputs, accessibility, performance and actual release artifact. Explicitly record N/A rationale and safe replacement for browser/iOS-specific rows superseded by owner platform scope; retain original rows and IDs. The owner has explicitly excluded legacy Planaday data migration and compatibility bridges. Preserve any historical row that references them, classify only that obsolete behavior as NOT_APPLICABLE_APPROVED with decision O-004 (no old data migration), and test the new-product equivalent for current backups, file interchange and upgrades. Do not use O-004 to exclude unrelated feature behavior. Test deterministic domain rules and property-based invariants; integration tests on real SQLite/PostgreSQL; contract golden fixtures for recurrence/units/format and sync; Android Compose instrumentation + emulator and physical phone, desktop UI tests with keyboard/mouse/IME, 320dp compact and tablet/landscape, Windows DPI and packaged binaries. Cross-platform test scenarios run both directions with network drops, duplicate/reordered messages, server replacement, concurrent edit/delete, missing/corrupt attachments, full disk, power loss, interrupted new-product schema upgrade, timezone shift, reboot, backup corruption and restore. Security tests include unauthorized enumeration/get/put of blob and record, cross-user relationships, token expiry, malicious imports and sandbox escape attempts. Lab/calculation tests independently sourced numerical references with tolerances and method/units. No live production or personal data tests. Record exact commands, environment, commit, artifact hashes, duration, failures and unavailable dependencies. Independent reviewer must be different from author and verify actual evidence; no self-certification. Release gates: all required rows accepted or owner-approved explicit exception, all 56 PF accepted or explicitly scoped platform equivalent, no open P0/P1 data/security/correctness defect, all new-product schema upgrade and backup drills pass, stable native packages install/update/uninstall, no unexplained performance regressions, policy/asset review and owner authorization. Absolute bug-free guarantees are impossible; never hide residual risks.

## 12. Development program — full scope from inception
Workstream A (day one): freeze read-only source, scan all 152 files and their functions/dependencies, recover original detailed scope, map all 652+56 IDs, known gaps, issue evidence, architecture decisions, setup and prototype gates. Use original requirements even where tests absent. Create all domain boundaries, navigation map, schemas/contracts and cross-feature event graph immediately; no empty public feature screens. Workstream B: shared database, identities, commands, durable drafts, document/graphics/math engines, security, fresh database initialization, backup and sync protocol foundation; create Android/Windows shells and isolated server concurrently with fixture and test infrastructure. Gate foundational transactions, IDs and contract tests before dependent persistence code. Workstream C: concurrent vertical feature bundles: planning/time; knowledge/documents; physical planner/whiteboard; kitchen/life/finance; lab/math; office/files/photos; project/creative/code; customization/automation/reports. Each bundle implements both platforms, persistence, sync, permission checks, integrations and tests. Assign engine owners and avoid concurrent edits to shared modules; merge through CI. Workstream D: all PF expansions and additional N-series targets integrated in same first complete release, not deferred merely because advanced. Build compiler/debugger and format parity behind security/compatibility review; unresolved requirement details escalated without blocking unrelated domains. Workstream E: cross-feature integration and adversarial testing runs throughout, followed by full coverage review, install packages, new-product upgrade/recovery drills, performance budgets, accessibility and policy checks. Workstream F: owner-approved release and maintenance, signed upgrades, protocol compatibility and regression suite. Do not assign arbitrary calendar/feature deadlines; measure work. Commit coherent changes, use review branches, review independently at architecture/security/data durability and release gates. Developer may continue beyond initial analysis in the same activation where verified gates and tools permit; if an independent decision is needed, stop only affected risky path and proceed on safe isolated work. Do not pretend unlimited work can fit into one model session. Use durable checkpoints and handoff rather than restarting or discarding verified work.

## 13. Additional future-ready features and proposed expansion register
All PF-01–56 are binding first full-release requirements. The following new proposals (N-001 onward) are additional first-release targets, not claims from original source. Design extension points, assess feasibility now and implement alongside relevant workstreams, not as disconnected add-ons. Where a proposed item cannot be delivered safely or is incompatible with platform policy, document the evidence and obtain an owner-approved equivalent or exception. Preserve privacy and platform boundaries.
- N-001 Universal linked-record picker and reverse references with typed integrity and permissions; N-002 custom fields with computed values, validators and schema versions; N-003 universal templates with parameter prompts and safe cloning; N-004 command palette plus user-defined keyboard shortcuts; N-005 workspace layout profiles with import/export and recovery.
- N-006 full-text search across authorized notes, files and metadata with saved filters; N-007 universal reporting/pivot engine with drill-down and explainable calculations; N-008 cross-feature dependency graph and change impact preview; N-009 record-level audit history, compare and selective recovery; N-010 offline universal capture with share-sheet, camera, voice and inbox dedup.
- N-011 scheduling constraint solver with energy/context/travel/resource availability; N-012 what-if schedules, compare alternatives and undo; N-013 focus dashboard with estimate calibration and privacy; N-014 calendar subscription cache and duplicate-safe ICS import; N-015 widgets and quick actions where OS supports them.
- N-016 large-document editor with outline, citations, footnotes, TOC, styles and cross-document links; N-017 document annotation and OCR with local model option and manual fallback; N-018 DOCX/EPUB structured export and fidelity checks; N-019 portable notebook and markdown interoperability; N-020 universal evidence snapshots with source provenance.
- N-021 household shared shopping and conflict-safe quantities; N-022 budget forecasting and configurable account-independent categories; N-023 inventory barcode scanning and expiry reminders; N-024 media collections with user-defined scoring, progress and lists; N-025 study spaced repetition with evidence/learning goals.
- N-026 lab configurable method worksheets and uncertainty reference fixtures; N-027 instrument maintenance and consumable lot traceability; N-028 configurable sample workflow transitions with authorized sign-off; N-029 lot/capacity dashboards and report templates; N-030 lab data export with method version and raw-result provenance (not regulatory certification).
- N-031 project Gantt/timeline and critical dependency paths; N-032 reusable game balance scenario simulator with deterministic seeds; N-033 manuscript scene/character continuity graph and editorial workflow; N-034 asset provenance and license registry; N-035 visual automation builder with dry run, impact preview and loop guard.
- N-036 opt-in local assistance provider interface with no external AI required; N-037 local privacy-preserving recommendation/ranking with user-controlled learning/reset; N-038 extension SDK with capability manifest, version compatibility and no arbitrary host execution; N-039 safe template marketplace/import format without mandated network or purchases; N-040 external integration adapter registry (calendars, files, authorized feeds) with scope and revocation.
- N-041 selective sync by workspace and attachment size policy; N-042 transfer inspector and conflict resolution workspace; N-043 local application health dashboard with test backup/restore; N-044 portable open data export manifest with no vendor lock-in; N-045 offline desktop entitlement with signed updates and read/export survival; N-046 secure sharing bundles with explicit ACL/recovery; N-047 multi-user role templates with audit trails; N-048 encrypted export and documented key recovery; N-049 accessibility customization and localization extension points; N-050 diagnostic benchmark suite and user-controlled support package.

## 14. Source audit, gap closure and canonical project records
Inspect each actual source file and symbols, callers, inputs/outputs, state, persistence, security, performance, requirements and tests; map recovered behavioral contracts to newly designed module responsibilities and independently written acceptance tests. Do not prescribe one-to-one source replacements or port code. Appendix D is a starting inventory, not a completed function-level audit. Review legacy `src/core/AppContext.tsx`, `backup.ts`, `server/blobs.ts`, `sync.ts`, `SafeTextEditor.tsx`, `db.ts`, `server/auth.ts` for whole-table subscriptions, restore ordering, authorization, draft binding, conflict handling and backup recovery; these are investigation targets, not new proven exploits. The previous historical tests reported 178 passing across 12 test files, but no runtime tests were rerun in preparation of this specification. Archive includes 13 test/setup files; inventory test scenarios. Historical execution is optional only in an independently authorized sandbox without old data. New tests and their own counts determine acceptance. Appendix C retains the 28 issue entries; do not treat developer-verified repairs as accepted without regression tests. Explicitly close R-0072 bookmarks capacity, R-0233 nested hierarchy capacity, R-0322 compiler, R-0325 debugging, R-0373 unspecified lab requirements, R-0455 presentation import, C-0061 release identity. Define practical limits via measured workloads and owner acceptance, not literal infinity. Audit all 414 unverified and 143 partial rows; missing may be broader than named seven.

Canonical repository docs: `PLANADAY_NATIVE_MASTER.md` (this plan plus accepted decisions), `REQUIREMENT_TRACEABILITY.csv` (exact 652 rows with new implementation/test fields, original labels preserved), `EXPANSION_TRACEABILITY.csv` (56), `NEW_FEATURE_REGISTRY.csv` (N IDs and approvals), `LEGACY_REFERENCE_LEDGER.csv` (152 baseline plus discovered), `FEATURE_INTEGRATION_MATRIX.csv`, `MISSING_FEATURES.md`, `ARCHITECTURE_DECISIONS.md`, `DATA_MODEL.md`, `SYNC_PROTOCOL.md`, `SECURITY_THREAT_MODEL.md`, `DESIGN_SYSTEM.md`, `PERFORMANCE_BASELINE.md`, `TEST_PLAN.md`, `TEST_RESULTS.md`, `RELEASE_GATES.md`, `KNOWN_ISSUES.md`, `DEVELOPMENT_ROADMAP.md`, `CHANGELOG.md`, `AI_HANDOFF.md`. Keep legacy files read-only outside the new repository. Maintain `GREENFIELD_PROVENANCE.md` and `GREENFIELD_CHECKS.md` as binding evidence of new implementation provenance. Every agent reads canonical master, active ADRs, handoff and relevant requirements before work. Handoff includes commit hashes, actual changed files, tests, observed limitations, exact next bounded task and review status. Author cannot independently accept own change. No unrecorded changes to scope, licensing, dependencies, package identity or data semantics. Automate count/unique-ID and coverage checks in CI; fail on missing IDs, missing required tests, invalid schema compatibility or unapproved scope reduction. At end of every session report owner-readable progress by feature and platform, not just file counts or compilation.

## 15. Official reference links and provisional choices
Official references checked 25 November 2026: Android KMP Room setup https://developer.android.com/kotlin/multiplatform/room ; bundled SQLite https://developer.android.com/kotlin/multiplatform/sqlite ; Play target SDK https://developer.android.com/google/play/requirements/target-sdk ; Play testing requirements https://support.google.com/googleplay/android-developer/answer/14151465 ; release tracks https://support.google.com/googleplay/android-developer/answer/9859348 ; Compose Desktop packaging documentation https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-native-distribution.html (verify current instructions). Recheck applicable policies before publishing. Versions, minSdk, final desktop editor, server database, licensing, performance budgets and individual N feature feasibility and any explicit exceptions remain conditional on validation/owner decisions. No actual new app, native build, runtime or deployment is certified by this planning document.


## Appendix A. All 652 legacy requirements — exact names and historical status
The requirement text below is reproduced from the supplied matrix; the historical classification is abbreviated by the key. All rows must remain individually traceable. The original CSV in the clean archive retains the complete 20 columns, including evidence, source locations, platform-specific results and remaining limitations. New tests must establish new statuses. Browser/iOS implementation-specific criteria require an explicitly approved native equivalent or N/A, never deletion. Owner decision O-004 excludes legacy data migration only: retain any affected historical ID, document the exact excluded behavior and apply the remaining requirement to the fresh product. Historical descriptions are evidence, not permission to read or migrate old data. Grouping follows the source feature column; requirement IDs preserve original identity.

**Status key:** V = VERIFIED COMPLETE; M = MOSTLY COMPLETE; D = developer-verified pending independent verification; P = PARTIALLY IMPLEMENTED; U = NOT VERIFIED; B = BLOCKED — REQUIREMENT; N = NOT IMPLEMENTED; X = BROKEN.


### Calendar (26)

- **R-0001 [P]** — Minute-level zoom

- **R-0002 [P]** — Time intervals from approximately one minute to four hours

- **R-0003 [P]** — Major and minor gridlines

- **R-0004 [P]** — Adjustable gridline spacing

- **R-0005 [D]** — Calendar text scaling

- **R-0006 [P]** — Event positioning

- **R-0007 [D]** — Event resizing

- **R-0008 [U]** — Event drag-and-drop

- **R-0009 [U]** — Event overlap handling

- **R-0010 [U]** — Optional prevention of overlapping events

- **R-0011 [U]** — Multiple calendar support

- **R-0012 [P]** — Separate task calendars

- **R-0013 [U]** — Combined calendar views

- **R-0014 [P]** — Event descriptions

- **R-0015 [P]** — Recurring events

- **R-0016 [P]** — All-day events

- **R-0017 [U]** — Multi-day events

- **R-0018 [U]** — Event notifications

- **R-0019 [U]** — Time zone handling

- **R-0020 [U]** — Daylight saving time

- **R-0021 [U]** — Month transitions

- **R-0022 [U]** — Year transitions

- **R-0023 [U]** — Leap years

- **R-0024 [P]** — Calendar zooming

- **R-0025 [M]** — Mobile calendar navigation

- **R-0026 [U]** — Ink and sticker behavior where implemented


### Tasks and scheduling (28)

- **R-0027 [U]** — Manual reordering

- **R-0028 [U]** — Drag-and-drop

- **R-0029 [U]** — Custom sorting

- **R-0030 [U]** — Priority sorting

- **R-0031 [U]** — Priority editing

- **R-0032 [U]** — Favorite limits

- **R-0033 [U]** — Configurable favorite limits

- **R-0034 [U]** — Disabled favorite limits

- **R-0035 [U]** — Recurrence

- **R-0036 [U]** — Task dependencies

- **R-0037 [U]** — Task completion

- **R-0038 [U]** — Incomplete task carryover

- **R-0039 [U]** — Preservation of original creation dates

- **R-0040 [U]** — Task age

- **R-0041 [U]** — Overdue calculations

- **R-0042 [U]** — Completion timestamps

- **R-0043 [U]** — Manual time tracking

- **R-0044 [U]** — Timer-based time tracking

- **R-0045 [U]** — Estimated duration

- **R-0046 [U]** — Actual duration

- **R-0047 [U]** — Distraction tracking

- **R-0048 [U]** — Average duration calculations

- **R-0049 [U]** — Historical statistics

- **R-0050 [U]** — Daily preferred end times

- **R-0051 [U]** — Context-based scheduling

- **R-0052 [U]** — Automatic task calendar placement

- **R-0053 [U]** — Automatic rescheduling

- **R-0054 [U]** — Work and personal schedule separation


### Physical Planner and decoration (40)

- **R-0055 [U]** — Page creation

- **R-0056 [U]** — Page duplication

- **R-0057 [U]** — Page reordering

- **R-0058 [U]** — Page flipping

- **R-0059 [U]** — Two-page spreads

- **R-0060 [U]** — Page dimensions

- **R-0061 [U]** — Custom page layouts

- **R-0062 [U]** — Multiple pages per day

- **R-0063 [U]** — Custom lines and spacing

- **R-0064 [U]** — Covers

- **R-0065 [U]** — Bindings

- **R-0066 [U]** — Side tabs

- **R-0067 [U]** — Top bookmarks

- **R-0068 [U]** — Tab movement between left and right pages

- **R-0069 [U]** — Bookmark navigation

- **R-0070 [U]** — Custom tab designs

- **R-0071 [U]** — Unlimited or practically unrestricted tab creation

- **R-0072 [B]** — Unlimited or practically unrestricted bookmark creation

- **R-0073 [U]** — Zooming

- **R-0074 [U]** — Panning

- **R-0075 [U]** — Handwriting

- **R-0076 [U]** — Typing

- **R-0077 [U]** — Drawing

- **R-0078 [U]** — Stickers

- **R-0079 [U]** — Layering

- **R-0080 [U]** — Element locking

- **R-0081 [U]** — Undo and redo

- **R-0082 [U]** — Saving

- **R-0083 [D]** — Synchronization

- **R-0084 [U]** — Expanding sections

- **R-0085 [U]** — Collapsing sections

- **R-0086 [U]** — Adding text

- **R-0087 [U]** — Removing text

- **R-0088 [U]** — Resizing windows

- **R-0089 [U]** — Rotating a phone

- **R-0090 [U]** — Changing zoom

- **R-0091 [U]** — Switching themes

- **R-0092 [U]** — Changing layouts

- **R-0093 [U]** — Moving between devices

- **R-0094 [D]** — Synchronizing information


### Notifications (14)

- **R-0095 [P]** — Foreground notifications

- **R-0096 [P]** — Background notifications

- **R-0097 [P]** — Lock-screen notifications

- **R-0098 [P]** — Scheduled reminders

- **R-0099 [U]** — Recurring reminders

- **R-0100 [U]** — Missed reminders

- **R-0101 [U]** — Multiple simultaneous reminders

- **R-0102 [U]** — Notification cancellation

- **R-0103 [U]** — Snoozing

- **R-0104 [U]** — Quiet hours

- **R-0105 [U]** — Device restart recovery

- **R-0106 [U]** — Offline notification behavior

- **R-0107 [U]** — Permission changes

- **R-0108 [U]** — Notification delivery after application updates


### Photos and attachments (24)

- **R-0109 [U]** — Camera permissions

- **R-0110 [U]** — Camera capture

- **R-0111 [U]** — Photo library access

- **R-0112 [P]** — Image importing

- **R-0113 [U]** — Multiple photo selection

- **R-0114 [U]** — Clipboard importing

- **R-0115 [U]** — Photo editing

- **R-0116 [U]** — Cropping

- **R-0117 [U]** — Rotation

- **R-0118 [U]** — Image transformations

- **R-0119 [U]** — Drawing and annotations

- **R-0120 [U]** — Sticker placement

- **R-0121 [U]** — Original image preservation

- **R-0122 [U]** — Edited image versions

- **R-0123 [P]** — Image storage

- **R-0124 [U]** — Thumbnail generation

- **R-0125 [U]** — Large image handling

- **R-0126 [D]** — Upload reliability

- **R-0127 [D]** — Interrupted upload recovery

- **R-0128 [D]** — Image synchronization

- **R-0129 [U]** — Photo reuse across records

- **R-0130 [U]** — Attachment removal

- **R-0131 [U]** — Permanent image deletion

- **R-0132 [P]** — Backup and recovery


### Laboratory operations (39)

- **R-0133 [P]** — Sample receiving

- **R-0134 [P]** — Analysis assignments

- **R-0135 [U]** — Testing deadlines

- **R-0136 [U]** — Subcontract tracking

- **R-0137 [U]** — Result receipt

- **R-0138 [U]** — Inventory consumption

- **R-0139 [U]** — Reordering supplies

- **R-0140 [U]** — Purchase history

- **R-0141 [U]** — Historical prices

- **R-0142 [U]** — Laboratory budgets

- **R-0143 [U]** — Client pricing templates

- **R-0144 [U]** — Different prices for different methods

- **R-0145 [U]** — Different prices for different commodities

- **R-0146 [U]** — Client-specific turnaround times

- **R-0147 [U]** — Recurring quality control activities

- **R-0148 [U]** — Instrument maintenance

- **R-0149 [U]** — Calibration schedules

- **R-0150 [U]** — Meeting records

- **R-0151 [U]** — Meeting action items

- **R-0152 [U]** — Analyst schedules

- **R-0153 [U]** — Work hours

- **R-0154 [U]** — Breaks

- **R-0155 [U]** — Weekly and monthly responsibilities

- **R-0156 [U]** — Research documentation

- **R-0157 [U]** — Wet chemistry and NIR comparisons

- **R-0158 [U]** — Units

- **R-0159 [U]** — Significant figures

- **R-0160 [U]** — Missing values

- **R-0161 [U]** — Replicate results

- **R-0162 [U]** — Regression

- **R-0163 [U]** — Bias

- **R-0164 [U]** — Standard deviation

- **R-0165 [U]** — R-squared

- **R-0166 [U]** — Root mean square error

- **R-0167 [U]** — Statistical sample sizes

- **R-0168 [U]** — Historical records

- **R-0169 [U]** — Audit trails

- **R-0170 [U]** — Record corrections

- **R-0171 [U]** — Data export


### Cross-workspace integration (13)

- **R-0172 [P]** — A meal entered in Today appearing in Food History

- **R-0173 [U]** — A task scheduled in Today appearing on the Task Calendar

- **R-0174 [P]** — A Kanban task updating the corresponding task list

- **R-0175 [U]** — A meeting action item appearing in Laboratory Tasks

- **R-0176 [U]** — An inventory purchase updating the Laboratory Budget

- **R-0177 [P]** — A completed chore updating historical statistics

- **R-0178 [U]** — A calculator result being inserted into a spreadsheet

- **R-0179 [U]** — A spreadsheet chart appearing in a presentation

- **R-0180 [U]** — A game character being linked to skills and equipment

- **R-0181 [U]** — A book character being linked to multiple chapters

- **R-0182 [U]** — A photograph being reused across multiple records

- **R-0183 [U]** — A code snippet being linked to a game system

- **R-0184 [U]** — A laboratory calibration appearing in reminders


### Customization (24)

- **R-0185 [U]** — Reorder tabs

- **R-0186 [U]** — Create tab groups

- **R-0187 [U]** — Move tabs between groups

- **R-0188 [U]** — Hide tabs

- **R-0189 [U]** — Restore tabs

- **R-0190 [U]** — Rename groups

- **R-0191 [U]** — Reorder widgets

- **R-0192 [U]** — Resize widgets

- **R-0193 [U]** — Collapse sections

- **R-0194 [U]** — Hide sections

- **R-0195 [U]** — Change themes

- **R-0196 [U]** — Change fonts

- **R-0197 [U]** — Change colors

- **R-0198 [U]** — Change interface density

- **R-0199 [U]** — Change creation form complexity

- **R-0200 [U]** — Customize favorites

- **R-0201 [U]** — Customize limits

- **R-0202 [U]** — Create custom schedules

- **R-0203 [U]** — Create custom workspaces

- **R-0204 [U]** — Automatically switch layouts based on schedules

- **R-0205 [U]** — Create custom fields

- **R-0206 [U]** — Create custom categories

- **R-0207 [U]** — Create reusable templates

- **R-0208 [U]** — Add descriptions throughout the application


### Personal productivity (21)

- **R-0209 [U]** — Quick Glance dashboard

- **R-0210 [U]** — Today tab

- **R-0211 [P]** — Calendar

- **R-0212 [P]** — Task Calendar

- **R-0213 [P]** — Tasks and to-do lists

- **R-0214 [P]** — Chores

- **R-0215 [P]** — Habits

- **R-0216 [P]** — Goals

- **R-0217 [P]** — Reminders

- **R-0218 [P]** — Notifications

- **R-0219 [P]** — Timers

- **R-0220 [U]** — Stopwatches

- **R-0221 [U]** — Quick Capture Inbox

- **R-0222 [P]** — Recurring responsibilities

- **R-0223 [P]** — Automatic task scheduling

- **R-0224 [P]** — Time tracking

- **R-0225 [U]** — Task priorities

- **R-0226 [U]** — Favorites

- **R-0227 [U]** — Historical statistics

- **R-0228 [U]** — Custom schedules

- **R-0229 [U]** — Schedule-based workspaces


### Notes and personal organization (22)

- **R-0230 [D]** — Notes

- **R-0231 [U]** — Nested notes

- **R-0232 [U]** — Notebooks

- **R-0233 [B]** — Unlimited hierarchical organization

- **R-0234 [D]** — Rich text formatting

- **R-0235 [P]** — Word counts

- **R-0236 [U]** — Page-based navigation

- **R-0237 [U]** — Continuous scrolling

- **R-0238 [D]** — Version history

- **R-0239 [P]** — Manual saving

- **R-0240 [D]** — Automatic saving

- **R-0241 [D]** — Daily notes

- **R-0242 [U]** — Custom note sections

- **R-0243 [U]** — Daily diary

- **R-0244 [U]** — Daily ratings

- **R-0245 [U]** — Custom rating scales

- **R-0246 [U]** — Personal wellness tracking

- **R-0247 [U]** — Daily photos

- **R-0248 [U]** — Food tracking

- **R-0249 [U]** — Meal planning

- **R-0250 [U]** — Recipes

- **R-0251 [U]** — Food history


### Planner layout and decoration (19)

- **R-0252 [U]** — Physical-style digital planner

- **R-0253 [U]** — Planner page creation

- **R-0254 [U]** — Custom page layouts

- **R-0255 [U]** — Page flipping

- **R-0256 [U]** — Two-page spreads

- **R-0257 [U]** — Custom covers

- **R-0258 [U]** — Custom bindings

- **R-0259 [U]** — Side tabs

- **R-0260 [U]** — Top bookmarks

- **R-0261 [U]** — Special planner pages

- **R-0262 [U]** — Handwriting

- **R-0263 [U]** — Ink

- **R-0264 [U]** — Stickers

- **R-0265 [U]** — Drawing tools

- **R-0266 [U]** — Decoration anchoring

- **R-0267 [U]** — Decoration layering

- **R-0268 [U]** — Clock designs

- **R-0269 [U]** — Planner templates

- **R-0270 [U]** — Zooming and panning


### Office and mathematics (17)

- **R-0271 [P]** — Standard calculator

- **R-0272 [P]** — Scientific calculator

- **R-0273 [P]** — Graphing calculator

- **R-0274 [P]** — Advanced mathematical functions

- **R-0275 [P]** — Statistical calculations

- **R-0276 [U]** — Currency conversion

- **R-0277 [P]** — Unit conversion

- **R-0278 [P]** — Spreadsheet creation

- **R-0279 [D]** — Spreadsheet formulas

- **R-0280 [U]** — Spreadsheet charts

- **R-0281 [D]** — Spreadsheet import and export

- **R-0282 [P]** — Presentation creation

- **R-0283 [P]** — Slide editing

- **R-0284 [U]** — Presentation playback

- **R-0285 [D]** — Presentation import and export

- **R-0286 [P]** — Document management

- **R-0287 [P]** — File management


### Creative and project work (42)

- **R-0288 [P]** — General project management

- **R-0289 [U]** — Custom project templates

- **R-0290 [U]** — Custom databases

- **R-0291 [U]** — Kanban boards

- **R-0292 [P]** — Infinite whiteboards

- **R-0293 [U]** — Sticky notes

- **R-0294 [U]** — Advanced drawing tools

- **R-0295 [U]** — Diagrams

- **R-0296 [U]** — Mind maps

- **R-0297 [U]** — Game development workspace

- **R-0298 [U]** — Game systems

- **R-0299 [U]** — Game logic

- **R-0300 [U]** — Characters

- **R-0301 [U]** — Enemies

- **R-0302 [U]** — Skills

- **R-0303 [U]** — Abilities

- **R-0304 [U]** — Clothing

- **R-0305 [U]** — Items and equipment

- **R-0306 [U]** — Game balancing

- **R-0307 [P]** — Book writing workspace

- **R-0308 [U]** — Book series

- **R-0309 [P]** — Books

- **R-0310 [U]** — Chapters

- **R-0311 [U]** — Scenes

- **R-0312 [U]** — Character databases

- **R-0313 [U]** — Heroes

- **R-0314 [U]** — Villains

- **R-0315 [U]** — Animals

- **R-0316 [U]** — Story arcs

- **R-0317 [U]** — Summaries

- **R-0318 [U]** — Worldbuilding

- **R-0319 [U]** — Story continuity

- **R-0320 [U]** — Code editor

- **R-0321 [U]** — Code snippet library

- **R-0322 [N]** — Compiler

- **R-0323 [D]** — Code execution

- **R-0324 [U]** — Code testing

- **R-0325 [N]** — Debugging

- **R-0326 [U]** — Camera access

- **R-0327 [U]** — Photo importing

- **R-0328 [U]** — Photo editing

- **R-0329 [U]** — Photo placement throughout the application


### Laboratory management (44)

- **R-0330 [P]** — Laboratory dashboard

- **R-0331 [P]** — Sample tracking

- **R-0332 [P]** — Testing deadlines

- **R-0333 [U]** — Subcontract sample tracking

- **R-0334 [U]** — Outstanding subcontract results

- **R-0335 [P]** — Instrument-specific inventory

- **R-0336 [P]** — Laboratory supply inventory

- **R-0337 [P]** — Office supply inventory

- **R-0338 [P]** — Laboratory budget

- **R-0339 [P]** — Client-specific pricing sheets

- **R-0340 [P]** — Analysis-specific pricing

- **R-0341 [P]** — Commodity-specific pricing

- **R-0342 [P]** — Method-specific pricing

- **R-0343 [U]** — Turnaround times

- **R-0344 [P]** — Reusable client pricing templates

- **R-0345 [U]** — Method research

- **R-0346 [U]** — Email drafts

- **R-0347 [U]** — Laboratory tasks

- **R-0348 [U]** — Laboratory chores and habits

- **R-0349 [P]** — Meeting management

- **R-0350 [P]** — Meeting notes

- **R-0351 [P]** — Meeting attendees

- **R-0352 [P]** — Meeting action items

- **R-0353 [U]** — Instrument calibration

- **R-0354 [U]** — Weight and balance checks

- **R-0355 [U]** — Oven quality control

- **R-0356 [U]** — Pipette checks

- **R-0357 [U]** — Equipment maintenance

- **R-0358 [U]** — Recurring quality control requirements

- **R-0359 [P]** — Wet chemistry versus NIR comparisons

- **R-0360 [P]** — Analytical statistics

- **R-0361 [U]** — Purchasing history

- **R-0362 [U]** — Vendor history

- **R-0363 [P]** — Historical product pricing

- **R-0364 [U]** — Personal work hours

- **R-0365 [P]** — Analyst work hours

- **R-0366 [U]** — Break tracking

- **R-0367 [U]** — Timecard reviews

- **R-0368 [P]** — Weekly analyst meetings

- **R-0369 [P]** — Monthly safety meetings

- **R-0370 [U]** — Laboratory reports

- **R-0371 [P]** — SOP management

- **R-0372 [P]** — Training records

- **R-0373 [B]** — Other laboratory requirements defined in the master specification


### Application-wide systems (29)

- **R-0374 [P]** — Mobile layouts

- **R-0375 [P]** — Desktop layouts

- **R-0376 [P]** — Portrait orientation

- **R-0377 [P]** — Landscape orientation

- **R-0378 [U]** — Desktop-style mobile zoom

- **R-0379 [M]** — Navigation

- **R-0380 [U]** — Custom tab groups

- **R-0381 [U]** — Tab reordering

- **R-0382 [U]** — Widget reordering

- **R-0383 [U]** — Custom workspaces

- **R-0384 [U]** — Custom fields

- **R-0385 [P]** — Universal descriptions

- **R-0386 [U]** — Universal editing

- **R-0387 [D]** — Attachments

- **R-0388 [U]** — Universal search

- **R-0389 [U]** — Record relationships

- **R-0390 [U]** — Data sharing between features

- **R-0391 [U]** — User preferences

- **R-0392 [P]** — Storage

- **R-0393 [P]** — Backups

- **R-0394 [D]** — Synchronization

- **R-0395 [P]** — Offline operation

- **R-0396 [P]** — Background operation

- **R-0397 [P]** — Data recovery

- **R-0398 [P]** — Security

- **R-0399 [U]** — Feature directory

- **R-0400 [U]** — Application guide

- **R-0401 [U]** — Changelog

- **R-0402 [P]** — Application branding


### Calculator result correctness (21)

- **R-0403 [P]** — Basic arithmetic

- **R-0404 [P]** — Operator precedence

- **R-0405 [P]** — Parentheses

- **R-0406 [P]** — Fractions

- **R-0407 [P]** — Decimal precision

- **R-0408 [P]** — Trigonometry

- **R-0409 [P]** — Angle units

- **R-0410 [P]** — Logarithms

- **R-0411 [P]** — Exponents

- **R-0412 [P]** — Roots

- **R-0413 [P]** — Complex numbers

- **R-0414 [P]** — Statistical functions

- **R-0415 [P]** — Matrices

- **R-0416 [P]** — Graphing

- **R-0417 [D]** — Equation solving

- **R-0418 [P]** — Numerical derivatives

- **R-0419 [P]** — Numerical integration

- **R-0420 [P]** — Invalid mathematical expressions

- **R-0421 [P]** — Division by zero

- **R-0422 [P]** — Domain errors

- **R-0423 [P]** — Very large and very small values


### Spreadsheet lifecycle (18)

- **R-0424 [D]** — Formula accuracy

- **R-0425 [P]** — Cell references

- **R-0426 [D]** — Cross-sheet references

- **R-0427 [P]** — Formula dependencies

- **R-0428 [P]** — Recalculation

- **R-0429 [P]** — Circular reference detection

- **R-0430 [P]** — Error handling

- **R-0431 [U]** — Sorting

- **R-0432 [U]** — Filtering

- **R-0433 [U]** — Copy and paste

- **R-0434 [U]** — Cell formatting

- **R-0435 [U]** — Charts

- **R-0436 [U]** — Large worksheets

- **R-0437 [U]** — Saving

- **R-0438 [U]** — Reopening

- **R-0439 [D]** — Importing

- **R-0440 [D]** — Exporting

- **R-0441 [U]** — Version history


### Presentation lifecycle (15)

- **R-0442 [P]** — Slide creation

- **R-0443 [P]** — Slide editing

- **R-0444 [U]** — Object positioning

- **R-0445 [U]** — Image placement

- **R-0446 [U]** — Formatting

- **R-0447 [U]** — Text rendering

- **R-0448 [U]** — Object layering

- **R-0449 [U]** — Transitions

- **R-0450 [U]** — Animations

- **R-0451 [U]** — Playback

- **R-0452 [U]** — Presenter view

- **R-0453 [P]** — Saving

- **R-0454 [P]** — Reopening

- **R-0455 [N]** — Importing

- **R-0456 [D]** — Exporting


### Game development (15)

- **R-0457 [U]** — Multiple game systems

- **R-0458 [U]** — Characters

- **R-0459 [U]** — Enemy types

- **R-0460 [U]** — Skills

- **R-0461 [U]** — Clothing

- **R-0462 [U]** — Equipment

- **R-0463 [U]** — Crafting recipes

- **R-0464 [U]** — Character statistics

- **R-0465 [U]** — Game logic

- **R-0466 [U]** — Code snippets

- **R-0467 [U]** — Development tasks

- **R-0468 [U]** — Bugs

- **R-0469 [U]** — Milestones

- **R-0470 [U]** — Balancing spreadsheets

- **R-0471 [U]** — Whiteboard diagrams


### Book writing (13)

- **R-0472 [P]** — Multiple books

- **R-0473 [P]** — Multiple chapters

- **R-0474 [U]** — Scenes

- **R-0475 [U]** — Heroes

- **R-0476 [U]** — Villains

- **R-0477 [U]** — Supporting characters

- **R-0478 [U]** — Animals

- **R-0479 [U]** — Story arcs

- **R-0480 [U]** — Summaries

- **R-0481 [U]** — Timelines

- **R-0482 [U]** — Locations

- **R-0483 [U]** — Worldbuilding

- **R-0484 [U]** — Character relationships


### Whiteboard (10)

- **R-0485 [U]** — Drawings

- **R-0486 [U]** — Text

- **R-0487 [U]** — Sticky notes

- **R-0488 [U]** — Stickers

- **R-0489 [U]** — Photographs

- **R-0490 [U]** — Shapes

- **R-0491 [U]** — Connectors

- **R-0492 [U]** — Nested frames

- **R-0493 [U]** — Linked records

- **R-0494 [U]** — Diagrams


### Individual named modules, platform requirements and record types

- **C-0001 [M]** — All-in-One Scroll: Open All-in-One Scroll from the configured navigation and render its workspace.

- **C-0002 [M]** — Quick Glance: Open Quick Glance from the configured navigation and render its workspace.

- **C-0003 [M]** — Home: Open Home from the configured navigation and render its workspace.

- **C-0004 [M]** — Today Spread: Open Today Spread from the configured navigation and render its workspace.

- **C-0005 [M]** — Physical Planner: Open Physical Planner from the configured navigation and render its workspace.

- **C-0006 [M]** — Week / Month / Year: Open Week / Month / Year from the configured navigation and render its workspace.

- **C-0007 [M]** — Flow Calendar: Open Flow Calendar from the configured navigation and render its workspace.

- **C-0008 [M]** — Task Calendar: Open Task Calendar from the configured navigation and render its workspace.

- **C-0009 [M]** — Tasks: Open Tasks from the configured navigation and render its workspace.

- **C-0010 [M]** — Chores: Open Chores from the configured navigation and render its workspace.

- **C-0011 [M]** — History & Reviews: Open History & Reviews from the configured navigation and render its workspace.

- **C-0012 [M]** — Reports & Analytics: Open Reports & Analytics from the configured navigation and render its workspace.

- **C-0013 [M]** — Life Events: Open Life Events from the configured navigation and render its workspace.

- **C-0014 [M]** — Lab Management: Open Lab Management from the configured navigation and render its workspace.

- **C-0015 [M]** — Calculator & Graph: Open Calculator & Graph from the configured navigation and render its workspace.

- **C-0016 [M]** — Spreadsheets: Open Spreadsheets from the configured navigation and render its workspace.

- **C-0017 [M]** — Presentations: Open Presentations from the configured navigation and render its workspace.

- **C-0018 [M]** — Kanban Boards: Open Kanban Boards from the configured navigation and render its workspace.

- **C-0019 [M]** — Whiteboard Canvas: Open Whiteboard Canvas from the configured navigation and render its workspace.

- **C-0020 [P]** — Projects & Records: Open Projects & Records from the configured navigation and render its workspace.

- **C-0021 [M]** — Game Dev Studio: Open Game Dev Studio from the configured navigation and render its workspace.

- **C-0022 [M]** — Book Studio: Open Book Studio from the configured navigation and render its workspace.

- **C-0023 [M]** — Code Sandbox: Open Code Sandbox from the configured navigation and render its workspace.

- **C-0024 [P]** — Camera & Photos: Open Camera & Photos from the configured navigation and render its workspace.

- **C-0025 [P]** — Universal Files: Open Universal Files from the configured navigation and render its workspace.

- **C-0026 [M]** — Notes & Graph: Open Notes & Graph from the configured navigation and render its workspace.

- **C-0027 [M]** — Thought Inbox: Open Thought Inbox from the configured navigation and render its workspace.

- **C-0028 [M]** — Diary & Mood: Open Diary & Mood from the configured navigation and render its workspace.

- **C-0029 [M]** — Spaced Review: Open Spaced Review from the configured navigation and render its workspace.

- **C-0030 [M]** — Clock & World: Open Clock & World from the configured navigation and render its workspace.

- **C-0031 [M]** — Smart Alarms: Open Smart Alarms from the configured navigation and render its workspace.

- **C-0032 [M]** — Timers & Pomodoro: Open Timers & Pomodoro from the configured navigation and render its workspace.

- **C-0033 [M]** — Stopwatch & Ledger: Open Stopwatch & Ledger from the configured navigation and render its workspace.

- **C-0034 [M]** — Countdowns: Open Countdowns from the configured navigation and render its workspace.

- **C-0035 [M]** — Recipes: Open Recipes from the configured navigation and render its workspace.

- **C-0036 [M]** — Pantry Lots: Open Pantry Lots from the configured navigation and render its workspace.

- **C-0037 [M]** — Meal Planner: Open Meal Planner from the configured navigation and render its workspace.

- **C-0038 [M]** — Groceries: Open Groceries from the configured navigation and render its workspace.

- **C-0039 [M]** — Goals: Open Goals from the configured navigation and render its workspace.

- **C-0040 [M]** — Habits & Routines: Open Habits & Routines from the configured navigation and render its workspace.

- **C-0041 [M]** — Household Fairness: Open Household Fairness from the configured navigation and render its workspace.

- **C-0042 [M]** — People & Gifts: Open People & Gifts from the configured navigation and render its workspace.

- **C-0043 [M]** — Health & Meds: Open Health & Meds from the configured navigation and render its workspace.

- **C-0044 [M]** — Finance & Bills: Open Finance & Bills from the configured navigation and render its workspace.

- **C-0045 [M]** — Media & Hobbies: Open Media & Hobbies from the configured navigation and render its workspace.

- **C-0046 [M]** — Travel & Hosting: Open Travel & Hosting from the configured navigation and render its workspace.

- **C-0047 [M]** — Home, Pets & Bins: Open Home, Pets & Bins from the configured navigation and render its workspace.

- **C-0048 [M]** — Custom Spaces: Open Custom Spaces from the configured navigation and render its workspace.

- **C-0049 [M]** — Rituals & Contexts: Open Rituals & Contexts from the configured navigation and render its workspace.

- **C-0050 [M]** — Insights & Heatmaps: Open Insights & Heatmaps from the configured navigation and render its workspace.

- **C-0051 [M]** — Automations: Open Automations from the configured navigation and render its workspace.

- **C-0052 [M]** — Guide & Help: Open Guide & Help from the configured navigation and render its workspace.

- **C-0053 [M]** — Trash (30d): Open Trash (30d) from the configured navigation and render its workspace.

- **C-0054 [M]** — Settings & Themes: Open Settings & Themes from the configured navigation and render its workspace.

- **C-0055 [P]** — Native note autocorrection: Apply one Gboard correction mid-line and retain text after forced restart.

- **C-0056 [V]** — Docker nested persistence: Replace a container while retaining separately mounted SQLite and blob paths.

- **C-0057 [V]** — Docker SQLite snapshot placement: Write an on-demand SQLite snapshot under mounted blobs.

- **C-0058 [D]** — Blob transport types: Accept and return ordinary binary and declared media types by hash.

- **C-0059 [V]** — Sync idempotency: Duplicate submission of the same synthetic operation remains a single operation.

- **C-0060 [V]** — Calendars ICS export envelope: Download an empty calendar as a named UTF-8 ICS with valid envelope.

- **C-0061 [X]** — Release and live health identity: Expose a live version that can be reconciled with source and image.

- **C-0062 [D]** — Chromium offline lazy workspace: Open a previously unvisited studio after confirmed service-worker control and server shutdown.

- **C-0063 [D]** — Android minimum WebView: Current app boots on old but installable Android configuration.

- **C-0064 [D]** — Firefox app boot: Open application shell on tested Firefox and enter the workspace.

- **C-0065 [D]** — Book document downloads: Download valid Markdown and Word document content for a named book.

- **C-0066 [D]** — Whiteboard SVG and code downloads: Return actual SVG or source bytes under a portable filename.

- **C-0067 [D]** — Blob failure recovery status: Show pending or failure when the server fails to persist a referenced attachment.

- **C-0068 [P]** — Planner asset, stickers and canvas: Display the Physical Planner decoration controls and canvas.

- **C-0069 [P]** — Offline modes: Select the account-free local operating modes and persist user settings.

- **C-0070 [P]** — task record: Create, persist and round-trip the declared task record with its applicable fields.

- **C-0071 [P]** — chore record: Create, persist and round-trip the declared chore record with its applicable fields.

- **C-0072 [P]** — habit record: Create, persist and round-trip the declared habit record with its applicable fields.

- **C-0073 [P]** — goal record: Create, persist and round-trip the declared goal record with its applicable fields.

- **C-0074 [U]** — routine record: Create, persist and round-trip the declared routine record with its applicable fields.

- **C-0075 [P]** — event record: Create, persist and round-trip the declared event record with its applicable fields.

- **C-0076 [U]** — timeblock record: Create, persist and round-trip the declared timeblock record with its applicable fields.

- **C-0077 [P]** — note record: Create, persist and round-trip the declared note record with its applicable fields.

- **C-0078 [U]** — notebook record: Create, persist and round-trip the declared notebook record with its applicable fields.

- **C-0079 [U]** — thought record: Create, persist and round-trip the declared thought record with its applicable fields.

- **C-0080 [U]** — diary record: Create, persist and round-trip the declared diary record with its applicable fields.

- **C-0081 [U]** — life_event record: Create, persist and round-trip the declared life_event record with its applicable fields.

- **C-0082 [U]** — countdown record: Create, persist and round-trip the declared countdown record with its applicable fields.

- **C-0083 [U]** — alarm record: Create, persist and round-trip the declared alarm record with its applicable fields.

- **C-0084 [U]** — timer record: Create, persist and round-trip the declared timer record with its applicable fields.

- **C-0085 [U]** — interval_preset record: Create, persist and round-trip the declared interval_preset record with its applicable fields.

- **C-0086 [U]** — recipe record: Create, persist and round-trip the declared recipe record with its applicable fields.

- **C-0087 [P]** — pantry_lot record: Create, persist and round-trip the declared pantry_lot record with its applicable fields.

- **C-0088 [U]** — pantry_zone record: Create, persist and round-trip the declared pantry_zone record with its applicable fields.

- **C-0089 [U]** — meal_plan record: Create, persist and round-trip the declared meal_plan record with its applicable fields.

- **C-0090 [U]** — food_log record: Create, persist and round-trip the declared food_log record with its applicable fields.

- **C-0091 [P]** — grocery_item record: Create, persist and round-trip the declared grocery_item record with its applicable fields.

- **C-0092 [U]** — person record: Create, persist and round-trip the declared person record with its applicable fields.

- **C-0093 [U]** — household_member record: Create, persist and round-trip the declared household_member record with its applicable fields.

- **C-0094 [U]** — medication record: Create, persist and round-trip the declared medication record with its applicable fields.

- **C-0095 [U]** — workout record: Create, persist and round-trip the declared workout record with its applicable fields.

- **C-0096 [U]** — wellness_log record: Create, persist and round-trip the declared wellness_log record with its applicable fields.

- **C-0097 [U]** — bill record: Create, persist and round-trip the declared bill record with its applicable fields.

- **C-0098 [U]** — budget_envelope record: Create, persist and round-trip the declared budget_envelope record with its applicable fields.

- **C-0099 [U]** — expense record: Create, persist and round-trip the declared expense record with its applicable fields.

- **C-0100 [U]** — warranty record: Create, persist and round-trip the declared warranty record with its applicable fields.

- **C-0101 [U]** — wishlist_item record: Create, persist and round-trip the declared wishlist_item record with its applicable fields.

- **C-0102 [U]** — review_card record: Create, persist and round-trip the declared review_card record with its applicable fields.

- **C-0103 [U]** — media_item record: Create, persist and round-trip the declared media_item record with its applicable fields.

- **C-0104 [U]** — hobby_project record: Create, persist and round-trip the declared hobby_project record with its applicable fields.

- **C-0105 [U]** — trip record: Create, persist and round-trip the declared trip record with its applicable fields.

- **C-0106 [U]** — packing_template record: Create, persist and round-trip the declared packing_template record with its applicable fields.

- **C-0107 [U]** — party record: Create, persist and round-trip the declared party record with its applicable fields.

- **C-0108 [U]** — gift record: Create, persist and round-trip the declared gift record with its applicable fields.

- **C-0109 [U]** — home_asset record: Create, persist and round-trip the declared home_asset record with its applicable fields.

- **C-0110 [U]** — vehicle record: Create, persist and round-trip the declared vehicle record with its applicable fields.

- **C-0111 [U]** — pet_plant record: Create, persist and round-trip the declared pet_plant record with its applicable fields.

- **C-0112 [U]** — storage_item record: Create, persist and round-trip the declared storage_item record with its applicable fields.

- **C-0113 [P]** — custom_space record: Create, persist and round-trip the declared custom_space record with its applicable fields.

- **C-0114 [P]** — decor_item record: Create, persist and round-trip the declared decor_item record with its applicable fields.

- **C-0115 [U]** — custom_sticker record: Create, persist and round-trip the declared custom_sticker record with its applicable fields.

- **C-0116 [U]** — automation_rule record: Create, persist and round-trip the declared automation_rule record with its applicable fields.

- **C-0117 [U]** — spread_template record: Create, persist and round-trip the declared spread_template record with its applicable fields.

- **C-0118 [U]** — saved_view record: Create, persist and round-trip the declared saved_view record with its applicable fields.

- **C-0119 [U]** — period_reflection record: Create, persist and round-trip the declared period_reflection record with its applicable fields.

- **C-0120 [U]** — triage_item record: Create, persist and round-trip the declared triage_item record with its applicable fields.

- **C-0121 [U]** — context_mode record: Create, persist and round-trip the declared context_mode record with its applicable fields.

- **C-0122 [U]** — quick_capture record: Create, persist and round-trip the declared quick_capture record with its applicable fields.

- **C-0123 [U]** — planner_book record: Create, persist and round-trip the declared planner_book record with its applicable fields.

- **C-0124 [U]** — planner_page record: Create, persist and round-trip the declared planner_page record with its applicable fields.

- **C-0125 [P]** — lab_sample record: Create, persist and round-trip the declared lab_sample record with its applicable fields.

- **C-0126 [U]** — lab_subcontract record: Create, persist and round-trip the declared lab_subcontract record with its applicable fields.

- **C-0127 [U]** — lab_inventory record: Create, persist and round-trip the declared lab_inventory record with its applicable fields.

- **C-0128 [U]** — lab_order record: Create, persist and round-trip the declared lab_order record with its applicable fields.

- **C-0129 [U]** — lab_budget record: Create, persist and round-trip the declared lab_budget record with its applicable fields.

- **C-0130 [U]** — lab_price_entry record: Create, persist and round-trip the declared lab_price_entry record with its applicable fields.

- **C-0131 [U]** — lab_price_template record: Create, persist and round-trip the declared lab_price_template record with its applicable fields.

- **C-0132 [U]** — lab_research record: Create, persist and round-trip the declared lab_research record with its applicable fields.

- **C-0133 [U]** — lab_email_draft record: Create, persist and round-trip the declared lab_email_draft record with its applicable fields.

- **C-0134 [U]** — lab_task record: Create, persist and round-trip the declared lab_task record with its applicable fields.

- **C-0135 [U]** — lab_meeting record: Create, persist and round-trip the declared lab_meeting record with its applicable fields.

- **C-0136 [U]** — lab_calibration record: Create, persist and round-trip the declared lab_calibration record with its applicable fields.

- **C-0137 [U]** — lab_wet_nir record: Create, persist and round-trip the declared lab_wet_nir record with its applicable fields.

- **C-0138 [U]** — lab_work_hour record: Create, persist and round-trip the declared lab_work_hour record with its applicable fields.

- **C-0139 [U]** — lab_qms_item record: Create, persist and round-trip the declared lab_qms_item record with its applicable fields.

- **C-0140 [U]** — calc_saved record: Create, persist and round-trip the declared calc_saved record with its applicable fields.

- **C-0141 [P]** — spreadsheet record: Create, persist and round-trip the declared spreadsheet record with its applicable fields.

- **C-0142 [P]** — presentation record: Create, persist and round-trip the declared presentation record with its applicable fields.

- **C-0143 [P]** — project record: Create, persist and round-trip the declared project record with its applicable fields.

- **C-0144 [U]** — custom_schema record: Create, persist and round-trip the declared custom_schema record with its applicable fields.

- **C-0145 [U]** — custom_entity record: Create, persist and round-trip the declared custom_entity record with its applicable fields.

- **C-0146 [P]** — game_project record: Create, persist and round-trip the declared game_project record with its applicable fields.

- **C-0147 [U]** — game_entity record: Create, persist and round-trip the declared game_entity record with its applicable fields.

- **C-0148 [P]** — book_project record: Create, persist and round-trip the declared book_project record with its applicable fields.

- **C-0149 [P]** — book_chapter record: Create, persist and round-trip the declared book_chapter record with its applicable fields.

- **C-0150 [U]** — book_entity record: Create, persist and round-trip the declared book_entity record with its applicable fields.

- **C-0151 [U]** — kanban_board record: Create, persist and round-trip the declared kanban_board record with its applicable fields.

- **C-0152 [U]** — kanban_card record: Create, persist and round-trip the declared kanban_card record with its applicable fields.

- **C-0153 [P]** — whiteboard record: Create, persist and round-trip the declared whiteboard record with its applicable fields.

- **C-0154 [U]** — code_project record: Create, persist and round-trip the declared code_project record with its applicable fields.

- **C-0155 [U]** — code_snippet record: Create, persist and round-trip the declared code_snippet record with its applicable fields.

- **C-0156 [P]** — photo_asset record: Create, persist and round-trip the declared photo_asset record with its applicable fields.

- **C-0157 [U]** — file_folder record: Create, persist and round-trip the declared file_folder record with its applicable fields.

- **C-0158 [U]** — Custom tab instance: Create a custom tab and open its configured instance route.


## Appendix B. All 56 expansion packages — first complete-release scope
Original package names, priorities, dependencies and historical status notes from PLANADAY_EXPANSION_CATALOG.md. All packages now belong to the first full product scope, even if their previous classification was planned. Dependencies are legacy references, not necessarily new code dependencies.

- **PF-01** | P0 — **Reliable atomic saves**. Dependencies: `PD-001`, `PD-016`. Historical: **Implemented (Developer-verified)** — per-record lock + atomic Dexie transactions + record-bound drafts

- **PF-02** | P0 — **Verified attachment delivery**. Dependencies: `PD-002`, `PD-015`, `CONT-003`, `CONT-004`. Historical: **Implemented (Developer-verified)** — RegExp MIME parser + SHA-256 verification + bidirectional downloads

- **PF-03** | P0 — **Verified restoration and server rebind**. Dependencies: `PD-003`. Historical: **Implemented (Developer-verified)** — SQLite `server_id`/`sync_epoch` + replacement-server reconciliation

- **PF-04** | P0 — **Authenticated workspace pairing**. Dependencies: `PD-004`. Historical: **Implemented (Developer-verified)** — owner passphrase, revocable Bearer tokens, 10-min pairing codes, workspace scoping + Settings UI

- **PF-05** | P1 — **Build-versioned offline assets**. Dependencies: `PD-009`. Historical: **Implemented (Developer-verified)** — Vite `offline-manifest.json` (19 assets) + buildId-scoped `sw.js` precaching

- **PF-06** | P1 — **Responsive forms and accessible controls**. Dependencies: `PD-011`, `PD-012`, `PD-013`, `PD-019`. Historical: **Implemented (Developer-verified)** — `.dp-studio-split`/`.dp-studio-split-3` + labeled ≥28px targets (live 320px browser pass pending)

- **PF-07** | P1 — **Long-note editor performance and recovery**. Dependencies: `PD-010`, `PD-016`. Historical: **Implemented (Developer-verified)** — deferred O(n) counters, 12k autoGrow cap, durable `db.drafts` (live 100k browser timing pending)

- **PF-08** | P1 — **File interchange and calculator correctness**. Dependencies: `PD-006`, `PD-007`, `PD-008`, `CONT-006`, `CONT-007`, `PD-018`. Historical: **Implemented (Developer-verified)** — 117/117 math/unit/sheet/lab refs, RFC 4180 CSV, typed `downloadFile`, rich HTML deck export

- **PF-09** | P2 — **Typed relationships and deep links**. Dependencies: `PF-01`. Historical: Partially Implemented (unified `EntityLink` + project links)

- **PF-10** | P2 — **Incremental search and saved search scopes**. Dependencies: `PF-01`. Historical: Partially Implemented (`GlobalSearchDialog` + Personal/Lab filter)

- **PF-11** | P2 — **Schedule proposals with decision history**. Dependencies: `R-0005`..`R-0054`. Historical: Partially Implemented (`TaskCalendarView` auto-placement + `FlowCalendarView` drag-resize)

- **PF-12** | P2 — **Shared availability, resources, and travel buffers**. Dependencies: `PF-04`, `PF-11`. Historical: Planned

- **PF-13** | P2 — **Project dependencies, timelines, and capacity**. Dependencies: `PF-09`, `PF-11`. Historical: Partially Implemented (`ProjectsView` milestones & subprojects)

- **PF-14** | P2 — **Daily workspace templates and onboarding**. Dependencies: `PF-06`. Historical: Partially Implemented (`OnboardingModal` + workspace schedules)

- **PF-15** | P3 — **Kitchen costs, inventory lots, and meal reconciliation**. Dependencies: `PF-09`. Historical: Partially Implemented (`PantryView`, `MealsView`, `GroceryView`)

- **PF-16** | P3 — **Household budgets and receipt ledger**. Dependencies: `PF-02`, `PF-09`. Historical: Partially Implemented (`FinanceView`)

- **PF-17** | P3 — **Private wellness measurements and trends**. Dependencies: `PF-04`. Historical: Partially Implemented (`DiaryView` 1–10 + bathroom logs + trends)

- **PF-18** | P3 — **Shared household responsibilities**. Dependencies: `PF-04`, `PF-09`. Historical: Partially Implemented (`HouseholdHealthView` + Family Profiles)

- **PF-19** | P3 — **Offline travel packs with time zones**. Dependencies: `PF-05`. Historical: Partially Implemented (`TravelPeopleHomeView`)

- **PF-20** | P3 — **Collections and reading templates**. Dependencies: `PF-09`. Historical: Partially Implemented (`LearningMediaView` + Custom Record Types)

- **PF-21** | P3 — **Learning plans and evidence notebooks**. Dependencies: `PF-09`, `PF-02`. Historical: Partially Implemented (`LearningMediaView` + Notes)

- **PF-22** | P3 — **Structured document and PDF review**. Dependencies: `PF-02`, `PF-08`. Historical: Planned

- **PF-23** | P3 — **Laboratory custody and sample execution**. Dependencies: `PF-01`, `PF-04`. Historical: Partially Implemented (`LabWorkspaceView` 15 tabs)

- **PF-24** | P3 — **Laboratory QC, method validation, and uncertainty**. Dependencies: `PF-23`, `PF-08`. Historical: Partially Implemented (`LabWetNirView` + Calibration/QC)

- **PF-25** | P3 — **Laboratory procurement and capacity planning**. Dependencies: `PF-23`. Historical: Partially Implemented (`LabWorkspaceView` inventory/purchasing/budget)

- **PF-26** | P3 — **Game design entities and balancing scenarios**. Dependencies: `PF-08`, `PF-09`. Historical: Partially Implemented (`GameDevView`)

- **PF-27** | P3 — **Playtesting, assets, and release evidence**. Dependencies: `PF-02`, `PF-26`. Historical: Partially Implemented (`GameDevView` dev progress)

- **PF-28** | P3 — **Scene-based writing and story continuity**. Dependencies: `PF-07`, `PF-09`. Historical: Partially Implemented (`BookStudioView` + Continuity Checker)

- **PF-29** | P3 — **Editorial and publishing handoff**. Dependencies: `PF-08`, `PF-28`. Historical: Partially Implemented (`BookStudioView` `.md`/`.doc`/PDF export)

- **PF-30** | P3 — **Trusted programming learning workbench**. Dependencies: `PD-005`. Historical: **Implemented (Developer-verified)** — null-origin CSP iframe sandbox + unit tests + external toolchain export

- **PF-31** | P3 — **Visual record canvas and printable planner**. Dependencies: `PF-06`, `PF-09`. Historical: Partially Implemented (`DigitalPlannerView` + `WhiteboardView`)

- **PF-32** | P3 — **Reusable photo originals and editing recipes**. Dependencies: `PF-02`, `PD-019`. Historical: Partially Implemented (`PhotoStudioView` non-destructive copies)

- **PF-33** | P3 — **Scientific calculation worksheets**. Dependencies: `PF-08`, `PF-24`. Historical: Partially Implemented (`CalculatorView` Lab Mode + Save to Note)

- **PF-34** | P4 — **Previewable user automation**. Dependencies: `PF-01`, `PF-04`. Historical: Partially Implemented (`StudioExtrasView` automations)

- **PF-35** | P4 — **Owner-controlled local assistance**. Dependencies: `PF-04`, `PD-014`. Historical: Planned (guarded by explicit owner opt-in & outbound policy)

- **PF-36** | P4 — **Declarative workspace builder**. Dependencies: `PF-09`, `PF-06`. Historical: Partially Implemented (`ProjectsView` Custom Record Builder + Custom Tabs)

- **PF-37** | P2 — **Native capture and device actions**. Dependencies: `PF-02`, `CONT-005`. Historical: Partially Implemented (Camera input + Quick Capture)

- **PF-38** | P2 — **Desktop project panes and keyboard workflows**. Dependencies: `PD-020`, `PF-06`. Historical: Partially Implemented (Command Palette + shortcuts + Electron 39)

- **PF-39** | P4 — **Permission-controlled historical timelines**. Dependencies: `PF-04`, `PF-09`. Historical: Partially Implemented (`HistoryView` + `ReportsView`)

- **PF-40** | P4 — **Living documents and evidence snapshots**. Dependencies: `PF-01`, `PF-08`. Historical: Partially Implemented (Live Spreadsheet Data Pull + Note checkpoints)

- **PF-41** | P4 — **Decision journal linked to outcomes**. Dependencies: `PF-09`. Historical: Planned

- **PF-42** | P0 — **Recovery rehearsal with receipts**. Dependencies: `PF-03`, `CONT-009`. Historical: **Implemented (Developer-verified)** — ZIP v3 SHA-256 manifest + SQLite `.manifest.json` + automated replacement-server restore test (live TrueNAS check pending owner authorization)

- **PF-43** | P4 — **Private evidence-aware relationship explorer**. Dependencies: `PF-04`, `PF-09`. Historical: Partially Implemented (`NotesView` Knowledge Graph)

- **PF-44** | P4 — **Adaptive dashboards with pinned preferences**. Dependencies: `PF-14`. Historical: Partially Implemented (`HomeDashboard`, `QuickGlanceView`, workspace schedules)

- **PF-45** | P4 — **Open template exchange with ownership controls**. Dependencies: `PF-04`, `PF-36`. Historical: Planned (untrusted template import guard required)

- **PF-46** | P1 — **Native and web notification reliability**. Dependencies: `CONT-005`. Historical: Partially Implemented (`nativeAlarms.ts` + diagnostics; physical Doze check pending)

- **PF-47** | P1 — **Accessible application-wide diagnostics**. Dependencies: `CONT-004`, `PD-017`. Historical: Partially Implemented (Sync queue, blob receipts, server storage & release version diagnostics in Settings)

- **PF-48** | P3 — **External development tool integration**. Dependencies: `PD-005`, `PD-020`. Historical: Partially Implemented (`CodeStudioView` external toolchain download)

- **PF-49** | P4 — **Personal time calibration and scheduling estimates**. Dependencies: `PF-11`. Historical: Partially Implemented (`TaskCalendarView` learned task averages + distracted stats)

- **PF-50** | P3 — **Advanced file-format and engine compatibility**. Dependencies: `PF-08`. Historical: Planned (progressive `.xlsx`/`.pptx`/`.pdf` parity)

- **PF-51** | P1 — **Attachment transfer receipts and retry visibility**. Dependencies: `PD-002`, `CONT-004`. Historical: **Implemented (Developer-verified)** — `BlobTransferReceipt` tracking in `sync.ts` + expandable receipts panel in `SettingsView.tsx`

- **PF-52** | P1 — **Release and storage identity diagnostics**. Dependencies: `PD-017`, `CONT-001`, `CONT-009`. Historical: **Implemented (Developer-verified)** — `PLANADAY_RELEASE`, `/api/health` + `/api/storage/info` version/serverId/syncEpoch surfaced in Settings

- **PF-53** | P2 — **Native compatibility and notification diagnostics**. Dependencies: `CONT-005`, `PF-46`. Historical: Partially Implemented (Chrome 74 compatibility guard in `index.html` + runtime platform check in Settings; physical device run pending)

- **PF-54** | P2 — **Export and formula previews with fidelity checks**. Dependencies: `PD-006`, `PD-008`, `PD-018`. Historical: Partially Implemented (RFC 4180 CSV, typed `downloadFile`, rich HTML deck export; interactive pre-export preview modal planned)

- **PF-55** | P2 — **Draft recovery inbox**. Dependencies: `PD-016`. Historical: **Implemented (Developer-verified)** — durable Dexie v2 `db.drafts` + 1-click Restore/Discard Draft Recovery Inbox banner in `NotesView.tsx`

- **PF-56** | P3 — **Scientific and laboratory reference workbook**. Dependencies: `PF-08`, `PF-24`. Historical: Planned (next expansion batch)


## Appendix C. Historical issue regression register
Issue title, priority and source-side classification. Retain detailed evidence and historical repair descriptions from PLANADAY_ISSUES.md; repeat actual regressions in new architecture. Missing production evidence is not a new pass.

- **PD-001** | P0 — Concurrent updates lose a disjoint edit. Historical: **Developer-verified (Pending independent verification)**.

- **PD-002** | P1 — Clean second client lacks synced attachment bytes. Historical: **Developer-verified (Pending independent verification)**.

- **PD-003** | P1 — Restored client reports sync success against empty replacement server. Historical: **Developer-verified (Pending independent verification)**.

- **PD-004** | P0 — Self-hosted records and configuration lack application-layer access control. Historical: **Developer-verified (Pending independent verification)**.

- **PD-005** | P0 — Code worker lacks a separate security boundary for untrusted snippets. Historical: **Developer-verified (Pending independent verification)**.

- **PD-006** | P1 — Quoted CSV round trip changes cell boundaries. Historical: **Developer-verified (Pending independent verification)**.

- **PD-007** | P1 — Graph root finder mistakes a pole for a root and misses a tangency. Historical: **Developer-verified (Pending independent verification)**.

- **PD-008** | P1 — Several download callers reverse filename and data. Historical: **Developer-verified (Pending independent verification)**.

- **PD-009** | P1 — Initial offline assets do not include build chunks. Historical: **Developer-verified (Pending independent verification)**.

- **PD-010** | P1 — Large multiline note insertion sluggish / timed out at 50k–100k words. Historical: **Developer-verified (Pending independent verification)**.

- **PD-011** | P2 — File Hub filter horizontally clipped on 320px phones (146px overflow). Historical: **Developer-verified (Pending independent verification)**.

- **PD-012** | P2 — Projects & Records retains 2-column minimum on 320px phones (21px overflow). Historical: **Developer-verified (Pending independent verification)**.

- **PD-013** | P2 — Planner, Diary and Calendar need accessible control affordances. Historical: **Developer-verified (Pending independent verification)**.

- **PD-014** | P1 — Remote integration destinations need an outbound policy. Historical: **Developer-verified (Pending independent verification)**.

- **PD-015** | P1 — Server blob publication does not verify content identity. Historical: **Developer-verified (Pending independent verification)**.

- **PD-016** | P0 — Focused note draft writes into next selected note without blur. Historical: **Developer-verified (Pending independent verification)**.

- **PD-017** | P2 — Release metadata and CI gates are inconsistent. Historical: **Developer-verified (Pending independent verification)**.

- **PD-018** | P2 — HTML presentation export omits rendering fidelity. Historical: **Developer-verified (Pending independent verification)**.

- **PD-019** | P2 — Photo Studio uses wide 2-column grid on 320px phones (32px overflow). Historical: **Developer-verified (Pending independent verification)**.

- **PD-020** | P1 — Electron packaging pins EOL Electron 35. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-001** | P1 — Live production deployment differs from audited source. Historical: **Blocked (Owner Action)**.

- **CONT-003** | P1 — Ordinary PNG, JPEG, and WAV blob PUT returns HTTP 415. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-004** | P1 — Blob transfer failure falsely reports `synced` with 0 pending. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-005** | P1 — Android 10 (API 29) stock WebView 74 hits `Unexpected token ?`. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-006** | P1 — Missing-sheet formula silently returns active sheet data. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-007** | P2 — Composite formula & zero-length `LEFT`/`RIGHT` semantics fail. Historical: **Developer-verified (Pending independent verification)**.

- **CONT-008** | P1 — Firefox startup stays blocked on "Opening Planaday workspace…". Historical: **Developer-verified (Pending independent verification)**.

- **CONT-009** | P1 — Production backup coverage unverified. Historical: **Blocked (Owner Action)**.


## Appendix D. Complete read-only source inventory and new responsibility map
152 reference files; these are hypotheses about which independently designed new modules need the recovered behavior, not source-port instructions or verified function-by-function findings. Count lines from original bytes. Inspect every symbol and dependency to extract behavioral contracts and failure cases. The reference-only archive retains unchanged historical files; none may be copied into the new implementation.

- `.dockerignore` — 10 lines; deployment/; redesign server containers and persistent volumes.

- `.github/workflows/build-apps.yml` — 128 lines; CI build/test/release workflows, no automatic production publish.

- `.github/workflows/docker-publish.yml` — 53 lines; CI build/test/release workflows, no automatic production publish.

- `.gitignore` — 12 lines; native application bootstrap or repository configuration; inspect relevant requirements; independently author new configuration.

- `CHANGELOG.md` — 24 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `Dockerfile` — 38 lines; deployment/; redesign server containers and persistent volumes.

- `PLANADAY_DEVELOPMENT_ROADMAP.md` — 18 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_EXPANSION_CATALOG.md` — 67 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_FEATURE_MATRIX.csv` — 653 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_ISSUES.md` — 43 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_MASTER.md` — 65 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_RELEASE_CHECKLIST.md` — 16 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_TEST_RESULTS.md` — 54 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `PLANADAY_WORKING.md` — 58 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `README.md` — 37 lines; immutable reference + canonical docs/traceability; preserve requirements.

- `capacitor.config.json` — 15 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.

- `desktop/main.cjs` — 211 lines; apps/desktop + distribution; extract installer requirements; independently author native packaging and desktop shell.

- `desktop/package.json` — 36 lines; apps/desktop + distribution; extract installer requirements; independently author native packaging and desktop shell.

- `desktop/preload.cjs` — 7 lines; apps/desktop + distribution; extract installer requirements; independently author native packaging and desktop shell.

- `docker-compose.yml` — 24 lines; deployment/; redesign server containers and persistent volumes.

- `index.html` — 45 lines; native resources/offline packaging equivalent; reference behavior only; independently author native resources and UI, no web shell.

- `package-lock.json` — 4,522 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.

- `package.json` — 45 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.

- `public/icons/icon.svg` — 22 lines; native resources/offline packaging equivalent; reference behavior only; independently author native resources and UI, no web shell.

- `public/manifest.webmanifest` — 51 lines; native resources/offline packaging equivalent; reference behavior only; independently author native resources and UI, no web shell.

- `public/sw.js` — 92 lines; native resources/offline packaging equivalent; reference behavior only; independently author native resources and UI, no web shell.

- `server/alarmScheduler.ts` — 272 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/auth.ts` — 301 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/blobs.ts` — 116 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/db.ts` — 267 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/icsProxy.ts` — 46 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/index.ts` — 538 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/outboundPolicy.ts` — 172 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `server/recipeProxy.ts` — 209 lines; server/app + storage/auth/sync/operations; redesign transport and access control.

- `src/App.tsx` — 189 lines; native application bootstrap or repository configuration; inspect relevant requirements; independently author new configuration.

- `src/components/Common/ClockWidget.tsx` — 227 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/CommandPalette.tsx` — 152 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/GlobalAlarmRingModal.tsx` — 112 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/GlobalSearchDialog.tsx` — 271 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/ModalDialog.tsx` — 93 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/QuickCaptureDialog.tsx` — 277 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/SafeTextEditor.tsx` — 505 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/SortFilterBar.tsx` — 184 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/TabSettingsDrawer.tsx` — 65 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/ToastStack.tsx` — 38 lines; shared design tokens + platform-specific UI components.

- `src/components/Common/WeatherCard.tsx` — 293 lines; shared design tokens + platform-specific UI components.

- `src/components/Decor/DecorCanvas.tsx` — 1,137 lines; shared design tokens + platform-specific UI components.

- `src/components/Settings/OnboardingModal.tsx` — 93 lines; shared design tokens + platform-specific UI components.

- `src/components/Settings/SettingsView.tsx` — 1,869 lines; shared design tokens + platform-specific UI components.

- `src/components/Settings/TrashView.tsx` — 80 lines; shared design tokens + platform-specific UI components.

- `src/components/Shell/MiniBar.tsx` — 110 lines; shared design tokens + platform-specific UI components.

- `src/components/Shell/MobileNav.tsx` — 447 lines; shared design tokens + platform-specific UI components.

- `src/components/Shell/Sidebar.tsx` — 364 lines; shared design tokens + platform-specific UI components.

- `src/components/Shell/TopBar.tsx` — 445 lines; shared design tokens + platform-specific UI components.

- `src/core/AppContext.tsx` — 1,114 lines; platform composition roots + scoped repositories.

- `src/core/alarmSchedule.ts` — 178 lines; core/notifications.

- `src/core/audio.ts` — 416 lines; platform audio.

- `src/core/automations.ts` — 174 lines; core/automation.

- `src/core/backup.ts` — 302 lines; core/interchange + recovery.

- `src/core/capture.ts` — 208 lines; core/usecases capture.

- `src/core/db.ts` — 604 lines; core/persistence; inspect behavioral rules only; no legacy importer.

- `src/core/ics.ts` — 260 lines; core/interchange calendar.

- `src/core/kitchenActions.ts` — 761 lines; features/kitchen usecases.

- `src/core/labStats.ts` — 166 lines; core/math + lab reference tests.

- `src/core/mathEngine.ts` — 785 lines; core/math.

- `src/core/mindActions.ts` — 247 lines; features/knowledge usecases.

- `src/core/nativeAlarms.ts` — 183 lines; platform notifications.

- `src/core/planActions.ts` — 544 lines; features/planning usecases.

- `src/core/recurrence.ts` — 291 lines; core/domain recurrence.

- `src/core/releaseInfo.ts` — 11 lines; shared build metadata.

- `src/core/repository.ts` — 572 lines; core/persistence + usecases.

- `src/core/sandboxRunner.ts` — 230 lines; isolated code execution adapter.

- `src/core/sanitizer.ts` — 70 lines; core/security import policy.

- `src/core/search.ts` — 194 lines; core/search.

- `src/core/spreadsheetEngine.ts` — 725 lines; core/math + office dependency graph.

- `src/core/sync.ts` — 1,113 lines; core/sync-client + sync-protocol.

- `src/core/taskScheduler.ts` — 417 lines; features/planning scheduler.

- `src/core/temporal.ts` — 252 lines; core/domain time adapter.

- `src/core/types.ts` — 1,022 lines; core/model + typed domain models.

- `src/core/weather.ts` — 153 lines; platform integration/cache.

- `src/data/changelog.ts` — 193 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/densities.ts` — 264 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/facts.ts` — 158 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/prompts.ts` — 138 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/sampleData.ts` — 446 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/stickers.ts` — 442 lines; validated asset/data fixtures; review licenses and provenance.

- `src/data/themes.ts` — 421 lines; validated asset/data fixtures; review licenses and provenance.

- `src/main.tsx` — 30 lines; native application bootstrap or repository configuration; inspect relevant requirements; independently author new configuration.

- `src/modules/creative/BookStudioView.tsx` — 482 lines; features/projects/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/creative/GameDevView.tsx` — 390 lines; features/projects/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/creative/ProjectsView.tsx` — 580 lines; features/projects/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/kitchen/GroceryView.tsx` — 277 lines; features/kitchen + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/kitchen/MealsView.tsx` — 533 lines; features/kitchen + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/kitchen/PantryView.tsx` — 322 lines; features/kitchen + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/kitchen/RecipesView.tsx` — 796 lines; features/kitchen + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/lab/LabWetNirView.tsx` — 391 lines; features/lab + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/lab/LabWorkspaceView.tsx` — 1,501 lines; features/lab + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/AllInOneView.tsx` — 164 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/CustomTabView.tsx` — 1,211 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/FinanceView.tsx` — 410 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/GuideView.tsx` — 565 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/HistoryView.tsx` — 374 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/HouseholdHealthView.tsx` — 764 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/LearningMediaView.tsx` — 302 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/ReportsView.tsx` — 310 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/StudioExtrasView.tsx` — 488 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/life/TravelPeopleHomeView.tsx` — 485 lines; features/life/customization/reports + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/mind/DiaryView.tsx` — 1,092 lines; features/knowledge + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/mind/LifeEventsView.tsx` — 271 lines; features/knowledge + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/mind/NotesView.tsx` — 1,618 lines; features/knowledge + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/mind/ThoughtInboxView.tsx` — 454 lines; features/knowledge + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/ChoresView.tsx` — 809 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/FlowCalendarView.tsx` — 2,353 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/GoalsHabitsView.tsx` — 479 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/HomeDashboard.tsx` — 622 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/PeriodPlannerView.tsx` — 372 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/QuickGlanceView.tsx` — 668 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/TaskCalendarView.tsx` — 504 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/TasksView.tsx` — 1,031 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/plan/TodaySpread.tsx` — 1,858 lines; features/planning/tasks + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/planner/DigitalPlannerView.tsx` — 769 lines; features/planner/graphics + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/CalculatorView.tsx` — 1,240 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/CodeStudioView.tsx` — 478 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/FileManagerView.tsx` — 270 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/KanbanView.tsx` — 503 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/PhotoStudioView.tsx` — 445 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/PresentationView.tsx` — 915 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/SpreadsheetView.tsx` — 897 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/studio/WhiteboardView.tsx` — 571 lines; features/office/files/creative + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/time/TimeSuiteView.tsx` — 1,640 lines; features/time/notifications + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/modules/time/ZenSheetOverlay.tsx` — 91 lines; features/time/notifications + Android/Windows screens; extract behavior; independently implement separate UI and domain rules.

- `src/styles/modules.css` — 371 lines; shared design tokens + platform styles; retire CSS runtime.

- `src/styles/print.css` — 56 lines; shared design tokens + platform styles; retire CSS runtime.

- `src/styles/shell.css` — 762 lines; shared design tokens + platform styles; retire CSS runtime.

- `src/styles/tokens.css` — 271 lines; shared design tokens + platform styles; retire CSS runtime.

- `src/vite-env.d.ts` — 1 lines; native application bootstrap or repository configuration; inspect relevant requirements; independently author new configuration.

- `tests/audit-references.test.ts` — 312 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/expansion-v4.test.ts` — 196 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase0.test.ts` — 217 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase1-reliability.test.ts` — 344 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase1.test.ts` — 184 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase2.test.ts` — 105 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase3.test.ts` — 152 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phase4.test.ts` — 215 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/phases3-10.test.ts` — 275 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/repair-verification-v41.test.ts` — 641 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/setup.ts` — 6 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/ui-smoke.test.tsx` — 124 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tests/upgrades.test.ts` — 288 lines; tests/contract + platform integration; extract expected outcomes; independently author new acceptance tests and regressions.

- `tsconfig.json` — 20 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.

- `tsconfig.server.json` — 14 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.

- `vite.config.ts` — 128 lines; legacy build reference + new Gradle/toolchain configuration; record historical identity; choose independent new development and production identities; never overwrite legacy installation.


## Appendix E. Coverage verification and handoff checklist
This document enumerates each of the 652 unique requirement IDs once in Appendix A, each of the 56 PF entries in Appendix B, each of the 28 issue entries in Appendix C and each of the 152 retained source files in Appendix D. The reference-only ZIP is an unchanged content subset of the original archive; it must not become a source tree for the new product. Use the full CSV and original documentation as historical evidence. New implementation coverage starts at zero until code and tests exist. Attach the revision 3 Markdown master, greenfield AI development prompt and reference-only ZIP to the development agent; the Word version is a human-readable equivalent. Do not attach the private original database or Git history. Run a local secret scan before external sharing.
