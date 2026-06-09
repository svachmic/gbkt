# SEED-018 — RPG character codegen extern/declaration mismatch (dungeon + explorer buildRom)

**Origin phase:** 11.1 plan 09 (regression sweep, 2026-05-20)
**Status:** Open — discovered via the Phase 11.1 phase-close BLOCKING `:buildRom` sweep. Pre-existing defect at the Phase 11.1 base commit (same commit as Phase 11.2 close `21a12148`).
**Routing:** Open; not yet bound to a target phase. Needs its own discuss-phase + research per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` — system-wide codegen/type-system fix.
**Blast radius:** WIDE — touches `gbkt-genre-rpg` character codegen and/or `gbkt-backend-gbdk` character visitor + `Character` DSL builder. Affects every game that declares a `character { stats { ... } }` block.

## Failure pattern (verbatim from `:buildRom`)

```
gbkt-examples/<game>/build/gbkt/generated/main.c:60: error 91:
  extern definition for '_char_adventurer_hp' mismatches with declaration.
gbkt-examples/<game>/build/gbkt/generated/game.h:66: error 177: previously defined here
gbkt-examples/<game>/build/gbkt/generated/main.c:61: error 91:
  extern definition for '_char_adventurer_sp' mismatches with declaration.
gbkt-examples/<game>/build/gbkt/generated/game.h:67: error 177: previously defined here
...
```

Affected stats: hp, sp, atk, def, matk, mdef, agl (full RPG stat set, all 7).

## Affected games (Phase 11.1 Plan 09 regression sweep)

| Game | Character | Symbol prefix | `:buildRom` |
|------|-----------|---------------|-------------|
| `gbkt-examples/dungeon` | `adventurer` | `_char_adventurer_*` | FAILED |
| `gbkt-examples/explorer` | `hero` | `_char_hero_*` | FAILED |
| `gbkt-examples/banks` | (no character) | — | GREEN |
| `gbkt-examples/racer` | (no character) | — | GREEN |

Banks + racer build clean because neither declares a `character { ... }` block — the defect is gated on the RPG character codegen path.

## Provenance — pre-existing, NOT introduced by Phase 11.1 / 11.2

- **Same failure at the pre-Phase-11.2 base commit `dfe52566`** per `.planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/deferred-items.md` §"Pre-existing RPG character codegen extern/declaration mismatch".
- **Same failure documented in user memory `project_rpg_char_codegen_debt.md`** (2026-05-20).
- Phase 11.1 changes (plans 01–08) touched `gbkt-ir/SceneIR.kt`, `gbkt-lang/SceneBuilder.kt`, `gbkt-backend-gbdk/GBDKPipelineV2.kt + SceneVisitor.kt + postprocess/FunctionDeduplicationPass.kt + postprocess/COutputOptimizer.kt`, `gbkt-emulator/SavestateManager.kt`, and `gbkt-examples/banks/Banks.kt`. None of these paths emit `_char_*` symbols.
- `git diff --stat 21a1214852c83aff014f844e4286103f201dc9e7 HEAD -- gbkt-genre-rpg/ ...` shows zero modifications to the RPG character codegen path on the Phase 11.1 branch.

The defect existed before Phase 11.1 plan 01 and survives unchanged through plan 09.

## Why a separate phase is required

Per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`:

> "For system-wide codegen / type-system changes, stop driving inline recommendations; route to gsd-phase → gsd-spec-phase → gsd-discuss-phase → gsd-plan-phase WITH research."

The choice of which declaration is authoritative (the extern in `main.c` vs the declaration in `game.h`) is a type-system decision that touches:

- `gbkt-genre-rpg/.../character/` — Character DSL + IR
- `gbkt-backend-gbdk/.../codegen/visitor/CharacterVisitor.kt` (and/or wherever `_char_*` are emitted)
- `gbkt-backend-gbdk/.../codegen/visitor/` — the header-emission site that writes `game.h`'s declarations
- Every game with a `character { ... }` block — Pokémon-scale RPG ambitions in PROJECT.md depend on this path being sound.

The fix needs its own discuss-phase to decide:
1. Which side is authoritative? (extern in `.c` or declaration in `.h`)
2. What type are RPG stats? (`UINT8`? `UINT16`? signed?)
3. Should `_char_<name>_<stat>` symbols be moved to a per-character struct?
4. Should the build emit `static const` vs `extern` based on linkage scope?

