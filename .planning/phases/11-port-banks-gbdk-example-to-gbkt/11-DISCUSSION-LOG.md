# Phase 11: Port banks GBDK example to gbkt - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `11-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 11-port-banks-gbdk-example-to-gbkt
**Areas discussed:** Port substrate idiom, UAT 3 anchor behaviors, SRAM banking in/out of scope, 4th signal bank-layout assertion shape

---

## Top-level area selection

| Option | Description | Selected |
|--------|-------------|----------|
| Port substrate idiom (A/B/C/D) | How gbkt port produces multi-bank codegen without exposing manual-banking DSL | ✓ |
| UAT — 3 anchor behaviors | Tight 3-behavior UAT floor candidates | ✓ |
| SRAM (external RAM) banking — in/out of scope | Reference uses SRAM banks; gbkt has no SRAM-bank DSL | ✓ |
| 4th signal — bank-layout assertion shape | ROADMAP adds bank-layout signal beyond Phase 9/10's three | ✓ |

**User's choice:** All four areas selected.
**Notes:** The four areas are interdependent — substrate decision frames UAT anchors, which frame SRAM scope, which frames the 4th-signal threshold.

---

## Area 1 — Port substrate idiom

| Option | Description | Selected |
|--------|-------------|----------|
| (A) Multi-scene game | ~3 scenes, FFD spreads them across bank 1+2; closest to gbkt's value prop; no new DSL surface | ✓ |
| (B) Single scene + multiple banked zones | One scene; multiple ZoneIRs auto-allocated across banks 2, 3; reuses Phase 06.7 bankOverride | |
| (C) Hybrid multi-scene + multi-zone | Title + play scenes (banks 1+2) AND multiple zones (banks 2/3); closer to real game's banking profile | |
| (D) Explicit `bank()` DSL | New `bank(2) { fun foo() { ... } }` primitive; mirrors reference's bo<N> filename hints; STRONGLY violates anti-overfitting rail 1 + REQUIREMENTS.md "Manual banking DSL syntax defeats the core value proposition" out-of-scope clause | |

**User's choice:** (A) Multi-scene game — 3 small distinct scenes, FFD spreads them across bank 1+ (whatever banks FFD chooses).
**Notes:** Closest analog to Phase 9/10 doctrine (port-as-codegen-exerciser, not feature-demo). No new DSL surface. Reference becomes a black-box codegen-shape oracle for BANKED keyword + cross-bank trampolines + multi-bank `.noi`, NOT a DSL authoring template.

### Area 1.1 — Multi-bank shape (how to force vs accept FFD)

| Option | Description | Selected |
|--------|-------------|----------|
| (i) Accept FFD verdict — small scenes, whatever banks land | Closest to Phase 9/10 idiom; don't pre-engineer the codegen output. Even HOME+bank-1 validates BANKED+trampoline | ✓ |
| (ii) Force 2-bank code split via padded scene content | Pad scenes with long whenever chains; total code exceeds bank-1 capacity at threshold | |
| (iii) Force split via config knob — maxBanks or fill threshold | AnalysisConfig.maxBanks low + bankFillErrorThreshold low; might need new DSL surface | |

**User's choice:** (i) Accept FFD verdict.
**Notes:** Even if FFD packs all 3 scenes into bank 1, HOME→bank-1 BANKED trampoline is still a real cross-bank call validating the calling convention. If FFD splits to banks 1+2, that's a bonus codegen surface but NOT a UAT requirement. The port doesn't engineer-to-target the codegen output.

---

## Area 2 — UAT anchor behaviors

### Initial pass — what to anchor on

