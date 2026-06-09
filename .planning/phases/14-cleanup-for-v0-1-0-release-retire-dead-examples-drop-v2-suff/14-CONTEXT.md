# Phase 14: cleanup for v0.1.0 release — retire dead examples, drop V2 suffixes, remove pre-AST dead code - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Leave a lean, fully-building tree that ships only examples which both compile
(`:buildRom` EXIT 0) and run, carries zero `V2` migration-suffix identifiers,
contains no proof-dead pre-AST code, and is labeled v0.1.0 — ready for a human
to tag and publish the v0.1.0 GitHub release. **No new features. No behavior
change.** Correctness of surviving examples is preserved under a generated-C
byte-identity gate. The git tag / GitHub release is NOT cut in-phase.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**5 requirements are locked.** See `14-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `14-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- Per-example build+run audit with a documented keep/retire verdict (Req 1)
- `git rm` of `LabyrinthOfTheDragon-port/`, `LabyrinthOfTheDragon/`, `gbkt-examples/.archive/`, and all RETIRE examples (Req 2)
- Renaming every `V2` identifier/file/doc-reference across main src, examples, and tests (Req 3)
- Conservative, evidence-gated removal of proof-dead pre-AST code (Req 4)
- Updating `settings.gradle.kts`, CI workflows, README/CLAUDE.md/context docs to the surviving set + v0.1.0 (Req 5)
- Generated-C byte-identity regression sweep over surviving examples (Req 5)

**Out of scope (from SPEC.md):**
- Creating the `git tag` or publishing the GitHub release — manual post-phase step
- Any new features or behavior changes — cleanup-only; byte-shape of survivors preserved
- Fixing/reviving dead examples (racer, archived) so they build — they are retired, not repaired
- Broad aggressive unused-symbol sweeps beyond proof-dead pre-AST code — Req 4 is conservative
- Cleaning `.claude/worktrees/` stale agent worktrees — git-ignored tooling artifacts
- Changing the version number away from 0.1.0 — already set

</spec_lock>

<decisions>
## Implementation Decisions

