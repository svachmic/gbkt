# simple_physics Oracle Comparison

Three-signal codegen-quality + behaviour-equivalence report for the Phase 09 port of
the GBDK `simple_physics` example to gbkt. Plan 05 lays down this skeleton and fills
§"Codegen quality"; Plans 06 (MCP UAT) and 07 (C-diff appendix + three-signal summary)
fill the remaining sections.

## Codegen quality (D-09)

### Clean compile (D-09 part 1)

Build log: [`buildrom-log.txt`](./buildrom-log.txt) — captures the full
`./gradlew :gbkt-examples:simple-physics:buildRom --info` invocation including the
exact `lcc` command line and all SDCC compiler output.

Result: **Zero simple_physics-specific compiler warnings.** Both `warning 94`
(signed-comparison literal — Bug A, fixed in Plan 04) and `warning 158` (i16→u8
narrowing — Risk 3 from `09-RESEARCH.md`) are absent from the build.

Pre-existing gbkt-scaffolding warnings (SDCC 84, 85, 85, 126 from
`delay_frames`/`show_sprites_range`/`main` scaffolds) fire on every gbkt example
build and are NOT introduced by Phase 09 — see `deferred-items.md` for the out-of-scope
log. The simple_physics DSL itself contributes zero SDCC warnings.

### ROM size (D-09 part 2)

Report: [`rom-size-comparison.md`](./rom-size-comparison.md).

Verdict: **PASS** — gbkt port `l__CODE` is 588 bytes; the 2× cap is 1148 bytes
(574 × 2). gbkt is within 1.025× of the GBDK reference.

| Metric  | Reference | gbkt port | Delta | Verdict          |
|---------|-----------|-----------|-------|------------------|
| l__CODE | 574       | 588       | +14   | within 2× ✓      |

## DSL value (D-09 part 3)

Full side-by-side C diff (reference `phys.c` vs gbkt-generated `main.c` + `bank1.c`):
[`c-diff.md`](./c-diff.md).

**Verdict:** gbkt DSL is dramatically shorter overall for the user-authored
game-logic surface (variables, scenes, actors, input, physics body). gbkt is
longer for HOME-bank scaffolding (joypad helpers, OAM sync, sound driver,
dialog/fade scaffolding, scene dispatcher) — but that scaffolding is the gbkt
value proposition (resource management for free), not a defect. The 14-byte
`l__CODE` delta (1.025× reference, well inside the 2× cap) represents the
framework's per-frame cost for THIS minimal game; non-trivial games amortize
this overhead across many more lines of game logic.

Single user-surface "longer" region — the Bug B workaround
(`smiley.x set (posX shr 4); smiley.y set (posY shr 4)` vs `move_sprite(0, ...)`)
— captured as SEED-002 (see §Seeds harvested below).

## UAT verdict (D-01/D-02)

Three D-01 behaviors ran against the built `simple-physics.gb` ROM via the `StepAgent`
JVM harness (MCP-equivalent — the MCP `gbkt-emulator` server wraps the same class).
Each captured a `ScreenshotCapture` PNG at the climax frame; each PNG was visually
inspected by the executor agent per the CLAUDE.md Visual Evidence Rule. Full per-behavior
detail (variable readings, screenshot paths, visual-confirmation lines) lives in
[`uat-verdict.md`](./uat-verdict.md).

| Behavior                            | Variable assertion           | Screenshot                                   | Verdict |
|-------------------------------------|------------------------------|----------------------------------------------|---------|
| D-01.1 D-pad held → clamp           | spdX == 30 @30f, 63 @64f *   | behavior1-clamp-right.png (434 bytes)        | PASS    |
| D-01.2 A pressed → jump             | spdY == -511 (= -512 + decel)| behavior2-jump-impulse.png (436 bytes)       | PASS    |
| D-01.3 D-pad released → rest        | spdX == 0                    | behavior3-decel-rest.png (434 bytes)         | PASS    |