| Option | Description | Selected |
|--------|-------------|----------|
| Cross-bank scene navigation (HOME→bank trampoline) | Press Start on title → play scene; BANKED play_enter() runs without 'MBC5 unknown address/value' crash | ✓ |
| Cross-bank zone tilemap load (SWITCH_ROM emission) | Scene in bank 1 loads tilemap from zone in bank 2 via set_bkg_tiles; exercises Plan 07.4-22/07.4-30 | (eventually included via substrate expansion) |
| MBC5 cartridge header byte 0x0147 = 0x1B/0x19 auto-set | ROM byte 0x0147 matches MBC5+RAM+BATT or MBC5 | (eventually included) |
| Multi-bank .noi signal — ≥2 banked CODE sections | Read built .noi file: assert ≥2 DEF l__CODE_N banked sections beyond HOME; RISK: if FFD packs all into bank 1, this FAILS | (moved to 4th-signal artifact) |

**User's choice (first pass):** Cross-bank scene navigation only — implied the others are misaimed for substrate (A)+(i).
**Notes:** With substrate (A)+(i), all 3 scenes probably end up in bank 1 → cross-bank scene nav is the only inherently *visible* behavior. The "multi-bank .noi signal" anchor was rejected because it would FAIL under substrate (i)'s "accept FFD verdict" choice if FFD packs everything in bank 1 — better as the 4th-signal artifact than as a UAT anchor.

### Area 2.1 — Floor shape (1 anchor vs expand substrate)

| Option | Description | Selected |
|--------|-------------|----------|
| 1 UAT anchor + 2 mechanism-level signals (asymmetric) | Behavior 1: cross-bank scene nav with screenshot. Signals 2 & 3: MBC5 byte + .noi sections. Honest to Visual Evidence Rule | |
| Expand substrate — add 1 zone for tilemap-load behavior | Adds 1 ZoneIR; behaviors become (1) scene nav (2) cross-bank tilemap load (3) MBC5 byte. 2 visual + 1 mechanism | ✓ |
| 1 UAT anchor only — mechanism signals → JVM emission invariants | Single anchor; mechanism signals (cartridge byte, .noi, BANKED count) move into Tier-1 JVM emission invariants slot | |

**User's choice:** Expand substrate — add 1 banked zone.
**Notes:** Substrate becomes 3 scenes + 1 zone. Adds the cross-bank tilemap-load behavior — regression check on Phase 07.4-22 / 07.4-30 SWITCH_ROM-from-HOME wrapper.

---

## Area 3 — SRAM (external RAM) banking

| Option | Description | Selected |
|--------|-------------|----------|
| Skip SRAM — ROM banking only; seed for future | Phase 11 validates ROM banking only; SRAM banking → seed (SEED-014-sram-bank-assignment-dsl) for future phase. Smallest scope cap | |
| Validate via SaveDataBuilder — multi-slot saves naturally hit SRAM | gbkt-engine has SaveDataBuilder writing to SRAM; add save() to play scene with `ramBanks = 2`; verify persistence across reboot. Validates SRAM bank-1 writes via existing surface, NO new DSL | ✓ |
| Add minimal SRAM bank DSL this phase | New DSL like `u8Var(sramBank = 1)` or `sramVar(bank = 1)`; mirrors reference's var_0..var_3 split. Violates anti-overfitting rail 1; STRONGLY recommend NO | |

**User's choice:** Validate via SaveDataBuilder.
**Notes:** No new SRAM-bank DSL — existing SaveDataBuilder surface covers the SRAM write path. If SaveDataBuilder doesn't cleanly emit `ENABLE_RAM` / `SWITCH_RAM(N)` today, that's the candidate named codegen bug-fix slot (D-13 candidate (a)).

### Area 3.1 — Anchor count restructure (4 candidates, 3-cap pattern)

| Option | Description | Selected |
|--------|-------------|----------|
| 3 anchors: drop MBC5 byte to 4th-signal | (1) scene nav (2) tilemap load (3) SRAM save persistence. MBC5 byte moves to 4th-signal artifact | |
| 3 anchors: keep MBC5, drop tilemap load | (1) scene nav (2) MBC5 byte (3) SRAM save persistence. Drops 1-zone substrate addition | |
| Allow 4 anchors this phase (one-time exception) | (1) scene nav (2) tilemap load (3) MBC5 byte (4) SRAM save persistence. Breaks Phase 9/10 pattern but acknowledges bank-port has 4 distinct contracts | ✓ |

