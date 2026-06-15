# Phase 21: Codegen Fixes — Platformer and Remaining Seeds - Context

**Gathered:** 2026-06-14
**Status:** Ready for planning

<domain>
## Phase Boundary

The **final phase of milestone v0.1.1**. Close out every remaining open seed so
`.planning/seeds/` is **empty at phase close** (Success Criterion 5, interpreted
literally — every seed reaches a terminal disposition: fixed-in-place or
explicitly re-deferred to v0.2.0 backlog with a one-line rationale).

Unlike Phases 19/20 (pure no-codegen-change *confirmation* phases), Phase 21 is a
**mixed fix + confirmation phase**:

- **Real fixes (FIX-05 platformer, FIX-06 contained):** SEED-021 (pivot_adjust
  DSL lift), spawn-polish (per-zone spawn coords), SEED-PHASE-13 sub-pixel
  investigation, SEED-020 (serializer round-trip), SEED-022 (predicate
  consolidation), plus three doc/constant residuals (SEED-027/028/029).
- **Confirmation (pre-satisfied):** Criterion 1 — all platformer `cEmit()`
  escapes are already VERIFIED-ALREADY-FIXED by Phase 13.5; Phase 21 adds UAT
  re-verification only. Criterion 2 — 3 GBC UAT anchor screenshots re-shot.
- **Re-deferral with evidence:** the wide-blast-radius refactors
  (SEED-ZONE-MAGIC-STRING, SEED-017) move to the v0.2.0 backlog — Criterion 3
  explicitly permits "fixed **or explicitly re-deferred with evidence**".

All four platformer seeds are **LOCKED-visual** — closure requires a binding
user visual sign-off per the Visual Evidence Rule, not variable assertions.

</domain>

<decisions>
## Implementation Decisions

### Scope — Criterion 5 ("seeds/ empty at close")
- **D-01:** Criterion 5 is interpreted **literally** — every file under
  `.planning/seeds/` reaches a terminal disposition in Phase 21. No orphans. Each
  seed is either fixed-in-place (seed deleted/archived after fix) or moved to
  `.planning/backlog/v0.2.0/` with a one-line rationale. This matches Phase 16
  triage rigor and the "empty at close" wording.

### Seed dispositions (the full 13)
- **D-02 — Land in Phase 21 (real fixes):**
  - **SEED-021** pivot_adjust auto-derive (FIX-05) — lift resolution into the DSL.
  - **Spawn-polish** (SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY +
    SEED-platformer-template-spawn-polish, same root cause — superseded pair).
  - **SEED-PHASE-13** sub-pixel sink (FIX-05) — investigate-then-decide.
  - **SEED-020** GameIRSerializer full round-trip (FIX-06).
  - **SEED-022** tilemap-collision predicate consolidation (FIX-06).
  - **SEED-027** GBC `bitsPerPixel` constant + KDoc correction (byte-identical).
  - **SEED-028** ConfigBuilder removal migration note (doc-only).
  - **SEED-029** `whenever`→`runIf` cosmetic doc/KDoc sweep (~25 files).
- **D-03 — Re-defer to `.planning/backlog/v0.2.0/` with evidence:**
  - **SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION** — wide blast radius (every
    `zone()` site + gbkt-lang/gbkt-engine IR), needs its own discuss/spec phase.
    Too risky for a hardening release close. Rule #1 violation stays documented.
  - **SEED-017** sport-zone tileset pipeline unification — moderate refactor,
    no shipping example exercises it heavily; v0.2.0.
  - **SEED-023** `whenever`→`runIf` DSL unification — needs a deprecation cycle.
  - **SEED-025** remove deprecated `combatIsInState(String,String)` overload —
    explicitly v0.2.0 (can't remove until one release after its deprecation
    ships; v0.1.0 shipped it un-deprecated).
- **D-04 (scope-change flag for planner):** D-03 re-defers two seeds
  (ZONE-MAGIC-STRING, SEED-017) that the ROADMAP/REQUIREMENTS list under "active
  FIX-06 scope". This is permitted by Criterion 3 ("re-deferred with evidence").
  The planner MUST: (a) update `REQUIREMENTS.md` FIX-06 disposition + status,
  (b) physically move the four re-deferred seed files to
  `.planning/backlog/v0.2.0/`, (c) leave an evidence note in the phase artifacts.

