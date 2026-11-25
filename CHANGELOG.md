# Changelog

All notable changes to **Planaday Native** (fresh greenfield product line) are documented in this file.

## [1.0.0-alpha.1] - 2026-11-25

### Added — Greenfield Foundation & Full-Scope Architecture (Revision 4.0)
- Initialized brand-new empty repository (`cf6c9cbd35bd8b8a5b75db692d39d02b5f581917`) with zero legacy source, tests, assets, or database migration bridges (Owner Decision **O-004**).
- Completed read-only audit of all 152 legacy reference files (SHA-256 manifest, dependency graph, secret/private-IP redaction) and established canonical traceability registries covering all **652** original requirements, **56** expansion packages (`PF-01`..`PF-56`), **50** additional feature proposals (`N-001`..`N-050`), **28** historical issue regressions, and **15** cross-feature integration journeys.
- Created verified Gradle `8.14.3` multi-module Kotlin Multiplatform workspace spanning shared core engines (`core:*`), all feature domains (`features:*`), optional Ktor + PostgreSQL sync server (`server:*`), Windows Compose Desktop app (`apps:desktop`), Android Jetpack Compose app (`apps:android`, `minSdk 29`, `targetSdk 36`), deployment scripts (`deployment/`), and end-to-end verification suite (`tests:integration`).
- Implemented automated CI greenfield separation & traceability verification gate (`scripts/verify_greenfield_and_traceability.py`).
