# Phase 11: Port banks GBDK example to gbkt — Context

**Gathered:** 2026-05-19
**Status:** Ready for `/gsd-plan-phase 11` — research-driven planning recommended (the named codegen bug-fix slot is exploratory per Phase 9/10 D-04/D-05; SaveDataBuilder SRAM-write path may surface unknowns).

---

<domain>
## Phase Boundary

Phase 11 re-implements the GBDK `banks` example
(`/Users/michalsvacha/gbdk/examples/cross-platform/banks/`, 5 files, ~60 LoC total) as
an idiomatic gbkt DSL game. Third reference port — exercises **ROM banking config +
cross-bank calls + BANKED calling convention** plus (newly added this phase) **SRAM
persistence via SaveDataBuilder**. Validates the CLAUDE.md memory entry
("splitByBank now auto-adds BANKED to ALL function defs in non-zero banks") against a
small known-good multi-bank baseline that Phase 9 (single-scene HOME-only) and Phase 10
(single-scene single-bank) did NOT exercise.

The GBDK reference is *explicitly about* manual banking via `BANKED` + `bo<N>` / `ba<N>`
filename hints. gbkt's North Star (PROJECT.md) and REQUIREMENTS.md (out-of-scope clause:
"Manual banking DSL syntax | Defeats the core value proposition") FORBID exposing manual
banking as user DSL. The port therefore uses the reference as a **black-box codegen-shape
oracle** for the BANKED keyword + cross-bank trampoline + MBC5 cartridge upgrade + SRAM
write path — NOT as a DSL authoring template. The substrate is multi-scene + multi-zone +
SaveDataBuilder; banking emerges automatically from `BankingAnalysisPass` (FFD) +
`allocateZoneBanks` + the existing RAM allocation path.

**In scope:**

- Lock the per-example UAT contract (4 anchor behaviors — one-time exception to Phase
  9/10's 3-behavior cap; see D-09 for justification) BEFORE any DSL is written (Plan 1).
- Port `banks` to idiomatic gbkt DSL — substrate: **3 small distinct scenes** (e.g.
  title / play / pause, modeled after dungeon's shape but smaller) + **1 banked zone**
  (tilemap data in bank 2 via existing ZoneIR + `allocateZoneBanks` path) +
  **SaveDataBuilder slot** (`save() { slots(N) }` writing into SRAM).
- Cartridge config: `config { cartridge = "MBC5"; ramBanks = 2; gbcTarget = DMG }` —
  matches reference's `0x1B` MBC5+RAM+BATT byte at ROM offset `0x0147` (or `0x19`
  MBC5-without-BATT depending on what SaveDataBuilder actually wires; planner verifies).
- Accept FFD's bank-allocation verdict (no contrived scene padding, no forced split via
  `maxBanks` / `bankFillErrorThreshold` knobs) — substrate option (i). If FFD lands all
  scenes in bank 1, that still validates the HOME→bank-1 BANKED-trampoline contract.
- Build the ROM via the standard gbkt pipeline (`:gbkt-examples:banks:buildRom`),
  zero lcc warnings, zero SDCC `unknown address/value` errors, zero MBC5 trap during
  emulator boot.
- Identify and fix ONE named codegen bug-fix surfaced by the port (exploratory per
  Phase 9 D-04 / Phase 10 D-05 — name the bug after the first build, not before).
- Capture the three-signal comparison artifact (ROM size, generated-C diff, UAT verdict)
  under `evidence/` PLUS the explicit 4th "bank-layout" signal required by ROADMAP:
  each `DEF l__CODE_<N>` byte size in the built `.noi` file is ≤ 16384 (hard ROM-bank
  capacity). The MBC5-cartridge-byte signal is one of the 4 UAT anchors (mechanism-level),
  not part of the 4th-signal artifact.
- Lock 4 JVM-tier emission invariants matching the 4 UAT anchors (Tier-1 codegen oracle,
  per Phase 9 D-11 / Phase 10 D-12 — per-function awk brace-walk before grep per CLAUDE.md
  scope-level grep gates corollary).
- Capture surplus codegen defects as seeds via `/gsd-capture --seed`.
- If ≥1 surplus seed surfaces at port-close, insert a placeholder follow-up phase
  (Phase 11.1) in ROADMAP in the same commit that closes Phase 11. Phase 11.1 (if
  created) MUST be the terminal closer for the bank-port defect cluster — no Phase
  11.1.1 / 11.2 (per user memory `feedback_many_small_plans_terminal_subphase.md`).
- Edit Phase 13's requirements list (`/gsd-phase --edit 13`) for any framework-shaping
  DSL gaps surfaced during the port (e.g., a missing typed `Cartridge` enum — Phase 13
  already lists this — or other discoveries).

**Out of scope:**

- Anti-overfitting rails 1, 2, 3 (carried forward from Phase 9 / Phase 10 unchanged —
  see Decisions D-overfitting-* below).
- Manual-banking DSL surface — no `bank(N) { ... }` / `bankedFunction(N) { ... }` /
  filename-hint-style primitive. Substrate option (D) was considered and rejected;
  REQUIREMENTS.md explicitly puts manual-banking DSL out of scope. The ONLY exception
  in this phase's substrate is the existing `ZoneIR.bankOverride` (Phase 06.7 surface,
  internal codegen control, not DSL-fluent authoring).
- SRAM-bank-assignment DSL — no `u8Var(sramBank = N)` / `sramVar(bank = N)` primitive.
  SRAM banking is validated VIA SaveDataBuilder (existing surface, no new DSL). If the
  port surfaces a SRAM-bank-DSL gap that future ports would also need, route to Phase
  13 via `/gsd-phase --edit 13` — not in Phase 11 scope (D-13).
- Forcing FFD into a deliberately multi-bank layout (substrate options (ii) and (iii)
  rejected — see D-04).
- Bank-count parity with reference (substrate option-D rejection corollary — FFD
  output is non-deterministic on small games, mapping gbkt-bank-N ↔ reference-bank-N
  is not honest).
- Per-function ratio comparison against reference (4th-signal threshold option-B
  rejected — same nondeterminism issue as bank-count parity).