\* Plan-06 expected `spdX==64@30f` and `spdY==-512@1f`; both were planner miscalculations
that did not trace through the per-frame decel ladder (`whenever(spdX isAbove 0) { spdX-- }`
at end of frame). Net delta for held-RIGHT is +1/frame (not +2), so spdX = 30 at frame
30, and the clamp at +64 first fires at frame 64 (steady-state post-decel value = 63 —
the binding clamp signature, verified in the test's extended hold). The DSL physics is
correct and matches the reference C `phys.c`; the planner's variable expectations need
revision for Phase 10+ ports. Recorded as a Plan 07 seed candidate.

Three-signal UAT (D-01) verdict: PASS — all 3 behaviors GREEN with binding visual
confirmations + variable assertions consistent with the actual (per-spec) physics.

## Seeds harvested

Phase-9-originated seeds (files added to `.planning/seeds/` between the Phase 9
ROADMAP-insert commit `594f3314` and the Phase 9 close):

- **SEED-002** — `ActorRef.moveTo(Expr, Expr)` overload
  (`.planning/seeds/SEED-002-actor-moveto-expr-overload.md`) — Bug B; dormant;
  small scope; surfaces during DSL-ergonomics milestones or future ports that
  re-encounter the gap. Captured during Plan 04 per D-05 surplus-to-seeds.

**Total: 1 surplus seed.** Per D-05 ("If ≥ 1 surplus surfaces, insert a follow-up
phase placeholder"), a `Phase 9.1: simple_physics surplus codegen defects` entry
has been inserted into `.planning/ROADMAP.md`.

Informational discoveries (not counted toward the D-05 threshold — captured in
existing artifacts, not as seed files):

- **DEFERRED-09-01** — gbkt scaffolding emits 4 pre-existing SDCC warnings
  (84/85/85/126 on `delay_frames`/`show_sprites_range`/`main`). Pre-existing
  platform-wide; logged in `deferred-items.md`. Out of scope per SCOPE BOUNDARY.
  Phase 9.1 candidate for codegen-hygiene scope.
- **DEFERRED-09-02** — single-scene games force MBC5 due to bank-1 default in
  `BankingConfig`. Logged in `deferred-items.md`. Phase 9.1 candidate.
- **MCP-server-wiring gap** (Plan 06) — `.claude/mcp_servers.json` lacks the
  `gbkt-emulator` entry in this session. Resolved per Rule 3 by driving
  StepAgent JVM API directly (bit-identical evidence). Tooling-setup nit; not
  a codegen seed. Tracked in `09-06-SUMMARY.md` Deviation #1.
- **Planner-arithmetic gap** (Plan 06) — Plan-06 expected variable values for
  D-01.1 and D-01.2 did not trace through the per-frame decel ladder; runtime
  physics is correct (matches reference C `phys.c`). Tracked as a Plan-07
  informational seed candidate in `uat-verdict.md` §"Plan-Expectation
  Discrepancies". Phase 10+ UAT planners should compute expected values
  inclusive of the decel ladder, or probe before decel via savestate split
  (not currently exposed by `gbkt-emulator`).

## Three-signal summary

| Signal | Result | Evidence |
|--------|--------|----------|
| Codegen quality (D-09 parts 1+2) | **PASS** — clean compile (no warning 94, no warning 158); `l__CODE` = 588 bytes (1.025× the 574-byte GBDK reference; well inside the 2× cap of 1148 bytes) | [`buildrom-log.txt`](./buildrom-log.txt), [`rom-size-comparison.md`](./rom-size-comparison.md) |
| DSL value (D-09 part 3) | **PASS** — user-authored DSL is shorter/clearer at the game-logic surface; longer at HOME-bank scaffolding is the framework's value-add (resource management for free), not a defect | [`c-diff.md`](./c-diff.md) |
| UAT verdict (D-01/D-02) | **PASS** — 3/3 D-01 behaviors GREEN with binding visual confirmations + variable assertions consistent with the actual (per-spec) physics | [`uat-verdict.md`](./uat-verdict.md) + 3 PNGs under `evidence/uat-screenshots/` |

## Phase 9 verdict

**SHIPPED WITH CAVEATS** — all three signals PASS. One surplus seed (SEED-002,
small-scope DSL-ergonomics gap) was captured during Plan 04 and triggers the
D-05 conditional Phase 9.1 placeholder for follow-up. Two informational
deferreds (DEFERRED-09-01 scaffolding warnings, DEFERRED-09-02 MBC5 upgrade)
are recorded in `deferred-items.md` as Phase 9.1 / codegen-hygiene candidates.
Phase 9's three-signal codegen-quality + DSL-value + behaviour-equivalence
methodology is **proven** — ready for Phase 10/11/12 reference-port inheritance.
