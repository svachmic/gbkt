---
status: partial
phase: 11-port-banks-gbdk-example-to-gbkt
source: [11-CONTEXT.md, 11-RESEARCH.md, 11-PATTERNS.md]
started: 2026-05-19
updated: 2026-05-20
---

## Phase 11 UAT Outcome (2026-05-20)

- **Anchor 1 (cross-bank scene nav):** Variable evidence GREEN (Observation.scene == "play"
  after Start). Visual evidence FAILED — captured PNG is a blank 413-byte DMG frame. The
  cross-bank BANKED trampoline IS firing, but no pixels reach VRAM. Blocked by INV-2.
- **Anchor 2 (banked zone tilemap visible):** Visual evidence FAILED — same blank PNG.
  Root cause confirmed: `_bkg_tiles_load_banked` helper at `GBDKPipelineV2.kt:972-980` is
  gated behind `hasSportRacing && bank > 1`. Banks has no sport_racing → helper never
  emitted → SWITCH_ROM(2) → set_bkg_tiles(...) → SWITCH_ROM(1) sequence never executes
  → tilemap never loaded. Wave 2 INV-2 sentinel from Plan 11-07 was the JVM-tier
  prediction of this exact runtime failure.
- **Anchor 3 (MBC5 cartridge byte 0x0147):** GREEN — `0x1b` confirmed by file read.
- **Anchor 4 (SRAM persistence):** Not executed — Plan 11-12 skipped pending Phase 11.1
  resolution (anchor 4 doesn't visually depend on the tilemap but is queued behind 11-11
  on the same UAT test file).

Anchors 1+2 routed to Phase 11.1 (terminal subphase per CONTEXT D-14 + Plan 11-14 escape
valve) per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide` — the
`_bkg_tiles_load_banked` gating fix touches every game with zones (pong, dungeon, racer)
and needs proper discuss-phase + research before a plan ships.

## Visual Evidence Rule

> For verification truths shaped **"X is visible on screen"** (e.g., "track tilemap is
> visible", "HUD shows lap count", "menu cursor is highlighted"), evidence MUST include a
> runtime screenshot, NOT just a variable-state assertion.
>
> Variable assertions like `assertVariable("_current_tileset_id", 1)` prove that the
> codegen wrote a value at one point in scene-enter — they do NOT prove the value is
> visually reflected by the time the player sees the screen. A subsequent op (e.g., a
> user-authored `clear()` lowering to `cls()`) can wipe the visual outcome while leaving
> the variable intact.

(Quoted verbatim from `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule".)

**Anchors 1 + 2 are visual truths; anchors 3 + 4 are mechanism truths and need
variable/file evidence only.** Anchors 1 and 2 phrase their behavior as "rendered on
screen" / "visible in screenshot" and therefore MUST end with an `emulator_screenshot`
call at the climax frame. Anchors 3 and 4 are internal state truths (ROM-byte at
offset 0x0147; SRAM byte at 0xA000 across a GBST round-trip) where the visual surface
is downstream of (and inferred from) the state — variable/file evidence is the binding
artifact.

Screenshots and text-evidence files are written to:

```
.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/{anchor-slug}.png
.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/{anchor-slug}.txt
```

The screenshot (anchors 1 + 2) and the text-evidence file (anchors 3 + 4) are the
BINDING evidence artifacts. The accompanying `emulator_assert` / `emulator_read_*`
calls are **necessary but never sufficient** for visual anchors — Phase 07.4 plans
14–18 verified SC-4 (track visible) via `_current_tileset_id=1` variable evidence and
burned 5 plans before the user UAT revealed the runtime ROM never rendered the track.
Phase 11 does not repeat that mistake for anchors 1 + 2; anchors 3 + 4 are explicitly
classified as mechanism-level per CONTEXT D-08(3,4) and D-10, so variable/file evidence
alone is sufficient for those two.

## Current Test

<!-- OVERWRITE each test - shows where we are -->

All 4 anchors pending. UAT execution awaits ROM build completion (Plans 11-09 .. 11-11
for anchors 1+2; Plan 11-13 for anchor 3; Plan 11-12 for anchor 4).