- Console-mode `puts` / `printf` text output (reference uses GBDK console; gbkt uses
  window-layer text via `WindowTextCodegen` per CLAUDE.md root §"Window-Layer UI" —
  divergence is intentional, fits anti-overfitting rail 3).
- Pixel-and-frame parity with reference (reference is text-on-console + bank prints —
  doesn't translate to gbkt's window-text-layer architecture; UAT verifies BANKED
  contract, not text rendering shape).
- Phase 12 / Phase 13 work (this phase validates the BANKED contract; downstream
  ports / Phase 13 collector items consume that validation).
- Pre-inserting Phase 11.1 placeholder before port surfaces surplus seeds (Phase 9 D-05
  / Phase 10 D-06 discipline — conditional on ≥1 surplus at port-close).
- A 5th UAT anchor (4 is the explicit one-time cap; further surface goes to seeds or
  Phase 11.1 if it surfaces).

</domain>

<decisions>
## Implementation Decisions

### Anti-overfitting doctrine (inherited from Phase 9 / Phase 10 — overarching guardrail)

- **D-overfitting-1 (inherited):** Do not add DSL features just to make THIS port pretty.
  Manual-banking DSL would be an extreme violation — REQUIREMENTS.md explicitly forbids
  it. Any OTHER DSL surface surfaced during the port (e.g., SRAM-bank assignment,
  typed-Cartridge enum, Bank2-load-via-trampoline syntax) → seed or Phase 13 edit, NOT
  Phase 11 expansion.
- **D-overfitting-2 (inherited):** Do not tune codegen visitors to this example's
  shape. If the named codegen bug-fix is a real class of bugs (e.g., a BANKED auto-add
  edge case that didn't surface in Phase 9 / Phase 10), fine. Cosmetic emission tuning
  to match reference's text-output formatting → no.
- **D-overfitting-3 (inherited):** Do not let GBDK reference style become THE gbkt
  style. Reference's authoring shape (explicit BANKED, `bo<N>` filename hints,
  `puts`/`printf` console output, manual `SWITCH_RAM(N)` calls) is GBDK C convention,
  NOT gbkt convention. Use reference for codegen-quality comparison only.

### Port shape — substrate selection

- **D-01: Substrate = 3 small distinct scenes + 1 banked zone + SaveDataBuilder slot.**
  Selected over substrate-(B) single-scene-multi-zone, substrate-(C) hybrid (~equivalent
  to D-01 but more surface), and substrate-(D) explicit-`bank()`-DSL (rejected — North
  Star violation). Justification: 3 scenes naturally produce BANKED `*_enter` / `*_frame`
  / `*_exit` functions in bank ≥1 via `SceneVisitor` + `BankingAnalysisPass`; HOME→bank-N
  scene navigation already calls through GBDK's BANKED trampoline. The 1-banked-zone
  exercises Phase 06.7's `allocateZoneBanks` + Phase 07.4-22 / 07.4-30 SWITCH_ROM-from-HOME
  wrapper path. SaveDataBuilder exercises SRAM bank write/read across emulator reboot.
  All three are EXISTING surfaces in gbkt — no new DSL.
