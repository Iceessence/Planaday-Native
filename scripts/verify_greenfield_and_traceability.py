#!/usr/bin/env python3
"""
Planaday Native — Automated Greenfield Separation & Traceability CI Gate.

Verifies:
1. Zero legacy paths, databases, or web-shell runtime files in the repository.
2. Zero exact SHA-256 file duplicates against all 152 legacy reference files.
3. Zero legacy runtime dependencies (electron, capacitor, dexie, vite, fastify, react-dom).
4. Zero legacy storage/package identities (O-004) or private LAN IP leaks in code/config.
5. Exact traceability completeness:
   - 652 unique original requirement IDs in docs/REQUIREMENT_TRACEABILITY.csv
   - 56 unique PF expansion IDs in docs/EXPANSION_TRACEABILITY.csv
   - 50 unique N-series IDs in docs/NEW_FEATURE_REGISTRY.csv
   - 152 unique baseline reference entries in docs/LEGACY_REFERENCE_LEDGER.csv
   - 28 historical regression IDs in docs/KNOWN_ISSUES.md
"""
import csv
import hashlib
import os
import re
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOCS_DIR = os.path.join(REPO_ROOT, "docs")

PROHIBITED_FILENAMES = {
    "package.json",
    "package-lock.json",
    "vite.config.ts",
    "capacitor.config.json",
    "main.cjs",
    "preload.cjs",
    "sw.js",
    "AppContext.tsx",
    "dayplana.sqlite",
    "dayplana.sqlite-wal",
    "dayplana.sqlite-shm",
}

PROHIBITED_PATH_FRAGMENTS = (
    "Planaday-legacy",
    "dayplana-data",
    "dist-server/",
    "node_modules/",
)

PROHIBITED_CODE_TOKENS = (
    "dayplana-v2",
    "dayplana.sqlite",
    "dayplana_db",
    "com.dayplana",
    "dayplana-backup-v2",
    "192.168.88.",
)

PROHIBITED_BUILD_TOKENS = (
    "electron",
    "capacitor",
    "dexie",
    "fastify",
    "vite",
    "react-dom",
)

# Docs that explicitly document prohibited legacy identifiers in provenance/exclusion tables
EXCLUSION_DOC_WHITELIST = {
    "docs/GREENFIELD_PROVENANCE.md",
    "docs/GREENFIELD_CHECKS.md",
    "docs/LEGACY_REFERENCE_LEDGER.csv",
    "docs/REQUIREMENT_TRACEABILITY.csv",
    "docs/EXPANSION_TRACEABILITY.csv",
    "scripts/verify_greenfield_and_traceability.py",
}

SKIP_DIRS = {".git", ".gradle", "build", ".kotlin", ".idea"}


def fail(errors):
    print("GREENFIELD & TRACEABILITY GATE FAILED:")
    for e in errors:
        print(f"  [ERROR] {e}")
    sys.exit(1)


