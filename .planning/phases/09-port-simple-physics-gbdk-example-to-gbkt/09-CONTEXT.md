# Phase 9: Port simple_physics GBDK example to gbkt — Context

**Gathered:** 2026-05-13
**Status:** Ready for `/gsd-plan-phase 9` — research-driven planning required (per-port methodology being established here).
**Trigger:** STATE.md pivot (2026-05-13) — the pattern of each phase fixing one codegen bug and surfacing the next (07.3 → 07.4 → 07.9) shows the existing 9 example games are too large to function as a tight feedback loop for codegen correctness. Phase 9 starts a new reference-port validation track: re-implement 4 small GBDK SDK examples (simple_physics → metasprites → banks → platformer_template) as idiomatic gbkt DSL games, using GBDK reference C as the codegen oracle. Hard scope cap of ONE example + ONE named codegen bug-fix per phase; surplus bugs → seeds, NOT scope creep (lesson from Plan 07.9-02).

---

<domain>
## Phase Boundary

Phase 9 re-implements the GBDK `simple_physics` example (`/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c`, 99 lines) as an idiomatic gbkt DSL game. First reference port — validates actor/input/i16/shift codegen against GBDK's reference C output. Establishes the per-port methodology that Phases 10/11/12 will inherit.

**In scope:**
- Lock per-example UAT contract (3 core runtime behaviors) BEFORE any DSL is written
- Port simple_physics to idiomatic gbkt DSL — single play scene, PNG asset, raw `i16Var` + `shr 4` mirroring reference's 12.4 fixed-point
- Build the ROM via the standard gbkt pipeline (lcc, no warnings)
- Identify and fix ONE named codegen bug-fix surfaced by the port (exploratory — name the bug after the first build, not before)
- Capture three-signal comparison artifact (ROM size, generated-C diff, UAT verdict) under `evidence/`
- Lock 3 JVM-tier emission invariants matching the 3 UAT behaviors (Tier-1 codegen oracle)
- Capture surplus codegen defects as seeds via `/gsd-capture --seed`
- If ≥1 surplus seed surfaces at port-close, insert a placeholder follow-up phase (Phase 9.1) in ROADMAP

