# Planaday Native — Synchronization Protocol Specification (`SYNC_PROTOCOL.md`)

**Protocol Version:** `1` (decoupled from app release version)  
**Transport:** HTTPS (TLS 1.2+ in production; isolated loopback HTTP permitted only in local dev/test harnesses)  
**Server Stack:** Ktor 3 + PostgreSQL 17 (optional — clients are 100% offline-first without any server).

---

## 1. Core Guarantees

1. **Identity & Epoch Binding (`PD-003`, `PF-03`):** Every server generates a persistent UUID `serverId` and monotonic `syncEpoch` (starting at `1`, incremented on restore/reset). Clients persist `(boundServerId, boundSyncEpoch, lastServerCursor)` per workspace. If a client reconnects to a different `serverId` or changed `syncEpoch` (e.g., replacement server or backup-restored client), sync enters **Reconciliation Mode** and never falsely reports `SYNCED`.
2. **Authenticated Workspace Pairing (`PD-004`, `PF-04`):**
   - Owner bootstrap passphrase hashed with salted PBKDF2-HMAC-SHA256 (600,000 iterations) + constant-time comparison.
   - Owner mints 10-minute single-use pairing code scoped to specific `workspaceIds` and role (`OWNER` | `EDITOR` | `VIEWER`).
   - Device redeems pairing code for a revocable 256-bit Bearer token (stored as SHA-256 hash on server, stored in OS secure storage on client).
   - Every `/api/v1/sync/*`, `/api/v1/blobs/*`, `/api/v1/backup/*`, and `/api/v1/diagnostics` route enforces token validation and per-workspace ACL (`401` unauthenticated, `403` unauthorized workspace/role).
3. **Atomic Idempotent Push & Cursor Pull (`PD-001`, `C-0059`):**
   - Each `OutboxOpEnvelope` carries `opId` (UUID idempotency key), `workspaceId`, `recordId`, `recordKind`, `operation` (`UPSERT` | `DELETE`), `baseRev`, `schemaVersion`, `payloadJson`, `referencedBlobHashes`, `deviceId`, `clientTimestampUtc`.
   - Server validates auth, workspace ACL, payload quota, schema version, and `opId` idempotency (duplicate `opId` returns existing receipt without double-applying).
   - If `baseRev == currentServerRev`, server commits record update + monotonic `serverCursor` + `applied_ops` receipt in ONE transaction.
   - If `baseRev < currentServerRev`, server returns `CONFLICT` with the authoritative server record & rev — **never silently overwriting** money, lab results, or destructive deletes!
4. **Verified Bidirectional Blob Delivery (`PD-002`, `PD-015`, `CONT-003`, `CONT-004`, `PF-02`, `PF-51`):**
   - Supported ordinary media types (`image/*`, `audio/*`, `video/*`, `application/pdf`, `text/*`, `application/octet-stream`, etc.) accepted via streaming binary PUT/GET.
   - Server independently computes SHA-256 while streaming to `.tmp`, verifies exact match against path `:sha256` (HTTP `400` on mismatch), then atomically renames to content-addressed store and records workspace `blob_acl` grant.
   - GET `/api/v1/blobs/{workspaceId}/{sha256}` verifies caller has read permission on `workspaceId` AND that `workspaceId` holds a live reference to `{sha256}` (`403` otherwise — knowing a hash NEVER grants cross-workspace access!). Response includes `X-Content-Type-Options: nosniff`, `Cache-Control: private`, and `Content-Security-Policy: default-src 'none'; sandbox` for SVG/HTML.
   - Client verifies SHA-256 on download and tracks `BlobTransferReceipt` (`PENDING` | `VERIFIED` | `FAILED`). Any missing or failed required blob marks sync state `ERROR` / `PENDING_ATTACHMENTS` with `pendingCount >= failedCount` — **never `SYNCED`**.

---

## 2. REST API Routes (Protocol v1)

| Method | Route | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/health` | Public (minimal) | Liveness + `appVersion`, `gitCommit`, `protocolVersion`, `serverId`, `syncEpoch` (`C-0061`) |
| `GET` | `/api/v1/capabilities` | Public | Supported protocol versions, max payload/blob limits, auth status |
| `POST` | `/api/v1/auth/bootstrap` | Initial setup only | Set owner passphrase on empty server |
| `POST` | `/api/v1/auth/login` | Owner passphrase | Mint owner session token |
| `POST` | `/api/v1/auth/pairing-codes` | Owner token | Mint 10-min single-use workspace-scoped pairing code |
| `POST` | `/api/v1/auth/pair` | Pairing code | Exchange code for revocable device token |
| `POST` | `/api/v1/auth/tokens/{tokenId}/revoke` | Owner token | Immediately revoke device token |
| `POST` | `/api/v1/sync/push` | Device token (Editor+) | Submit batch of idempotent `OutboxOpEnvelope`s |
| `GET` | `/api/v1/sync/pull` | Device token (Viewer+) | Pull changes after `cursor` for authorized `workspaceId` |
| `PUT` | `/api/v1/blobs/{workspaceId}/{sha256}` | Device token (Editor+) | Upload verified staged attachment bytes |
| `GET` | `/api/v1/blobs/{workspaceId}/{sha256}` | Device token (Viewer+) | Download authorized verified attachment bytes |
| `GET` | `/api/v1/diagnostics` | Owner token | Storage metrics, build identity, last backup receipt (zero personal content logged) |
| `POST` | `/api/v1/admin/backup` | Owner token | Trigger consistent server backup snapshot + SHA-256 manifest |

---

## 3. Conflict Resolution Policies by Record Kind

| Domain | Policy |
|---|---|
| Disjoint metadata fields (common ancestor verified) | Safe field-level merge when proven disjoint |
| Task/Chore recurrence completions | Deterministic `occurrenceId = hash(recordId, scheduledDate)` union + conflict log |
| Notes & Manuscripts | Preserve competing revisions in `conflicts` + 3-way line diff viewer (never silently discard) |
| Finance, Budgets, Lab Calibration/QC/Results, Destructive Deletes | **Explicit Conflict Review Required** — never unconditional last-writer-wins; delete-vs-edit preserves recovery copy |
| Selective Sync | Workspace/size filters never generate `DELETE` tombstones for filtered-out records |