def main():
    errors = []

    # 1. Load legacy SHA-256 hashes from ledger
    ledger_path = os.path.join(DOCS_DIR, "LEGACY_REFERENCE_LEDGER.csv")
    legacy_hashes = {}
    if not os.path.exists(ledger_path):
        errors.append("Missing docs/LEGACY_REFERENCE_LEDGER.csv")
    else:
        with open(ledger_path, "r", encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        if len(rows) != 152:
            errors.append(f"LEGACY_REFERENCE_LEDGER.csv expected 152 rows, found {len(rows)}")
        seen_paths = set()
        for r in rows:
            p = r.get("legacy_path", "").strip()
            h = r.get("sha256", "").strip().lower()
            if p in seen_paths:
                errors.append(f"Duplicate legacy_path in ledger: {p}")
            seen_paths.add(p)
            if len(h) == 64:
                legacy_hashes[h] = p

    # 2. Verify 652 unique requirement IDs
    req_path = os.path.join(DOCS_DIR, "REQUIREMENT_TRACEABILITY.csv")
    if not os.path.exists(req_path):
        errors.append("Missing docs/REQUIREMENT_TRACEABILITY.csv")
    else:
        with open(req_path, "r", encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        ids = [r.get("Requirement ID", "").strip() for r in rows]
        unique_ids = set(ids)
        if len(rows) != 652 or len(unique_ids) != 652:
            errors.append(f"REQUIREMENT_TRACEABILITY.csv expected 652 unique IDs, found rows={len(rows)} unique={len(unique_ids)}")
        expected_r = {f"R-{i:04d}" for i in range(1, 495)}
        expected_c = {f"C-{i:04d}" for i in range(1, 159)}
        missing_req = (expected_r | expected_c) - unique_ids
        if missing_req:
            errors.append(f"Missing requirement IDs: {sorted(missing_req)[:10]}")

    # 3. Verify 56 unique PF IDs
    exp_path = os.path.join(DOCS_DIR, "EXPANSION_TRACEABILITY.csv")
    if not os.path.exists(exp_path):
        errors.append("Missing docs/EXPANSION_TRACEABILITY.csv")
    else:
        with open(exp_path, "r", encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        pf_ids = {r.get("PF_ID", "").strip() for r in rows}
        expected_pf = {f"PF-{i:02d}" for i in range(1, 57)}
        if len(rows) != 56 or pf_ids != expected_pf:
            errors.append(f"EXPANSION_TRACEABILITY.csv mismatch: rows={len(rows)}, missing={sorted(expected_pf - pf_ids)}")

    # 4. Verify 50 unique N-series IDs
    n_path = os.path.join(DOCS_DIR, "NEW_FEATURE_REGISTRY.csv")
    if not os.path.exists(n_path):
        errors.append("Missing docs/NEW_FEATURE_REGISTRY.csv")
    else:
        with open(n_path, "r", encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        n_ids = {r.get("N_ID", "").strip() for r in rows}
        expected_n = {f"N-{i:03d}" for i in range(1, 51)}
        if len(rows) != 50 or n_ids != expected_n:
            errors.append(f"NEW_FEATURE_REGISTRY.csv mismatch: rows={len(rows)}, missing={sorted(expected_n - n_ids)}")

    # 5. Verify 28 historical regression IDs in docs/KNOWN_ISSUES.md
    issues_path = os.path.join(DOCS_DIR, "KNOWN_ISSUES.md")
    if not os.path.exists(issues_path):
        errors.append("Missing docs/KNOWN_ISSUES.md")
    else:
        issues_text = open(issues_path, "r", encoding="utf-8").read()
        expected_issues = [f"PD-{i:03d}" for i in range(1, 21)] + ["CONT-001"] + [f"CONT-{i:03d}" for i in range(3, 10)]
        missing_issues = [i for i in expected_issues if i not in issues_text]
        if missing_issues:
            errors.append(f"KNOWN_ISSUES.md missing historical IDs: {missing_issues}")

    # 6. Scan entire repository for legacy files, SHA-256 duplicates, prohibited identities & web-shell deps
    scanned_files = 0
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, REPO_ROOT).replace("\\", "/")
            scanned_files += 1

            if fn in PROHIBITED_FILENAMES or fn.endswith((".sqlite", ".sqlite-wal", ".sqlite-shm")):
                errors.append(f"Prohibited legacy filename/database in repo: {rel}")

            for frag in PROHIBITED_PATH_FRAGMENTS:
                if frag in rel:
                    errors.append(f"Prohibited legacy path fragment '{frag}' in: {rel}")

            raw = open(full, "rb").read()
            sha = hashlib.sha256(raw).hexdigest().lower()
            if sha in legacy_hashes and len(raw) > 32:
                errors.append(f"Exact SHA-256 duplicate of legacy file '{legacy_hashes[sha]}' found at: {rel}")

            if rel not in EXCLUSION_DOC_WHITELIST and not fn.endswith((".jar", ".png", ".jpg", ".zip", ".class")):
                try:
                    txt = raw.decode("utf-8")
                except UnicodeDecodeError:
                    continue
                for tok in PROHIBITED_CODE_TOKENS:
                    if tok in txt:
                        errors.append(f"Prohibited legacy identifier/IP '{tok}' found in: {rel}")
                if fn.endswith((".kts", ".toml", ".gradle")) or fn == "Dockerfile":
                    lower = txt.lower()
                    for dep in PROHIBITED_BUILD_TOKENS:
                        if re.search(rf"\b{re.escape(dep)}\b", lower):
                            errors.append(f"Prohibited legacy runtime dependency '{dep}' in build file: {rel}")

    if errors:
        fail(errors)

    print(
        f"PASS: Greenfield & Traceability CI Gate verified "
        f"(scanned={scanned_files} files, 0 legacy duplicates, 0 O-004 violations, "
        f"652/652 requirements, 56/56 PF, 50/50 N, 152/152 ledger, 28/28 historical issues)."
    )


if __name__ == "__main__":
    main()