### Run-Verification Method (Req 1 — "build AND run")
- **D-01:** Each KEEP example gets a **live MCP emulator run-check**, not just a JVM/variable assertion. Aligns with the project's Visual Evidence Rule and standing "visual evidence for visual truths" feedback. (`mcp__gbkt-emulator__*` must be live; GBDK required for the ROMs.)
- **D-02:** Run-check depth is **boot + one input cycle**: boot to first meaningful screen → capture screenshot → drive one input cycle (Start/move per the example's PLAYBOOK.md where present) → capture again, proving the loop is live, not a frozen first frame. Reuse each example's `PLAYBOOK.md` if it exists.
- **D-03:** `racer` is **RETIRE** (known-dead) — it is retired on documented-failure status, NOT repaired (per SPEC Out-of-scope). The audit only needs to confirm it fails build/run; do not invest effort reviving it.
- **D-04:** The per-example audit table (example → buildRom → run → KEEP/RETIRE) plus the boot/input screenshots live in the phase `evidence/` directory as the Req-1 acceptance artifact.

### Byte-Identity Baseline Strategy (Req 5)
- **D-05:** Capture a **full generated-C snapshot** (main.c + all bank*.c, SHA-256) for **every KEEP example** — not just sprites. Snapshot lands in the phase `evidence/` directory.
- **D-06:** The 2 committed `*GeneratedSpriteByteIdentityTest` baselines (metasprites, metasprites-stress; re-pinned in 13.6-07) stay as a **second, independent gate**. Verify they are current at phase start; a re-pin if stale is behavior-neutral and acceptable, but the full-snapshot gate is the primary check.
- **D-07:** Baseline is captured from the **pre-phase HEAD** (current `feat/d_and_d_gaps`), before any V2 rename or dead-code removal mutates the tree — so any diff proves the change was behavior-neutral.
- **D-08:** The gate runs **after each mutating track**: regenerate+diff after the V2 rename (proves rename behavior-neutral) AND after the dead-code sweep (proves removal behavior-neutral). This localizes any drift to the track that caused it. pong ROM is PASS\* (known toolchain nondeterminism — generated-C is the real gate, ROM hash exempt).

### V2 Rename Execution (Req 3)
- **D-09:** **Semantic-first, textual-sweep-second.** Use Serena/IDE `rename_symbol` for the big code symbols (`GBDKPipelineV2` 374 refs, `SimulationContextV2` 143, `generateV2` 27, `PongV2`/`BreakoutV2`) — follows references safely across modules. Then a textual sed pass for the residue: `*V2.kt` filenames, KDoc/comment mentions, and diagnostic test class names (`DV2BgAspectDiagnosticTest`, `DV3VisualV2DiagnosticTest`). The byte-identity gate (D-08) is the backstop against any missed/incorrect rename.
- **D-10:** Acceptance grep `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` (excluding build/.git/.claude/worktrees) must return **zero matches** across main src + examples + tests.

### Track Ordering (cross-Req sequencing)
- **D-11:** Execute tracks in this order:
  1. **Audit** all 8 included examples (build + live run-check) → KEEP/RETIRE verdicts
  2. **Retire** — `git rm` RETIRE examples + `LabyrinthOfTheDragon-port/` + `LabyrinthOfTheDragon/` + `gbkt-examples/.archive/`; update `settings.gradle.kts` to KEEP-only (Req 2)
  3. **Baseline** — full generated-C snapshot for surviving examples (D-05/D-07)
  4. **Dead-code sweep** — conservative, proof-gated; clears the dead `GBDKBackend.generate()` (Req 4) → gate (compile + full JVM suite GREEN) + byte-identity diff
  5. **V2 rename** — `generateV2`→`generate` is now collision-free because the dead `generate()` was removed in step 4 (Req 3) → byte-identity diff
  6. **CI / docs / version** — update workflows, README, CLAUDE.md, context docs to KEEP-set + v0.1.0 (Req 5)
  7. **Final regression sweep** — clean `:buildRom` EXIT 0 for each KEEP + whole-tree compile + full JVM suite GREEN
- **Rationale:** retiring before renaming shrinks the rename surface (don't rename code you're about to delete); sweeping before renaming pre-clears the `generate()`/`generateV2` name collision the SPEC flagged.

### CI Workflow Rewrite (Req 5)
- **D-12:** Rewrite `.github/workflows/kotlin.yml` to **build + generateC for ALL KEEP examples** (replacing the stale pong/breakout/explorer list, where `explorer` is archived/dead). CI then guards the entire shipping set against compile/codegen regressions.
- **D-13:** CI stays **build + generateC only — NO `:buildRom`** in CI (GBDK toolchain is not provisioned on the runner). buildRom verification stays local/manual per the regression sweep (D-08/D-11 step 7).

### Claude's Discretion
- Exact dead-code reachability-proof technique (grep-callers + compile + full-suite) is the planner/executor's call, within the conservative mandate.
- Whether to fold `rpgregistry-clear-never-called` into the Req-4 sweep depends on whether it survives the non-reachability proof — decide at sweep time.

### Folded Todos
- **`metasprites-byte-identity-baseline-stale-since-12.8`** — folded into the baseline strategy. Considered effectively addressed by 13.6-07's re-pin; the D-05 full-snapshot gate covers survivors regardless. Verify the committed baselines are current at phase start (D-06); a behavior-neutral re-pin is acceptable if stale.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements (LOCKED)
- `.planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/14-SPEC.md` — 5 locked requirements, boundaries, acceptance criteria. MUST read before planning.

### Verification methodology
- `CLAUDE.md` § "Verification Methodology — Visual Evidence Rule" — runtime-visible truths require a screenshot, not a variable assertion; governs the D-01/D-02 run-check.
- `context/TESTING.md` — test tiers (unit / emulator / UAT / MCP), `GbktTestExtension`, PLAYBOOK format, MCP tool reference; informs how the run-check is driven.
- `context/UAT_GUIDE.md` — MCP agent tooling for play-testing ROMs.

### Affected source / config (cleanup targets)
- `settings.gradle.kts` (lines 56–63) — the 8 included examples to audit/prune to KEEP-only.
- `.github/workflows/kotlin.yml` (lines 107, 113) — stale CI: builds/generateC `:gbkt-examples:explorer` (archived) + only pong/breakout/explorer; rewrite to KEEP-set.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — primary V2-suffix rename target (374 refs).
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt` — V2 rename target (143 refs).
- `gbkt-backend-gbdk/.../GBDKBackend.kt` — dead `generate()` override (Req-4 sweep) + real `generateV2()` entry (Req-3 rename collision point).
- `gbkt-examples/metasprites/.../MetaspritesGeneratedSpriteByteIdentityTest.kt`, `gbkt-examples/metasprites-stress/.../MetaspritesStressGeneratedSpriteByteIdentityTest.kt` — existing committed byte-identity gates (D-06).

### gradle / version
- `gradle.properties` — `gbktVersion=0.1.0` already set; release is `-SNAPSHOT` unless `-Prelease`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **MCP emulator tooling** (`mcp__gbkt-emulator__*`): emulator_start/observe/press/step/screenshot drive the D-01/D-02 live run-check. Each example may carry a `PLAYBOOK.md` (`emulator_get_playbook`) that scripts the boot+input cycle.
- **Committed byte-identity tests**: metasprites + metasprites-stress `*GeneratedSpriteByteIdentityTest` provide a ready-made second gate (D-06).
- **`generateC` Gradle task**: produces `build/gbkt/generated/main.c` (+ bank*.c) per example — the artifact snapshotted for D-05.

### Established Patterns
- **Byte-identity discipline** (13.6/13.7/13.8): generated-C SHA comparison vs a pinned pre-change baseline is the project's standard regression proof for behavior-neutral codegen changes. pong ROM is the documented PASS\* exception.
- **Non-parallel `gradle clean`** (project rule): never fan out two `clean` against the same root — chain into one task list or run serially.
- **`pluginTest`, not `:gbkt-gradle-plugin:test`** for gradle-plugin coverage.

### Integration Points
- V2 rename touches main src, examples, AND tests across ~20 modules — `rename_symbol` must follow cross-module references; the genre/backend/test trees all reference `GBDKPipelineV2`/`SimulationContextV2`.
- `generateV2`→`generate` rename collides with the dead `GBDKBackend.generate()` override — sweep (step 4) must remove the dead method before the rename (step 5).

</code_context>

<specifics>
## Specific Ideas

- "Build AND run" must be evidenced by a real ROM running in the emulator with a visible screenshot — not a JVM-tier proxy. The user explicitly chose the strongest run-evidence tier and a boot+input-cycle depth.
- The phase ends at "ready to tag" — do NOT run `git tag` or publish the GitHub release.

</specifics>

<deferred>
## Deferred Ideas

### Reviewed Todos (not folded)
These keyword-matched Phase 14 but are behavior/correctness fixes, explicitly out of scope for a cleanup-only, byte-shape-preserving phase:
- `compilerom-silent-mbc5-fallback-warning` — behavior change (warning emission). Deferred.
- `configbuilder-cartridge-setter-api-consistency` — API change. Deferred.
- `easetozero-oscillates-when-by-greater-than-one` — behavior fix. Deferred.
- `orelse-may-attach-to-wrap-guard-ifop` — behavior fix. Deferred.
- `triggersystem-ref-registry-validation` — behavior change (new validation). Deferred.
- `wrapat-decrement-asymmetry-mask-vs-compare` — behavior fix. Deferred.
- `wrapat-zero-silent-always-reset` — behavior change. Deferred.
- `13.8-palette-bank-codegen-followups`, `13.6-07-convertsprites-hardening-followups` — codegen correctness follow-ups; would change emitted C. Deferred.
- `rpgregistry-clear-never-called` — "remove dead method" intersects the Req-4 sweep; fold ONLY if it survives the non-reachability proof at sweep time (see D-Discretion). Otherwise deferred.

</deferred>

---

*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Context gathered: 2026-06-06*
