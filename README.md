# Planaday Native

**Offline-first personal & professional productivity operating system** built from scratch in Kotlin Multiplatform for **Android** (Jetpack Compose, API 29–36), **Windows Desktop** (Compose Desktop on JVM), and an **optional self-hosted sync server** (Ktor 3 + PostgreSQL / Docker Compose).

## Key Architectural Guarantees
- **100% Greenfield & Fresh-Start (Owner Decision O-004):** Clean-room Kotlin codebase initialized from an empty repository (`cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`). Starts with completely empty independent SQLite/PostgreSQL databases and side-by-side identities (`com.planaday.nativeapp`, `planaday_native_v1.db`, port `8420`). No legacy database importers or migration bridges.
- **Offline-First & Durable:** Room KMP + Bundled SQLite (WAL + FTS5 + foreign keys), optimistic concurrency revisions, record-bound draft sessions, 30-day recoverable trash, content-addressed SHA-256 attachments, and versioned SHA-256 backups (`planaday-native-backup-v1`).
- **Optional Self-Hosted Sync:** Ktor 3 + PostgreSQL with PBKDF2 owner auth, 10-min single-use workspace-scoped pairing codes, revocable Bearer tokens, idempotent outbox push/pull, persistent `serverId`/`syncEpoch` reconciliation, and SHA-256 verified staged blob delivery.

## Quick Start (Clean Machine)

### Prerequisites
- JDK 21 (`JAVA_HOME` pointing to JDK 21)
- Android SDK (`ANDROID_HOME` with `platforms;android-36`, `build-tools;36.0.0`) for Android builds
- Python 3.9+ (for CI greenfield & traceability verification script)
- Docker Desktop / Engine (optional, for containerized PostgreSQL + Ktor deployment)

### Reproducible Commands (Windows PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "C:\Program Files (x86)\Android\android-sdk"

# 1. Verify greenfield provenance & traceability counts (652 reqs, 56 PF, 50 N, 152 ledger, 28 issues)
py scripts/verify_greenfield_and_traceability.py

# 2. Run all Kotlin unit, persistence, sync, security, integration & performance tests
.\gradlew.bat check

# 3. Build Android debug APK (API 36, minSdk 29)
.\gradlew.bat :apps:android:assembleDebug

# 4. Run native Windows Desktop application
.\gradlew.bat :apps:desktop:run

# 5. Run optional self-hosted sync server locally (isolated dev mode)
.\gradlew.bat :server:app:run

# 6. Validate Docker Compose deployment configuration
docker compose -f deployment/docker-compose.yml config
```

See [docs/PLANADAY_NATIVE_MASTER.md](docs/PLANADAY_NATIVE_MASTER.md) and [AI_HANDOFF.md](AI_HANDOFF.md) for complete documentation.
