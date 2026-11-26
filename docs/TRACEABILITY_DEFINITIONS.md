# Planaday Native — Evidence-Based Traceability Definitions (`TRACEABILITY_DEFINITIONS.md`)

**Binding Policy (Update 2 §5 & §33):**  
A feature is **NEVER** marked `FUNCTIONALLY_IMPLEMENTED` or `DEVELOPER_TESTED` merely because an underlying engine exists, a database record type exists, a hardcoded demo button runs, or a generic persistence test creates `{"verified":true}` payloads.

---

## 1. Ten-Status Evidence Classification Taxonomy

| Status | Exact Qualification Criteria |
|---|---|
| `SPECIFIED` | Defined in the Revision 4.0 Master Specification and traced to target modules, with no runnable implementation yet. |
| `ENGINE_IMPLEMENTED` | Core domain/math/interchange/persistence engine code exists and compiles (and may have unit tests), **but** lacks a complete user-facing interactive workflow for real user content. |
| `UI_IMPLEMENTED` | User-facing Compose interface exists for viewing/interacting, **but** full durable persistence, editing, validation, export, or cross-feature wiring is still partial. |
| `FUNCTIONALLY_IMPLEMENTED` | End-to-end user workflow supports applicable portions of the 20-point completion rubric below (real user creation, viewing, editing, deletion, durable Room SQLite persistence across restarts, validation, searchability, offline operation, and integration), pending feature-specific automated test proof. |
| `DEVELOPER_TESTED` | Meets `FUNCTIONALLY_IMPLEMENTED` **AND** verified by feature-specific automated unit/integration/UI tests exercising the actual user workflow (not a generic `verified=true` record test) on the developer workstation. |
| `PLATFORM_TESTED` | Meets `DEVELOPER_TESTED` **AND** verified interactively on actual target platform runtime(s) (live Windows desktop executable/MSI AND/OR Android emulator/physical hardware with screenshots/evidence). |
| `INDEPENDENTLY_ACCEPTED` | Verified and signed off by an **independent reviewer separate from the authoring agent/engineer**. **NEVER self-assigned.** |
| `BLOCKED` | Cannot be verified or completed in the current environment due to an explicit external dependency (e.g., offline Docker daemon for PostgreSQL container test, absent physical Android 10/16 hardware or AVD, Authenticode signing key, or owner-authorized live server action). |
| `FAILED` | Executed verification failed and defect remains open. |
| `NOT_APPLICABLE` | Explicitly excluded by Owner Decision (e.g., `C-0064` iOS WebKit under Android+Windows+Server scope, or legacy data migration under `O-004`). |

---

## 2. Twenty-Point Feature Completion Rubric (Update 2 §5)

Every user-facing feature claiming `FUNCTIONALLY_IMPLEMENTED` or higher is evaluated against all applicable items:
1. Creation of actual user content (zero hardcoded demo-only flows)
2. Viewing actual saved content
3. Editing existing content
4. Deletion
5. Recovery where appropriate (30-day Trash / Undo)
6. Durable saving (atomic Room SQLite transaction)
7. Persistence after restart (reopen DB/app verification)
8. Correct validation & error messages
9. Undo and redo where appropriate
10. Searchability (workspace-scoped SQLite FTS5)
11. Customization where applicable
12. Importing and exporting where required
13. Offline operation (zero required network/server)
14. Synchronization where applicable (outbox/pull/conflict-safe)
15. Android usability (touch-first phone/tablet layout)
16. Windows usability (multi-pane/keyboard/mouse desktop layout)
17. Cross-feature integration (single canonical record UUID, typed relations)
18. Accessibility (semantic labels, contrast, keyboard focus, touch targets)
19. Performance under realistic workloads (bounded reactive queries, no global refresh fan-out)
20. Feature-specific automated and manual testing

Where a criterion does not apply (e.g., Undo on a read-only diagnostic readout), the traceability note explicitly states why.
