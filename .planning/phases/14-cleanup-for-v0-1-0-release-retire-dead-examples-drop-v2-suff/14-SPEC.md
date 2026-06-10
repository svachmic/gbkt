# Phase 14: cleanup for v0.1.0 release — retire dead examples, drop V2 suffixes, remove pre-AST dead code — Specification

**Created:** 2026-06-06
**Ambiguity score:** 0.15 (gate: ≤ 0.20)
**Requirements:** 5 locked

## Goal

Leave a lean, fully-building tree that ships only examples which both compile (`:buildRom` EXIT 0) and run, carries zero `V2` migration-suffix identifiers, contains no proof-dead pre-AST code, and is labeled v0.1.0 — ready for a human to tag and publish the v0.1.0 GitHub release. **No new features.** Correctness of surviving examples is preserved under a generated-C byte-identity gate.

## Background

Grounded in the current tree (scouted 2026-06-06):

- **Examples:** `settings.gradle.kts` includes 8 — `pong, breakout, racer, simple-physics, metasprites, metasprites-stress, banks, platformer-template`. `racer` is known-dead. `LabyrinthOfTheDragon-port/` is commented out of settings but **262 files are still git-tracked**; a sibling `LabyrinthOfTheDragon/` dir also exists. `gbkt-examples/.archive/` already holds `dungeon, explorer, platformer, platformer-gbc, rpg-lite, shmup`. CI (`.github/workflows/kotlin.yml`) is **stale** — it still builds/generates `:gbkt-examples:explorer` (archived), so CI references dead modules.
- **V2 suffixes:** ~172 main-src files reference `V2`. Top symbols: `GBDKPipelineV2` (374 refs), `SimulationContextV2` (143), `generateV2` (27), plus example classes `PongV2`/`BreakoutV2`/`ExplorerV2`, files named `*V2.kt`, and diagnostic test classes `DV2BgAspectDiagnosticTest` / `DV3VisualV2DiagnosticTest`. There was never a "V1" — the suffix was only a graceful-migration scaffold for the non-sealed-IR/visitor migration, now settled.
- **Dead code:** `GBDKBackend` has both `generate()` (CodegenBackend override — candidate dead path) and `generateV2()` (the real entry). Renaming `generateV2`→`generate` collides with the dead `generate()`, so the dead-code sweep and the rename interact (Track 3 must clear the dead `generate()` before Track 2 renames into that name).
- **Version:** `gradle.properties` already declares `gbktVersion=0.1.0`; release build is `-SNAPSHOT` unless `-Prelease`. No git tag / GitHub release exists yet. The internal "v1.0" milestone label was a working name; the project is honestly pre-1.0.

## Requirements

1. **Example audit & retirement**: Every included example is empirically verified, and any that does not both build and run is retired from the shipping tree.
   - Current: 8 examples in `settings.gradle.kts`; `racer` known-dead; CI builds an already-archived `explorer`. No documented per-example build+run verdict exists.
   - Target: Each of the 8 included examples gets a clean `:buildRom` AND a runtime check; the result is a documented keep/retire verdict per example with evidence. Examples that fail either gate are retired (removed from `settings.gradle.kts`, deleted from the tree per Req 2, and unreferenced by docs/CI).
   - Acceptance: A per-example audit table (example → buildRom result → run result → KEEP/RETIRE) exists in the phase artifacts; every KEEP example has evidence of a clean `:buildRom` and a successful run; `racer` (and any other failing example) is RETIRE.

2. **Dead-content deletion (git rm everything)**: All retired/dead content is hard-deleted from the working tree.
   - Current: `LabyrinthOfTheDragon-port/` (262 tracked files) + `LabyrinthOfTheDragon/` present; `gbkt-examples/.archive/` (6 archived examples) present; `racer` present and included.
   - Target: `git rm` of `LabyrinthOfTheDragon-port/`, `LabyrinthOfTheDragon/`, the entire `gbkt-examples/.archive/` directory, and every example the Req-1 audit marks RETIRE. History is preserved in git; nothing dead remains in the working tree.
   - Acceptance: `git ls-files` returns zero entries under `LabyrinthOfTheDragon`, `LabyrinthOfTheDragon-port`, and `gbkt-examples/.archive/`; no RETIRE example directory remains; `settings.gradle.kts` includes only KEEP examples; whole tree still configures (`./gradlew projects` succeeds).