## Tests

### Anchor 1: Cross-bank scene navigation (HOME→bank-1 BANKED trampoline)

**Behavior:** Press Start on the title scene; the play scene loads via the
HOME-bank `navigate_to_scene()` BANKED trampoline, MBC5 bank switch resolves, and
the play scene is visibly rendered on screen.

**Evidence type:** screenshot

**Evidence path:** evidence/uat-screenshots/anchor1-play-scene.png

```mcp_script
# Anchor 1 — cross-bank scene nav (HOME→bank-1 BANKED trampoline)
emulator_start(game="banks")
emulator_step(frames=10)                                              # boot
emulator_wait_for_scene(scene="title")
emulator_step(frames=1, buttons=["start"])                            # edge-triggered nav
emulator_wait_for_scene(scene="play", timeout_frames=60)
emulator_step(frames=2)                                               # PPU flush frames
emulator_screenshot(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png")
emulator_assert([{type:"variable_equals", name:"_current_scene", expected:"<play_scene_id>"}])
```

**Expected:** After Start press on title scene, scene transitions to `play` without
MBC5 trap; play scene visible in screenshot. The variable `_current_scene` reads the
play scene id (sym-resolved). The screenshot is the BINDING evidence — variable
assertion alone is insufficient per the Visual Evidence Rule. Implicit signal: no
"MBC5 unknown address/value" trap during the cross-bank call.

**Result:** see "Phase 11 UAT Outcome" at top

### Anchor 2: Cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper)

**Behavior:** Within the play scene, the banked zone tilemap is loaded via the
HOME-bank `_bkg_tiles_load_banked` SWITCH_ROM wrapper (Plan 07.4-30 path) and the
checker tilemap pattern is visibly rendered on the background layer.

**Evidence type:** screenshot

**Evidence path:** evidence/uat-screenshots/anchor2-tilemap.png

```mcp_script
# Anchor 2 — cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper)
# Continues from Anchor 1 — play scene already loaded.
emulator_step(frames=4)                                               # zone enter + PPU flush
emulator_screenshot(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png")
emulator_assert([{type:"variable_equals", name:"_current_zone", expected:"<zone_id>"}])
```

**Expected:** Within play scene, banked zone tilemap visible (checker pattern);
proves `_bkg_tiles_load_banked` HOME wrapper fired and `SWITCH_ROM(<N>)` +
`set_bkg_tiles(...)` + `SWITCH_ROM(1)` completed without trap. The screenshot is the
BINDING evidence — variable assertion alone is insufficient per the Visual Evidence
Rule.

**Result:** see "Phase 11 UAT Outcome" at top

### Anchor 3: MBC5 cartridge byte at ROM offset 0x0147

**Behavior:** The built ROM file's byte at offset `0x0147` is `0x1b`
(MBC5+RAM+BATT), matching the reference Makefile's `-Wl-yt0x1B` flag. This is a
mechanism-level signal (internal state truth) — no screenshot needed per CLAUDE.md
visual-evidence rule corollary.

**Evidence type:** variable/file

**Evidence path:** evidence/anchor3-cartridge-byte.txt

```mcp_script
# Anchor 3 — MBC5 cartridge byte at ROM offset 0x0147 (file read, no screenshot)
# Mechanism-level signal — variable/file evidence only per Visual Evidence Rule corollary.
python3 -c "f=open('build/gbkt/output/banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))" \
  > .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt
# expect file contents: 0x1b
```

**Expected:** `python3 -c "f=open('build/gbkt/output/banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))"`
prints `0x1b` (MBC5+RAM+BATT — matches reference `-Wl-yt0x1B`). Per RESEARCH §"Cartridge-Byte Emission"
+ §"Pitfall 5", DSL must use `cartridge = "MBC5_RAM_BATTERY"` (NOT `"MBC5"` — that yields
`0x19` without BATT) to get `0x1b`. The text file under
`evidence/anchor3-cartridge-byte.txt` containing `0x1b` is the BINDING evidence.

**Result:** see "Phase 11 UAT Outcome" at top

### Anchor 4: SRAM save persistence via GBST round-trip