### FIX-05 — SEED-021 pivot_adjust DSL lift
- **D-05:** Lift `pivot_adjust` resolution into the `tilemapCollision { }` builder
  so the user DSL is the single source of truth. The builder reads the bound
  metasprite via a typed ref (Project Rule #1 — no `"player"` magic string;
  [[feedback_no_magic_strings]]); the visitor consumes a resolved config value.
  Delete the visitor-side "metasprite lookup dance" + verbatim fallback constants
  (`PlatformerVisitor.kt:~615`), keep a validation diagnostic for "tilemapCollision
  bound but no metasprite resolvable". The underlying pivot fix already works —
  this is a robustness refactor. **Pairs with SEED-022** (same visitor; do them
  together). Visual regression gate: grounded-player GBC screenshot.

### FIX-05 — Spawn-polish
- **D-06:** Give each zone ownership of its player start coordinates (per-zone
  spawn), matching the GBDK reference's per-level `level_start_pos`. **CRITICAL
  RESEARCH FLAG:** `ZoneBuilder.spawn(x: UByte, y: UByte)` **already exists**
  (`gbkt-lang/.../WorldBuilders.kt:247`, added Phase 12.6 for DEFECT-2). The
  planner MUST reconcile against it — the fix is most likely **wiring the
  platformer-template to call the existing `spawn()` with correct world1Area1
  bottom-ground-row coords**, NOT adding a new duplicate `spawnPosition` primitive.
  Verify whether `spawn()` is fully consumed by the platformer codegen path before
  adding any new DSL surface. Re-shoot anchor-2 to confirm player visibly on a
  platform.

### FIX-05 — SEED-PHASE-13 sub-pixel sink
- **D-07:** **Investigate, then decide** (root cause genuinely unknown). Run the
  seed's diagnostic ladder: inspect player PNG for top/bottom gutters; verify
  `buildVerticalFootProbe` snap arithmetic uses **visible** sprite height (not
  collision-mask height); check `_playerY >> 4` floor-vs-round. Re-shoot the GBC
  anchor (the spawn-reposition + correct GBC palette may resolve the visual
  ambiguity). **If a real off-by-one** → fix it; **if intended/imperceptible** →
  close as-accepted with binding user visual sign-off. NOTE: distinct from
  [[learning_platformer_sprite_hitbox_overhang]] (that is *horizontal* overhang,
  by-design); this is *vertical foot* alignment — do not conflate. Add a JVM
  emission test for `_player_y` initial value + snap arithmetic regardless of
  fix-vs-accept outcome.

### FIX-06 — Contained refactors
- **D-08 — SEED-020:** Deserialize the 10 stubbed IR collections in
  `GameIRSerializer.deserialize()` + author round-trip tests in `gbkt-ir`.
  Contained to `gbkt-ir`; consumed only by external tooling (IDE plugin, MCP
  describe), **no compile-pipeline codegen blast radius** — low risk.
- **D-09 — SEED-022:** Consolidate the duplicated `gameUsesTilemapCollision`
  predicate (reflection path in `GBDKPipeline` vs direct path in
  `PlatformerVisitor.kt:~1656`) into a shared utility in the only common module,
  `gbkt-backend-api`. Small; do alongside D-05 (same visitor).

### FIX-06 — Doc/constant residuals
- **D-10 — SEED-027:** Correct `TargetProfiles.GAME_BOY_COLOR_SCREEN`
  `bitsPerPixel = 4` → `2` (+ KDoc prose). `ScreenSpec.bitsPerPixel` has zero
  readers → byte-identical by construction. Closes a Phase-18 loose end.
- **D-11 — SEED-028:** Add the ConfigBuilder property→function setter removal
  migration note + fix 4 stale doc/comment strings. Doc-only, no behavior change.
  Closes a Phase-18 loose end.
- **D-12 — SEED-029:** `whenever`→`runIf` cosmetic doc/KDoc sweep (~25 files:
  README, KDoc across modules, example CLAUDE.md). Pure docs, no compile impact.
  Functional sites already fixed in Phase 18 (18-29/18-30) — residual only. Use
  per-file judgment for historical test comments that may legitimately keep the
  name.