3. **V2-suffix removal (all identifiers everywhere)**: Every migration-era `V2` identifier is renamed with no behavior change.
   - Current: ~172 main-src files plus tests reference `V2`; symbols `GBDKPipelineV2`, `SimulationContextV2`, `generateV2`, example classes `PongV2`/`BreakoutV2`, diagnostic test classes `DV2.../DV3VisualV2`, and `*V2.kt` filenames.
   - Target: All `*V2` symbols, file names, and KDoc/doc references are renamed to their unsuffixed form across main src, examples, AND tests (including diagnostic test class names). The `generateV2`→`generate` rename is reconciled with Req 4's removal of the dead `generate()` so no name collision remains.
   - Acceptance: `grep -rE "\bV2\b|[A-Za-z_]+V2\b" --include=*.kt` over non-build, non-`.git`, non-`.claude/worktrees` paths returns zero matches; whole-tree compile GREEN; survivors' generated C is byte-identical per Req 5 (proving rename was behavior-neutral).

4. **Conservative proof-gated dead-code sweep**: Genuinely-unused pre-AST code is removed only with positive non-reachability evidence.
   - Current: Pre-AST leftovers such as the unused `GBDKBackend.generate()` override; possibly other orphaned files predating the AST/visitor codegen migration.
   - Target: Remove only code shown unreachable by evidence (e.g. no production callers). After each removal, the whole tree compiles and the full JVM test suite passes — anything still reachable would break and is therefore not deleted. Anything ambiguous is left in place.
   - Acceptance: Each removed item has a recorded reachability justification; whole-tree compile GREEN and full JVM test suite GREEN after the sweep; no reachable code was deleted (no broken references).

5. **Release readiness & regression preservation**: The tree builds GREEN, ships only working examples, is labeled v0.1.0, and surviving examples are byte-shape-preserved — ready for a human to tag/publish v0.1.0.
   - Current: `gbktVersion=0.1.0` already set; CI stale; no documented byte-identity regression sweep over survivors for this phase's changes; phase does NOT cut the tag.
   - Target: Whole tree builds GREEN; only KEEP examples remain and are wired into CI (`.github/workflows/kotlin.yml` references only KEEP examples); all docs/cross-refs (settings, README, CLAUDE.md, context docs) reflect the surviving example set and v0.1.0; the actual `git tag`/GitHub-release publish is left as a manual post-phase step (NOT performed in-phase).
   - Acceptance: For each KEEP example, generated C is byte-identical to its pre-phase baseline AND clean `:buildRom` EXIT 0 (pong ROM exempt as PASS\* — known toolchain nondeterminism); CI workflow contains no archived/retired example references; no doc references a retired example or the "v1.0" working label as the release version; version surfaces read 0.1.0.

## Boundaries

**In scope:**
- Per-example build+run audit with a documented keep/retire verdict (Req 1)
- `git rm` of `LabyrinthOfTheDragon-port/`, `LabyrinthOfTheDragon/`, `gbkt-examples/.archive/`, and all RETIRE examples (Req 2)
- Renaming every `V2` identifier/file/doc-reference across main src, examples, and tests (Req 3)
- Conservative, evidence-gated removal of proof-dead pre-AST code (Req 4)
- Updating `settings.gradle.kts`, CI workflows, README/CLAUDE.md/context docs to the surviving set + v0.1.0 (Req 5)
- Generated-C byte-identity regression sweep over surviving examples (Req 5)

