---
phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
plan: 07
subsystem: ci-docs
tags: [ci, docs, cleanup, retired-examples, v2-rename, release-readiness]

# Dependency graph
requires:
  - phase: 14
    plan: 06
    provides: V2 rename + dead-code sweep (textual sweep complete)
provides:
  - .github/workflows/kotlin.yml: KEEP-only build + generateC, no buildRom, no explorer/racer
  - docs free of retired-example references (racer/explorer removed from listings)
  - docs free of V2 mentions (GBDKPipelineV2/SimulationContextV2 updated to unsuffixed)
  - context/UAT-racer.md + context/UAT-explorer.md deleted
affects: [14-08 final-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CI YAML rewrite: KEEP-only build + generateC (7 examples), no GBDK-dependent buildRom step"
    - "Doc V2 → unsuffixed text replacement across 6 doc files"

key-files:
  created: []
  modified:
    - .github/workflows/kotlin.yml
    - README.md
    - CLAUDE.md
    - context/TESTING.md
    - context/TOOLING.md
    - gbkt-backend-gbdk/CLAUDE.md
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/CLAUDE.md
  deleted:
    - context/UAT-racer.md
    - context/UAT-explorer.md

key-decisions:
  - "CI: replace stale explorer entries with all 7 KEEP examples in build + generateC steps (D-12)"
  - "CI: no :buildRom added (D-13 — GBDK toolchain not on CI runner)"
  - "README.md: replace Racer table row with 7-example table; replace racer build commands"
  - "CLAUDE.md example listing: updated from 3 (incl. explorer) to all 7 KEEP examples"
  - "V2 mentions stripped from CLAUDE.md, context/TESTING.md, context/TOOLING.md, gbkt-backend-gbdk/CLAUDE.md, pipeline/CLAUDE.md"
  - "UAT-racer.md + UAT-explorer.md: git rm (retired UAT docs for dead examples)"
  - "gradle.properties confirmed 0.1.0 — not changed"
  - "context/CI_CD.md v1.0.0 tag pattern: left intact (it is a GitHub Actions tag-pattern example, not a release label)"

requirements-completed: [Req 5]

# Metrics
duration: 6min
completed: 2026-06-06
---

# Phase 14 / Plan 07: CI + docs cleanup — KEEP-only examples and V2 mention strip

**Rewrote CI workflow to build + generateC all 7 KEEP examples (dropping dead explorer), stripped retired-example references from README/CLAUDE.md and deleted UAT-racer/UAT-explorer docs, updated V2 doc mentions to unsuffixed forms across 6 doc files.**

## Performance

- **Duration:** ~6 min
- **Completed:** 2026-06-06
- **Tasks:** 2
- **Files changed:** 9 (7 modified, 2 deleted)

## Accomplishments

### Task 1: Rewrite .github/workflows/kotlin.yml

- "Build all modules" step: replaced `:gbkt-examples:explorer:build` with all 7 KEEP examples (pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template)
- "Verify Example C Generation" step: replaced `:gbkt-examples:explorer:generateC` with all 7 KEEP `:generateC` tasks
- No `:buildRom` step added (D-13: GBDK not on CI runner)
- mavenLocal-publish, Run-Tests, code-quality, version-consistency steps unchanged

### Task 2: Doc cleanup

- **README.md**: removed Racer row; replaced with 7-example table; replaced racer build commands with KEEP-example alternatives
- **CLAUDE.md line 94**: "three example projects (pong, breakout, explorer)" → "seven example projects (pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template)"
- **CLAUDE.md line ~214**: `GBDKPipelineV2.buildMetadataFile()` → `GBDKPipeline.buildMetadataFile()`
- **CLAUDE.md line ~698**: pipeline table entry `GBDKPipelineV2` → `GBDKPipeline`
- **CLAUDE.md line ~717**: `SimulationContextV2` → `SimulationContext` in test sub-package table
- **context/TESTING.md line 23**: `SimulationContextV2()` → `SimulationContext()` in code example
- **context/TOOLING.md line ~81**: `GBDKPipelineV2.buildMetadataFile()` → `GBDKPipeline.buildMetadataFile()`
- **gbkt-backend-gbdk/CLAUDE.md**: updated 5 V2 references (lines 11/36/51/59/66) — `GBDKPipelineV2` → `GBDKPipeline` throughout; `generateV2()` delegation removed from Entry Point description
- **pipeline/CLAUDE.md line 5**: `GBDKPipelineV2` → `GBDKPipeline`, `PipelineV2Output` → `PipelineOutput`
- **git rm context/UAT-racer.md + context/UAT-explorer.md**: retired UAT docs for dead examples deleted
- **gradle.properties**: confirmed `gbktVersion=0.1.0` — unchanged

## Task Commits

1. **Task 1: CI workflow rewrite** — `cbc87bc0`
2. **Task 2: Doc cleanup + UAT deletions** — `f5cbfb4a`

## Verification Results

| Check | Result |
|-------|--------|
| `grep -c "gbkt-examples:explorer" kotlin.yml` | 0 |
| `grep -c "gbkt-examples:racer" kotlin.yml` | 0 |
| `grep -c "buildRom" kotlin.yml` | 0 |
| All 7 KEEP examples in `:build` step | PASS |
| All 7 KEEP examples in `:generateC` step | PASS |
| `grep -rEc "GBDKPipelineV2\|SimulationContextV2\|generateV2" CLAUDE.md + 5 doc files` | 0 (all clean) |
| `grep -c "racer" README.md` | 0 |
| CLAUDE.md example listing: explorer absent | PASS |
| `test ! -f context/UAT-racer.md` | PASS |
| `test ! -f context/UAT-explorer.md` | PASS |
| `grep -c "gbktVersion=0.1.0" gradle.properties` | 1 (confirmed unchanged) |

## Deviations from Plan

None — plan executed exactly as written.

Note: `context/CI_CD.md` contains `v1.0.0` in a GitHub Actions tag-pattern example (`v*` matching `v1.0.0`). This is not a release label — it is a YAML syntax illustration. Left intact per conservative scope guidance.

## Threat Surface Scan

No new trust boundaries. CI YAML + Markdown doc edits only; no new inputs, endpoints, or data flows.

T-14-13 (CI referencing deleted module): MITIGATED — kotlin.yml rewrites to KEEP-only; grep gates confirm zero explorer/racer/buildRom.
T-14-14 (Docs advertising retired example): MITIGATED — zero racer/explorer refs in user-facing docs; UAT-racer/UAT-explorer deleted.

## Known Stubs

None.

## Self-Check: PASSED

- `cbc87bc0` exists: confirmed
- `f5cbfb4a` exists: confirmed
- No V2 mentions in any target doc file: confirmed (grep returns 0 for all 6 files)
- No racer in README.md: confirmed (count = 0)
- UAT files deleted: confirmed (`test ! -f` passes for both)
- gradle.properties gbktVersion=0.1.0: confirmed (count = 1)
- All 7 KEEP examples in kotlin.yml build + generateC: confirmed (grep shows all 14 task references)

---
*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Completed: 2026-06-06*
