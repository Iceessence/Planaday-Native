# Planaday Native — Security & Privacy Threat Model (`SECURITY_THREAT_MODEL.md`)

---

## 1. Security Boundaries & Threats

| Threat ID | Threat Surface | Mitigation Implemented in Planaday Native | Verification Test |
|---|---|---|---|
| **TM-01** | Unauthorized server API access / cross-workspace data leak (`PD-004`) | Deny-by-default Bearer auth middleware on all sync, blob, diagnostics, and backup endpoints; workspace ACL enforced per record and per blob reference. | `SyncServerSecurityTest#enforces401And403CrossWorkspace` |
| **TM-02** | Cross-workspace blob probing by SHA-256 hash | Server requires BOTH caller workspace read ACL AND active `workspace_blob_refs(workspace_id, sha256)` entry before returning blob bytes. | `SyncServerSecurityTest#blocksCrossWorkspaceBlobAccessByHash` |
| **TM-03** | Corrupted or spoofed blob upload (`PD-015`) | Server streams upload to `.tmp`, recomputes SHA-256 independently, rejects mismatch with HTTP `400`, atomically renames only on exact digest match. | `SyncServerSecurityTest#rejectsSpoofedBlobHash` |
| **TM-04** | SSRF via ICS / Recipe / external feed URLs (`PD-014`) | Centralized `OutboundUrlPolicy` in `core:security` blocking non-HTTPS, loopback (`127.0.0.0/8`, `::1`), RFC1918 (`10/8`, `172.16/12`, `192.168/16`), link-local/cloud metadata (`169.254.0.0/16`, `fe80::/10`), CGNAT (`100.64.0.0/10`), DNS rebinding on resolved IPs, max 3 validated redirects, 2 MB cap, strict timeout. | `SecurityPolicyTest#ssrfPolicyBlocksPrivateAndMetadataHosts` |
| **TM-05** | Untrusted user code executing inside app process (`PD-005`, `R-0322`, `R-0325`) | Zero `eval()` in app. Desktop Code Studio runs external compilers/debuggers strictly in user-opted-in isolated child processes with restricted temp workdir, stripped env, timeout, and output cap. Android runs pure-Kotlin deterministic learning interpreter with strict step & memory budget (Play policy compliant). | `CodeStudioSecurityTest#enforcesTimeoutAndNoInProcessEval` |
| **TM-06** | Malicious archive / CSV / PPTX / XLSX import (ZIP bomb, path traversal, XXE, CSV formula injection) | Strict ZIP entry count cap (`<= 5,000`), total uncompressed size cap, canonical path containment check rejecting `..` / absolute paths, XML external entities disabled, RFC 4180 CSV optional formula-prefix (`=`, `+`, `-`, `@`) neutralization. | `InterchangeSecurityTest#blocksZipTraversalZipBombAndCsvInjection` |
| **TM-07** | Backup tampering & sensitive export exposure | `planaday-native-backup-v1` verifies SHA-256 manifest of DB snapshot + every blob BEFORE committing staged restore; optional AES-256-GCM authenticated encryption with PBKDF2-HMAC-SHA256 (600k iterations) and user-held recovery key. | `BackupRecoveryTest#detectsTamperedArchiveAndEncryptedRoundTrip` |
| **TM-08** | Sensitive Health/Finance/Lab data leakage | Dedicated workspace & privacy scopes (`personal`, `lab`, `private_diary`), selective sync controls, export redaction preview, zero forced telemetry, PII scrubbing on diagnostic packages. | `WorkspaceAclTest#isolatesLabAndPrivateScopes` |
| **TM-09** | Offline desktop license spoofing / lockout | Ed25519 signature verification using embedded public key ONLY (zero private keys in code/artifacts). **Data survival:** local DB remains 100% readable & exportable regardless of license state. | `EntitlementVerifierTest#verifiesSignatureAndPreservesReadAccess` |
| **TM-10** | Production DB network exposure | Docker Compose binds PostgreSQL exclusively on internal Docker bridge network with zero published host port; non-root container UID; least-privilege PostgreSQL role. | `DeploymentConfigTest#postgresHasNoPublicHostPort` |

---

## 2. Explicit Scope & Non-Claims

- **Laboratory Operations:** Planaday Lab is an operational management aid. It does **NOT** claim ISO/IEC 17025 accreditation or FDA 21 CFR Part 11 regulatory validation.
- **Health & Wellness:** Medication and wellness tracking are personal organizational aids, **NOT** medical diagnosis or treatment.
- **Device Compromise:** OS keystores protect credentials at rest against casual extraction, not against a fully compromised/rooted host OS. Offline devices retain previously synced bytes until user action — remote wipe is not promised.