**Out of scope:**
- Adding DSL features to make THIS port pretty (anti-overfitting rail 1)
- Tuning codegen visitors to make output match reference cosmetically (anti-overfitting rail 2)
- Treating GBDK reference style as a DSL template (anti-overfitting rail 3 — reference is a codegen-quality oracle, not a style guide)
- Title-screen / game-flow surface not in reference
- Inline tile-data DSL primitive (use PNG asset pipeline)
- Pixel-and-frame trajectory parity with reference (UAT floor is tight, not exhaustive)
- Fixing MORE THAN ONE codegen bug (surplus → seeds)
- A 12.4 actor fixed-point mode (seed only — don't build in Phase 9)
- `.asm` output diff or bank/section size capture (probably overkill for an example that fits in HOME)
- Phases 10/11/12 work (this phase establishes the methodology; doesn't pre-scope downstream)

</domain>

<decisions>
## Implementation Decisions

### Anti-overfitting doctrine (overarching guardrail for the reference-port track)

- **D-overfitting-1:** Do not add DSL features just to make THIS port pretty. If the port surfaces a missing DSL primitive, the bar to add it is HIGH — must be defensible across future ports (10/11/12), not "shorter LOC for this example". Otherwise the framework grows a long tail of single-use DSL surface.
- **D-overfitting-2:** Do not tune codegen visitors to this example's shape. If the named codegen bug-fix is a real class of bugs, fine. If it's cosmetic emission tuning to make the generated C look like GBDK's reference, NO. The oracle is correctness on a representative shape, not a per-example skin.
- **D-overfitting-3:** Do not let GBDK reference style become THE gbkt style. The reference uses macros (INPUT_PROCESS/INPUT_KEY), inline tile data, raw `int16_t` — those are C conventions, not gbkt conventions. The port must read like gbkt (declarative `scene` + `actor` + `whenever`), even if it diverges from reference shape. Use reference for codegen-quality comparison only.

These three rails apply to Phases 9, 10, 11, 12. Downstream planner inherits.

### UAT contract floor

- **D-01: Tight UAT — 3 core behaviors only.** Lock: (1) D-pad held → sprite accelerates and clamps at max speed; (2) A pressed (edge) → instant Y impulse (jump); (3) D-pad released → sprite decelerates to rest. These three exercise i16 + signed comparison + signed/unsigned literal interop — the codegen surface that matters. NO pixel-perfect trajectory parity with reference.
- **D-02: MCP play-through + screenshot per behavior.** Each of the 3 behaviors gets an MCP scripted input sequence + variable assertion (`_spdx`, `_spdy`, `_posx`, `_posy`) + screenshot at the climax frame. Satisfies the visual-evidence rule (CLAUDE.md): the sprite's visual position is captured, not just the variable. Phase 07.4 plan 18 burned 5 plans on exactly the variable-only gap — do not repeat.
- **D-03: UAT first — written and reviewed BEFORE any DSL.** Plan 1 of the phase = lock `09-UAT.md` + `PLAYBOOK.md` (input scripts + variable assertions + screenshot targets) with NO DSL yet. Forces explicit scope cap; prevents UAT from rationalizing whatever the port happened to produce. Mirrors ROADMAP's "write per-example UAT first" phrasing.

### Named codegen bug-fix

- **D-04: Exploratory mode — port first, name the bug after the first build.** Reference port is small (99 lines). Port to gbkt DSL, run codegen, compile with lcc, run UAT. Whatever first concrete codegen defect blocks one of the 3 UAT behaviors becomes the named fix. Stays honest to the ROADMAP's "codegen oracle" framing — reference output drives discovery, not a pre-committed hypothesis.
- **D-05: Surplus defects → seeds + conditional ROADMAP placeholder.** Each surplus defect surfaced during the port → a seed in `.planning/seeds/` (title, repro recipe, blast-radius hint) via `/gsd-capture --seed`. At port-close: if ≥1 surplus seed exists, insert a placeholder phase ("Phase 9.1: simple_physics surplus codegen defects") in ROADMAP in the same commit that closes Phase 9. If zero surplus surfaces, no placeholder. Matches Plan 07.9-02 lesson (rejected PlatformerVisitor scope expansion) and STATE.md doctrine.

### Idiomatic mapping

- **D-06: Single `play` scene, no title.** One scene `play` with `enter { }` (init pos/spd, `showSprites`) and `frame { }` (input + physics + `actor.moveTo(...)` equivalent). NO title screen. Closest to reference shape while staying idiomatic. Validates `scene` + `frame { }` codegen with a near-empty surface.
- **D-07: PNG asset via `asset("sprites/smiley.png")`.** Mirror the asset pipeline used by every other gbkt example. The reference's inline 64-byte tile-data array is a C convention, not a gbkt one (anti-overfitting rail 3). Validates that the asset pipeline handles a minimal 4-frame 8x8 sprite. Generated C will include the equivalent tile-data array — that's the point.
- **D-08: Raw `i16Var` + manual `shr 4` mirroring reference (12.4 fixed-point).** Use `var posX by i16Var(...)`, `var spdX by i16Var(...)`, and write the `>> 4` translation explicitly when calling the actor move/position update. Validates `i16Var` compound `+=`/`-=`, signed comparison against negative literals, and `shr` codegen on a tiny surface. NO actor FP88 (8.8 format diverges from reference's 12.4 — apples-to-oranges trajectory comparison). NO new FP12.4 actor mode (anti-overfitting rail 1 — seed it if a future port also needs it). If `actor.moveTo()` doesn't accept `Expr` args, that gap is a candidate named-bug or seed.

### Three-signal comparison artifact

- **D-09: Three artifacts — ROM size + generated-C diff + UAT verdict.**
  1. **ROM size:** `gbkt.gb` byte size vs `simple_physics.gb` reference. Target: "within 2x of reference" (ROADMAP success criterion).
  2. **Generated-C diff:** gbkt's generated `main.c` vs GBDK's `phys.c` — side-by-side diff highlighting where gbkt is shorter/clearer (the "DSL value" signal). Where gbkt is NOT shorter/clearer, those become seeds and inform the per-port retrospective.
  3. **UAT verdict:** per-behavior verdict (3 GREEN MCP probes with screenshots from D-02).
  NO `.asm` output diff (fragile to SDCC drift, 4th artifact to maintain). NO bank/section size capture (simple_physics fits in HOME; bank artifacts wait for Phase 11 `banks` port).
- **D-10: Artifacts location — `.planning/phases/09-.../evidence/reference/`.** Reference C source (`phys.c`) + a small `BUILD.md` explaining how to rebuild the reference + the comparison report (`oracle-comparison.md`) get committed. The reference `.gb`, `.map`, `.noi` binaries stay local (gitignored — reproducible from `BUILD.md`). Mirrors existing `evidence/` discipline (Phase 07.4, 07.9). NO new top-level `references/` directory — don't anticipate Phase 10+ structure here.
- **D-11: Tier-1 JVM emission invariants — 3 tests matching the 3 UAT behaviors.** One JVM-tier test per behavior asserting the generated C contains the right shape: (1) signed-comparison emission for `if (_spdy < -64)` (clamping) — should land cleanly post-07.9 with `CIntLiteral(-64)` RHS; (2) edge-detect emission for `A pressed` vs `held`; (3) decel-to-zero ladder (`if (_spdy < 0) _spdy++` else-if pattern). Locks codegen contract independent of runtime. If a future refactor breaks emission, JVM test goes RED before the ROM does. NO golden-snapshot of full `main.c` (brittle). NO file-level grep counts (CLAUDE.md scope-level grep gate corollary — use awk brace-walk for per-function invariants if needed).

### Claude's Discretion

- **Plan count / wave structure:** Left to planner. Rough frame is plan 1 = UAT lock, plan 2 = DSL port, plan 3 = build + first-blocker analysis, plan 4 = named bug-fix, plan 5 = three-signal comparison + close. But the actual breakdown belongs in PLAN.md.
- **Phase 7.9 deliverable mapping:** Planner can reference 7.9's `CIntLiteral` split + signed-comparison emission discipline directly from `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention" without CONTEXT.md restating it.
- **PNG asset specifics:** 8x8 4-frame sprite mirroring the reference's 4 face variants. Whether all 4 frames are used (reference cycles them by setting tile id) or only 1 is left to the port — discovery moment during the port, not a context-locked decision.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Reference port source (external)
- `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c` — the GBDK reference being ported. 99 lines. Sub-pixel int16_t physics, D-pad accel/decel, A-button jump impulse, single sprite. THIS IS THE ORACLE — read it before writing the DSL port.
- `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/Makefile` — reference build invocation; needed for reproducible reference ROM build (D-10).

### Roadmap & doctrine
- `.planning/ROADMAP.md` §"Phase 9: Port simple_physics GBDK example to gbkt" — three-signal contract + hard scope cap.
- `.planning/ROADMAP.md` §"Phase 10/11/12" — confirms reference-port track structure and the per-port methodology being established here.
- `.planning/STATE.md` (current head) — 2026-05-13 pivot to reference-port track; Phase 07.9 SHIPPED, Phase 07.4 deferred to `--gaps-only`.

### Phase 07.9 deliverables Phase 9 rides on
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-CONTEXT.md` — context for the signed-vs-unsigned discipline.
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/` (VERIFICATION + SHIP artifacts) — D-09 three-GREEN-conditions verdict; informs Tier-1 invariant D-11.1 (signed-comparison emission should land cleanly).
- `gbkt-backend-gbdk/CLAUDE.md` §"Literal Emission Convention" — codified convention introduced by 07.9; required reading before writing D-11 emission-invariant tests.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CLAUDE.md` — package-local codification at the point of impact.

### Verification methodology
- `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" — drives D-02 (MCP play-through + screenshots). Variable assertions alone are insufficient when the truth is "sprite is at position X on screen".
- `CLAUDE.md` §"Scope-level grep gates (corollary)" — drives D-11 (per-function invariants need awk brace-walk, not file-level grep counts).
- `context/TESTING.md` — JVM-tier test recipes, `GbktTestExtension`, MCP tools reference (drives D-02 + D-11 implementation).
- `context/UAT_GUIDE.md` — MCP agent tool playbook (drives D-02 implementation).

### gbkt module surfaces the port will exercise
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:399-405` — `i16Var()` / `I16VarDelegate` (D-08).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` — actor `position`, `sprite`, `moveTo`, fixed-point modes (D-06, D-07, D-08).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — `scene { enter { } frame { } }` (D-06).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt` — `dpad.{left,right,up,down}.held`, `buttons.a.pressed` (D-01 behaviors 1, 2, 3).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **gbkt-examples/{pong, breakout, …}/** — existing example projects use the Gradle plugin directly. Phase 9 port should mirror that build shape (no duplicated build logic, plugin handles ROM build + asset pipeline). Likely target path: `gbkt-examples/simple-physics/` (slug picked at planner time).
- **`i16Var` / `u16Var` delegates** exist (`VariableBuilders.kt:395-405`) — no DSL extension needed for D-08.
- **`actor { sprite(asset(...)) }`** pattern in every existing example — no asset-pipeline gaps anticipated for D-07 (8x8 PNG).
- **`Round8CameraMonotonicityProbe`** (Phase 07.4 → 07.9) — shape template for D-02's per-behavior probes if planner picks the JVM probe approach instead of pure MCP.

### Established Patterns
- **Phase 07.9 Option C (CIntLiteral additive split)** — signed-context literals emit without `u` suffix. D-11.1 invariant should land cleanly without further codegen work (if it doesn't, that's the named bug-fix candidate D-04).
- **Visual-evidence rule** (CLAUDE.md) — drives the screenshot-at-climax-frame requirement in D-02. Codified after Phase 07.4-19/20 caught the gap.
- **Scope-level grep gates** — D-11's invariants must extract function bodies via awk brace-walk before grepping, not count tokens at file level (Plan 07.4-23 Task 1 step 3 demonstrates the pattern).
- **Per-port single-bug-fix doctrine** — STATE.md and ROADMAP both codify this from Plan 07.9-02's rejected PlatformerVisitor expansion. D-05 enforces it structurally via seeds + conditional placeholder.

### Integration Points
- **GBDK toolchain** — D-10 requires building the reference ROM via the GBDK Makefile to produce comparison artifacts. Local-only (binaries gitignored), reproducible from `BUILD.md`. No new gradle task — this is a one-off reference build per phase, not framework infrastructure.
- **MCP `gbkt-emulator`** — D-02 evidence capture uses `emulator_press`, `emulator_step`, `emulator_read_variable`, `emulator_screenshot`. All 17 tools already available; no new MCP surface.
- **`.planning/seeds/`** — D-05 surplus capture writes into the standard seeds directory via `/gsd-capture --seed`. No new tooling.

</code_context>

<specifics>
## Specific Ideas

- **Reference is the codegen-quality oracle, NOT a DSL style template.** The user emphasized this explicitly as part of the anti-overfitting doctrine. Future ports inherit.
- **Per-port methodology being established here generalizes to Phases 10/11/12.** Anti-overfitting doctrine, UAT-first sequencing, three-signal comparison artifact shape, evidence/reference/ + gitignore binaries pattern — all carry forward.
- **Surplus seeds, not surplus scope** — captured concretely in D-05. The user picked the "seeds + conditional ROADMAP placeholder" variant over the lighter "seeds-only" because they want surplus to be visible in ROADMAP, not buried.

</specifics>

<deferred>
## Deferred Ideas

- **FP12.4 actor fixed-point mode** — Reference's 12.4 format differs from gbkt's FP88. Could be added to `FixedPointMode` enum. Deferred to a future phase only if a follow-on port (10/11/12) also benefits. For Phase 9: raw `i16Var` + manual `shr 4` (D-08).
- **Inline tile-data DSL primitive** — Reference's `const uint8_t sprite_data[]` shape. Considered and rejected (anti-overfitting rail 1). Use PNG asset pipeline instead.
- **`.asm` output diff oracle** — Considered as a 4th comparison artifact. Deferred — fragile to SDCC version drift, and the 3 chosen artifacts (ROM size, C diff, UAT) already cover codegen-quality / DSL-value / UAT-contract signals.
- **Bank / section size capture from `.map` and `.noi`** — Useful when porting `banks` (Phase 11). Deferred — simple_physics fits in HOME entirely, so bank artifacts add cost without signal for this port.
- **Title screen / game-flow scene** — Adds DSL surface not in reference; muddies the codegen comparison. Deferred — not in any future GBDK reference port either.
- **Scene-less main loop primitive** — True mirror of reference shape. Would require a new DSL primitive. Anti-overfitting rail 1 says NO. Deferred indefinitely (not even a seed candidate).
- **Reuse-an-existing-sprite shortcut** — Considered ("just use Pong's paddle"). Rejected — loses "this port has its own assets" realism. Each port creates its own minimal asset set.
- **Phase 9.1 placeholder pre-insertion** — Pre-inserting a follow-up phase before the port surfaces surplus. Rejected — bureaucracy if no surplus surfaces. D-05 makes the placeholder CONDITIONAL on ≥1 surplus seed at port-close.

</deferred>

---

*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Context gathered: 2026-05-13*
