# Planaday Native — TrueNAS SCALE / Dockge Deployment & Backup Guide

**Important (Owner Decision O-004):** This stack initializes **brand-new, empty** PostgreSQL, blob, and backup datasets. It MUST NEVER mount or reuse legacy Planaday/Dayplana storage volumes or ports.

## 1. Host Dataset Preparation (TrueNAS SCALE)
Create three dedicated ZFS datasets (completely separate from any legacy apps) with automated ZFS snapshot schedules:
- `/mnt/<pool>/apps/planaday-native/pgdata`
- `/mnt/<pool>/apps/planaday-native/blobs` (owned by UID/GID `10001:10001`)
- `/mnt/<pool>/apps/planaday-native/backups` (owned by UID/GID `10001:10001`)

## 2. TLS & Network Hardening
- PostgreSQL runs strictly on `planaday_internal` (`internal: true`) with zero published host ports.
- Place `sync-server` (`127.0.0.1:8420`) behind a TLS reverse proxy (Caddy/Traefik/Nginx) or private WireGuard/Tailscale VPN. Never expose plaintext HTTP over untrusted networks.
- On first launch, call `POST /api/v1/auth/bootstrap` from the owner's Android or Windows client to set the PBKDF2-hashed owner passphrase.

## 3. Verified Backup & Disposable Restore Drill (`PF-42`)
1. Run `deployment/backup-restore.sh backup` (or schedule via TrueNAS Cron) to execute `pg_dump --format=custom` + SHA-256 blob manifest into `planaday_native_backups`.
2. Periodically verify restorability by running `deployment/backup-restore.sh verify-restore <backup_dir>` against a disposable container instance — never assume a backup succeeds until a test restore passes SHA-256 verification.
3. Keep at least one encrypted off-host copy (`N-048`).
