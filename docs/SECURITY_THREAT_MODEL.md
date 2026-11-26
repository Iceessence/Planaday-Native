# Planaday Native — Security & Privacy Threat Model (`SECURITY_THREAT_MODEL.md`)

**Updated for Update 2 (`1.0.0-alpha.2`, 2026-11-26):** Incorporates all Update 2 security hardening repairs (§23, §28, §29): disabled arbitrary host OS code execution by default, OS-protected credential vaults on Windows & Android, and mandatory HTTPS transport enforcement for remote self-hosted sync servers.

---

## 1. Security Boundaries & Threats

| Threat ID | Threat Surface | Mitigation Implemented in Planaday Native | Verification Test |
|---|---|---|---|
| **TM-01** | Unauthorized server API access / cross-workspace data leak (`PD-004`) | Deny-by-default Bearer auth middleware on all sync, blob, diagnostics, and backup endpoints; workspace ACL enforced per record and per blob reference. | `FreshStartAndSyncE2ETest` |
| **TM-02** | Cross-workspace blob probing by SHA-256 hash | Server requires BOTH caller workspace read ACL AND active `workspace_blob_refs(workspace_id, sha256)` entry before returning blob bytes (`403` verified). | `FreshStartAndSyncE2ETest` |
| **TM-03** | Corrupted or spoofed blob upload (`PD-015`) | Server streams upload to `.tmp`, recomputes SHA-256 independently, rejects mismatch with HTTP `400`, atomically renames only on exact digest match. Client verifies streaming SHA-256 on 64 MiB ingest and disk read. | `FreshStartAndSyncE2ETest`; `ScaleSoakConflictAndStreamingTest` |
| **TM-04** | SSRF via ICS / Recipe / external feed URLs (`PD-014`) | Centralized `OutboundUrlPolicy` in `core:security` blocking non-HTTPS, loopback (`127.0.0.0/8`, `::1`), RFC1918 (`10/8`, `172.16/12`, `192.168/16`), link-local/cloud metadata (`169.254.0.0/16`, `fe80::/10`), CGNAT (`100.64.0.0/10`), and DNS rebinding. | `CoreSecurityContractTest`; `FreshStartAndSyncE2ETest` |
| **TM-05** | Untrusted user code executing on host OS or inside app process (`PD-005`, `R-0322`, `R-0325`, Update 2 §23) | Zero `eval()` in app. **Update 2 Hardening:** Unrestricted host OS subprocess execution in `DesktopExternalToolchainAdapter.compileAndRunIsolated()` is **STRICTLY DISABLED BY DEFAULT** (throws `SecurityException`). Code Studio uses the deterministic pure-Kotlin `SafeLearningInterpreter` + step debugger (strict step/memory budget, Play-policy compliant) plus offline `analyzeProjectDiagnostics()` and portable `.zip` export (`exportPortableProjectZip()`). | `CoreSecurityContractTest`; `CrossFeatureIntegrationAndRegressionsTest` |
| **TM-06** | Malicious archive / CSV / PPTX / XLSX import (ZIP bomb, path traversal, XXE, CSV formula injection) | Strict ZIP entry count cap (`<= 5,000`), total uncompressed size cap, canonical path containment check rejecting `..`/absolute paths, XML DOCTYPE/ENTITY rejection, RFC 4180 CSV formula-prefix (`=`, `+`, `-`, `@`) neutralization. | `CoreInterchangeContractTest`; `ArchiveInterchangeJvmTest` |
| **TM-07** | Backup tampering & sensitive export exposure | `planaday-native-backup-v1` verifies SHA-256 manifest of DB snapshot + every blob BEFORE committing staged restore; optional AES-256-GCM authenticated encryption with PBKDF2-HMAC-SHA256 (`120,000` iterations) and wrong-key rejection. | `ArchiveInterchangeJvmTest`; `FreshStartAndSyncE2ETest` |
| **TM-08** | Sensitive Health/Finance/Lab data leakage & credential theft at rest (Update 2 §28) | Dedicated workspace scopes (`PERSONAL`, `LABORATORY`, `PRIVATE_DIARY`), selective sync policy (`N-041`), **OS-protected credential vaults** (`JvmProtectedCredentialVault` with Windows NTFS owner-only ACL / POSIX 0600 AES-256-GCM key; `AndroidKeystoreCredentialVault` with hardware/TEE `AndroidKeyStore` AES-256-GCM). Verified zero plaintext sync bearer tokens stored in SQLite. | `DesktopShellTest`; `Update2ScalabilitySecurityAndFeaturesTest` |
| **TM-09** | Plaintext credential transmission over LAN/WAN sync (Update 2 §28) | `SyncTransportSecurityPolicy` enforced inside `PlanadaySyncClient`: requires `https://` for all non-loopback sync servers; rejects remote `http://` unless user explicitly opts into development HTTP mode. | `Update2ScalabilitySecurityAndFeaturesTest` |
| **TM-10** | Offline desktop license spoofing / lockout & DB network exposure | Ed25519 signature verification using public key ONLY (data remains 100% readable/exportable regardless of license state). Docker Compose binds PostgreSQL 17 strictly on internal Docker bridge network with zero published host port and non-root container UID. | `CoreSecurityContractTest`; `docker compose config --quiet` |

---

## 2. Explicit Scope & Non-Claims

- **Laboratory Operations:** Planaday Lab is an operational management aid. It does **NOT** claim ISO/IEC 17025 accreditation or FDA 21 CFR Part 11 regulatory validation.
- **Health & Wellness:** Medication and wellness tracking are personal organizational aids, **NOT** medical diagnosis or treatment.
- **Device Compromise:** OS keystores and owner-only NTFS ACLs protect credentials at rest against casual extraction, not against a fully compromised/privileged host OS. Offline devices retain previously synced bytes until user action — remote wipe is not promised.
