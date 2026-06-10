# Phase 10: Port metasprites GBDK example to gbkt — Context

**Gathered:** 2026-05-18
**Status:** Ready for `/gsd-plan-phase 10` — research-driven planning required (port substrate is a NEW DSL primitive, not just an idiomatic remap).

---

<domain>
## Phase Boundary

Phase 10 re-implements the GBDK `metasprites` example (`/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c`, 309 lines) as an idiomatic gbkt DSL game. Second reference port — exercises sprite composition + OAM management + sub-palette + flip surfaces. Unlike Phase 9, this port adds a NEW DSL primitive (`metasprite { }`) as its substrate; the named codegen bug-fix slot stays exploratory per Phase 9 D-04 discipline.

**In scope:**
- Lock per-example UAT contract (3 core runtime behaviors) BEFORE any DSL is written (Plan 1)
- Add new `metasprite { frame { tile(x, y, id) } }` DSL primitive + IR node + GBDK visitor + analysis (where needed). Faithful to GBDK's variable-length OAM descriptor shape. This is the **port substrate** — NOT the named codegen bug-fix slot.
- Add runtime OAM-attribute accessors on actor/metasprite ref: `actor.flipX set true/false`, `actor.flipY set true/false`, `actor.subPalette set 0..3`. Lower to OAM attribute byte writes per frame.
- Port metasprites to idiomatic gbkt DSL using the new metasprite primitive + existing scene/input/i16 surfaces. GBC-compatible single ROM (`cgb_compatibility()` equivalent + `set_sprite_palette` for 4 sub-pals). Include the 1-tile checkerboard BG fill for visual parity.
- Build the ROM via the standard gbkt pipeline (lcc, no warnings)
- Identify and fix ONE named codegen bug-fix surfaced by the port (exploratory — name the bug after the first build, per Phase 9 D-04)
- Capture three-signal comparison artifact (ROM size, generated-C diff, UAT verdict) under `evidence/`
- Lock 3 JVM-tier emission invariants matching the 3 UAT behaviors (Tier-1 codegen oracle, per Phase 9 D-11)
- Capture surplus codegen defects as seeds via `/gsd-capture --seed`
- If ≥1 surplus seed surfaces at port-close, insert a placeholder follow-up phase (Phase 10.1) in ROADMAP in the same commit that closes Phase 10
- Edit Phase 13's requirements list (`/gsd-phase --edit 13`) for any framework-shaping DSL gaps surfaced during the port (e.g., missing if/unless analogous to Phase 9.4's PHASE-13 TODOs)