**Out of scope:**
- Creating the `git tag` or publishing the GitHub release — left as a manual post-phase step (phase ends at "ready to tag")
- Any new features or behavior changes — phase is cleanup-only; byte-shape of survivors must be preserved
- Fixing/reviving dead examples (racer, archived examples) so they build — they are retired, not repaired
- Broad aggressive unused-symbol sweeps beyond proof-dead pre-AST code — Req 4 is conservative by mandate
- Cleaning `.claude/worktrees/` stale agent worktrees — git-ignored tooling artifacts, not part of the shipped tree
- Changing the version number away from 0.1.0 — it is already set

## Constraints

- **No behavior change.** The V2 rename and dead-code removal must not alter emitted C; surviving examples' generated C must be byte-identical to their pre-phase baselines (pong ROM exempt — PASS\*, documented toolchain nondeterminism).
- **Proof before deletion.** Dead-code removal requires positive non-reachability evidence; whole-tree compile + full JVM test suite must stay GREEN after each removal.
- **Rename/removal ordering.** `generateV2`→`generate` must be reconciled with removal of the dead `GBDKBackend.generate()` so no name collision survives.
- **Standard project conventions** (Kotlin 2.3.0, JVM 21, `pluginTest` not `:gbkt-gradle-plugin:test`, no parallel `gradle clean` per project rule) apply.

## Acceptance Criteria

- [ ] A per-example audit table (example → buildRom → run → KEEP/RETIRE) exists with evidence; `racer` is RETIRE
- [ ] `git ls-files` returns zero entries under `LabyrinthOfTheDragon`, `LabyrinthOfTheDragon-port`, and `gbkt-examples/.archive/`
- [ ] No RETIRE example directory remains in the tree; `settings.gradle.kts` includes only KEEP examples
- [ ] `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` (excluding build/.git/.claude/worktrees) returns zero matches
- [ ] Whole-tree compile GREEN and full JVM test suite GREEN after the dead-code sweep
- [ ] Each removed dead-code item has a recorded reachability justification (no reachable code deleted)
- [ ] For each KEEP example: generated C byte-identical to pre-phase baseline AND `:buildRom` EXIT 0 (pong ROM exempt as PASS\*)
- [ ] `.github/workflows/kotlin.yml` references only KEEP examples (no `explorer`/archived/retired modules)
- [ ] No doc (README, CLAUDE.md, context/*) references a retired example or uses "v1.0" as the release version; version surfaces read 0.1.0
- [ ] The git tag / GitHub release is NOT created in-phase (left ready for manual publish)

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                              |
|--------------------|-------|------|--------|----------------------------------------------------|
| Goal Clarity       | 0.88  | 0.75 | ✓      | Audit-driven survivors, rename-all, ready-to-tag   |
| Boundary Clarity   | 0.85  | 0.70 | ✓      | git-rm-everything; tag/publish out of scope        |
| Constraint Clarity | 0.82  | 0.65 | ✓      | Byte-identity gate; proof-before-deletion          |
| Acceptance Criteria| 0.82  | 0.70 | ✓      | 10 pass/fail checks                                |
| **Ambiguity**      | 0.15  | ≤0.20| ✓      |                                                    |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

## Interview Log

| Round | Perspective     | Question summary                              | Decision locked                                              |
|-------|-----------------|-----------------------------------------------|-------------------------------------------------------------|
| 1     | Researcher      | Disposal of Labyrinth/dead/.archive?          | git rm everything (hard-delete all three)                   |
| 1     | Researcher      | What determines example survivor set?         | Audit each via clean :buildRom + run; empirical keep/retire |
| 1     | Boundary Keeper | V2 rename scope?                              | All identifiers everywhere (main src + examples + tests)    |
| 2     | Boundary Keeper | Cut the release or stop at ready?             | Stop at ready-to-tag; tag/publish is manual post-phase      |
| 2     | Failure Analyst | Dead-code sweep aggressiveness + gate?        | Conservative + proof-gated (compile + full suite GREEN)     |
| 2     | Failure Analyst | Regression gate for survivors?               | Generated-C byte-identity + :buildRom EXIT 0 (pong exempt)  |

---

*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Spec created: 2026-06-06*
*Next step: /gsd-discuss-phase 14 — implementation decisions (audit method, rename ordering, baseline capture, CI edits)*