### Phase shape & byte-identity oracle
- **D-13 (oracle, locked):** **Unchanged-set guard + targeted proof.**
  Byte-identity guards only the examples that NO fix touches (a collateral-drift
  proof on the untouched set). Examples that ARE changed (platformer-template via
  D-05/D-06/D-07; any serializer-affected output) are proven by **UAT visual
  re-shoots + JVM emission tests** for the specific new behavior, with per-fix
  attribution via same-session before/after diffs. This adapts the Phase 19/20
  whole-tree oracle to a mixed fix/confirm phase. pong stays PASS\* in any sweep
  ([[project_pong_toolchain_nondeterminism]]).
- **D-14 (sequencing, locked):** **Fix-first, then re-shoot all 3 GBC anchors**
  against the final ROM — so the anchor screenshots serve double duty as the
  fix's visual evidence AND the Criterion-1 cEmit confirmation. One capture pass,
  post-fix (no double-shoot).

### Inherited constraints (from Phases 19/20)
- **D-15:** UAT capture reuses the JVM `*UatTest` StepAgent `captureAndRename()`
  harness, emitting PNGs to the phase `evidence/` dir. Platformer captures MUST
  run `gbcMode=true` with the `.noi` symFile ([[learning_platformer_mcp_needs_gbc_mode]]),
  and the ROM MUST be rebuilt clean immediately before capture
  ([[feedback_rom_build_smoke_test_for_codegen_phases]]).
- **D-16:** Commit discipline — Phase 21 commits contain only seed-closure work;
  **zero PR-#77 / S3776 cognitive-complexity refactors interleaved**
  ([[project_18_hardening_pr77]]). Executors run `:module:spotlessApply
  :module:detekt` per-commit ([[project_executor_gate_misses_spotless_detekt]]).

### Claude's Discretion
- Exact test method/assertion names, evidence PNG filenames, hashing commands for
  byte-identity diffs, the precise diagnostic order for SEED-PHASE-13, and whether
  spawn-polish needs any new DSL at all (D-06 — likely not) are left to the
  planner/executor, provided the acceptance criteria and Visual Evidence Rule are met.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope (MUST read first)
- `.planning/ROADMAP.md` §"Phase 21: Codegen Fixes — Platformer and Remaining Seeds"
  — Goal, D-11 FIX-05/FIX-06 triage notes, 5 success criteria
- `.planning/REQUIREMENTS.md` — FIX-05 and FIX-06 definitions + per-seed triage
  dispositions (lines 47–48); planner updates FIX-06 status per D-04
- `.planning/phases/16-seed-triage/TRIAGE.md` — source-of-truth disposition for
  every Phase-21 seed (CONFIRMED-OPEN entries)

### Open seed files (the work items — all under `.planning/seeds/`)
- `.planning/seeds/SEED-021-platformer-pivot-adjust-auto-derive.md` (D-05)
- `.planning/seeds/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` (D-06)
- `.planning/seeds/SEED-platformer-template-spawn-polish.md` (D-06, supersedes the above)
- `.planning/seeds/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` (D-07)
- `.planning/seeds/SEED-020-gameir-serializer-full-roundtrip.md` (D-08)
- `.planning/seeds/SEED-022-tilemap-collision-predicate-consolidation.md` (D-09)
- `.planning/seeds/SEED-027-gbc-screen-bitsperpixel-correctness.md` (D-10)
- `.planning/seeds/SEED-028-configbuilder-removal-migration-guidance.md` (D-11)
- `.planning/seeds/SEED-029-whenever-doc-reference-cleanup.md` (D-12)
- `.planning/seeds/SEED-017-sport-zone-tileset-pipeline-unification.md` (D-03 → backlog)
- `.planning/seeds/SEED-023-whenever-runif-unification.md` (D-03 → backlog)
- `.planning/seeds/SEED-025-remove-deprecated-combat-string-overload.md` (D-03 → backlog)
- `.planning/seeds/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md` (D-03 → backlog)

### Re-deferral destination
- `.planning/backlog/v0.2.0/` — existing v0.2.0 backlog dir (Plan 16-10 precedent);
  the four D-03 seeds move here

### Phase 19/20 precedent (the confirmation pattern this phase adapts)
- `.planning/phases/19-codegen-fixes-metasprite-cluster/19-CONTEXT.md` — UAT
  harness (D-01), audit-first guards, procedural byte-identity diff, commit discipline
- `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-CONTEXT.md`
  — two-tier byte-identity oracle, GBC-mode capture constraint, fix-evidence pattern

