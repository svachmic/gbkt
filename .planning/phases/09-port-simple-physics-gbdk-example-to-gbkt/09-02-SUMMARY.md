---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
plan: 02
subsystem: examples-scaffolding
tags: [gradle-module, gbkt-examples, asset-pipeline, reference-port, evidence-artifacts]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt
    provides: "Phase research (09-RESEARCH.md), patterns (09-PATTERNS.md), context (09-CONTEXT.md) — define module shape, asset choice, reference ROM baseline"
provides:
  - "gbkt-examples/simple-physics/ Gradle subproject scaffold (no DSL yet — Plan 03)"
  - "8x8 PNG sprite asset committed at res/sprites/smiley.png (copied from breakout/ball.png per D-07)"
  - "Reference oracle: evidence/reference/phys.c (byte-identical to upstream GBDK source) + BUILD.md (reproducible build instructions, 574-byte baseline, 1148-byte two-times target)"
  - "evidence/.gitignore scopes reference binaries out (D-10)"
affects: [09-03-DSL-port, 09-04-tests, 09-05-rom-build-and-size-compare, 09-06-mcp-playbook, 09-07-c-diff-appendix]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "gbkt-examples convention layout: build.gradle.kts + README.md + CLAUDE.md + res/ (no GenerateC.kt — plugin's gbkt { game(...) } is sufficient)"
    - "evidence/reference/ convention: source-of-truth committed, binaries gitignored, BUILD.md documents reproducibility"
    - "Anti-overfitting rail D-overfitting-3: ship a single-frame 8x8 sprite (validates physics, not animation) rather than reproducing the reference's 4-frame smiley"

key-files:
  created:
    - "gbkt-examples/simple-physics/build.gradle.kts"
    - "gbkt-examples/simple-physics/README.md"
    - "gbkt-examples/simple-physics/CLAUDE.md"
    - "gbkt-examples/simple-physics/res/sprites/smiley.png"
    - ".planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c"
    - ".planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/BUILD.md"
    - ".planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/.gitignore"
  modified:
    - "settings.gradle.kts (one new include line)"

key-decisions:
  - "Sprite asset: copy breakout/ball.png verbatim to smiley.png — single-frame 8x8 PNG already known to pass the gbkt asset pipeline; reference's 4-frame smiley animation is OUT of scope per anti-overfitting rail D-overfitting-3"
  - "Module entry point follows pong/ pattern exactly: gbkt { game(\"io.github.gbkt.examples.simple_physics.SimplePhysicsKt::simplePhysics\") }; no GenerateC.kt entry-point file needed"
  - "Output name set to simple-physics (kebab-case, matches module path)"
  - "Reference binaries (.gb/.map/.noi) gitignored — reproducible from BUILD.md, no need to commit a 32 KB artifact tracked in git"

patterns-established:
  - "evidence/reference/ holds source-of-truth artifacts that future plans (07 C-diff, 05 ROM-size compare) consume as oracles"
  - "BUILD.md format: Purpose, Prerequisites, Build Command, Outputs table, Within-2× target, Verification recipe"
  - "Scoped .gitignore lives at evidence/.gitignore (not phase root) — keeps binary-exclusion rules local to where binaries would land"

requirements-completed: [D-07, D-10]

# Metrics
duration: ~4min
completed: 2026-05-13
---

# Phase 09 Plan 02: Scaffold simple-physics module + reference artifacts Summary

**New `gbkt-examples/simple-physics/` Gradle subproject (mirroring pong/ convention) with 8x8 PNG sprite asset and reference `phys.c` + `BUILD.md` committed under `evidence/reference/` — foundation for Plan 03 DSL authoring and Plan 07 C-diff comparison.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-13T14:38:44Z
- **Completed:** 2026-05-13T14:42:59Z
- **Tasks:** 2
- **Files modified:** 8 (7 created + 1 modified)

## Accomplishments

- New `gbkt-examples/simple-physics/` module scaffold resolves `:gbkt-examples:simple-physics:tasks` cleanly via Gradle
- `settings.gradle.kts` updated with the single `include("gbkt-examples:simple-physics")` line after the existing `racer` include — no reorder, no deletion
- 8x8 PNG sprite asset committed (copied verbatim from `gbkt-examples/breakout/res/sprites/ball.png`); preserves binary contents via `cp`
- README.md (player-facing) and CLAUDE.md (developer notes) modeled directly on pong/ analog — CLAUDE.md documents the sprite-source decision and the upcoming Bug B workaround for Plan 03
- Reference `phys.c` copied byte-identically (99 lines) into `evidence/reference/`; `diff -q` against upstream returns 0
- BUILD.md documents reproducible build (`GBDK_HOME=/Users/michalsvacha/gbdk make gb`), 574-byte `l__CODE` baseline, 32768-byte ROM file size, 1148-byte two-times target, and `.noi` extraction recipe
- evidence/.gitignore scopes reference binaries out (per D-10): `reference/*.gb`, `*.map`, `*.noi`, `*.ihx`, `*.sym`, `reference/build/` plus generic `*.gb/*.map/*.noi` to catch port outputs

## Task Commits

Each task was committed atomically:

