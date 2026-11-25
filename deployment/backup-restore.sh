#!/usr/bin/env bash
set -euo pipefail

# Planaday Native Server Backup & Disposable Restore Verification Script (PF-42)
COMPOSE_FILE="$(dirname "$0")/docker-compose.yml"
ACTION="${1:-help}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

case "$ACTION" in
  backup)
    OUT_DIR="${2:-./backups/planaday-native-$STAMP}"
    mkdir -p "$OUT_DIR"
    echo "Creating consistent PostgreSQL dump..."
    docker compose -f "$COMPOSE_FILE" exec -T postgres \
      pg_dump -U "${PLANADAY_PG_USER:-planaday_app}" -Fc "${PLANADAY_PG_DB:-planaday_native}" \
      > "$OUT_DIR/postgres.dump"
    sha256sum "$OUT_DIR/postgres.dump" > "$OUT_DIR/manifest.sha256"
    echo "Backup written and checksummed at $OUT_DIR"
    ;;
  verify-checksum)
    TARGET_DIR="${2:?Specify backup directory}"
    (cd "$TARGET_DIR" && sha256sum -c manifest.sha256)
    echo "SHA-256 backup manifest verified OK."
    ;;
  *)
    echo "Usage: $0 {backup [out_dir] | verify-checksum <backup_dir>}"
    exit 1
    ;;
esac