**User's choice:** Allow 4 anchors (one-time exception).
**Notes:** Captured as D-09 in CONTEXT.md. The BANKED contract has 4 distinct surfaces (ROM code-banks, ROM data-banks, MBC type, SRAM banks); folding two into one would reduce honesty. Future ports MUST justify any anchor-count expansion the same way — this is NOT a precedent. Phase 12 is NOT pre-licensed to ≥4 anchors.

---

## Area 4 — 4th signal: bank-layout assertion shape

| Option | Description | Selected |
|--------|-------------|----------|
| Threshold check — each l__CODE_N ≤ 16KB | Just verify no banked CODE section exceeds hard bank capacity. Tightly coupled to substrate (i)+FFD nondeterminism; smallest, hardest-to-game | ✓ |
| Relative ratio — each gbkt l__CODE_N within 2× of reference's same bank | Same as ROM size signal: per-bank ratio. Requires matching banks by index — gbkt's FFD vs reference's bo<N> mapping is dishonest | |
| Presence + cross-bank-call-resolve only | Two assertions: (a) ≥2 distinct l__CODE_N sections; (b) ROM boots past navigate_to_scene without MBC5 trap | |
| Bank-count parity — same number of CODE banks as reference | Reference has CODE_0..CODE_3; gbkt must also produce 4. Conflicts with substrate (i); NOT recommended | |

**User's choice:** Threshold check — each l__CODE_N ≤ 16KB.
**Notes:** Hardest-to-game (linker would fail anyway if exceeded, but asserting the bound makes the codegen contract explicit). FFD nondeterminism made the per-bank ratio comparison dishonest; the threshold is the honest framing.

---

## Claude's Discretion

The following were explicitly left to the planner / research / executor — they are listed in CONTEXT.md's "Claude's Discretion" section but were NOT discussed in detail with the user:

- **D-claude-1: Exact plan count / wave structure.** Targeted ≥12 plans (D-18 rough frame ~18 plans). Planner refines after research.
- **D-claude-2: Exact scene names + contents.** "title / play / pause" is a suggestion; planner picks names and minimum-viable contents (no contrived padding per D-02).
- **D-claude-3: Zone tileset contents.** 1 small asset; planner picks shape (must be non-trivial enough that `set_bkg_tiles` actually fires).
- **D-claude-4: SaveDataBuilder DSL shape.** Use existing surface; planner verifies the current write-trigger shape (`save.write()` or equivalent).
- **D-claude-5: Cartridge magic-string.** Use `"MBC5"` string; typed Cartridge enum is Phase 13 territory, NOT Phase 11.
- **D-claude-6: MCP emulator soft-reboot recipe.** Anchor 4 needs `.sav` preservation across reboot; planner verifies whether `emulator_stop` + `emulator_start` preserves `.sav` in the current test harness.

## Deferred Ideas

(Captured in detail in CONTEXT.md `<deferred>` section. Summary list:)

- Manual-banking DSL (`bank(N) { ... }`) — STRUCTURAL North Star rejection, not even a seed
- SRAM-bank-assignment DSL — Phase 13 route if a future port needs it (Phase 11 covers via SaveDataBuilder)
- Typed `Cartridge` enum — already a Phase 13 requirement item 1
- Bank-count parity with reference — FFD nondeterminism makes 1:1 mapping dishonest
- Per-bank ratio comparison against reference — same FFD-nondeterminism rejection
- Forcing FFD via config knobs (substrate iii) — would itself be new DSL surface
- Forcing FFD via padded scene content (substrate ii) — contrived scene authoring
- Console-mode `puts` / `printf` text output — gbkt uses window-layer text
- Pixel-and-frame parity with reference — UAT verifies BANKED contract, not text rendering
- Pre-inserting Phase 11.1 placeholder before port surfaces surplus — conditional only
- 5th UAT anchor — 4 is the explicit one-time cap
- 2nd banked zone (inter-zone bank locality) — Phase 12 or future zone-banking-specific phase
