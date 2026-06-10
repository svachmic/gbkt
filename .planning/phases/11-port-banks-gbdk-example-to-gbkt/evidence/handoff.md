# Phase 11 Verification Handoff

**Phase:** 11 — Port banks GBDK example to gbkt
**Closed:** 2026-05-20
**Plan-checker entry point:** this file

## Quick Verdict Table

| Signal | Status | Evidence |
|--------|--------|----------|
| Anchor 1 (cross-bank scene nav) | FAIL (visual) / PASS (variable) | evidence/uat-screenshots/anchor1-play-scene.png + .json |
| Anchor 2 (zone tilemap visible) | FAIL | evidence/uat-screenshots/anchor2-tilemap.png + .json |
| Anchor 3 (MBC5 byte 0x0147) | PASS | evidence/anchor3-cartridge-byte.txt |
| Anchor 4 (SRAM GBST round-trip) | NOT EXECUTED | — (Plan 11-12 skipped; routed to Phase 11.1 via SEED-016) |
| INV-1 (BANKED keyword) | PASS | evidence/tier1-shape/inv1-play-enter.txt + inv1-play-frame.txt + inv1-play-exit.txt |
| INV-2 (SWITCH_ROM wrapper) | FAIL (RED-by-design) | evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt (0 bytes) + evidence/inv2-failure.txt |
| INV-3 (mbcType propagation) | PASS | evidence/tier1-shape/inv3-build-properties.txt |
| INV-4 (SRAM write path + trigger_saves) | PASS | evidence/tier1-shape/inv4-save-game-saves.txt |
| 4th-signal (.noi bank thresholds) | PASS | evidence/oracle-comparison.md |
| BLOCKING smoke test | PASS | evidence/final-buildrom.log |
| Named codegen bug | CLOSED | evidence/named-bug.md (commit 56b70d74 + arity-fix d4be4679) |

## Detail Sections

### Anchor 1 — Cross-bank scene navigation (HOME→bank-1 BANKED trampoline)

**Result:** FAIL (visual) / PASS (variable).

Variable evidence GREEN: post-Start press, `current_scene` reads `1` (play scene id) in `evidence/uat-screenshots/anchor1-play-scene.json` (captured frame 14). The cross-bank BANKED trampoline IS firing.

Visual evidence FAILED: captured PNG `evidence/uat-screenshots/anchor1-play-scene.png` is a blank 413-byte DMG frame — no pixels reach VRAM despite the scene transition firing. Per CLAUDE.md Visual Evidence Rule, variable evidence is NECESSARY but NEVER SUFFICIENT for visual truths; the BINDING evidence is the screenshot and the screenshot is blank.

**Root cause:** SEED-014 (transitively). The `_bkg_tiles_load_banked` HOME wrapper is gated behind `hasSportRacing && bank > 1` in `GBDKPipelineV2.kt:972-980`; Banks has no sport_racing → no wrapper → tilemap never loaded → play scene renders as a blank frame. Anchor 1 + Anchor 2 share this single root cause.

**Routing:** Phase 11.1 (terminal closer subphase) — bundled with SEED-014.

### Anchor 2 — Cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper)

**Result:** FAIL.

Same root cause as Anchor 1: SEED-014 `_bkg_tiles_load_banked` gating. Screenshot `evidence/uat-screenshots/anchor2-tilemap.png` (413 bytes) is a blank DMG frame; the checker tilemap is never rendered because the SWITCH_ROM-from-HOME wrapper that would load it is never emitted. Confirmed by `grep -E "_bkg_tiles|set_bkg_tiles|SWITCH_ROM" gbkt-examples/banks/build/gbkt/generated/main.c` returning zero matches (see `evidence/inv2-failure.txt`).

**Routing:** Phase 11.1 (terminal closer subphase) — same SEED-014 fix unblocks both anchors.

### Anchor 3 — MBC5 cartridge byte at ROM offset 0x0147

**Result:** PASS.

```
ROM file: gbkt-examples/banks/build/gbkt/output/banks.gb
Offset:   0x0147
Byte:     0x1b
Expected: 0x1b (MBC5+RAM+BATT — matches reference -Wl-yt0x1B)
Result:   PASS
```

`config { cartridge = "MBC5_RAM_BATTERY"; ramBanks = 2 }` correctly produces `0x1b` at offset `0x0147`. Confirmed by file read in Plan 11-13.

### Anchor 4 — SRAM save persistence via GBST round-trip

**Result:** NOT EXECUTED.

