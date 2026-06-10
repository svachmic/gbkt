# Named codegen bug — Phase 11

**Human-gate outcome (2026-05-20):** APPROVED — Plan 11-10 to implement Candidate 1 fix as specified below. INV-2 helper gating + D11-05-1 title-trampoline skew confirmed deferred to seeds via Plan 11-14.

**Surfaced by:** First clean buildRom (Plan 11-09)
**Log evidence:** `evidence/first-buildrom.log` (search: `Undefined Global '_trigger_saves'`)
**Bug class:** Candidate 1 — SaveSystem has no `trigger_<id>()` trampoline (per 11-RESEARCH §Top-2 Likely Codegen Bug Candidates + §DSL Call Surface Gap, HIGH probability prediction)
**UAT anchor blocked:** Anchor 4 (SRAM save persistence) — but also **the entire build** (compile-time linker error blocks ROM production for all 4 anchors)
**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`
**Line:** `visitSaveSystem()` opens at line 299; returns `listOf(saveGame, loadGame)` at line 485 — no `trigger_<id>` function is constructed. (Line numbers verified live against worktree HEAD 7e689d6b on 2026-05-20.)

## Exact log signature

From `evidence/first-buildrom.log` (lcc compile/link stage):

```
?ASlink-Warning-Undefined Global '_trigger_saves' referenced by module 'bank1'
.../banks/build/gbkt/generated/bank1.c:26: warning 112: function 'trigger_saves' implicit declaration
.../banks/build/gbkt/generated/bank1.c:26: warning 84: 'auto' variable 'trigger_saves' may be used before initialization
```

And in `bank1.c:24-26`:
```c
void play_frame(void) BANKED {
    if (button_pressed(J_SELECT)) {
        trigger_saves();       // ← unresolved external; SaveSystem visitor never emits this symbol
    }
```

## Fix spec (Plan 11-10 will implement)

Per 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" + 11-RESEARCH.md §"DSL Call Surface Gap" Option 1:

- In `gbkt-backend-gbdk/.../GBDKSystemVisitor.kt`, inside `visitSaveSystem()` after the existing `loadGame` construction (line ~483), construct a third `CFunction`:
  - `name = "trigger_$sanitizedId"`
  - `returnType = CVoid`
  - `params = listOf(CParam("slotIndex", CU8))`
  - `body = listOf(CExprStatement(CCall("save_game_$sanitizedId", listOf(CVar("slotIndex")))))`
  - `sectionComment = "SaveSystem trigger stub — called by ScriptOpVisitor.visitTriggerSystem"`
- Change `return listOf(saveGame, loadGame)` at line 485 to `return listOf(saveGame, loadGame, triggerStub)`.
- Lock the contract with a JVM-tier emission invariant in `BanksEmissionTest` (this is INV-4 in 11-RESEARCH §JVM-Tier Brace-Walk Pattern Reference): `mainC.contains("trigger_saves")` AND `extractFunctionBody(mainC, "trigger_saves").contains("save_game_saves(")`.

**Blast radius:** SINGLE function (`visitSaveSystem`) in a single visitor file. No ScriptOpVisitor changes (already calls `trigger_<id>()`). No IR additions. No new ScriptOp class. No new C AST node — uses existing `CFunction`/`CCall`/`CVar`/`CExprStatement`. This is exactly the Option-1 path 11-RESEARCH highlighted as "smallest change that unblocks anchor 4 without expanding DSL surface".

## Scope cap — surplus deferred to Plan 11-14 seeds

Per CONTEXT D-13 (ONE named bug per phase) and D-14 (surplus → seeds + Plan 11-14 sweep), the first buildRom and the prior waves surfaced TWO additional defect candidates that are NOT folded into Plan 11-10:

### Surplus #1 — INV-2 RED (`_bkg_tiles_load_banked` wrapper absent)

- **Routing:** Already deferred in `evidence/inv2-failure.txt` (Plan 11-07 RED-by-design routing). The helper at `GBDKPipelineV2.kt:972-980` is gated behind `hasSportRacing && bank > 1`; Banks.kt has no sport_racing genre, so the helper is never emitted.
- **First-buildrom evidence:** `grep -E "_bkg_tiles|set_bkg_tiles|SWITCH_ROM" main.c` returns zero matches; matches the INV-2 RED prediction.
- **Why NOT named:** This is RESEARCH Candidate 2 (MEDIUM probability), it's a runtime bug (Anchor 2), and it is NOT the FIRST blocking issue surfaced by the build. Per the task action "If the log contains multiple distinct errors, pick the FIRST blocking one as the named bug and capture the rest as seeds in Plan 11-14", this becomes seed material.
- **Disposition:** Seed for Plan 11-14 sweep. Expected fix touches `GBDKPipelineV2.buildHomeFile()` or `buildBkgTilesLoadBankedHelper()` — un-gate the helper from genre detection so any game with `gameIR.zones.isNotEmpty()` gets the wrapper.

### Surplus #2 — D11-05-1 (trampoline body inheritance)

- **Routing:** Already deferred in `deferred-items.md` (Plan 11-05 discovery). The first-buildrom regenerated `main.c` reproduces the symptom: `title_enter_trampoline()` at lines 202-205 carries `// Trampoline: pause_enter (bank 1)` comment and delegates body to `pause_enter();` (lines 206 + 211).
- **First-buildrom evidence:** `main.c:202-209` reads:
  ```c
  // Trampoline: pause_enter (bank 1)
  void title_enter_trampoline(void) {
      pause_enter();
  }
  void title_frame_trampoline(void) {
      pause_frame();
  }
  ```
- **Why NOT named:** It's a runtime correctness defect (would crash Anchor 1 at runtime), NOT the build-time blocker. The build fails on `trigger_saves` LONG before this bug could be observed. Per scope cap, only one named bug.
- **Disposition:** Seed for Plan 11-14 sweep. Expected fix is in trampoline emission in `GBDKPipelineV2` (or wherever `buildSceneNavigationFunction` / trampolines are constructed) — the pass inherits the last-emitted banked scene's body when the current scene has no banked function of its own. Title scene is HOME-resident, so its trampoline should be a no-op or omitted, not a delegation to the previous scene's body.

## Why Candidate 1 (`trigger_saves`) is the correct named bug — not 11-05-1 or INV-2

1. **It is the FIRST blocking issue**: lcc link fails on `trigger_saves` before any of the runtime issues (anchor 1 trampoline crash, anchor 2 missing tilemap load) could be tested. The ROM literally cannot be produced.
2. **Plan 11-10 can fix it in ONE commit**: blast radius is one function in one visitor file. Matches the scope-cap intent of "one named bug-fix" and avoids the `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` concern.
3. **RESEARCH §Top-2 ranked it HIGH probability**; the build outcome confirms the prediction verbatim.
4. **11-PATTERNS.md provides a precise analog** (`visitGenericSystem` else-branch already emits a `trigger_<id>` stub at lines 2616-2631) — the fix is "copy the analog, point the body at `save_game_<id>` instead of a no-op comment", which is exactly the "concrete named file, named function, named change" the checkpoint gate (Task 3) requires.
5. **Once fixed, Plan 11-10 can also LOCK the contract via INV-4 emission invariant** so the gap cannot regress. That is the productive cousin of the absence-of-bug "Branch C" path.

## Scope-cap statement

Per CONTEXT D-13: ONE named bug per phase. Plan 11-10 will fix Candidate 1 (`trigger_saves` stub in `visitSaveSystem`) AND lock it with the INV-4 JVM-tier emission invariant. Surplus defects (INV-2 `_bkg_tiles_load_banked` gating + D11-05-1 trampoline body inheritance) are NOT folded into Plan 11-10; they become seeds via Plan 11-14's `/gsd-capture --seed` sweep and, if surplus count ≥ 1 at port-close, into the conditional Phase 11.1 placeholder (per D-14 + D-19 terminal-subphase policy).
