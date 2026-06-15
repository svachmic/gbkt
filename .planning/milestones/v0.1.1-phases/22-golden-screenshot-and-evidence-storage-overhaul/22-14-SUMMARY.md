---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: 14
subsystem: documentation
tags: [testing, goldens, documentation, evidence-storage]
dependency_graph:
  requires: ["22-01", "22-03", "22-06", "22-07"]
  provides: ["TESTING.md goldens documentation"]
  affects: ["context/TESTING.md"]
tech_stack:
  added: []
  patterns:
    - "Visual Goldens section in TESTING.md"
    - "assertGoldenMatch / compareOrBless API documented"
    - "GBC auto-detect via discoverFiles 0x143 documented"
key_files:
  modified:
    - context/TESTING.md
decisions:
  - "Documentation-only plan; all API names verified against GoldenAssertions.kt and AgentSessionConfig.kt before documenting"
metrics:
  duration: "1 min"
  completed: "2026-06-15"
  tasks: 1
  files: 1
---

# Phase 22 Plan 14: TESTING.md Golden Screenshot Documentation Summary

Updates `context/TESTING.md` to document the central goldens scheme delivered by Phase 22 (plans 22-01 through 22-12).

## What Was Done

Added a "Visual Goldens" section to `context/TESTING.md` (between the GbktTestExtension Lifecycle table and the Fluent Assertions section) documenting:

1. **Layout:** `gbkt-examples/<module>/src/test/resources/goldens/<rom>/<anchor>.png` — per-module, co-located with tests, anchors named descriptively and phase-agnostically.
2. **Scratch locations:** Captured PNGs and diff images go to gitignored `build/gbkt/screenshots/`; emission `.txt` dumps go to gitignored `build/gbkt/test-evidence/` (the in-test C assertion is the correctness gate).
3. **`assertGoldenMatch` helper:** Signature, capture-to-scratch flow, exact pixel diff (tolerance 0.0), failure with pixel count and diff image path.
4. **Missing-golden behavior:** Hard `AssertionError` with `-Pgbkt.updateGoldens` re-baseline hint — a plain `./gradlew test` never writes a golden.
5. **Re-baseline command:** `./gradlew test -Pgbkt.updateGoldens`, composable with `--tests` for subset re-bless. Documented as the only sanctioned path — no manual PNG copy.
6. **GBC-header guard (D-07):** `AgentSessionConfig.discoverFiles` auto-detects `gbcMode` from ROM offset `0x143` (`0x80`/`0xC0`); GBC-target tests must assert `config.gbcMode == true` before blessing to prevent inverted-palette DMG captures from being committed as goldens.
7. **`.planning/phases/**/evidence/`:** Noted as gitignored scratch, not the golden store.

Also updated the `gbcMode` constructor parameter description for `GbktTestExtension`: the parameter is optional because `discoverFiles` auto-detects GBC mode; manual `gbcMode = true` is only needed when bypassing `discoverFiles` with a custom `AgentSessionConfig`.

## Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add goldens + re-baseline section; update gbcMode guidance | a5929abd | context/TESTING.md |

## Deviations from Plan

None - plan executed exactly as written.

## Threat Flags

None. Documentation-only plan; no executable surface added.

## Known Stubs

None. All API names and behavior verified against the actual implementation:
- `GoldenAssertions.kt` (assertGoldenMatch, compareOrBless, GBKT_UPDATE_GOLDENS_PROP)
- `AgentSessionConfig.kt` (discoverFiles, CGB_FLAG_OFFSET = 0x143, CGB_ENHANCED = 0x80, CGB_ONLY = 0xC0)

## Self-Check: PASSED

- [x] `context/TESTING.md` modified and committed at a5929abd
- [x] `grep -c "goldens" context/TESTING.md` → 7 matches
- [x] `grep -c "gbkt.updateGoldens" context/TESTING.md` → 3 matches
- [x] `grep -c "0x143" context/TESTING.md` → 2 matches
- [x] No `EVIDENCE_DIR` text in TESTING.md (verified: grep returns no match)
