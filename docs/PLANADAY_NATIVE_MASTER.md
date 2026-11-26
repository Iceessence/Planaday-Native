# Planaday Native — Canonical Documentation Hierarchy (`PLANADAY_NATIVE_MASTER.md`)

**Authoritative Specification Policy (Update 2 §2):**  
This document is the **Canonical Documentation Hierarchy Index** for Planaday Native. It **DOES NOT** replace, abbreviate, or supersede the complete Revision 4.0 master specification. Every future agent and developer **MUST** read the complete verbatim specification before planning, implementing, or modifying any requirement.

---

## 1. Authoritative Specification Tier (Binding Source of Truth)

| Priority | Document | Role |
|---|---|---|
| **1 (Binding)** | [spec/PLANADAY_FRESH_START_MASTER_REV4_FULL.md](spec/PLANADAY_FRESH_START_MASTER_REV4_FULL.md) | **Complete Verbatim Revision 4.0 Master Specification** (`139,573` bytes, every section & appendix intact — all 652 requirements, 56 PF packages, 50 N-series proposals, 28 historical issues, 15 integrations, Owner Decision O-004). |
| **2 (Binding)** | [spec/PLANADAY_FRESH_START_AI_PROMPT_REV4.md](spec/PLANADAY_FRESH_START_AI_PROMPT_REV4.md) | **Complete Verbatim Revision 4.0 AI Prompt** (`19,321` bytes) establishing binding greenfield discipline, non-migration rules, and honest verification standards. |
| **3 (Binding)** | [TRACEABILITY_DEFINITIONS.md](TRACEABILITY_DEFINITIONS.md) | **10-Status Evidence Taxonomy & 20-Point Feature Completion Definition** (Update 2 §5 & §33) preventing over-classification of engine-only or demo implementations. |

---

## 2. Architecture, Provenance & Engineering Registries

| Document | Purpose |
|---|---|
| [GREENFIELD_PROVENANCE.md](GREENFIELD_PROVENANCE.md) | Empty root commit (`cf6c9cb`), legacy archive SHA-256, independent product identities & dependencies |
| [GREENFIELD_CHECKS.md](GREENFIELD_CHECKS.md) | Automated CI separation checks (`scripts/verify_greenfield_and_traceability.py`) & independent review protocol |
| [LEGACY_REFERENCE_LEDGER.csv](LEGACY_REFERENCE_LEDGER.csv) | All 152 inspected read-only legacy files mapped to behavioral contracts & greenfield ownership |
| [REQUIREMENT_TRACEABILITY.csv](REQUIREMENT_TRACEABILITY.csv) | All 652 original requirement IDs (`R-0001`..`R-0494`, `C-0001`..`C-0158`) with strict 10-status evidence tracking |
| [EXPANSION_TRACEABILITY.csv](EXPANSION_TRACEABILITY.csv) | All 56 first-release expansion packages (`PF-01`..`PF-56`) |
| [NEW_FEATURE_REGISTRY.csv](NEW_FEATURE_REGISTRY.csv) | All 50 additional feature proposals (`N-001`..`N-050`) |
| [FEATURE_INTEGRATION_MATRIX.csv](FEATURE_INTEGRATION_MATRIX.csv) | 15 mandatory cross-feature integration journeys (`INT-001`..`INT-015`) & live verification evidence |
| [MISSING_FEATURES.md](MISSING_FEATURES.md) | Honest gap register tracking partial, missing, and hardware-blocked capabilities |
| [ARCHITECTURE_DECISIONS.md](ARCHITECTURE_DECISIONS.md) | ADRs 001–008 (provisional until independent review) |
| [DATA_MODEL.md](DATA_MODEL.md) | Canonical Room KMP + Bundled SQLite Schema `v1 -> v2`, keyset pagination, date-range indexes, FTS5, PostgreSQL server schema |
| [SYNC_PROTOCOL.md](SYNC_PROTOCOL.md) | Sync Protocol v1, persistent credentials, pairing, verified blobs, conflict resolution, server epoch reconciliation |
| [SECURITY_THREAT_MODEL.md](SECURITY_THREAT_MODEL.md) | Threat model, Code Studio disabled-by-default host execution policy, OS credential vaults, SSRF, ACLs, encryption |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Commercial Planaday visual identity, Windows desktop multi-pane/tabs/windows & Android phone-first touch patterns |
| [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md) | Hardware declarations, reactive query-isolation telemetry, 10k/100k/250k+ scaling & UI latency measurements |
| [TEST_PLAN.md](TEST_PLAN.md) & [TEST_RESULTS.md](TEST_RESULTS.md) | Feature-specific automated & manual verification pyramid + exact executed results |
| [KNOWN_ISSUES.md](KNOWN_ISSUES.md) | All 28 historical regression targets (`PD-001`..`PD-020`, `CONT-001`, `CONT-003`..`CONT-009`) & active blockers |
| [RELEASE_GATES.md](RELEASE_GATES.md) | Mandatory Gates 0 & A–J (unchecked until independent second-party review) |
| [../CHANGELOG.md](../CHANGELOG.md) & [../AI_HANDOFF.md](../AI_HANDOFF.md) | Authoritative changelog & session handoff checkpoint |