Plus research across:
- All `_char_*` emission sites in `gbkt-genre-rpg` and `gbkt-backend-gbdk`
- All `Character` / `Stats` DSL builders in `gbkt-lang` / `gbkt-genre-rpg`
- All RPG-using example games (currently dungeon, explorer; future: rpg-lite, any Pokémon-scale port)

## Hard requirements for the fix phase

- **Both `dungeon:buildRom` and `explorer:buildRom` flip from RED to GREEN.** No more `error 91` / `error 177` on `_char_*` symbols.
- **No regression on `banks:buildRom` or `racer:buildRom`.** They don't exercise the path today, but they must stay GREEN after the fix.
- **Per-game JVM-tier sentinel test locking the extern/decl alignment.** The test prevents drift — if a future codegen change re-introduces the mismatch, the test catches it at JVM-tier (millisecond feedback) instead of waiting for `:buildRom` (slow `lcc` invocation).
- **Per memory `feedback_visual_evidence_for_visual_truths.md`:** include a screenshot of dungeon and explorer ROMs actually booting and rendering the title scene, not just `:buildRom` SUCCESS.

## How to apply (proper route — do NOT inline patch)

1. `/gsd-phase add` to insert a new phase (likely `11.3-rpg-character-codegen-extern-decl-alignment-inserted` or similar). The phase number depends on whether Phase 12 has started yet; if 12 has started, insert before 12, otherwise insert as 11.3.
2. `/gsd-discuss-phase <new>` to clarify scope + decide extern-vs-decl authority + RPG stat type contract.
3. `/gsd-plan-phase <new>` with research into all `_char_*` emission sites across `gbkt-genre-rpg`, `gbkt-backend-gbdk` character visitors, and `Character` DSL builders.
4. Add a JVM-tier extern/decl-alignment sentinel test (per game with a `character { ... }` block).

## Discovery hooks (so a future maintainer finds this seed)

- `.planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/deferred-items.md` §"Pre-existing RPG character codegen extern/declaration mismatch" — references SEED-018.
- `.planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/evidence/regression-sweep-buildrom.log` — the concrete failure log that surfaced SEED-018 at phase-close time.
- `.planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/evidence/handoff.md` §"Out-of-cluster pre-existing defect (SEED-018)" — phase-close handoff cross-link.
- User memory `project_rpg_char_codegen_debt.md` (2026-05-20) — earlier capture of the same defect.

## Related artifacts

- `.planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/11.1-CONTEXT.md` §D-14 (absorption rule that authorized seed-capture instead of inline patching) + §D-15 (BLOCKING gate that surfaced the defect).
- `.planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/11.1-09-PLAN.md` `<behavior>` (the plan that explicitly authorized this seed-capture path: "If the failure is genuinely outside the seed cluster ... capture as a new seed file in `.planning/seeds/`").
- `.planning/seeds/SEED-017-sport-zone-tileset-pipeline-unification.md` — sibling pre-existing seed from Phase 11.2.


## Archive note (Phase 11.3, 2026-05-21)

As of Phase 11.3, the 3 RPG examples affected by this seed — `dungeon`, `explorer`,
and `rpg-lite` — have been archived under `gbkt-examples/.archive/` (gitignored, no
longer in the root `settings.gradle.kts` build graph). This is a **scope-down**, not
a fix: the underlying extern/declaration mismatch in `_char_<name>_<stat>` symbols
is unchanged. The seed remains **Open**. Routing remains unbound.

Practical effects:

- `./gradlew clean build` no longer fails on the `_char_*` mismatch — none of the 3
  affected games is in the build graph any more.
- The defect surface is masked, not fixed. Any future phase that revives `dungeon`,
  `explorer`, or `rpg-lite` (by moving the dir back from `.archive/` and restoring
  its `include(...)` line) MUST first resolve this seed.
- Discovery / ROM-build smoke is no longer a forcing function for the fix. The
  forcing function is now the revival path documented in `gbkt-examples/CLAUDE.md`
  §"Archived examples".

See `.planning/phases/11.3-milestone-scope-down-archive-aspirational-examples-rpg-clust/`
for the archive decision context.