- **D-02: Accept FFD verdict — small scenes, whatever banks land.** Substrate option
  (i) chosen over (ii) deliberate content size and (iii) config-knob forcing. Even if
  FFD packs all 3 scenes into bank 1 (likely given <500 bytes of scene code per the
  dungeon example's bank1.c shape), the HOME→bank-1 BANKED trampoline is a real
  cross-bank call that validates the calling convention. If FFD splits to banks 1+2
  (less likely with small scenes), that's a bonus codegen surface but NOT a UAT
  requirement. The substrate is honest: the port doesn't engineer-to-target the
  codegen output.
- **D-03: Scene shape is title / play / pause (or similar 3-scene navigation).** Each
  scene has `enter { }` + `frame { }` + `exit { }`. Navigation transitions: title→play
  (Start), play→pause (Start), pause→play (Start). Validates the HOME-bank
  `navigate_to_scene()` trampoline against multiple targets. Exact scene names and
  contents are planner discretion (D-claude-1) — only the count (3) and the
  transition graph shape are locked here.
- **D-04: No `maxBanks` / `bankFillErrorThreshold` config-knob tuning to force a
  multi-bank split.** Substrate option (iii) rejected: AnalysisConfig isn't currently
  user-facing DSL, so exposing it as a knob would itself be a new DSL surface
  (D-overfitting-1 violation). Plus the resulting layout would be a test-only artifact,
  not a real-game shape — exactly the kind of overfitting Phase 9 D-overfitting-2
  warns against.

### Port shape — substrate additions

- **D-05: Add 1 banked zone for cross-bank tilemap-load behavior.** Adds one
  `val z by zone { tileset(asset("tiles.png")); tiles(...); collision { ... } }` to the
  play scene. `allocateZoneBanks` places its `_zone_<id>_tiles` const in bank 2
  (banks 0/1 reserved per pipeline comment). On scene enter, `set_bkg_tiles(...)` is
  emitted through the SWITCH_ROM-from-HOME wrapper (Plan 07.4-30, `bg_load_zone_tiles`).
  This is a regression check on the Phase 07.4-22 / 07.4-30 fix as much as a new
  contract — but a useful one, given that Phase 9 (no zone) and Phase 10 (no zone) did
  not exercise it. Zone tilemap content: minimal (e.g. an 8x8 checkerboard or single
  small dungeon-room tile set) — not a feature demo (per D-overfitting-1).
- **D-06: Add SaveDataBuilder slot for SRAM-persistence behavior.** Adds one
  `val save by save { slots(2); store(...) }` (or whatever the existing
  SaveDataBuilder DSL shape is) writing a single u8 value to SRAM bank N. Play scene
  has a `whenever(buttons.select.pressed) { save.write() }` or equivalent trigger.
  The UAT (D-08) verifies the SRAM byte persists across emulator soft-reset
  (`emulator_stop` + `emulator_start` reuse of the same `.sav` file). SRAM banking
  validated via existing SaveDataBuilder surface — NO new SRAM-bank-DSL. Research
  must confirm: (a) SaveDataBuilder actually wires `ENABLE_RAM` + `SWITCH_RAM(N)`
  emission today (or this is the candidate named codegen bug-fix); (b) the emulator
  preserves `.sav` across reboot in the test harness.
- **D-07: Cartridge config = `MBC5` with `ramBanks = 2`.** The reference uses
  `0x1B` (MBC5+RAM+BATT) — gbkt's existing `ConfigBuilder.cartridge: String` accepts
  the string `"MBC5"`, and `CompileRomTask.readMbcType` already auto-upgrades the
  cartridge byte based on detected bank count (Plan 09.1-05 / Phase 09 single-scene-HOME
  fast-path comment). Set `ramBanks = 2` to give SaveDataBuilder a non-zero SRAM
  allocation; planner verifies the exact ROM-byte value (`0x1B` if BATT inferred,
  else `0x19`). Magic-string `"MBC5"` is the current state — typed `Cartridge` enum
  is Phase 13 requirement item 1, NOT Phase 11 scope (Phase 11 inherits Phase 9.4 +
  Phase 10's same magic-string).

### UAT contract floor (4 anchors — one-time exception)

- **D-08: Tight UAT — 4 anchor behaviors.** Lock:
  1. **Cross-bank scene navigation (HOME→bank trampoline).** Press Start on title
     scene → play scene loads. Variable assertion: scene id transitions (read via sym
     file). Screenshot: play scene rendered. Locks the HOME→bank-N BANKED-trampoline
     contract that Phase 07.4 first surfaced.
  2. **Cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper).** On play_enter,
     `bg_load_zone_tiles` fires; tilemap visible in play scene. Screenshot: zone
     tilemap rendered (the checker / dungeon tile pattern visible). Regression check
     on Plan 07.4-22 / 07.4-30 + new contract for the banks port.
  3. **MBC5 cartridge byte 0x0147 = 0x1B (or 0x19).** ROM file byte at offset
     `0x0147` matches MBC5+RAM+BATT (or MBC5 without BATT). Variable evidence
     (ROM-byte read via emulator or file inspection) — NO screenshot, this is a
     mechanism-level signal (CLAUDE.md visual-evidence rule corollary: internal
     state truths don't need screenshots).
  4. **SRAM save persistence across emulator reboot.** Trigger save (e.g. press
     Select to write); read SRAM byte; emulator stop + restart with same `.sav`
     file; read SRAM byte again — must match. Variable evidence only — mechanism-level
     signal. Validates SaveDataBuilder + `ENABLE_RAM` + SRAM write path.
- **D-09: 4-anchor cap is a ONE-TIME EXCEPTION to Phase 9/10's 3-anchor pattern.**
  Justification: the BANKED contract has 4 distinct surfaces (ROM code-banks, ROM
  data-banks, MBC type byte, SRAM banks). Folding two into one would reduce honesty.
  Future ports (Phase 12 platformer_template) MUST justify any anchor-count
  expansion the same way — this is NOT a precedent. If Phase 11.1 surfaces, it
  inherits the 4-anchor cap, NOT a 5-anchor expansion.
- **D-10: MCP play-through + screenshot for visual anchors (1 + 2); ROM-byte +
  emulator-RAM-read for mechanism anchors (3 + 4).** Anchors 1 and 2 are visible
  truths ("scene rendered", "tilemap rendered") and MUST follow the CLAUDE.md
  visual-evidence rule — screenshots are binding. Anchors 3 and 4 are internal
  state truths (ROM byte / SRAM byte) — variable assertions are sufficient. Each
  anchor gets a JVM-tier emission invariant (D-12).
- **D-11: UAT first — `11-UAT.md` + `PLAYBOOK.md` BEFORE any DSL.** Plan 1 of the
  phase = lock UAT contract with no DSL yet. Mirrors Phase 9 D-03 / Phase 10 D-03.

### Tier-1 JVM emission invariants

- **D-12: 4 JVM-tier emission invariants — one per UAT anchor.** Each asserts the
  generated C contains the right shape (per-function awk brace-walk before grep, per
  CLAUDE.md scope-level grep gates corollary):
  1. **HOME→bank trampoline shape**: `main.c` contains `navigate_to_scene(N)` call
     pattern; `bank1.c` (or wherever play scene lands) declares `play_enter()` /
     `play_frame()` / `play_exit()` with `BANKED` keyword; `game.h` declares the
     scene's prototype with `BANKED`.
  2. **SWITCH_ROM wrapper emission**: `main.c` (HOME) contains a `bg_load_zone_<id>_tiles`
     wrapper function whose body emits `SWITCH_ROM(<N>);` + `set_bkg_tiles(...);` +
     `SWITCH_ROM(1);` (per Plan 07.4-30 shape).
  3. **CartridgeConfig MBC5 propagation**: pipeline output's `gbkt-build.properties`
     (or the AnalysisConfig threading) carries `mbc=MBC5` such that `CompileRomTask`
     emits `-Wl-yt0x1B` (or `0x19`) to lcc. JVM-tier: assert the property value, NOT
     the ROM byte (that's anchor 3's territory; this invariant locks the codegen
     surface upstream of compilation).
  4. **SRAM write path**: SaveDataBuilder DSL → IR → visitor emits `ENABLE_RAM` +
     `SWITCH_RAM(<N>)` + the write op, in HOME bank (SRAM-touching code MUST be in
     HOME per the same SWITCH_ROM-from-banked-context constraint that Phase 07.4-30
     codified for ROM banks). If SaveDataBuilder DOESN'T emit `ENABLE_RAM` /
     `SWITCH_RAM`, that's the candidate named codegen bug-fix (D-14).

### Named codegen bug-fix slot

- **D-13: Exploratory — name the bug after the first build.** Same Phase 9 D-04 /
  Phase 10 D-05 discipline. Build the port, run UAT, identify whichever first
  concrete codegen defect blocks one of the 4 UAT anchors. Plausible candidate
  classes ahead of build (do NOT pre-commit — the build names the bug):
  - **(a) SaveDataBuilder SRAM write path missing** — `ENABLE_RAM` /
    `SWITCH_RAM(N)` emission absent or in wrong bank. Anchor 4 would fail.
  - **(b) MBC5 auto-upgrade threshold off-by-one** — `CompileRomTask.readMbcType`
    or `detectMaxBank` mis-detects the multi-bank state, producing `0x00` ROM_ONLY
    byte. Anchor 3 would fail.
  - **(c) BANKED forward declarations in non-zero banks missing BANKED** — per
    CLAUDE.md memory entry; symptom: "MBC5 unknown address/value" errors at
    0xBA00+ / value 64. Anchor 1 would fail at boot.
  - **(d) Cross-bank zone-tilemap load wrapper not generated for non-RPG genres**
    — Phase 07.4-30 may have wired the wrapper conditionally on the
    sport/RPG-genre detection; banks port has neither, so the wrapper may not
    fire. Anchor 2 would fail.
  - **(e) BANKED keyword absent from `*_frame` body BUT present in `*_enter`**
    — Phase 06.7 / 07.3 area; symptom: bank1.c compiles but frame call traps at
    runtime.
- **D-14: Surplus codegen defects → seeds + conditional Phase 11.1 placeholder.**
  Same Phase 9 D-05 / Phase 10 D-06 discipline. Each surplus defect → seed via
  `/gsd-capture --seed`. At port-close: if ≥1 surplus seed exists, insert
  Phase 11.1 placeholder in the same commit that closes Phase 11. Phase 11.1 (if
  created) MUST close ALL surplus from Phase 11 — no Phase 11.1.1 / 11.2 (user
  memory `feedback_many_small_plans_terminal_subphase.md`).

### Three-signal + 4th-signal artifact

- **D-15: Three artifacts + 4th bank-layout signal.**
  1. **ROM size**: `gbkt.gb` byte size vs `banks.gb` reference (target: within 2×,
     per ROADMAP).
  2. **Generated-C diff**: gbkt's `main.c` + `bank1.c` (+ `bank2.c` if FFD splits)
     vs GBDK's `banks.c` + `bank.ba0.bo0.c` + `bank.ba0.bo2.c` + `bank.ba1.bo1.c` +
     `bank.ba1.bo3.c`. Side-by-side; gbkt's declarative shape vs reference's
     manual-banking shape. Where gbkt is NOT shorter/clearer → seed.
  3. **UAT verdict**: per-anchor verdict (4 GREEN with screenshots for anchors
     1+2, variable assertions for 3+4).
  4. **Bank-layout signal**: built `.noi` file's every `DEF l__CODE_<N>` byte size
     is ≤ 16384 (hard ROM-bank capacity). Threshold check — no per-bank ratio
     comparison against reference (4th-signal option-B rejected per D-04 corollary:
     FFD nondeterminism makes 1:1 bank mapping dishonest). Plus the implicit signal
     in UAT anchor 1: cross-bank calls resolve without "MBC5 unknown address/value"
     errors (proven by emulator boot reaching the play scene visibly).
- **D-16: Artifacts location — `.planning/phases/11-.../evidence/reference/` +
  `.../evidence/oracle-comparison.md` + `.../evidence/uat-screenshots/`.** Same
  Phase 9 D-10 / Phase 10 layout. Reference `.gb`/`.map`/`.noi` binaries stay
  local (gitignored — reproducible from `evidence/reference/BUILD.md`). MBC5
  cartridge byte 0x0147 captured as a small text artifact under `evidence/`
  (hex dump of ROM offset 0x0147 from both gbkt's and reference's `.gb`).

### Phase 13 routing

- **D-17: Keep Phase 11 scoped to D-01..D-16. Framework-shaping DSL gaps surfaced
  AFTER the port works → Phase 13 via `/gsd-phase --edit 13`.** Specifically:
  - Typed `Cartridge` enum (already a Phase 13 requirement item 1 — Phase 11
    will use `"MBC5"` magic string, NOT add the enum).
  - SRAM-bank-assignment DSL (`sramVar(bank = N)` or equivalent) — only if the
    port surfaces a real need and a future port would also need it. If
    SaveDataBuilder alone covers SRAM honestly, no Phase 13 item.
  - Any cross-port banking primitive (e.g. a declarative way to declare a "data
    pool that should live in a banked zone") — Phase 13 if and only if
    future ports also need it.

### Plan sizing — many small plans + terminal subphase policy

- **D-18: Target ≥12 plans, expect ~13–16 given scope (4 anchors + named bug-fix
  + surplus seeds + 4 JVM-tier invariants + synthetic verification).** Inherits
  Phase 10 D-14 / Phase 10.1 D-03 — RE-EMPHASIZED by user memory
  `feedback_many_small_plans_terminal_subphase.md` (2026-05-19).
  - ≤2 distinct concerns per plan; "and also" twice → split.
  - >1 IR node + >1 visitor + >1 test file → split.
  - Plan-checker MUST flag any plan count under 12 as a sizing concern, not an
    efficiency win.
  - Rough frame (planner refines after research):
    1. Lock `11-UAT.md` + `PLAYBOOK.md` (per D-11) — 4 anchors, 4 invariants,
       reference-port build recipe. NO DSL yet.
    2. Reference ROM build — `evidence/reference/BUILD.md` + reproducible
       reference `.gb`/`.map`/`.noi` invocation; gitignore reference binaries.
    3. Scene substrate — 3 scenes (title / play / pause) with transitions; no
       zone, no save, just scene navigation.
    4. First-build #1 — `:gbkt-examples:banks:buildRom` from substrate plan 3;
       capture which banks FFD landed scenes in. Names the bug, if any.
    5. Anchor 1 evidence — MCP play-through HOME→bank scene nav + screenshot
       capture + variable assertion (scene id transitions).
    6. JVM-tier invariant 1 — BANKED keyword + trampoline shape lock.
    7. Zone addition — 1 banked zone in play scene; check `allocateZoneBanks`
       places it in bank ≥2; check SWITCH_ROM wrapper emits.
    8. Anchor 2 evidence — MCP play-through tilemap load + screenshot.
    9. JVM-tier invariant 2 — `SWITCH_ROM(<N>)` + `set_bkg_tiles` + `SWITCH_ROM(1)`
       wrapper-shape lock.
    10. Cartridge + MBC5 wiring — `config { cartridge = "MBC5"; ramBanks = 2 }`;
        verify `gbkt-build.properties` + ROM byte 0x0147.
    11. Anchor 3 evidence — ROM-byte hex dump from both gbkt and reference;
        comparison artifact.
    12. JVM-tier invariant 3 — `gbkt-build.properties` MBC5 propagation lock.
    13. SaveDataBuilder integration — save slot in play scene; SRAM write
        trigger.
    14. First-build #2 (post-save) — re-build; if SRAM emission missing,
        this is the candidate named codegen bug-fix (D-13 candidate (a)).
    15. Named codegen bug-fix — size depends on what surfaces; split further
        if the fix has multiple sub-changes.
    16. Anchor 4 evidence — SRAM persistence across emulator reboot; variable
        assertion.
    17. JVM-tier invariant 4 — `ENABLE_RAM` + `SWITCH_RAM(N)` emission shape
        lock.
    18. 4th-signal artifact — `.noi` parse + `DEF l__CODE_<N>` ≤ 16384 assertion;
        captured in `evidence/oracle-comparison.md`.
    19. Phase close — surplus seeds via `/gsd-capture --seed`; conditional Phase
        11.1 placeholder if ≥1 surplus; Phase 13 edits if framework-shaping gaps
        surfaced.
  - Plan-checker MUST verify ≥12 plans before approving planning; if research
    collapses any of the above (e.g. #2 reference build is trivial, #14
    first-build #2 produces no bug → fold), planner should SPLIT another plan
    rather than ship under 12.
- **D-19: Phase 11.1 (if it surfaces) MUST be terminal. No Phase 11.1.1 / 11.2.**
  User memory `feedback_many_small_plans_terminal_subphase.md` explicit
  constraint. The planner sizes Phase 11.1's plans small enough that any
  in-execution surplus discovery is ABSORBED (split a plan, insert a plan in
  a wave), NOT escalated to a new follow-up subphase.

### ROM-build smoke test (memory rule)

- **D-20: Verifier MUST run a clean `:gbkt-examples:banks:buildRom` AND the
  reference `make` (or its `evidence/reference/BUILD.md`-documented equivalent)
  before declaring the phase complete.** JVM tests cannot see staleness in
  `build/gbkt/generated/`. Per the user memory
  `feedback_rom_build_smoke_test_for_codegen_phases.md` — codegen phases that
  touch `GBDKPipelineV2` / `BankingAnalysisPass` / `GenerateCTask` / visitor
  surface MUST include this gate.

### Claude's Discretion

- **Plan count / wave structure (D-claude-1):** Targeted ≥12 plans per D-18.
  Concrete rough frame above is a starting point — planner refines after
  research, but the count floor is 12 with sizing rules in D-18.
- **Scene names / contents (D-claude-2):** `title` / `play` / `pause` are
  suggestions — planner picks names + minimum-viable contents that produce
  navigation transitions without contrived padding (per D-02). Title scene may
  be the smallest scene with just `whenever(buttons.start.pressed) { navigate(play) }`;
  pause scene similar.
- **Zone contents (D-claude-3):** 1 small tileset (e.g. 4-tile checker, single
  8×8 PNG asset) — planner picks the asset shape. Must be non-trivial enough that
  `set_bkg_tiles` is actually called (i.e. tiles ≠ all-clear).
- **SaveDataBuilder DSL shape (D-claude-4):** Use whatever existing
  SaveDataBuilder + slot wiring is current per `gbkt-engine` /
  `SystemBuilders.kt:SaveDataBuilder`. If the existing DSL is missing the
  write-trigger surface (`save.write()` or equivalent), planner decides whether
  the gap is the named codegen bug-fix (D-13) or a Phase 13 routing concern.
- **Cartridge magic-string vs typed enum (D-claude-5):** Use `cartridge = "MBC5"`
  (magic string) — typed `Cartridge` enum is a Phase 13 requirement, not Phase 11
  scope (D-17).
- **MCP emulator soft-reboot recipe (D-claude-6):** Anchor 4 needs an emulator
  reboot recipe. Planner verifies whether `emulator_stop` + `emulator_start`
  preserves `.sav` content in the test harness today; if not, this is a candidate
  for the named codegen bug-fix slot's MCP-test-infra cousin (escalate via
  research before committing).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Reference port source (external — THIS IS THE ORACLE)

- `/Users/michalsvacha/gbdk/examples/cross-platform/banks/src/banks.c` (~56 lines)
  — the GBDK reference being ported. Multi-bank ROM functions (`bank_1`/`bank_2`/
  `bank_3` BANKED, `bank_fixed` NONBANKED), SRAM banking with `ENABLE_RAM` +
  `SWITCH_RAM(N)`, `printf` console output. **Read before writing any DSL.**
- `/Users/michalsvacha/gbdk/examples/cross-platform/banks/src/bank.ba0.bo0.c`,
  `bank.ba0.bo2.c`, `bank.ba1.bo1.c`, `bank.ba1.bo3.c` — sibling source files
  illustrating the GBDK filename-suffix convention for explicit bank assignment.
  Use as codegen-shape reference, NOT as DSL authoring template.
- `/Users/michalsvacha/gbdk/examples/cross-platform/banks/Makefile` — reference
  build invocation with `-Wl-yt0x1B` (MBC5+RAM+BATT) + `-autobank` + `-Wb-ext=.rel`
  + per-source `BAFLAG`/`BOFLAG` extraction. Needed for reproducible reference ROM
  build (D-16) and to derive the `0x1B` MBC5+RAM+BATT cartridge byte expectation
  (UAT anchor 3).

### Roadmap & doctrine

- `.planning/ROADMAP.md` §"Phase 11: Port banks GBDK example to gbkt"
  (line 1327-1339) — three-signal contract + additional bank-layout signal + hard
  scope cap (ONE example, ONE named codegen bug-fix). Surplus → seeds.
- `.planning/ROADMAP.md` §"Phase 12 / Phase 13" (line 1341-1385) — downstream
  ports that depend on Phase 11's BANKED-contract validation. Phase 13 requirement
  item 1 (typed Cartridge enum) is the canonical Phase 13 route for the magic-string
  gap surfaced here.
- `.planning/REQUIREMENTS.md` §"Out of Scope" (line 137-150) — "Manual banking
  DSL syntax | Defeats the core value proposition". Locks D-01 substrate option
  (D) rejection structurally.
- `.planning/PROJECT.md` §"North Star" + §"Core Principles" — declarative over
  imperative; the user never writes manual banking. Frames the substrate selection
  as a hard constraint, not a stylistic preference.
- `.planning/STATE.md` (head, line 1-46) — Phase 10.2 EXECUTING (paused Wave 2);
  Phase 10 + 10.1 SHIPPED. Phase 11 begins parallel to 10.2 since 10.2 is
  orthogonal (GBC palette write path, not banking).

### Phase 9 / 10 / 10.1 deliverables Phase 11 inherits

- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-CONTEXT.md`
  — anti-overfitting doctrine D-overfitting-1/2/3 (carried forward unchanged),
  UAT-first sequencing (D-11 mirror), three-signal artifact + evidence/reference/
  layout, surplus-seed + conditional placeholder discipline. **Required reading
  for the planner.**
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-CONTEXT.md`
  — plan-sizing D-14 (≥12 plans, ≤2 concerns per plan, planner must err small);
  Tier-1 JVM emission invariants D-12; per-function awk brace-walk before grep.
  **Required reading for the planner.**
- `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-CONTEXT.md`
  — terminal-subphase policy D-03b (Phase 11.1, if it surfaces, MUST be terminal);
  file-affinity grouping D-02; SEED-009 metasprites-header-in-bank1 fix (already
  shipped — Phase 11 inherits the bank1.c includes path). **Required reading for
  Phase 11.1 planning, IF Phase 11.1 surfaces.**
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/` — the Phase that
  originally surfaced bank-overflow + BANKED-trampoline + SWITCH_ROM-from-banked-context
  problems. Plan 07.4-22 (cross-bank set_bkg_tiles guard) and Plan 07.4-30
  (HOME-bank SWITCH_ROM wrapper) are the existing codegen surface Phase 11
  exercises via UAT anchor 2.
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-CONTEXT.md`
  — literal-emission convention (relevant if the named codegen bug-fix or any of
  the 4 invariants involves cartridge-byte / bank-number literal emission).

### Verification methodology

- `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" — drives D-10
  (anchors 1 + 2 visual → screenshots; anchors 3 + 4 mechanism → variable
  assertions only).
- `CLAUDE.md` §"Scope-level grep gates (corollary)" — drives D-12 (per-function
  awk brace-walk before grep for emission invariants).
- `CLAUDE.md` §"GBDK Setup & ROM Building" + §"Common Errors" — drives D-20
  (ROM-build smoke test gate); "MBC5 unknown address/value" is the explicit
  failure mode UAT anchor 1 is the regression guard for.
- `CLAUDE.md` §"Banking Defaults" — `BankingConfig` defaults; Phase 11 uses the
  defaults for substrate option (i) + accepts FFD verdict.
- `context/TESTING.md` — JVM-tier test recipes, GbktTestExtension API, MCP tools
  reference (drives anchor 1 + 2 implementation).
- `context/UAT_GUIDE.md` — MCP agent tool playbook (drives anchor 1 + 2 + 4
  scripted-input implementation).
- User memory `feedback_rom_build_smoke_test_for_codegen_phases.md` — codegen
  phases touching GBDKPipelineV2 / BankingAnalysisPass / GenerateCTask MUST
  include a clean buildRom gate in verification (D-20).
- User memory `feedback_many_small_plans_terminal_subphase.md` — ≥12 plans
  doctrine + terminal-subphase rule (D-18, D-19).
- User memory `feedback_visual_evidence_for_visual_truths.md` — visual SCs need
  screenshots; codegen GREEN is necessary but never sufficient (drives D-10).
- User memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` —
  if the named codegen bug-fix has blast-radius across multiple visitors /
  IR nodes, route to a proper new phase rather than driving inline.

### gbkt module surfaces this port will exercise

- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt`
  — FFD bin-packing for scene code; HOME-bank scene budget; single-scene fast-path
  (Plan 09.1-04). Substrate option (i) accepts whatever this pass produces.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  — `buildSceneFile`, `buildHeaderFile`, `allocateZoneBanks`, `buildTilemapBankFiles`,
  the `allScenesInHome` fold (line 522+), and the Plan 07.4-30 `bg_load_zone_tiles`
  SWITCH_ROM wrapper (line 1882+). All four UAT anchors run through this file.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt`
  — `isBanked: Boolean` typed field driving the BANKED keyword emission (line 36+).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt`
  line 192 — the `if (fn.isBanked) " BANKED"` emission site. Anchor 1's JVM-tier
  invariant locks this surface.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt`
  — scene-level `isBanked` determination (line 77 referenced in GBDKPipelineV2:519
  comment: `val sceneBanked = sceneBank == null || sceneBank > 0`). The HOME-bank
  fast-path companion lives here.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt` line 525+
  — `ConfigBuilder` with `cartridge: String`, `romBanks: Int`, `ramBanks: Int`,
  `gbcTarget: GbcTarget`. Phase 11 sets `cartridge = "MBC5"`, `ramBanks = 2`.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt`
  `SaveDataBuilder` (search within file; the exact location TBD by planner) — the
  existing SRAM-write surface. UAT anchor 4 + JVM-tier invariant 4 are driven by
  whatever this builder emits today; the gap (if any) is candidate (a) for the
  named codegen bug-fix (D-13).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` —
  `ZoneBuilder` (tileset, tiles, collision, encounters); Phase 11 substrate adds
  1 zone via this builder.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — 3-scene
  navigation graph via existing `scene { enter { } frame { } exit { } }` shape.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt` —
  `buttons.start.pressed` (anchor 1 trigger), `buttons.select.pressed` (anchor 4
  save trigger).
- `gbkt-gradle-plugin/` (per CLAUDE.md root §"Build Commands") — `:buildRom` task
  + `CompileRomTask.readMbcType` MBC auto-upgrade logic. UAT anchor 3 + JVM-tier
  invariant 3 verify this surface end-to-end.

### Example references in gbkt-examples

- `gbkt-examples/dungeon/build/gbkt/generated/bank1.c` — existing multi-bank
  output reference; 9 `BANKED` keywords in bank1.c, 0 in main.c, plus
  `zone_bank2.c` for tilemap data. Phase 11's expected shape is similar (smaller
  scope, but same banking architecture).
- `gbkt-examples/dungeon/build/gbkt/output/dungeon.noi` — existing `.noi` file
  showing `DEF l__CODE_0`, `DEF l__CODE_1`, `DEF l__CODE_2`. Phase 11's 4th-signal
  artifact reads the equivalent file for the banks port.
- `gbkt-examples/simple-physics/` — Phase 9.4's finished port shape; reference
  for the play-scene + actor + i16Var idiom (general gbkt port-skeleton template).
- `gbkt-examples/CLAUDE.md` — "Adding a New Example" 5-step recipe; Phase 11's
  port subdirectory `gbkt-examples/banks/` follows it.

### Project-level

- `CLAUDE.md` (root) — verification methodology, BANKED calling convention,
  banking defaults, scope-level grep gates. Read before planning.
- `.planning/PROJECT.md` — north star (complexity ceiling); declarative-over-
  imperative + no-manual-banking are the hard constraints framing D-01.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`gbkt-examples/dungeon/`** — closest existing multi-bank gbkt example (3 banks:
  HOME + bank 1 scene + bank 2 zone tilemap). 9 BANKED keywords already emitted
  cleanly. Phase 11 port (`gbkt-examples/banks/`) builds on the same codegen
  architecture — smaller scope (no RPG / no monsters / no inventory), but same
  bank shape.
- **`gbkt-examples/simple-physics/`** — Phase 9.4's finished port shape; same
  build/test pattern (`:gbkt-examples:banks:generateC` / `:buildRom` / `:test` /
  `:runEmulator`). Test files (`*IRTest`, `*EmissionTest`, `*UatTest`, `*GameTest`)
  inherit the structure verbatim.
- **`gbkt-examples/CLAUDE.md`** — "Adding a New Example" 5-step recipe.
  `gbkt-examples/banks/` follows it (NEW subdirectory).
- **`BankingAnalysisPass`** (`gbkt-analysis/.../BankingAnalysisPass.kt`) — FFD
  bin-packing already battle-tested through Phase 9 / 9.1 / 9.2 / 10 / 10.1.
  Substrate (i) accepts whatever this pass produces; the JVM-tier invariant 1
  locks `isBanked = true` for non-HOME scenes.
- **`allocateZoneBanks`** (`GBDKPipelineV2.kt:573+`) — zone tilemap auto-allocation
  to banks 2+; bank 0 / 1 reserved. Phase 11's 1-banked-zone hits this directly.
- **Plan 07.4-30 `bg_load_zone_<id>_tiles` HOME-bank SWITCH_ROM wrapper**
  (`GBDKPipelineV2.kt:1882+`) — the existing SWITCH_ROM-from-HOME path that UAT
  anchor 2 + JVM-tier invariant 2 verify. Regression check is the principal
  contribution of those two anchors.
- **`SaveDataBuilder`** (`gbkt-lang/.../SystemBuilders.kt`) — existing SRAM-write
  surface; Phase 11 uses verbatim (no DSL additions). The exact wiring shape +
  current SRAM emission (if any) is research territory (D-claude-4).
- **`CFunction.isBanked` + `CEmitter` BANKED emission**
  (`gbkt-backend-gbdk/.../CFunction.kt:36`, `CEmitter.kt:192`) — the single
  emission point for the BANKED keyword. JVM-tier invariant 1's brace-walked
  grep targets the lines that come out of these.
- **`ConfigBuilder`** (`gbkt-lang/.../SystemBuilders.kt:524+`) — `cartridge`,
  `romBanks`, `ramBanks` fields. Phase 11 sets `cartridge = "MBC5"; ramBanks = 2`
  with no DSL additions.

### Established Patterns

- **Anti-overfitting D-overfitting-1/2/3** (inherited from Phase 9 / Phase 10) —
  no DSL features added just to fix this port; reference is a codegen-quality
  oracle, NOT a DSL style guide. Manual-banking DSL is the extreme violation
  that REQUIREMENTS.md explicitly forbids.
- **UAT-first sequencing** (Phase 9 D-03 / Phase 10 D-03) — Plan 1 is UAT lock,
  no DSL yet (D-11).
- **Three-signal artifact + 4th bank-layout signal** (Phase 9 D-09 + D-10 / Phase
  10 D-11) — ROM size + C diff + UAT verdict + (this phase) per-bank ≤ 16KB
  threshold from `.noi`.
- **Surplus-seed + conditional placeholder** (Phase 9 D-05 / Phase 10 D-06) —
  Phase 11.1 placeholder inserted only if ≥1 surplus seed at port-close. Phase
  11.1 (if created) is TERMINAL — no 11.1.1 / 11.2 (D-19).
- **Tier-1 JVM emission invariants** (Phase 9 D-11 / Phase 10 D-12) — one per
  UAT anchor; per-function awk brace-walk before grep.
- **Visual-evidence rule** (CLAUDE.md) — codegen GREEN is upstream of visual;
  UAT screenshots are binding evidence for visual anchors (1 + 2). Variable
  assertions are sufficient for mechanism-level anchors (3 + 4).
- **ROM-build smoke test in verifier** (user memory rule, D-20) — verifier MUST
  run `:gbkt-examples:banks:buildRom` cleanly before flipping the phase verdict
  to passed.

### Integration Points

- **GBDK toolchain** — D-16 requires building the reference ROM via the GBDK
  Makefile to produce comparison artifacts (`banks.gb`, `banks.map`, `banks.noi`).
  Local-only (binaries gitignored), reproducible from `evidence/reference/BUILD.md`.
  Same pattern as Phase 9 / Phase 10.
- **MCP `gbkt-emulator`** — anchor 1 + 2 evidence capture uses `emulator_press`,
  `emulator_step`, `emulator_read_variable`, `emulator_screenshot`. Anchor 4 needs
  `emulator_stop` + `emulator_start` with `.sav` preservation across reboot —
  research must confirm this is supported in the current harness (D-claude-6).
  All 17 base tools already available + Phase 10.2's `emulator_read_memory` +
  `emulator_write_memory` extensions (relevant if anchor 4's SRAM-byte read needs
  raw-memory access vs sym-file-resolved variable read).
- **`.planning/seeds/`** — D-14 surplus capture writes here via
  `/gsd-capture --seed`. No new tooling.
- **`/gsd-phase --edit 13`** — D-17 routing for framework-shaping DSL gaps.
  Existing GSD workflow, no new tooling.
- **`gbkt-build.properties`** — pipeline-emitted properties file consumed by
  `CompileRomTask`. JVM-tier invariant 3 reads this file's `mbc=MBC5` line; UAT
  anchor 3 reads the resulting ROM byte 0x0147 from the built `.gb`.

</code_context>

<specifics>
## Specific Ideas

- **Reference is a black-box codegen-shape oracle, NOT a DSL authoring template.**
  The user emphasized this distinction explicitly as part of the substrate-(D)
  rejection. Future ports inherit. The reference's `BANKED` keyword + filename
  suffixes + `SWITCH_RAM(N)` calls are GBDK C convention; gbkt's substrate is
  "small multi-scene game + 1 zone + 1 save slot" which produces the same codegen
  surface declaratively.
- **4-anchor cap is a one-time exception.** The user explicitly accepted "4
  anchors this phase" over "drop one to keep the 3-cap" because the BANKED
  contract has 4 distinct surfaces (ROM code-banks, ROM data-banks, MBC type,
  SRAM banks). Future ports MUST justify any anchor-count expansion the same way
  (D-09). Phase 12 (platformer_template) is NOT pre-licensed to ≥4 anchors.
- **SRAM banking validated via existing SaveDataBuilder — no new SRAM-bank-DSL.**
  The user chose option (B) "validate via SaveDataBuilder" over option (C) "add
  minimal SRAM bank DSL this phase". Reasoning (implicit): existing surface
  validates the codegen path without expanding DSL; if SaveDataBuilder doesn't
  cleanly support SRAM today, that's the candidate named codegen bug-fix slot
  (D-13 candidate (a)).
- **Bank-layout 4th signal is a hard threshold (≤16384 bytes per banked CODE
  section), not a per-bank ratio comparison.** The user explicitly picked the
  "threshold check" option over the "relative ratio" / "presence-only" /
  "bank-count parity" options. Rationale: FFD nondeterminism makes 1:1 bank
  mapping dishonest; the threshold is hardest-to-game (gbkt cannot accidentally
  emit a banked section >16KB because the linker would fail anyway, but
  asserting the bound makes the codegen contract explicit).

</specifics>

<deferred>
## Deferred Ideas

- **Manual-banking DSL (`bank(N) { ... }` / `bankedFunction(N) { ... }`)** —
  Considered and REJECTED. REQUIREMENTS.md explicitly forbids manual-banking DSL
  ("Defeats the core value proposition"). Not even a seed candidate — this is a
  structural North Star constraint, not a deferred feature.
- **SRAM-bank-assignment DSL (`sramVar(bank = N)` / `u8Var(sramBank = N)`)** —
  Considered and deferred. SRAM banking is validated via SaveDataBuilder in
  Phase 11 (D-06). If a future port surfaces a real need (and SaveDataBuilder
  doesn't already cover it), route to Phase 13 via `/gsd-phase --edit 13`. Not
  a Phase 11 candidate.
- **Typed `Cartridge` enum (already a Phase 13 requirement item 1)** — Phase 11
  uses `cartridge = "MBC5"` magic string. Replacing the string with the typed
  enum is Phase 13 territory; Phase 11 doesn't pull it forward.
- **Bank-count parity with reference (substrate / 4th-signal option D-9-corollary)**
  — Considered (asserting gbkt produces the same number of `l__CODE_N` sections
  as the reference). Rejected: reference's bank assignment is hand-driven via
  `bo<N>` filename hints; gbkt's is FFD-driven. 1:1 mapping is dishonest, and
  forcing a target count requires substrate engineering (option (ii) or (iii))
  that contradicts substrate (i)'s "accept FFD verdict" choice.
- **Per-bank ratio comparison against reference (4th-signal option B)** —
  Considered. Rejected per the same FFD-nondeterminism reasoning as bank-count
  parity. ROM size signal already gives a global ratio; per-bank ratio adds
  cost without signal.
- **Forcing FFD to produce a 2+ code-bank split via config knobs
  (substrate option iii — maxBanks / bankFillErrorThreshold)** — Considered.
  Rejected: AnalysisConfig isn't user-facing DSL, exposing it as a knob would
  itself be a new DSL surface (D-overfitting-1 violation). Plus the resulting
  layout would be a test-only artifact.
- **Forcing 2-bank split via padded scene content (substrate option ii)** —
  Considered. Rejected: contrived scene authoring exactly contradicts Phase 9 D-overfitting-2.
- **Console-mode `puts` / `printf` text output (faithful to reference)** —
  Considered. Rejected: gbkt uses window-layer text rendering (per CLAUDE.md
  §"Window-Layer UI"); reproducing GBDK's console mode would either require a
  new DSL surface OR break the window-text-layer convention. Both bad.
- **Pixel-and-frame parity with reference** — Same Phase 9 / Phase 10 rejection.
  UAT verifies BANKED contract, not text rendering shape.
- **Pre-inserting Phase 11.1 placeholder before port surfaces surplus seeds** —
  Same Phase 9 / Phase 10 rejection (bureaucracy if no surplus surfaces). D-14
  makes the placeholder conditional on ≥1 surplus seed at port-close.
- **5th UAT anchor (e.g. Phase 13 typed-Cartridge enum demo, or a 4th-bank-tilemap
  anchor)** — Considered. Rejected per D-09: 4 anchors is a one-time exception,
  not a stepping stone to 5.
- **Adding a 2nd banked zone to exercise inter-zone bank locality** —
  Considered. Deferred to Phase 12 (platformer_template) if relevant, or future
  zone-banking-specific phase. Phase 11's 1-zone substrate already validates the
  cross-bank tilemap-load path; 2 zones is incremental, not new contract.

</deferred>

---

*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Context gathered: 2026-05-19*