1. **Task 1: Create simple-physics Gradle module + sprite asset** — `833cf40d` (feat)
2. **Task 2: Commit reference artifacts under evidence/reference/** — `c9a99b39` (docs)

## Files Created/Modified

- `gbkt-examples/simple-physics/build.gradle.kts` — Gradle subproject config: kotlin/jvm + gbkt plugin, jvmToolchain 21, `gbkt { game("...SimplePhysicsKt::simplePhysics"); assets("res"); outputName.set("simple-physics") }`
- `gbkt-examples/simple-physics/README.md` — player-facing readme: how to play, demonstrated features (i16Var, signed comparison, sub-pixel physics, D-pad/A input), build/run commands, reference path, PLAYBOOK.md link
- `gbkt-examples/simple-physics/CLAUDE.md` — module developer notes: Build Commands, Code Structure (planned for Plan 03), Key DSL Patterns (signed clamp + ActorPropertyRef Bug B workaround + manual `shr 4` sub-pixel conversion), How to Modify, Dependencies, Asset Source provenance
- `gbkt-examples/simple-physics/res/sprites/smiley.png` — 8x8 PNG sprite (82 bytes, binary-safe copy of `breakout/ball.png`)
- `settings.gradle.kts` — appended `include("gbkt-examples:simple-physics")` after `racer`
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c` — verbatim 99-line GBDK reference source
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — reproducible build + size baseline doc
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/.gitignore` — scopes reference binaries and port artefacts out of git

## Decisions Made

- **Asset source = breakout/ball.png copy (not a new smiley artwork):** chosen for two reasons. (1) The existing PNG is already known to pass the gbkt asset pipeline, eliminating a class of risk that has nothing to do with the physics-DSL port goal. (2) Per anti-overfitting rail D-overfitting-3 in `09-CONTEXT.md`, single-frame validates physics; reproducing the reference's 4-frame smiley animation is out of scope for the port. Documented in `CLAUDE.md` § Asset Source.
- **No `GenerateC.kt` entry-point file:** `gbkt-examples/CLAUDE.md` explicitly notes that newer examples use only the gbkt plugin's `game(...)` declaration — pong/ has no `GenerateC.kt`, only `PongV2.kt`. Plan 02 honors this convention.
- **Reference binaries gitignored, not committed:** ROM is 32 KB padded; gradle build can reproduce it from `phys.c` + Makefile any time. Committing binaries adds noise to diffs without preserving auditable information that BUILD.md doesn't already document.

## Deviations from Plan

None — plan executed exactly as written. All Task 1 and Task 2 acceptance criteria pass (verified before commit):

- `:gbkt-examples:simple-physics:tasks --quiet` exits 0
- All required literal strings present in `build.gradle.kts` (`SimplePhysicsKt::simplePhysics`, `outputName.set("simple-physics")`, `assets("res")`, `id("io.github.gbkt")`)
- `settings.gradle.kts` includes the new module exactly once
- No `.kt` files exist under `gbkt-examples/simple-physics/src/` (intentional — DSL belongs to Plan 03)
- `evidence/reference/phys.c` byte-identical to upstream (`diff -q` returns 0)
- `BUILD.md` contains `GBDK_HOME`, `make gb`, `574`, `1148`, `32768`, `## Within-2×` section
- `.gitignore` scopes `reference/*.gb` and `reference/*.map`
- No `.gb`/`.map` binaries committed under `evidence/reference/`

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

**Ready for Plan 03 (DSL port):**

- Module scaffold resolves via Gradle, so Plan 03 can immediately add `src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt` and start authoring the DSL.
- Sprite asset at `res/sprites/smiley.png` is available for `asset("sprites/smiley.png")` references.
- Reference `phys.c` in `evidence/reference/` is the oracle Plan 03 will read line-by-line when porting (and Plan 07 will use for the C-diff appendix).
- CLAUDE.md flags the ActorPropertyRef Bug B workaround so Plan 03 authors check VALIDATION.md before choosing the cleanest expression form.

**Ready for Plan 05 (ROM-size compare):**

- BUILD.md documents the 574-byte `l__CODE` baseline and the 1148-byte ≤2× target — Plan 05 can grep these from BUILD.md or recompute from the reference ROM via the documented `.noi` extraction recipe.

**Ready for Plan 07 (C-diff appendix):**

- `evidence/reference/phys.c` is byte-identical to upstream and committed; Plan 07 can diff it against `gbkt-examples/simple-physics/build/gbkt/generated/main.c`.

## Self-Check: PASSED

Verified before SUMMARY emission:

- `gbkt-examples/simple-physics/build.gradle.kts` — FOUND
- `gbkt-examples/simple-physics/README.md` — FOUND
- `gbkt-examples/simple-physics/CLAUDE.md` — FOUND
- `gbkt-examples/simple-physics/res/sprites/smiley.png` — FOUND (non-empty, 82 bytes)
- `settings.gradle.kts` — modified (include count = 1)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c` — FOUND (byte-identical to upstream)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — FOUND (contains required strings)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/.gitignore` — FOUND
- Commit `833cf40d` — FOUND in git log
- Commit `c9a99b39` — FOUND in git log
- `./gradlew :gbkt-examples:simple-physics:tasks --quiet` — exits 0

---
*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Completed: 2026-05-13*