**Out of scope:**
- Anti-overfitting rails 1, 2, 3 (carried forward from Phase 9 unchanged — see Decisions)
- Per-tile attribute granularity inside `metasprite { frame { } }` (defer — runtime accessors apply to whole metasprite, matching reference's runtime pattern)
- Declarative defaults for flip / sub-palette in the metasprite { } block (runtime accessors only this phase)
- Sub-pixel movement as a UAT behavior (Phase 9 already validates it; may appear as a smoke test in the port code but is NOT one of the 3 anchor behaviors)
- Variable-length OAM hiwater behavior as a UAT anchor (the OAM-tail / hiwater story is implicit in the metasprite visitor codegen; if it bites, that's a candidate named bug-fix or surplus seed — NOT a 4th UAT behavior)
- Tile-duplication-fallback path for X/Y flip (reference's `#if !HARDWARE_SPRITE_CAN_FLIP_X` macro chain) — gbkt is GBC-compatible, hardware flip works on both DMG and GBC for sprites
- DMG-only ROM (port is single GBC-compatible ROM — DMG mode runs without sub-palettes per hardware)
- Phases 11/12/13 work (this phase delivers the metasprite substrate; downstream ports use it)
- Adding 4th comparison artifact (.asm diff, bank/section size) — same Phase 9 D-09 discipline
- Pre-inserting Phase 10.1 placeholder — only conditional on ≥1 surplus seed at port-close (Phase 9 D-05)

</domain>

<decisions>
## Implementation Decisions

### Anti-overfitting doctrine (inherited from Phase 9 — overarching guardrail)

- **D-overfitting-1 (inherited):** Do not add DSL features just to make THIS port pretty. EXCEPTION this phase: the `metasprite { }` primitive itself IS the port substrate (D-02). The bar for adding it is met because the reference's descriptor shape has no honest fixed-grid analog AND the same primitive is expected to be useful for Phases 11/12. Any OTHER DSL surface surfaced during the port (e.g., declarative metasprite { flipX(...) } defaults, per-tile attribute granularity) → seed or Phase 13 edit, NOT Phase 10 expansion.
- **D-overfitting-2 (inherited):** Do not tune codegen visitors to this example's shape. If the named codegen bug-fix is a real class of bugs, fine. Cosmetic emission tuning to match reference → no.
- **D-overfitting-3 (inherited):** Do not let GBDK reference style become THE gbkt style. Reference uses macros, raw int16_t, inline tile data — those are C conventions. Use reference for codegen-quality comparison only. Skip the `#if HARDWARE_SPRITE_CAN_FLIP_*` macro fallback path entirely — gbkt is GBC-compatible, hardware flip works.

### UAT contract floor

- **D-01: Tight UAT — 3 core behaviors only.** Lock: (1) **B pressed (edge)** → metasprite animation index advances and visibly different frame renders; (2) **A pressed (edge)** → cycles through Normal / Flip-Y / Flip-XY / Flip-X visibly via OAM attribute byte writes (rot & 0x3 ladder); (3) **A pressed (after 4 flip states wrap)** → cycles through 4 sprite sub-palettes (gray / pink / cyan / green) visibly on GBC. These three exercise the NEW DSL surface (metasprite primitive + runtime OAM-attr accessors) — the codegen story this port is for. Behaviors 1 and 2 work on DMG and GBC; behavior 3 requires GBC. Sub-pixel movement is NOT a UAT anchor (Phase 9 covers that surface).
- **D-02: MCP play-through + screenshot per behavior.** Each behavior gets an MCP scripted input sequence + variable assertion + screenshot at the climax frame. Satisfies the visual-evidence rule (CLAUDE.md). For behavior 3 (sub-palette), at least one screenshot MUST be captured in GBC mode (palette change is invisible on DMG). For behaviors 1 and 2, DMG screenshots are sufficient.
- **D-03: UAT first — written and reviewed BEFORE any DSL.** Plan 1 of the phase = lock `10-UAT.md` + `PLAYBOOK.md` (input scripts + variable assertions + screenshot targets) with NO DSL yet. Mirrors Phase 9 D-03.

### Port fidelity & substrate

- **D-04: Port substrate is the new `metasprite { }` DSL primitive.** Faithful to GBDK's variable-length OAM descriptor model:
  ```
  val playerMeta by metasprite {
      frame { tile(0, 0, baseId = 0); tile(8, 0, baseId = 1); tile(0, 8, baseId = 2) }     // 3-tile frame
      frame { tile(0, 0, baseId = 3); tile(8, 0, baseId = 4); tile(0, 8, baseId = 5); tile(8, 8, baseId = 6) }  // 4-tile frame
      ...
  }
  ```
  Each frame can carry a different tile count → exercises OAM-tail / hiwater management codegen in the visitor (the same path Phase 07.3 entity-pool RAM corruption sat on). New IR node, builder, visitor, optional analysis pass. NOT counted against the "one named codegen bug-fix" cap — it's the **substrate** the port is built on.
- **D-05: Named codegen bug-fix is exploratory — whatever surfaces first.** Same Phase 9 D-04 discipline. Build the metasprite substrate, port the example, compile with lcc, run UAT. Whatever first concrete codegen defect blocks one of the 3 UAT behaviors becomes the named fix. Plausible candidates ahead of build: (a) OAM-tail hiwater off-by-one when frame N has fewer tiles than frame N-1; (b) sprite-palette emission ordering vs `cgb_compatibility()` boot; (c) flip OAM attribute byte not flushed in `update_sprites()`. Do NOT pre-commit; let the build name the bug.
- **D-06: Surplus codegen defects → seeds + conditional ROADMAP placeholder.** Same Phase 9 D-05 discipline. Each surplus defect → seed via `/gsd-capture --seed`. At port-close: if ≥1 surplus seed, insert Phase 10.1 placeholder in the same commit that closes Phase 10.

### Runtime OAM-attribute accessors (flip + sub-palette)

- **D-07: Runtime accessors on actor/metasprite ref — not declarative defaults.** API shape:
  ```
  whenever(buttons.a.pressed) {
      rot++; rot and 0xF
      // flipX/flipY ladder from (rot and 0x3); subPalette from (rot shr 2)
      actor.flipX set (...)
      actor.flipY set (...)
      actor.subPalette set (rot shr 2)
  }
  ```
  Mirrors existing `actor.x` / `actor.visible` pattern (typed assignable property refs). Codegen lowers to OAM attribute byte writes per frame in the actor's `update_sprites()` body. No declarative `metasprite { flipX(...) }` defaults this phase — runtime mutability is the whole point of the UAT behaviors.
- **D-08: subPalette range is 0..3 (GBC); DMG behavior is no-op or compile-time error.** Sub-palette is a CGB-only OAM bit; on DMG the bit is ignored by hardware. Decision deferred to research/planning — research should check whether GBDK's `set_sprite_prop` masks this gracefully or requires conditional codegen. If it requires conditional codegen, that's a candidate named bug-fix OR an explicit `#if defined(GAMEBOY)` style guard in the visitor.

### GBC compatibility + background

- **D-09: GBC-compatible single ROM via `cgb_compatibility()` equivalent + `set_sprite_palette` for 4 sub-pals.** Reference uses `cgb_compatibility()` followed by 4 `set_sprite_palette()` calls. Port must produce a single ROM that boots clean on both DMG and GBC; sub-palette switching is visible on GBC, invisible (single-shade) on DMG. Research should determine the existing gbkt surface for `cgb_compatibility()` and `set_sprite_palette` — if those have no DSL today, they may be candidate named bug-fixes OR small DSL additions justified by the same "metasprite substrate" reasoning as D-04.
- **D-10: Include the 1-tile checkerboard BG fill for visual parity.** Cheap; gives the port the same look as the reference. Uses existing background/tilemap DSL surfaces (no new substrate). BG-fill surplus defects → seeds, NOT the named codegen bug-fix slot (per D-06).

### Three-signal comparison artifact (inherited from Phase 9)

- **D-11: Three artifacts — ROM size + generated-C diff + UAT verdict.** Same Phase 9 D-09 + D-10:
  1. **ROM size:** `gbkt.gb` byte size vs `metasprites.gb` reference (target: within 2x).
  2. **Generated-C diff:** gbkt's generated `main.c` (+ metasprite-related visitor output) vs GBDK's `metasprites.c`. Shorter/clearer wins; not-shorter → seeds.
  3. **UAT verdict:** per-behavior verdict (3 GREEN MCP probes with screenshots).
  NO .asm diff (Phase 9 D-09 rejection still applies). NO bank/section size capture (metasprites fits in HOME entirely — bank artifacts wait for Phase 11 banks port).
- **D-12: Tier-1 JVM emission invariants — 3 tests matching the 3 UAT behaviors.** Same Phase 9 D-11. One JVM-tier test per behavior asserting the generated C contains the right shape: (1) animation-index advance emission (the `_meta_idx++; if (...) _meta_idx = 0;` ladder); (2) flip OAM-attribute byte write emission (the `set_sprite_prop(0, OAMF_X_FLIP | ...)` shape); (3) sub-palette OAM-attribute byte write emission (the `set_sprite_prop(0, ...PAL0|PAL1|PAL2|PAL3)` shape). Per-function awk brace-walk before grep (scope-level grep gate corollary).

### Phase 13 routing (user-added)

- **D-13: Keep Phase 10 scoped to the decisions above. Framework-shaping DSL gaps surfaced AFTER the port works → Phase 13 via `/gsd-phase --edit 13`.** Specifically: if the port surfaces a missing `if`/`unless` single-frame conditional (analogous to Phase 9.4's `PHASE-13` TODO markers in SimplePhysics.kt), a missing typed `Cartridge` enum, or a missing fixed-point primitive — those go INTO Phase 13's requirements list, NOT into Phase 10's scope. The `metasprite { }` primitive itself does NOT go to Phase 13 (it's Phase 10's substrate, not a cross-port collector item).

### Plan sizing — many small plans over few large

- **D-14: Target ≥12 plans, expect ~15–18 given the substrate scope. The planner MUST NOT compress work into fewer plans to look efficient.** Phase 10 is structurally bigger than Phase 9 (D-04 acknowledged) because the metasprite DSL substrate is a NEW IR node + builder + visitor + runtime accessors + GBC compat surface, ON TOP OF a faithful port with 3 UAT anchor behaviors and a named codegen bug-fix. Compressing this into 7–8 plans (Phase 9's count) will either (a) stall mid-plan with partial commits the executor can't atomically land, (b) silently drop scope to "fit" each plan, or (c) generate surprises that should have been their own plan. The doctrine: **err toward smaller plans**, even if it inflates the count.
  - **Plan-sizing heuristic:** ≤ ~2 distinct concerns per plan. If a plan paragraph contains "and also" twice, split it. If a plan touches >1 IR node + >1 visitor + >1 test file, split it.
  - **Substrate fans out:** the metasprite DSL substrate alone is expected to be 6–8 plans on its own — IR node, builder, validation pass (if needed), visitor-tiledata (set_sprite_data equivalent), visitor-descriptor (sprite_metasprites[] equivalent with variable-length OAM per frame), visitor-frameswitch + hiwater hide_sprites_range cleanup, runtime accessor flipX/flipY, runtime accessor subPalette. Each is a coherent atomic change with its own JVM-tier test.
  - **What this enables:** wave-based parallel execution (gsd-execute-phase runs independent plans concurrently); cleaner blast-radius per plan if executor stalls or reverts; faster code review per atomic commit; honest plan-checker validation (smaller plans are easier to verify against phase goal).
  - **What this guards against:** the failure mode where 90% of intended work ends up deferred because a single oversized plan stalled. If planner produces <12 plans, plan-checker MUST flag that as a sizing concern, not an efficiency win.

### Claude's Discretion

- **Plan count / wave structure:** Targeted ≥12 plans per D-14. Concrete rough frame (~15–18 plans expected; planner refines after research):
  1. UAT lock — write `10-UAT.md` + `PLAYBOOK.md` + asset spec (4-frame variable-tile-count layout), NO DSL yet (D-03).
  2. Metasprite IR node — `MetaspriteIR` + `MetaspriteFrame` + `MetaspriteTile` data classes in gbkt-ir; no visitor yet.
  3. Metasprite DSL builder — `metasprite { frame { tile(x,y,id) } }` in gbkt-lang; `MetaspriteDelegate` for `val foo by metasprite`.
  4. Metasprite analysis / validation pass — tile-count bounds, base-id bounds, frame consistency checks (only if research surfaces a need; otherwise fold into builder).
  5. MetaspriteVisitor — tile-data emission (sprite_tiles equivalent → `set_sprite_data` call sequence in scene enter).
  6. MetaspriteVisitor — descriptor emission (`sprite_metasprites[]` equivalent — variable-length OAM per frame).
  7. MetaspriteVisitor — frame-switch + `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` tail cleanup (the 07.3-area codegen).
  8. Runtime accessor — `actor.flipX` / `actor.flipY` assignable refs + codegen lowering to OAM attribute byte write (`set_sprite_prop` with `OAMF_X_FLIP` / `OAMF_Y_FLIP`).
  9. Runtime accessor — `actor.subPalette` assignable ref + codegen lowering to OAM attribute byte write (`OAMF_CGB_PAL0..3` mask).
  10. GBC compat surface — `cgb_compatibility()` equivalent + `set_sprite_palette` DSL surface (research first: confirm whether existing `palette { } by` + `PaletteType.SPRITE` already covers this; if so, plan shrinks to "wire it up in the port"; if not, add the missing piece).
  11. BG checkerboard fill — single-tile pattern + tilemap fill via existing tilemap DSL.
  12. Reference ROM build — `evidence/reference/BUILD.md` + Makefile invocation + gitignore reference `.gb`/`.map`/`.noi` binaries.
  13. Port assembly — `gbkt-examples/metasprites/src/main/kotlin/…/Metasprites.kt` wiring the new substrate into the play scene with the 3 UAT behaviors driving the shape.
  14. First-build + first-blocker analysis — `:gbkt-examples:metasprites:buildRom`, run UAT, name the first concrete codegen defect that blocks a UAT behavior (D-05 exploratory).
  15. Named codegen bug-fix — size depends on what surfaces; may split further if the fix has multiple sub-changes.
  16. Three-signal comparison artifact — ROM size + generated-C diff + UAT verdict in `evidence/oracle-comparison.md`; per-behavior screenshots committed.
  17. Tier-1 JVM emission invariants — 3 tests matching the 3 UAT behaviors (per-function awk brace-walk before grep, per D-12).
  18. Phase close — surplus seeds via `/gsd-capture --seed`; conditional Phase 10.1 placeholder if ≥1 surplus; Phase 13 edits via `/gsd-phase --edit 13` for any framework-shaping gaps surfaced (D-13).

  Plan-checker MUST verify ≥12 plans before approving planning; if research collapses any of the above (e.g., #4 not needed, #10 trivial), planner should SPLIT another plan rather than ship under 12.
- **Scene shape:** Single `play` scene mirroring Phase 9 D-06 (no title, no game-over). Reference is a single `while(TRUE)` loop; single scene with `enter { }` + `frame { }` is the obvious idiomatic mirror.
- **PNG asset specifics:** Faithful to reference's 4-frame sprite shape (some frames 3-tile, others 4-tile — exercises variable-length OAM). Actual frame count + tile layout is a discovery moment during the port; the asset can be re-derived from the reference's `sprite.png` (which `png2asset` processes into `sprite_metasprites[]`).
- **Cartridge type:** Inherits Phase 9.4's `CARTRIDGE_ROM_ONLY = "ROM_ONLY"` string magic for now. If port surfaces a need for MBC (it likely doesn't — metasprites fits in HOME), router to Phase 13 typed-Cartridge req (D-13).
- **Metasprite tile-load timing:** Reference does it once in `main()` after `DISPLAY_OFF`. gbkt's analog is `scene.enter { }` or game-level init — planner decides based on metasprite visitor's emission model.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Reference port source (external — THIS IS THE ORACLE)
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c` — the GBDK reference. 309 lines. Sub-pixel int16_t physics, B-press animation cycling, A-press flip+subpal cycling, single-sprite metasprite descriptor (`sprite_metasprites[]`), GBC-compatible single ROM. **Read before writing any DSL.**
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png` — the source PNG that `png2asset` processes into the metasprite descriptor.
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/Makefile` — reference build invocation; needed for reproducible reference ROM build (D-11).

### Roadmap & doctrine
- `.planning/ROADMAP.md` §"Phase 10: Port metasprites GBDK example to gbkt" — three-signal contract + hard scope cap + OAM-management rationale.
- `.planning/ROADMAP.md` §"Phase 11/12/13" — confirms reference-port track structure; Phase 11 depends on this phase's sprite/OAM primitives being validated; Phase 13 is the cross-port primitives collector (see D-13).
- `.planning/STATE.md` (head) — current pivot to reference-port track; Phase 09.4 SHIPPED.

### Phase 9 deliverables Phase 10 inherits
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-CONTEXT.md` — anti-overfitting doctrine, UAT-first sequencing, three-signal comparison artifact shape, evidence/reference/ layout, surplus-seed discipline. **Required reading for the planner — Phase 10 inherits all of this.**
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md` — UAT contract template (the shape Phase 10's `10-UAT.md` should mirror).
- `.planning/phases/09.1-simple-physics-surplus-codegen-defects-inserted/` — pattern for the conditional Phase 10.1 placeholder (D-06).
- `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt` — Phase 9.4's finished port shape; reference for the play-scene + actor + i16Var idiom.

### Verification methodology
- `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" — drives D-02 (MCP play-through + screenshots). Critical for behavior 3 (sub-palette is a pure visual change with no semantically observable variable beyond `_subpal`).
- `CLAUDE.md` §"Scope-level grep gates (corollary)" — drives D-12 (per-function awk brace-walk before grep for emission invariants).
- `context/TESTING.md` — JVM-tier test recipes, GbktTestExtension, MCP tools reference.
- `context/UAT_GUIDE.md` — MCP agent tool playbook (drives D-02 implementation).

### gbkt module surfaces this port + substrate will exercise
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` — existing actor `size`, `animationStates`, `sprite`; reference for how the new `metasprite { }` builder should slot in (and where actor.flipX / actor.subPalette assignable refs live).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt` + `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GbcColor.kt` — existing palette DSL (BG-centric by default). `PaletteType.SPRITE` exists in `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt:331` but is unexercised. Research must confirm whether sprite palettes can be declared and bound via the existing DSL or require a small DSL addition (D-09).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt` — `i16Var()` / `u8Var()` delegates (D-08 sub-pixel state vars + rot/idx u8 vars).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — single play scene shape (D-06 inherited from Phase 9).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt` — `dpad.*`, `buttons.a.pressed`, `buttons.b.pressed` (D-01 behaviors 1, 2, 3).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt` — existing composite-actor OAM management (`update_sprites`, `hide_sprites_range`). Phase 07.3 / 07.4-32 lived here. The new metasprite visitor either extends this file or adds a sibling `MetaspriteVisitor.kt`.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CLAUDE.md` — Phase 07.9 literal-emission convention; required reading before writing D-12 emission-invariant tests.
- `gbkt-backend-gbdk/CLAUDE.md` §"Literal Emission Convention" — same convention from a higher level.

### Project-level
- `CLAUDE.md` (root) — verification methodology, BANKING calling convention, debugGraphics, scope-level grep gates. Read before planning.
- `.planning/PROJECT.md` — north star (complexity ceiling: Pokémon Red / Super Mario Land / Tetris / top-down racer).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`gbkt-examples/simple-physics/`** — Phase 9.4's finished port. Same build/test pattern: `:gbkt-examples:metasprites:generateC` / `:buildRom` / `:test` / `:runEmulator`. Test files (`*IRTest`, `*EmissionTest`, `*UatTest`, `*GameTest`) mirror Phase 9's shape; Phase 10's equivalents inherit the structure verbatim.
- **`gbkt-examples/CLAUDE.md`** — "Adding a New Example" 5-step recipe. Phase 10's port subdirectory will follow it.
- **Actor composite OAM management** (`ActorVisitor.kt`) — existing `update_sprites`, `hide_sprites_range`, OAM init/move. The new metasprite visitor reuses these helpers; variable-length frame count means the visitor emits a per-frame switch with different `hide_sprites_range(hiwater, MAX)` tails.
- **`PaletteType.SPRITE` enum value** (CoreTypes.kt:331) — exists, unused. Sprite palette declaration may already be technically possible via existing palette DSL with the SPRITE type explicitly set; research should verify whether the existing `palette { } by` registration writes the right `set_sprite_palette()` call in scene enter, or whether the binding logic is BG-only today.
- **`i16Var` + `shr 4` idiom** — Phase 9.4-validated. Port reuses verbatim for the sub-pixel movement smoke test (not a UAT anchor, but the reference uses it).
- **Phase 07.9 signed-vs-unsigned literal discipline** (`gbkt-backend-gbdk/CLAUDE.md` §"Literal Emission Convention") — the OAM attribute byte flags (`OAMF_X_FLIP`, `OAMF_Y_FLIP`, `OAMF_CGB_PAL0..3`) are unsigned bitmasks. New emission code must use the unsigned-context emission rules from 07.9.

### Established Patterns
- **Anti-overfitting doctrine** (Phase 9 D-overfitting-1/2/3) — carried forward unchanged. The exception this phase makes (new metasprite primitive as substrate) is explicitly justified in D-04 and bounded by D-13 (no further DSL expansion goes to Phase 10).
- **UAT-first sequencing** (Phase 9 D-03) — Plan 1 is UAT lock, no DSL yet. Forces explicit scope cap.
- **Three-signal artifact** (Phase 9 D-09 + D-10) — ROM size + C diff + UAT verdict. Reference `.gb`/`.map`/`.noi` gitignored, reproducible from `BUILD.md`.
- **Surplus-seed + conditional placeholder** (Phase 9 D-05 → applied by Phase 09.1) — Phase 10.1 placeholder inserted only if ≥1 surplus seed at port-close.
- **Tier-1 JVM emission invariants** (Phase 9 D-11) — one per UAT behavior; per-function awk brace-walk before grep.
- **Visual-evidence rule** (CLAUDE.md) — codegen GREEN is upstream of visual; UAT screenshots are binding evidence. Especially load-bearing for behavior 3 (sub-palette is invisible to variable assertions on DMG — GBC screenshot is the only honest evidence).

### Integration Points
- **GBDK toolchain** — D-11 requires building the reference ROM via the GBDK Makefile to produce comparison artifacts. Local-only (binaries gitignored), reproducible from `BUILD.md`. Same pattern as Phase 9.
- **MCP `gbkt-emulator`** — D-02 evidence capture uses `emulator_press`, `emulator_step`, `emulator_read_variable`, `emulator_screenshot`, and `emulator_start` with a GBC-target flag for behavior 3. Research must confirm whether the existing emulator harness boots GBC mode; if not, that's a candidate seed or a small infra add.
- **`.planning/seeds/`** — D-06 surplus capture writes here via `/gsd-capture --seed`. No new tooling.
- **`/gsd-phase --edit 13`** — D-13 routing for framework-shaping DSL gaps. Existing GSD workflow, no new tooling.

</code_context>

<specifics>
## Specific Ideas

- **Port substrate is a NEW DSL primitive — defensible across future ports.** The user picked the heaviest fidelity option ((B) faithful — `metasprite { frame { tile(x,y,id) } }` DSL + visitor) and the heaviest scope-cap option (metasprite DSL is foundational, not a bug-fix). This is a deliberate exception to anti-overfitting rail 1; the user's reasoning is that the metasprite descriptor model is what makes the port HONEST (variable-length OAM per frame, exactly what Phase 07.3 / 07.4-32 sat on), and the same primitive is expected to be useful for Phase 11 (banks) and Phase 12 (platformer_template).
- **Runtime accessors over declarative defaults.** The user explicitly picked runtime `actor.flipX set true / actor.subPalette set 2` over per-tile or per-frame declarative attributes inside the `metasprite { }` block. Rationale: the UAT behaviors are inherently runtime (A pressed → cycle), so runtime mutability IS the API; declarative defaults can be added later if a future port needs them.
- **Phase 13 routing for AFTER-the-port discoveries.** The user emphasized that Phase 10 stays scoped to what was discussed in this discussion. NEW DSL surface that surfaces while running the port (e.g., a missing `if`/`unless` analogous to Phase 9.4's `PHASE-13` TODO markers) goes to Phase 13's requirements list via `/gsd-phase --edit 13`, NOT into Phase 10. This bounds Phase 10's scope structurally even though it's bigger than Phase 9.

</specifics>

<deferred>
## Deferred Ideas

- **Per-tile attribute granularity inside metasprite { frame { } }** — `tile(x, y, id, flipX = ..., flipY = ..., subPal = ...)` syntax. Considered and deferred — runtime accessors (D-07) cover the UAT behaviors; per-tile granularity would be a strictly larger DSL surface with no current port demanding it. Seed only if a future port needs different sub-pals on different tiles within one metasprite frame.
- **Declarative defaults for flip / subPal inside metasprite { } block** — `metasprite { flipX(true); subPal(2) }` syntax. Considered and deferred — runtime mutability is what the UAT behaviors test; defaults can be layered later non-breakingly.
- **Tile-duplication-fallback for X/Y flip** — Reference's `#if !HARDWARE_SPRITE_CAN_FLIP_*` macro chain (uploads pre-flipped tiles for platforms without hardware flip). Considered and rejected — gbkt's GBDK backend targets DMG/GBC/Analogue Pocket, all of which have hardware flip. Cross-platform fallback (NES, SMS, GG) is way out of scope for v1.0.
- **Variable-length OAM hiwater as a UAT anchor (4th behavior)** — Considered. Rejected as a UAT anchor; the variable-length OAM path is implicit in the metasprite visitor and will be exercised by the port even though it's not directly asserted. If hiwater bugs surface, they're either the named codegen bug-fix (D-05) or a surplus seed (D-06).
- **Sub-pixel movement as a UAT anchor (4th behavior)** — Considered. Rejected — Phase 9 already validates this surface (its UAT contract is the regression guard). The port code WILL use sub-pixel physics (reference does), but it's not an anchor behavior here.
- **DMG-only ROM** — Considered. Rejected — sub-palette behavior #3 requires GBC, and `cgb_compatibility()` cost is small. Single GBC-compatible ROM is the right shape for a faithful port.
- **GBC-only ROM (skip DMG fallback)** — Considered. Rejected — diverges from reference's dual-mode shape, and `cgb_compatibility()` is a well-trodden GBDK convention. Behaviors 1+2 work on DMG; behavior 3 requires GBC. Single ROM, both modes.
- **Pre-inserting Phase 10.1 placeholder before port surfaces surplus** — Same Phase 9 rejection (bureaucracy if no surplus surfaces). D-06 makes the placeholder conditional on ≥1 surplus seed at port-close.
- **4th comparison artifact (.asm diff or bank/section size)** — Same Phase 9 D-09 rejection. Bank artifacts wait for Phase 11.
- **Move metasprite primitive into Phase 13 (rolling collector)** — Considered. Rejected per D-13 — the metasprite primitive IS Phase 10's port substrate; Phase 13 is for cross-port collector items that no single port can fully validate. Metasprite primitive is fully validated by THIS port's UAT contract.
- **Title screen / game-flow scene** — Same Phase 9 rejection. Adds DSL surface not in reference.

</deferred>

---

*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Context gathered: 2026-05-18*