Plan 11-12 was orchestrator-skipped because it queued behind 11-11 on the same `BanksUatTest` source file (Plan 11-11 RED'd on visual evidence and 11-12 inherited the skip). Anchor 4 mechanism (`save_game_saves` → `ENABLE_RAM` → SRAM byte write → GBST capture → reload → read-back) does NOT visually depend on tilemap rendering, so the test could in theory run, but it was not exercised at port-close.

**Codegen contract is GREEN** at JVM tier (see INV-4 below). The deferred status is a UAT execution gap, not a codegen gap.

**Routing:** Phase 11.1 (terminal closer subphase) — bundled with SEED-016. Phase 11.1 will re-run UAT against banks anyway once SEED-014 unblocks visual evidence; Anchor 4 rides on the same UAT @Test sweep at zero additional cost.

### INV-1 — BANKED keyword on play_enter / play_frame / play_exit

**Result:** PASS.

```
void play_enter(void) BANKED { SHOW_SPRITES; }
void play_frame(void) BANKED { /* trigger_saves + navigate_to_scene */ }
void play_exit(void) BANKED { /* exit logic */ }
```

`BANKED` keyword on all three play scene lifecycle functions (lives in `bank1.c`); HOME→bank-1 trampoline contract locked.

### INV-2 — _bkg_tiles_load_banked SWITCH_ROM wrapper

**Result:** FAIL (RED-by-design).

`evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt` is 0 bytes — brace-walk extraction found no `_bkg_tiles_load_banked` function in `main.c`. This is the **JVM-tier prediction** of the runtime failure observed in Anchors 1+2. The RED sentinel is kept intentionally per Plan 11-07 disposition; it locks the SEED-014 routing gate so Phase 11.1 has a clean RED→GREEN cycle.

### INV-3 — gbkt-build.properties carries MBC5_RAM_BATTERY

**Result:** PASS.

```
#gbkt build metadata
cartridge=MBC5_RAM_BATTERY
gbcMode=DISABLED
mbcType=0x1B
```

DSL `config { cartridge = "MBC5_RAM_BATTERY" }` propagates correctly to `gbkt-build.properties` → `CompileRomTask` → lcc `-Wl-yt0x1B`. Upstream of Anchor 3.

### INV-4 — SRAM write path + trigger_saves stub

**Result:** PASS.

```c
void save_game_saves(UINT8 slotIndex) {
    ENABLE_RAM;
    UINT8* sram = (volatile UINT8 *)(0xA000 + (UINT16)slotIndex * 2u);
    sram[0u] = _saveFlag;
    sram[1u] = 171u;
    DISABLE_RAM;
}
```

`ENABLE_RAM` + SRAM byte writes at `0xA000+slotIndex*2` + `DISABLE_RAM` sequence emitted in `bank1.c`. Plus the `trigger_saves()` trampoline (Plan 11-10 fix, commits 56b70d74 + d4be4679) routes `play_frame`'s `triggerSystem("saves")` call to `save_game_saves(slot)`. SRAM write path is provably emitted; Anchor 4's deferred runtime verification rides on this codegen GREEN.

### 4th-signal — Bank-layout threshold (.noi `DEF l__CODE_<N>` ≤ 16384 bytes)

**Result:** PASS.

| Bank | Code section size (bytes) | Hex | % of 16384 |
|------|---------------------------|-----|-----------|
| 0    |        0                  | 0x0000 |   0.0%    |
| 1    |       51                  | 0x0033 |   0.3%    |
| 2    |        1                  | 0x0001 |   0.0%    |

All 3 bank(s) within 16384-byte capacity. Source: `evidence/oracle-comparison.md`. No per-bank parity comparison with reference (FFD nondeterminism — per CONTEXT D-04 corollary).

### BLOCKING smoke test (final-buildrom.log)

**Result:** PASS.

```
> Task :gbkt-examples:banks:compileRom
Banking: 4 banks (highest bank: 2), MBC: 0x1B
RAM banks: 2
Compiling ROM: 3 source files -> banks.gb
ROM created: banks.gb (64 KB)

BUILD SUCCESSFUL in 6s
EXIT_CODE=0
```

`./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom` exits 0. ROM `gbkt-examples/banks/build/gbkt/output/banks.gb` materialized (65536 bytes). No `warning:`, no `error:`, no `SDCC:`, no `undefined identifier`, no `unknown address`, no `unknown value`, no `BUILD FAILED` patterns in log. The lone informational pre-build Kotlin compiler note about reified `T` inference in `GenerateCTask.kt:564` is a Kotlin language warning unrelated to Phase 11.

Banks JVM test suite: 13/14 GREEN. The lone RED is `BanksEmissionTest > INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence` — INV-2 sentinel kept RED-by-design (locks SEED-014 routing).

### Named codegen bug

**Result:** CLOSED.

Plan 11-09 named the bug: `trigger_saves` symbol unresolved at lcc link stage; `visitSaveSystem` in `GBDKSystemVisitor.kt` did not emit the `trigger_<id>` trampoline that ScriptOpVisitor's `visitTriggerSystem` already calls.

Plan 11-10 fix (TDD RED→GREEN):

- Commit `56b70d74` — `fix(11-10): GREEN — emit trigger_<id> stub in visitSaveSystem`
- Commit `d4be4679` — `fix(11-10): correct trigger_<id> trampoline arity to match caller` (follow-on arity correction — caller calls `trigger_saves()` zero-arg, so trampoline signature must match)

Verified by INV-4 PASS above + Plan 11-10's RED→GREEN cycle on `BanksEmissionTest > "INV-4 trigger_saves stub in main_c delegates to save_game_saves"`.

### Surplus seeds (Phase 11.1 cluster)

3 surplus seeds captured at port-close:

- **SEED-014** `_bkg_tiles_load_banked helper gated behind sport-racing genre` — root cause of INV-2 RED + Anchor 1+2 visual failures. WIDE blast radius (every game with zones). Routed to Phase 11.1 via discuss-phase + research per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide`.
- **SEED-015** `Scene trampoline body delegates to wrong (prior) scene when source scene has no banked body` — D11-05-1 from Plan 11-05. LOCAL blast radius. Banks dodges the runtime crash by input-handler coincidence; would crash on a different scene-transition pattern.
- **SEED-016** `Phase 11 Anchor 4 (SRAM GBST round-trip) — not executed` — UAT execution gap from Plan 11-12 skip. Codegen contract is GREEN at INV-4 tier.

Phase 11.1 placeholder inserted in ROADMAP §1354 with TERMINAL marker explicit.

### Phase 13 audit edits

**None — port surfaced no framework-shaping DSL gaps.**

The surplus defects (SEED-014, SEED-015) are codegen-pass bugs (incorrect gating expression + trampoline emission loop bug) in `GBDKPipelineV2` — NOT DSL gaps. They are correctly routed to Phase 11.1 (terminal codegen closer), NOT Phase 13 (framework primitives surfaced by example ports).

Phase 13 candidates considered and rejected:

- **Typed `Cartridge` enum.** Already Phase 13 item 1. Phase 11 used the magic string `"MBC5_RAM_BATTERY"` per CONTEXT D-claude-5; no new edit needed (item already tracked).
- **SRAM-bank-assignment DSL.** Explicitly rejected per CONTEXT D-17: "only if a future port needs it AND SaveDataBuilder doesn't already cover it — Phase 11 confirmed SaveDataBuilder covers SRAM via SaveDataBuilder, so no re-route."
- **SaveDataBuilder fluent `save.write()` surface (replacing `triggerSystem("saves")` magic string).** This is a known DSL-aesthetic gap (would mirror `feedback_no_magic_strings.md`), but the port did NOT prove it was a BARRIER — Plan 11-10 fixed the underlying codegen gap (`trigger_<id>` stub) without needing a DSL surface change. The existing `triggerSystem(id)` shape worked once codegen emitted the trampoline. A future port may surface this more sharply; until then, no Phase 13 item.
- **Cartridge config dual-source** (DSL `config { ... }` + Gradle `gbkt { ramBanks.set(2) }`). Mentioned in CONTEXT/RESEARCH but did NOT surface as a Phase 11 blocker — the existing single-source path through `config { ramBanks = 2 }` worked end-to-end. Subsumed under Phase 13 item 1 (typed Cartridge) when that lands.

## Phase Verdict

**RED** — 2/4 anchors FAIL (Anchor 1 visual / Anchor 2 visual) + 1/4 NOT EXECUTED (Anchor 4) + 1/4 INV RED-by-design (INV-2). The BLOCKING smoke test PASSES and the ROM is produced cleanly, so the codegen + linker contract is sound for the BANKED-keyword + MBC5-cartridge-byte + SRAM-write-path surfaces (Anchors 3 + INV-1 + INV-3 + INV-4 + 4th-signal all GREEN); the failing signals share a single root cause (SEED-014 `_bkg_tiles_load_banked` gating) that is routed to Phase 11.1 with WIDE-blast-radius discipline.

The named-bug-fix scope cap held: Plan 11-10 closed `trigger_<id>` cleanly; the other two codegen defects (SEED-014 + SEED-015) and the deferred Anchor 4 (SEED-016) are bundled into the terminal closer phase, not folded into Phase 11.

## ROADMAP follow-up state

- **Phase 11.1 placeholder:** created (3 seeds: SEED-014, SEED-015, SEED-016). Marked TERMINAL per CONTEXT D-19 + user memory `feedback_many_small_plans_terminal_subphase`. SEED-014 flagged WIDE blast radius — `/gsd-discuss-phase 11.1 --research-phase 11.1` required before any plan ships.
- **Phase 13 edits:** unchanged (no framework-shaping DSL gaps surfaced — surplus defects are codegen-pass bugs, not DSL surface gaps; correctly routed to Phase 11.1 instead).
