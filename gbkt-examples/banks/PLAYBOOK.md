# Banks

## Overview
Multi-bank ROM banking demonstration. 3 scenes (title, play, pause) over a multi-bank
ROM. Exercises: cross-bank scene navigation (HOME→bank-1 BANKED trampoline), banked
zone tilemap load (SWITCH_ROM-from-HOME wrapper), MBC5+RAM+BATT cartridge byte, SRAM
save slot via SaveDataBuilder. Third reference port for the Phase 9–12 GBDK
reference-port validation track — the GBDK `banks` reference C
(`/Users/michalsvacha/gbdk/examples/cross-platform/banks/`) is used as a codegen-shape
oracle, NOT as a DSL authoring template (manual `BANKED` keyword + `bo<N>` filename
hints are GBDK convention, not gbkt convention).

## How to Play
Boot the ROM to the title scene. Press Start on title to enter the play scene
(cross-bank trampoline: HOME → bank-1 BANKED dispatch). In the play scene a banked
zone tilemap is loaded via the HOME-bank SWITCH_ROM wrapper. Press Select in play to
trigger save slot 0 (writes `saveFlag` into SRAM bank 0 via SaveDataBuilder). Press
Start in play to enter the pause scene. Press Start in pause to navigate back to the
play scene. There is no win or lose state — this is a codegen-exercise example, not a
playable game.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Navigate to play scene (cross-bank trampoline: anchor 1) |
| play | SELECT | Trigger save slot 0 (SRAM write: anchor 4) |
| play | START | Navigate to pause scene |
| pause | START | Navigate back to play scene |

## Scene Flow
- title → play → pause → play (loop). No game-over or end-state scene exists.

## Win / Lose Conditions
None — this is a codegen-exercise example, not a playable game. Success criterion is
anchor evidence captured, not gameplay completion.

## Known Quirks
- `triggerSystem("saves")` requires the named codegen bug fix (Plan 11-10) — adds
  `trigger_saves()` stub in `GBDKSystemVisitor.visitSaveSystem()`. Without it, lcc
  reports `undefined identifier 'trigger_saves'`.
- SRAM persistence across GBST save_state/load_state round-trip ONLY — Coffee-GB
  uses `MemoryBattery` (in-memory); `emulator_stop` + `emulator_start` does NOT
  preserve SRAM. Per RESEARCH §Pitfall 3.
- MBC5 cartridge byte requires `cartridge = "MBC5_RAM_BATTERY"` in DSL config to get
  `0x1b` byte. `"MBC5"` alone maps to `0x19` (without battery).

## Variables Reference
| Name | Type | Initial | Purpose |
|------|------|---------|---------|
| saveFlag | UINT8 | 0 | Persisted via SaveDataBuilder slot 0 for SRAM round-trip verification (anchor 4) |

## MCP Input Scripts

The following input scripts back the Phase 11 UAT contract (see
`.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-UAT.md`). Each anchor verifies
one of the four D-08 behaviors. Anchors 1 and 2 are visual truths and capture a
runtime screenshot at the climax frame — the screenshot is the binding evidence (per
`CLAUDE.md` §"Verification Methodology — Visual Evidence Rule"). Anchors 3 and 4 are
mechanism-level truths (ROM byte / SRAM byte) — variable / memory assertions are
sufficient.

### Anchor 1 — Cross-bank scene navigation

```
emulator_start(game="banks")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="title")
emulator_step(frames=1, buttons=["start"])            # cross-bank trampoline fires
emulator_wait_for_scene(scene="play", timeout_frames=60)
emulator_screenshot(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png")
emulator_assert([{type:"scene_is", expected:"play"}])
```

### Anchor 2 — Cross-bank zone tilemap load

```
# Continues from anchor 1's session — play scene already entered, tilemap loaded
# via _bkg_tiles_load_banked() (HOME-bank SWITCH_ROM wrapper, Plan 07.4-30 shape)
emulator_screenshot(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png")
```

### Anchor 3 — MBC5 cartridge byte

```
# ROM-file read, no emulator session needed
python3 -c "f=open('gbkt-examples/banks/build/gbkt/output/banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))"
# Expect: 0x1b (MBC5+RAM+BATT) — or 0x19 if cartridge = "MBC5" without battery
```

### Anchor 4 — SRAM persistence via GBST round-trip

```
emulator_start(game="banks")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="title")
emulator_step(frames=1, buttons=["start"])            # title -> play
emulator_wait_for_scene(scene="play", timeout_frames=60)
emulator_step(frames=1, buttons=["select"])           # trigger save slot 0
emulator_read_memory("0xA000", 4)                     # capture SRAM bytes post-save
emulator_save_state("anchor4-pre-reboot")             # GBST snapshot (WRAM/OAM/HRAM)
emulator_load_state("anchor4-pre-reboot")             # GBST round-trip ("reboot" substitute)
emulator_read_memory("0xA000", 4)                     # must match pre-reboot bytes
```