**Behavior:** After pressing Select (save trigger), the SRAM bytes at `0xA000`
contain the saved value. After a GBST `emulator_save_state` + `emulator_load_state`
round-trip, the SRAM bytes at `0xA000` STILL contain the same value. Per RESEARCH
§Pitfall 3: Coffee-GB uses `MemoryBattery` (in-memory) — `SavestateManager` captures
WRAM/OAM/HRAM but **NOT** SRAM (0xA000–0xBFFF) across `emulator_stop` +
`emulator_start`; the GBST save-state round-trip is the substitute "reboot" recipe
that exercises the same write/read path. Mechanism-level signal — no screenshot
needed.

**Evidence type:** variable/file

**Evidence path:** evidence/anchor4-sram-persistence.txt

```mcp_script
# Anchor 4 — SRAM persistence via GBST save-state round-trip
# Per RESEARCH §Pitfall 3: emulator_stop + emulator_start does NOT preserve SRAM
# (Coffee-GB MemoryBattery is in-memory; SavestateManager captures WRAM/OAM/HRAM
# but NOT SRAM 0xA000-0xBFFF). Use GBST save_state/load_state as the "reboot"
# substitute — the write path is exercised end-to-end, persistence verified via the
# GBST round-trip.
emulator_start(game="banks")
emulator_step(frames=10)                                              # boot
emulator_wait_for_scene(scene="play", timeout_frames=120)             # boot to play
emulator_step(frames=1, buttons=["select"])                           # trigger save (SRAM write)
emulator_step(frames=2)                                               # write settle
pre_bytes = emulator_read_memory("0xA000", 4)                         # read SRAM bytes pre-roundtrip
emulator_save_state(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-pre-reboot.gbst")
emulator_load_state(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-pre-reboot.gbst")
post_bytes = emulator_read_memory("0xA000", 4)                        # read SRAM bytes post-roundtrip
# write evidence file with pre/post bytes
write_file(
  ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt",
  "pre:  " + pre_bytes + "\npost: " + post_bytes + "\nmatch: " + (pre_bytes == post_bytes)
)
emulator_assert([{type:"memory_equals", address:"0xA000", length:4, expected:pre_bytes}])
```

**Expected:** After Select press (save trigger), `emulator_read_memory(0xA000, 4)`
returns 4 bytes (non-zero, reflecting the saved value); after
`emulator_save_state` + `emulator_load_state` round-trip,
`emulator_read_memory(0xA000, 4)` returns the SAME 4 bytes. Per RESEARCH §Pitfall 3:
use GBST round-trip, NOT `emulator_stop` + `emulator_start` (Coffee-GB MemoryBattery
does not persist SRAM). The text file under `evidence/anchor4-sram-persistence.txt`
recording matching pre/post bytes is the BINDING evidence.

**Result:** see "Phase 11 UAT Outcome" at top

## Anti-overfitting note

These four anchors are the entire UAT floor for Phase 11. The 4-anchor cap is a
**ONE-TIME EXCEPTION** to Phase 9/10's 3-anchor pattern, justified by the BANKED
contract's four distinct surfaces (ROM code-banks → anchor 1; ROM data-banks →
anchor 2; MBC type byte → anchor 3; SRAM banks → anchor 4) — see CONTEXT D-09.
Future ports (Phase 12 platformer_template) are NOT pre-licensed to ≥4 anchors;
they must justify any anchor-count expansion the same way. No 5th anchor — any
further surface goes to seeds via `/gsd-capture --seed` or to a conditional Phase
11.1 placeholder.

Per CONTEXT D-overfitting-1/2/3 (inherited from Phase 9 / Phase 10): UAT verifies
the BANKED contract (HOME→bank trampoline, SWITCH_ROM-from-HOME wrapper, MBC5
cartridge byte, SRAM write path), NOT the GBDK reference's text-rendering shape or
console-mode `puts`/`printf` output. No DSL features were added to make
screenshots pretty — the substrate is "3 small scenes + 1 banked zone + 1 save
slot" using existing gbkt DSL surfaces; cosmetic emission tuning to match
reference style is explicitly OUT of scope.

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0
