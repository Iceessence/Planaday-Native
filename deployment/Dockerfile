# Multi-stage hardened non-root Dockerfile for Planaday Native Optional Sync Server
# Pinned JDK 21 runtime container; clean-room Ktor + PostgreSQL stack.

FROM eclipse-temurin:21.0.9_10-jdk-alpine AS builder
WORKDIR /build
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN chmod +x ./gradlew && ./gradlew :server:app:installDist --no-daemon -x test

FROM eclipse-temurin:21.0.9_10-jre-alpine AS runtime
RUN addgroup -S planaday && adduser -S -G planaday -u 10001 planaday
WORKDIR /opt/planaday-native
COPY --from=builder --chown=planaday:planaday /build/server/app/build/install/app ./
RUN mkdir -p /var/lib/planaday-native/blobs /var/lib/planaday-native/backups \
    && chown -R planaday:planaday /var/lib/planaday-native

USER 10001:10001
ENV PLANADAY_PORT=8420 \
    PLANADAY_BLOB_DIR=/var/lib/planaday-native/blobs \
    PLANADAY_BACKUP_DIR=/var/lib/planaday-native/backups

EXPOSE 8420
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8420/api/v1/health || exit 1

ENTRYPOINT ["/opt/planaday-native/bin/app"]
