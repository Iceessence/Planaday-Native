# Planaday Native — Canonical Master Specification (`PLANADAY_NATIVE_MASTER.md`)

**Revision:** 4.0 (Greenfield Fresh-Start Edition)  
**Product Version:** `1.0.0-alpha.1`  
**Platforms:** Android (Kotlin + Jetpack Compose, API 29–36), Windows Desktop (Kotlin/JVM + Compose Desktop), Optional Self-Hosted Sync Server (Ktor 3 + PostgreSQL / Docker Compose).  
**Binding Policy:** Clean-room greenfield construction from empty repository `cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`; Owner Decision **O-004** (fresh databases only — zero legacy Planaday data migration).

---

## 1. Product Mandate

Planaday Native is a single, integrated, offline-first personal and professional productivity operating system uniting:
1. **Daily Planning & Intelligent Scheduling:** Today Spread, Home Dashboard, Quick Glance, Quick Capture Inbox, All-in-One Scroll, Week/Month/Year, Flow Calendar (1m–4h zoom, 7 overlap policies, multi-calendar layers, ICS), Intelligent Task Calendar (Do vs Hard Due, carryover with original creation age, learned durations, contexts, preferred end time), Chores (completion-driven recurrence, room health & fairness), Goals, Habits & Routines.
2. **Mind, Knowledge & Time Suite:** Recursive cycle-safe Notebooks/Notes (`100k+` words, `[[WikiLinks]]`, backlinks, graph, revision diffs, Draft Recovery Inbox), Thought Inbox, Diary & Wellness (1–10 & custom scales, private optional bathroom counters, trends), Life Events, Countdowns, 6+ Clock styles, World Clock, Smart Alarms, Timers/Pomodoro/Intervals, Stopwatch & Time Ledger, Zen Overlay.
3. **Physical-Style Digital Planner & Infinite Whiteboard:** Covers, bindings, 2-page spreads, dynamic left/right side tabs & top bookmarks (`5,000+` tested), custom layouts, interactive 50-state US map, stickers/ink/layers, infinite-feel spatial-indexed whiteboard with live connectors, SVG/PDF export.
4. **Kitchen & Personal Life-OS:** Recipes & scaling, FIFO/FEFO Lot Pantry, Meal Planner, Unified Food History, Aisle Groceries reconciled to Pantry & Finance Expenses, Household Fairness & Family Profiles, People & Gifts, Health & Medications, Workouts, Personal Finance/Bills/Budgets/Warranties/Wishlists (decimal precision), Spaced Review, Media & Hobbies, Offline Travel Packs, Home Assets/Vehicles/Pets/Plants/Bins.
5. **Comprehensive Laboratory Workspace (Operational Aid — Non-LIMS):** All 15 submodules (Overview, Samples/TAT/Custody, Subcontracts, Inventory, Purchasing/Budgets, Client Pricing/Templates, Method Research, Email Drafts, Lab Tasks, Lab Meetings->Tasks, Immutable Calibration/QC, Wet Chemistry vs NIR statistical analysis, Work Hours/Breaks, QMS/SOP/CAPA/PT/Training) + Method & Uncertainty Reference Workbook.
6. **Mathematics, Office, Creative & Files:** Standard/Scientific/Lab/2D/3D Graphing Calculator (pole-safe roots, 23-category unit converter, cached/manual currency), Sparse DAG Spreadsheets (cross-sheet `#REF!`, charts, live data pulls, RFC 4180 CSV, XLSX), Presentations (slide designer, presenter view, PPTX import/export, HTML/PDF fidelity), Structured Document & PDF Review, Nested Projects & Custom Schema Builder, Kanban Boards (live Tasks/Chores + custom), Game Dev Studio, Book Studio & Continuity Checker, Code Studio (isolated desktop compiler/debugger adapter + safe Android learning sandbox), Photo Studio (SHA-256 dedup, non-destructive edits), Universal File Hub (Personal/Lab split).
7. **Customization, Automation, Reports & Optional Sync:** Workspace schedules, custom tabs/widgets, declarative schemas, previewable idempotent automations, decision journal, evidence snapshots, local opt-in assistance, FTS5 global search, 30-day trash, versioned SHA-256 backups, and optional self-hosted Ktor + PostgreSQL sync with authenticated pairing & verified blob delivery.

---

## 2. Canonical Document Index

| Document | Purpose |
|---|---|
| [GREENFIELD_PROVENANCE.md](GREENFIELD_PROVENANCE.md) | Empty repo commit, legacy archive SHA-256, independent identities & dependencies |
| [GREENFIELD_CHECKS.md](GREENFIELD_CHECKS.md) | CI separation checks, duplicate hash guards, manual review protocol |
| [LEGACY_REFERENCE_LEDGER.csv](LEGACY_REFERENCE_LEDGER.csv) | 152 inspected reference files mapped to extracted contracts & new module ownership |
| [REQUIREMENT_TRACEABILITY.csv](REQUIREMENT_TRACEABILITY.csv) | All 652 original requirement IDs (`R-0001`..`R-0494`, `C-0001`..`C-0158`) with native status |
| [EXPANSION_TRACEABILITY.csv](EXPANSION_TRACEABILITY.csv) | All 56 first-release expansion packages (`PF-01`..`PF-56`) |
| [NEW_FEATURE_REGISTRY.csv](NEW_FEATURE_REGISTRY.csv) | All 50 additional feature targets (`N-001`..`N-050`) |
| [FEATURE_INTEGRATION_MATRIX.csv](FEATURE_INTEGRATION_MATRIX.csv) | 15 mandatory cross-feature integration journeys & contracts |
| [MISSING_FEATURES.md](MISSING_FEATURES.md) | Explicit closure of the 7 named gaps + unverified/partial audit plan |
| [ARCHITECTURE_DECISIONS.md](ARCHITECTURE_DECISIONS.md) | ADRs 001–008 (provisional until independent review) |
| [DATA_MODEL.md](DATA_MODEL.md) | Canonical Room KMP + SQLite v1 schema & PostgreSQL server schema |
| [SYNC_PROTOCOL.md](SYNC_PROTOCOL.md) | Versioned Sync Protocol v1, pairing, blobs, conflicts, epoch reconciliation |
| [SECURITY_THREAT_MODEL.md](SECURITY_THREAT_MODEL.md) | Threats, mitigations, SSRF, ACLs, encryption, sandbox policy |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Tactile design tokens, Android touch-first & Windows multi-pane specs |
| [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md) | Reference hardware declaration, provisional budgets & benchmark log |
| [TEST_PLAN.md](TEST_PLAN.md) & [TEST_RESULTS.md](TEST_RESULTS.md) | Verification pyramid & reproducible executed test results |
| [KNOWN_ISSUES.md](KNOWN_ISSUES.md) | 28 historical regression targets & active native blockers |
| [RELEASE_GATES.md](RELEASE_GATES.md) | Mandatory A–J + Fresh-Start acceptance gates |
| [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) | Dependency-ordered full-scope vertical slices |
| [../CHANGELOG.md](../CHANGELOG.md) & [../AI_HANDOFF.md](../AI_HANDOFF.md) | Authoritative changelog & session handoff checkpoint |