### Code targets (verify before changing)
- `gbkt-lang/.../WorldBuilders.kt:247` — **existing `ZoneBuilder.spawn(x,y)`**;
  reconcile spawn-polish against it (D-06) — do NOT add a duplicate primitive
- `gbkt-genre-platformer/.../PlatformerVisitor.kt` — `Deferred (SEED-021)` markers
  (~615–625 call-site resolution, ~1283–1293 KDoc) and `gameUsesTilemapCollision`
  (~1656) (D-05, D-09)
- `gbkt-ir/.../GameIRSerializer.kt` — `deserialize()` 10 stubbed collections (D-08)
- `gbkt-core/.../constraints/TargetProfiles.kt:50` — `GAME_BOY_COLOR_SCREEN`
  bitsPerPixel literal + KDoc (D-10)
- `gbkt-examples/platformer-template/.../**UatTest.kt` — GBC anchor re-shoot
  harness (D-14, D-15); `gbkt-examples/platformer-template/.../PlatformerTemplate.kt`
  — spawn coords (D-06)

### Methodology gates
- `.planning/verifier-gates.md` — Visual Evidence Rule (all four LOCKED-visual
  platformer seeds + the 3 anchor re-shoots require runtime GBC screenshots +
  binding user sign-off, not variable assertions)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- JVM `*UatTest` StepAgent + `captureAndRename()` — drives the built ROM, emits
  PNGs to evidence/; the anchor re-shoot + visual-fix-evidence mechanism (D-14/D-15).
- `ZoneBuilder.spawn(x: UByte, y: UByte)` (`WorldBuilders.kt:247`) — already
  present (Phase 12.6); the spawn-polish fix should USE this, not duplicate it (D-06).
- Phase 19/20 AUDIT-doc format + procedural before/after byte-identity diff — the
  oracle pattern adapted to the untouched-set in D-13.

### Established Patterns
- Per-seed evidence layout: `evidence/<SEED>/screenshot.png` + `capture-note.txt`
  (visual) / `main-c-excerpt.txt` (structural) — follow under the phase evidence dir.
- Emission guards assert against generated C via JVM tests; RED-by-design noted in
  a comment, no revert demonstration.
- Seeds move to `.planning/seeds/archive/` (fixed) or `.planning/backlog/v0.2.0/`
  (re-deferred) — leaving `seeds/` empty (D-01).

### Integration Points
- SEED-022 shared util lands in `gbkt-backend-api` (the only module both
  `PlatformerVisitor` and `GBDKPipeline` depend on).
- SEED-020 hooks `gbkt-ir` round-trip tests only — no codegen pipeline touch.
- Byte-identity diffs read `build/gbkt/generated/` — require a clean
  `:buildRom`/`generateC` before sampling (staleness caveat).

</code_context>

<specifics>
## Specific Ideas

- This is the **last phase of v0.1.1**; after it, `seeds/` empty + PR #77 (S3776)
  can be assessed for merge (held open through Phases 19/20/21).
- The MCP `gbkt-emulator` server wraps the same `StepAgent` API as the JVM UAT
  harness — JVM-tier captures are deterministically equivalent to MCP-tier (no
  dual capture path).
- If any fix touches plugin fixtures, use `pluginTest` (not `:gbkt-gradle-plugin:test`)
  — known publish/test ordering race; verify via two invocations.

</specifics>

<deferred>
## Deferred Ideas

Re-deferred to `.planning/backlog/v0.2.0/` (D-03, with evidence per Criterion 3):
- **SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION** — wide-blast `zone()` → delegate
  migration; needs its own discuss/spec phase in v0.2.0. Rule #1 violation tracked.
- **SEED-017** — sport-zone tileset pipeline unification (moderate refactor).
- **SEED-023** — `whenever`→`runIf` DSL unification (needs deprecation cycle).
- **SEED-025** — remove deprecated `combatIsInState(String,String)` (v0.2.0 by design).

Out of Phase 21 scope:
- Merging PR #77 (S3776 cognitive-complexity burn-down) — assess after this phase
  closes; not Phase 21 work.

None of the above are Phase 21 fix scope — discussion stayed within phase boundary.

</deferred>

---

*Phase: 21-codegen-fixes-platformer-and-remaining-seeds*
*Context gathered: 2026-06-14*
