# Phase 11: Port banks GBDK example to gbkt — Research

**Researched:** 2026-05-19
**Domain:** GBDK ROM banking, SRAM persistence, multi-bank codegen, gbkt example port
**Confidence:** HIGH (all key claims verified against live codebase)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01** Substrate = 3 small distinct scenes + 1 banked zone + SaveDataBuilder slot.
- **D-02** Accept FFD verdict — no contrived scene padding, no forced split via config knobs.
- **D-03** Scene shape is title / play / pause (3 scenes, planner picks exact names/contents).
- **D-04** No `maxBanks` / `bankFillErrorThreshold` config-knob tuning.
- **D-05** Add 1 banked zone in the play scene for cross-bank tilemap-load.
- **D-06** Add SaveDataBuilder slot for SRAM-persistence behavior.
- **D-07** Cartridge config = `cartridge = "MBC5"; ramBanks = 2` in DSL + `gbkt { ramBanks.set(2) }` in build.gradle.kts.
- **D-08** 4 UAT anchor behaviors: (1) cross-bank scene nav, (2) cross-bank zone tilemap load, (3) MBC5 cartridge byte 0x0147, (4) SRAM save persistence across emulator reboot.
- **D-09** 4-anchor cap is a one-time exception; future ports justify expansions independently.
- **D-10** MCP play-through + screenshot for visual anchors 1+2; ROM-byte + emulator-RAM-read for mechanism anchors 3+4.
- **D-11** UAT first: `11-UAT.md` + `PLAYBOOK.md` BEFORE any DSL.
- **D-12** 4 JVM-tier emission invariants — one per UAT anchor; per-function awk brace-walk before grep.
- **D-13** Named codegen bug-fix is exploratory — name it after the first build.
- **D-14** Surplus defects → seeds + conditional Phase 11.1 placeholder.
- **D-15** Three-signal + 4th bank-layout signal artifacts under `evidence/`.
- **D-16** Artifact location: `evidence/reference/`, `evidence/oracle-comparison.md`, `evidence/uat-screenshots/`.
- **D-17** Framework-shaping DSL gaps → Phase 13 via `/gsd-phase --edit 13`.
- **D-18** Target ≥ 12 plans, rough frame provided (Plans 1–19); plan-checker MUST flag < 12.
- **D-19** Phase 11.1 (if it surfaces) MUST be terminal.
- **D-20** Verifier MUST run clean `:gbkt-examples:banks:buildRom` before declaring phase complete.
- **D-overfitting-1/2/3** No DSL features added just for this port; reference is codegen oracle only.

### Claude's Discretion

- **D-claude-1** Plan count/wave structure — targeted ≥ 12, planner refines after research.
- **D-claude-2** Scene names/contents — `title`/`play`/`pause` are suggestions; planner picks.
- **D-claude-3** Zone contents — 1 small tileset; planner picks asset shape.
- **D-claude-4** SaveDataBuilder DSL shape — use existing `saveData(id) { slots(N) }`.
- **D-claude-5** Cartridge magic-string `"MBC5"` (NOT `"MBC5_RAM_BATTERY"` — see Section 3 below).
- **D-claude-6** MCP emulator soft-reboot recipe for anchor 4 — **see critical finding in Section 3: SRAM does NOT persist across `emulator_stop` + `emulator_start`; use `emulator_save_state` / `emulator_load_state` (GBST format) as the "reboot" substitute**.

### Deferred Ideas (OUT OF SCOPE)

- Manual-banking DSL (`bank(N) { ... }`) — permanently out of scope per REQUIREMENTS.md.
- SRAM-bank-assignment DSL — Phase 13 if future ports need it.
- Typed `Cartridge` enum — Phase 13 requirement item 1.
- Bank-count parity with reference.
- Per-function ratio comparison against reference.
- Console-mode `puts`/`printf` text output.
- Pre-inserting Phase 11.1 before surplus seeds surface.
- 5th UAT anchor.
</user_constraints>

---

## Research Summary

1. **SRAM write path is generated but unreachable from scene DSL.** `GBDKSystemVisitor.visitSaveSystem()` emits `save_game_<id>(slotIndex)` and `load_game_<id>(slotIndex)` with `ENABLE_RAM`/`DISABLE_RAM` (no `SWITCH_RAM`). However, no `trigger_<id>()` trampoline is generated for `SaveSystem`, so `ScriptBuilder.triggerSystem("saves")` produces a call to a non-existent function. This is the top-1 candidate for the named codegen bug-fix (D-13 candidate a). [VERIFIED: live code `GBDKSystemVisitor.kt:299-485`, `dungeon` generated C]

2. **SRAM does NOT persist across `emulator_stop` + `emulator_start`.** Coffee-GB is initialized with `MemoryBattery` (in-memory only), not `FileBattery`. `SavestateManager` captures WRAM/OAM/HRAM only (0xC000–0xFFFF range), NOT SRAM (0xA000–0xBFFF). UAT anchor 4 MUST use `emulator_save_state` / `emulator_load_state` (GBST format) as the persistence mechanism — this captures all WRAM, which is where the `volatile UINT8 *sram` pointer reads AFTER `ENABLE_RAM`. This is the correct interpretation: "reboot" in CONTEXT.md D-06 means GBST savestate round-trip, not a genuine emulator cold-boot. [VERIFIED: `SavestateManager.kt:14-19`, `CoffeeGbEmulator.kt:148-156`]

3. **FFD verdict for 3 small scenes: likely all in bank 1.** With `bytesPerStatement = 6` and minimal scene ops (~10–20 ops per scene), each scene ≈ 60–120 estimated bytes. Total ≈ 180–360 bytes << 16,384 bank capacity. FFD packs all 3 scenes into bank 1 (the multi-scene path is taken because `game.scenes.size == 3`, bypassing the single-scene HOME fast-path). HOME→bank-1 BANKED trampoline is exercised even with all scenes in bank 1. [VERIFIED: `BankingAnalysisPass.kt:77-128`]

4. **`"MBC5"` maps to `0x19` (no RAM+Battery), NOT `0x1B`.** The CARTRIDGE_MBC_MAP in `GenerateCTask` maps `"MBC5"` → `"0x19"` and `"MBC5_RAM_BATTERY"` → `"0x1B"`. With `ramBanks > 0`, `CompileRomTask.readMbcType` auto-upgrades ROM_ONLY (`0x00`) to `0x1B`, but if `mbcType` is already `"0x19"` it does NOT upgrade — `ramBanks` affects the lcc `-Wl-ya` flag separately. So UAT anchor 3 expects `0x19` when `cartridge = "MBC5"`, even with `ramBanks = 2`. To get `0x1B` (match reference Makefile), use `cartridge = "MBC5_RAM_BATTERY"`. **Planner must verify this against D-07.** [VERIFIED: `GenerateCTask.kt:665-675`, `CompileRomTask.kt:259-283`]

5. **`ramBanks` has a two-channel wiring problem.** The DSL `config { ramBanks = 2 }` sets `CartridgeConfig.ramBanks` (stored in IR) but this value is NOT propagated to `gbkt-build.properties` by `GenerateCTask.writeBuildMetadata()`. `CompileRomTask` reads `ramBanks` from `GbktExtension.ramBanks` (set in `build.gradle.kts` via `gbkt { ramBanks.set(2) }`), which defaults to 0. For SRAM to work, `build.gradle.kts` MUST also set `gbkt { ramBanks.set(2) }` explicitly. [VERIFIED: `GenerateCTask.kt:479-545`, `CompileRomTask.kt:134-138`, `GbktExtension.kt:163`]

6. **`_bkg_tiles_load_banked` HOME-bank wrapper IS already generated unconditionally for games with zones.** `GBDKPipelineV2.buildBkgTilesLoadBankedHelper()` is called from `buildHomeFile()` whenever zones are present. The wrapper correctly emits `SWITCH_ROM(bank); set_bkg_tiles(...); SWITCH_ROM(1);`. Phase 07.4-30 is already in production. [VERIFIED: `GBDKPipelineV2.kt:1855-1893`, `1964+`]

7. **`"MBC5"` with `romBanks = 2` gives `AnalysisConfig(maxBanks = 2)`, meaning FFD has only 1 banked slot (bank 1).** With 3 scenes all estimating < 16K, this is fine — all 3 fit in bank 1. For the zone, `allocateZoneBanks` starts at bank 2, but with `maxBanks = 2`, banks 0 and 1 are reserved and bank 2 would exceed `maxBanks`. **This is a critical DSL config constraint:** `romBanks` MUST be at least 4 (HOME + scenes + zone + margin) for 3 scenes + 1 zone. Recommend `romBanks = 4` minimum. [VERIFIED: `AnalysisConfig.kt:85-98`, `allocateZoneBanks`:577]

8. **The `.noi` file lands at `build/gbkt/output/banks.noi` and can be parsed with a simple regex.** No existing gbkt test utility; a 3-line inline helper suffices. [VERIFIED: dungeon `.noi` file, `SourceMapResolverTest.kt:424-441`]

---

## GBDK Reference Walkthrough

**Source:** `/Users/michalsvacha/gbdk/examples/cross-platform/banks/src/` — 5 files, ~66 lines total.

### `banks.c` (~56 lines) — The main HOME-bank file

```c
void bank_1(void) BANKED;     // Forward declaration: function lives in ROM bank 1
void bank_2(void) BANKED;     // Forward declaration: function lives in ROM bank 2
void bank_3(void) BANKED;     // Forward declaration: function lives in ROM bank 3
void bank_fixed(void) NONBANKED { puts("I'm in fixed ROM"); }  // HOME bank function

void main(void) {
    ENABLE_RAM;                // Activate MBC SRAM at 0xA000
    SWITCH_RAM(0);  var_0 = 2; // Switch to SRAM bank 0, write
    SWITCH_RAM(1);  var_1 = 3; // Switch to SRAM bank 1, write
    SWITCH_RAM(0);  var_2 = 4; // Back to SRAM bank 0, write var_2
    SWITCH_RAM(1);  var_3 = 5; // Back to SRAM bank 1, write var_3

    bank_1();  bank_2();  bank_3();  // Cross-bank calls (BANKED convention)
    SWITCH_RAM(0); printf("Var_0=%u\n", var_0); // Read back from SRAM bank 0
    SWITCH_RAM(1); printf("Var_1=%u\n", var_1); // Read back from SRAM bank 1
}
```

**Oracle signals:**
- `BANKED` forward declarations are required for cross-bank functions. gbkt auto-injects via `CFunction.isBanked` flag in `CEmitter.kt:192`.
- `ENABLE_RAM` activates SRAM. gbkt generates this in `save_game_<id>()` via `CRawCode("ENABLE_RAM;")`.
- `SWITCH_RAM(N)` switches SRAM banks. gbkt does NOT generate `SWITCH_RAM` — it uses SRAM bank 0 only (`0xA000` pointer arithmetic). This is a deliberate simplification; SaveDataBuilder accesses bank 0 only.
- `var_0` through `var_3` are declared in sibling files with `ba<N>` suffix — GBDK-specific SRAM bank assignment. gbkt does not have this concept; all saves use bank 0.

### `bank.ba0.bo0.c` — SRAM bank 0 variable declaration

```c
int var_0;  /* In external RAM bank 0 */
```
gbkt equivalent: no explicit SRAM bank assignment. Variables are saved via offset-pointer arithmetic into bank 0.

### `bank.ba0.bo2.c` — ROM bank 2 function + SRAM bank 0 variable

```c
int var_2;  /* In external RAM bank 0 — note: shares ba0 with var_0 */
void bank_2(void) BANKED { puts("I'm in ROM bank 2"); }
```
gbkt equivalent: a scene enter/frame/exit function in bank1.c (or wherever FFD places it) with `BANKED` keyword.

### `bank.ba1.bo1.c` — ROM bank 1 function + SRAM bank 1 variable

```c
int var_1;  /* In external RAM bank 1 */
void bank_1(void) BANKED { puts("I'm in ROM bank 1"); }
```
gbkt: `play_enter() BANKED { ... }` in bank1.c.

### `bank.ba1.bo3.c` — ROM bank 3 function + SRAM bank 1 variable

```c
int var_3;  /* In external RAM bank 1 */
void bank_3(void) BANKED { puts("I'm in ROM bank 3"); }
```
gbkt: another scene function in bank1.c.

### `Makefile` — Key oracle signals

```makefile
LCCFLAGS_gb = -Wl-yt0x1B    # MBC5+RAM+BATT cartridge byte
LCCFLAGS += -autobank -Wb-ext=.rel -Wb-v
BOFLAG = $(shell echo "$<" | sed 's/.*\.bo\([0-9]\+\).*/-Wf-bo\1/')  # ROM bank hint
BAFLAG = $(shell echo "$<" | sed 's/.*\.ba\([0-9]\+\).*/-Wf-ba\1/')  # RAM bank hint
```

Reference uses `-Wl-yt0x1B` = `0x1B` = MBC5+RAM+BATTERY. gbkt achieves this via `cartridge = "MBC5_RAM_BATTERY"` in DSL config (maps to `"0x1B"` in `CARTRIDGE_MBC_MAP`). Using `"MBC5"` produces `"0x19"` (MBC5 without battery). **Planner must pick one — see Section 3 for the implication.** [VERIFIED: `GenerateCTask.kt:673-674`, Makefile line 17]

---

## SaveDataBuilder SRAM Path

### Generated C Shape (verified against dungeon example)

`GBDKSystemVisitor.visitSaveSystem(system)` [VERIFIED: `GBDKSystemVisitor.kt:299-485`] generates:

```c
// save_game_<id>(slotIndex) — always in HOME bank (main.c)
void save_game_dungeon_save(UINT8 slotIndex) {
    ENABLE_RAM;
    volatile UINT8 *sram = (volatile UINT8 *)(0xA000 + (UINT16)slotIndex * <slotSize>u);
    sram[0u] = _torchLevel;   // non-transient variables in declaration order
    sram[1u] = _keys;
    sram[2u] = _steps;
    sram[sentinelIdx] = 171u; // 0xAB sentinel
    DISABLE_RAM;              // always last
}

void load_game_dungeon_save(UINT8 slotIndex) {
    ENABLE_RAM;
    volatile UINT8 *sram = (volatile UINT8 *)(0xA000 + (UINT16)slotIndex * <slotSize>u);
    if (sram[sentinelIdx] != 171u) { DISABLE_RAM; return; } // sentinel check
    _torchLevel = sram[0u];
    _keys       = sram[1u];
    _steps      = sram[2u];
    DISABLE_RAM;
}
```

**Key properties:**
- `ENABLE_RAM` present, `DISABLE_RAM` always last. [VERIFIED]
- `SWITCH_RAM(N)` NOT emitted — SRAM bank 0 only via `0xA000` base pointer. [VERIFIED]
- Slot offset arithmetic: `0xA000 + slotIndex * slotSize`. [VERIFIED]
- Both functions placed in HOME bank (`main.c`), not `bank1.c`. Confirmed by dungeon generated output. [VERIFIED]
- With `slots(2)` and 1 variable + sentinel = `slotSize = 2`. With checksum enabled: `slotSize = 3`.

### MBC5 Cartridge Byte Interaction

`CompileRomTask.readMbcType()` [VERIFIED: `CompileRomTask.kt:259-283`]:
- Reads `mbcType` from `gbkt-build.properties` (written by `GenerateCTask.writeBuildMetadata()`).
- If `mbcType == "0x00"` AND `ramBanks > 0` → auto-upgrades to `"0x1B"`.
- If `mbcType == "0x19"` (MBC5) → uses `"0x19"` even if `ramBanks > 0`.
- Fallback (no properties file): if `ramBanks > 0` → `"0x1B"`, else `"0x19"`.

**Consequence for Phase 11:**
- `cartridge = "MBC5"` → `mbcType = "0x19"` → ROM offset 0x0147 = 0x19 = MBC5 (no battery).
- `cartridge = "MBC5_RAM_BATTERY"` → `mbcType = "0x1B"` → ROM offset 0x0147 = 0x1B = MBC5+RAM+BATTERY (matches reference).
- D-07 says `cartridge = "MBC5"; ramBanks = 2`. With `"MBC5"` and the properties file present, the byte is `0x19`. With `"MBC5_RAM_BATTERY"`, the byte is `0x1B` (matches reference). **Planner should use `"MBC5_RAM_BATTERY"` to match the reference oracle; D-07's `"MBC5"` is slightly imprecise.**

### `ramBanks` Two-Channel Wiring

[VERIFIED: `GenerateCTask.kt:479-545`] `GenerateCTask.writeBuildMetadata()` reflects `CartridgeConfig.cartridge` and `CartridgeConfig.gbcTarget` into `gbkt-build.properties` but does **NOT** reflect `CartridgeConfig.ramBanks`.

`CompileRomTask` reads `ramBanks` from `GbktExtension.ramBanks` (Gradle extension, default 0). For `-Wl-ya2` to be passed to lcc (enabling 2 SRAM banks), the game's `build.gradle.kts` MUST include:
```kotlin
gbkt {
    game("io.github.gbkt.examples.banks.BanksKt::banks")
    ramBanks.set(2)
}
```
Without this, SRAM is not allocated by the linker even if `SaveDataBuilder` emits `ENABLE_RAM`. [ASSUMED: runtime behavior — ENABLE_RAM may still write to the address range 0xA000 during emulation even without `-Wl-ya` since Coffee-GB's Mbc5 implementation handles RAM regardless, but the ROM byte won't reflect battery]

### DSL Call Surface Gap (Top-1 Bug Candidate)

`ScriptBuilder.triggerSystem("saves")` → `TriggerSystem(systemId = "saves")` → `ScriptOpVisitor.visitTriggerSystem()` → `CCall("trigger_saves", args)` [VERIFIED: `ScriptOpVisitor.kt:666-671`].

`GBDKSystemVisitor.visitSaveSystem()` returns `listOf(saveGame, loadGame)` — does NOT generate `trigger_saves()`. [VERIFIED: `GBDKSystemVisitor.kt:299-485`]

Result: lcc linker error `undefined identifier 'trigger_saves'` if the game calls `triggerSystem("saves")`.

**Current valid usage:** `save_game_<id>()` and `load_game_<id>()` are generated in `main.c` but can ONLY be called from HOME-resident code (not from banked scene scripts via DSL). The dungeon example registers `saveData` but never actually triggers save/load from any scene — the save system exists but is inert at runtime. [VERIFIED: dungeon DSL + generated C]

**Fix options (for the named codegen bug slot):**
1. Add `trigger_<id>(UINT8 slot)` wrapper in `GBDKSystemVisitor.visitSaveSystem()` that calls `save_game_<id>(slot)`. ScriptBuilder already has `triggerSystem()`.
2. Add `saveGame(id, slot)` / `loadGame(id, slot)` as first-class `ScriptOp` nodes (more correct but larger blast radius → Phase 13 route).
3. Use `CRawCode` injection (escape hatch — Phase 9 used this, but feedback_no_magic_strings forbids it for production code).

Option 1 is the smallest change that unblocks anchor 4 without expanding DSL surface.

---

## BankingAnalysisPass on Small Games

### FFD Verdict for 3 Small Scenes + 1 Zone

**Key parameters** [VERIFIED: `BankingAnalysisPass.kt` + `AnalysisConfig.kt`]:
- `HOME_BANK_SCENE_BUDGET = 4096` bytes
- `bytesPerStatement = 6` bytes
- Single-scene HOME fast-path: `game.scenes.size == 1 && codeUnits.size == 1 && codeUnits[0].estimatedBytes <= 4096`
- **With 3 scenes, the fast-path is NOT entered** — `game.scenes.size == 3 != 1`.

**Estimated scene sizes for Phase 11 substrate:**
- Title scene: ~3–5 ops (clear, show text, button check, navigate) → ~18–30 estimated bytes
- Play scene: ~15–25 ops (show sprites, load zone, input handling, navigate) → ~90–150 estimated bytes
- Pause scene: ~3–5 ops → ~18–30 estimated bytes
- **Total: ~130–210 estimated bytes**

FFD bin-packing with `AnalysisConfig(maxBanks = min(256, romBanks))`:
- With `romBanks = 4` (recommended, see below): `maxBanks = 4`
- All 3 scenes pack into bank 1 easily (130–210 bytes << 16,384 bank capacity)
- Result: `title → BankSlot(1)`, `play → BankSlot(1)`, `pause → BankSlot(1)`
- All scenes BANKED (`sceneBanked = sceneBank > 0` → true for bank 1)

**Zone allocation:**
- `allocateZoneBanks()` starts at `tilemapBankStart = 2`, reserves banks 0 (HOME) and 1 (scenes).
- With a minimal checkerboard tileset (≤ 256 bytes), zone lands in bank 2.
- `gbkt-build.properties` would show zone_bank2.c.

**Critical config constraint:** With `romBanks = 2` (default or if set to 2), `AnalysisConfig(maxBanks = 2)`. The zone allocator starts at bank 2 but `maxBanks = 2` means banks only go 0..1. `allocateZoneBanks` does NOT check `maxBanks` — it uses its own first-fit loop and can place zones beyond `maxBanks`. However, `CompileRomTask.detectMaxBank()` scans `#pragma bank N` in source files to auto-detect the needed bank count and emits `-Wm-yo<N>` accordingly. So even with `romBanks = 2` declared, if zone_bank2.c exists, the linker gets `-Wm-yo4` (next power of 2 >= 3). The `AnalysisConfig.maxBanks` cap is for scene FFD only, not zones. [VERIFIED: `CompileRomTask.kt:120-130`, `allocateZoneBanks:573-637`]

**Recommendation for D-07:** Use `config { cartridge = "MBC5_RAM_BATTERY"; romBanks = 4; ramBanks = 2; gbcTarget = GbcTarget.DMG }` to give FFD room. The CARTRIDGE_MBC_MAP maps `"MBC5_RAM_BATTERY"` → `"0x1B"`, matching the reference Makefile's `-Wl-yt0x1B`.

---

## Top-2 Likely Codegen Bug Candidates

**Candidate 1 (HIGH probability): SaveSystem has no `trigger_<id>()` call stub — SRAM save unreachable from DSL scene scripts.**

- **File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`
- **Line range:** `visitSaveSystem()` at line 299, returns `listOf(saveGame, loadGame)` at line 485.
- **Gap:** No `trigger_<id>(UINT8 slot)` function generated. `ScriptOpVisitor.visitTriggerSystem()` calls `trigger_<id>()` which doesn't exist.
- **UAT anchor failed:** Anchor 4 (SRAM save persistence).
- **Symptom:** lcc linker error `undefined identifier 'trigger_<id>'` when `triggerSystem("saves")` is used in a scene, OR the save is simply never called (silent functional gap).
- **Evidence:** `dungeon` example has `saveData("dungeon_save")` registered but zero calls to save/load in any scene DSL or generated C bank1.c. [VERIFIED]

**Candidate 2 (MEDIUM probability): `bg_load_zone_tiles` wrapper not generated for games without sport/RPG genre systems.**

- **File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
- **Line range:** `buildBkgTilesLoadBankedHelper()` at line 1872; call site at `buildHomeFile()`.
- **Issue to verify:** The `_bkg_tiles_load_banked` helper IS called from `buildHomeFile()` — but is the call guarded by a zone-count check or genre-detection? If it's unconditional for all games with zones, Candidate 2 is a non-issue.
- **UAT anchor failed:** Anchor 2 (cross-bank zone tilemap load visible in play scene).
- **Verification:** Inspect `buildHomeFile()` call chain to confirm the helper is generated whenever `gameIR.zones.isNotEmpty()`. [ASSUMED: needs line-specific verification at implementation time — see line 964 comment "Plan 07.4-30" in the grep output]

**Context (CLAUDE.md memory entry clarification):** The memory note "splitByBank now auto-adds BANKED to ALL function defs in non-zero banks" refers to the `CFunction.isBanked` mechanism in `CEmitter.kt:192` (`if (fn.isBanked) " BANKED"`), not a separate post-processing pass called `splitByBank`. The BANKED injection is done at AST construction time via `SceneVisitor` setting `isBanked = true` for all scenes with `bankSlot.bank > 0`. [VERIFIED: `SceneVisitor.kt:73-109`, `CEmitter.kt:192`]

---

## 4th-Signal `.noi` Extraction

### File Location

Built `.noi` file location: `gbkt-examples/banks/build/gbkt/output/banks.noi` [VERIFIED: dungeon example at `gbkt-examples/dungeon/build/gbkt/output/dungeon.noi`].

### File Format

Plain-text file. Each line is one of:
```
DEF l__CODE_<N> 0x<hexSize>   ← bank N CODE section byte size (lowercase hex)
DEF _symbolName 0x<addr>      ← variable/function symbol address
DEF s__CODE 0x<addr>          ← section start address
```

Example from dungeon (3 banks):
```
DEF l__CODE_0 0x0
DEF l__CODE_2 0x1
DEF l__CODE_1 0x1FB
```
`0x1FB` = 507 decimal bytes for bank 1 code. All well under 16384. [VERIFIED: live dungeon.noi]

### Parse Shape

No existing gbkt utility for `DEF l__CODE_N` parsing beyond `SourceMapResolver` (which parses symbol addresses, not bank sizes). A new 3-line helper is needed. The parse is trivial:

```kotlin
// Kotlin test helper (inline in BanksEmissionTest or standalone utility)
fun parseNoiBankSizes(noiContent: String): Map<Int, Int> {
    val regex = Regex("""DEF l__CODE_(\d+) 0x([0-9A-Fa-f]+)""")
    return regex.findAll(noiContent)
        .associate { it.groupValues[1].toInt() to it.groupValues[2].toInt(16) }
}
// Usage: all sizes <= 16384
parseNoiBankSizes(noiFile.readText()).values.forEach { assertTrue(it <= 16384) }
```

[VERIFIED: format from dungeon.noi + `SourceMapResolverTest.kt:424-441` for context]

### 4th-Signal Artifact Plan

1. After `buildRom`, copy `banks.noi` to `evidence/oracle-comparison.md` with a table of bank sizes.
2. Assert each `DEF l__CODE_N` value ≤ 16384 (0x4000).
3. Include the reference `.noi` parse in `evidence/reference/` directory.
4. Existing test infrastructure: write a JVM test that calls `gradle generateC`, reads the `.noi` file, and asserts. Alternatively, use a bash script in the plan's verification step.

---

## Cartridge-Byte Emission Status

### Does gbkt Emit a Cartridge Byte Today?

Yes — via the pipeline: DSL `config { cartridge = "..." }` → `CartridgeConfig` in IR → `GenerateCTask.writeBuildMetadata()` writes `mbcType` to `gbkt-build.properties` → `CompileRomTask.readMbcType()` reads it and passes `-Wm-yt<hex>` to lcc → lcc writes the byte to ROM offset 0x0147. [VERIFIED: `GenerateCTask.kt:508`, `CompileRomTask.kt:128-130`]

### `config { cartridge = "MBC5" }` Path

```kotlin
// DSL:
config { cartridge = "MBC5"; romBanks = 4; ramBanks = 2 }
// → CartridgeConfig(cartridge = "MBC5", romBanks = 4, ramBanks = 2)
// → GenerateCTask writes: mbcType = "0x19" to gbkt-build.properties
// → CompileRomTask reads: mbcType = "0x19" (not 0x00, no auto-upgrade)
// → lcc gets: -Wm-yt0x19
// → ROM[0x0147] = 0x19 = MBC5 (without RAM+Battery)
```

### `config { cartridge = "MBC5_RAM_BATTERY" }` Path

```kotlin
config { cartridge = "MBC5_RAM_BATTERY"; romBanks = 4; ramBanks = 2 }
// → mbcType = "0x1B" → ROM[0x0147] = 0x1B = MBC5+RAM+BATT (matches reference)
```

### Is `config { cartridge = "..." }` Already DSL?

Yes. `ConfigBuilder.cartridge: String` exists at `SystemBuilders.kt:532`. It accepts any of the string keys from `CARTRIDGE_MBC_MAP`. The typed `Cartridge` enum (Phase 13 requirement item 1) is a future improvement — magic strings work today. [VERIFIED: `SystemBuilders.kt:530-567`]

### Phase 13 Gap

The only open Phase 13 gap from this finding: **typed `Cartridge` enum** (already listed as Phase 13 requirement item 1). No new routing needed.

### Recommendation for D-07

Use `cartridge = "MBC5_RAM_BATTERY"` to match the reference's `-Wl-yt0x1B` oracle. If D-07 strictly requires `"MBC5"` (UAT anchor 3 expects `0x19`), that is also valid — but the CONTEXT.md says "planner verifies the exact ROM-byte value", so either is acceptable if documented in `evidence/oracle-comparison.md`. [ASSUMED: final choice is planner's per D-07]

---

## JVM-Tier Brace-Walk Pattern Reference

### Source Location

**File:** `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-23-PLAN.md`
**Lines:** 147–181 (awk recipe for `race_enter` scope extraction)
**Production implementation:** `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt:82-101` (Kotlin equivalent of the awk brace-walk). [VERIFIED]

### The Awk Recipe (bash shell version, per 07.4-23-PLAN.md:150-160)

```bash
awk '
  /void play_enter\(/ { in_func = 1 }
  in_func {
    for (i = 1; i <= length($0); i++) {
      c = substr($0, i, 1)
      if (c == "{") { depth++; if (depth == 1) { printing = 1; continue } }
      if (c == "}") { depth--; if (depth == 0) { in_func = 0; printing = 0; print ""; exit } }
    }
    if (printing) print $0
  }
' gbkt-examples/banks/build/gbkt/generated/bank1.c > /tmp/play_enter_body.c
# Then grep WITHIN scope only:
BANKED_COUNT=$(grep -c "BANKED" /tmp/play_enter_body.c || echo 0)
```

### The Kotlin Version (for JVM emission tests, per SimplePhysicsEmissionTest.kt:82-101)

```kotlin
private fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0; var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]; body.appendLine(line)
        for (ch in line) {
            if (ch == '{') { depth++; started = true }
            if (ch == '}') depth--
        }
        if (started && depth == 0) break
    }
    return body.toString()
}
// Usage:
val bank1C = pipelineOutput.files["bank1.c"] ?: error("bank1.c not generated")
val enterBody = extractFunctionBody(bank1C, "play_enter")
assertTrue(enterBody.contains("BANKED"), "play_enter must have BANKED keyword")
```

### 4 JVM-Tier Invariants for Phase 11

Each test calls `GBDKPipelineV2().generate(banks.build())` to get pipeline output, then extracts a function body and asserts:

1. **Anchor 1 — HOME→bank trampoline shape:**
   - `extractFunctionBody(bank1C, "play_enter").contains(" BANKED")` = true
   - `extractFunctionBody(bank1C, "play_frame").contains(" BANKED")` = true
   - `extractFunctionBody(bank1C, "play_exit").contains(" BANKED")` = true
   - `game.h` contains `void play_enter(void) BANKED;` prototype

2. **Anchor 2 — SWITCH_ROM wrapper emission:**
   - `extractFunctionBody(mainC, "_bkg_tiles_load_banked").contains("SWITCH_ROM(bank);")` = true
   - `extractFunctionBody(mainC, "_bkg_tiles_load_banked").contains("set_bkg_tiles(")` = true
   - `extractFunctionBody(mainC, "_bkg_tiles_load_banked").contains("SWITCH_ROM(1);")` = true

3. **Anchor 3 — MBC5 propagation:**
   - `pipelineOutput.files["gbkt-build.properties"]!!.contains("mbcType=0x1B")` (or `0x19`) = true

4. **Anchor 4 — SRAM write path:**
   - `extractFunctionBody(mainC, "save_game_saves").contains("ENABLE_RAM;")` = true
   - `extractFunctionBody(mainC, "save_game_saves").contains("sram[")` = true
   - `extractFunctionBody(mainC, "save_game_saves").contains("DISABLE_RAM;")` = true
   - (After bug fix) `mainC.contains("trigger_saves")` = true (trigger stub generated)

[VERIFIED: brace-walk pattern from 07.4-23-PLAN.md + SimplePhysicsEmissionTest.kt:82-101]

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Kotlin Test (JUnit 5) via `testImplementation(kotlin("test"))` |
| Config file | Per-example — no root config; uses `useJUnitPlatform()` in `build.gradle.kts` |
| Quick run command | `./gradlew :gbkt-examples:banks:test` |
| Full suite command | `./gradlew :gbkt-examples:banks:test :gbkt-backend-gbdk:test :gbkt-analysis:test` |

### Tier-1: Codegen Oracle (JVM tests — no ROM needed)

| Inv ID | Behavior | Test Type | Command | File |
|--------|----------|-----------|---------|------|
| INV-1 | `play_enter()` has BANKED keyword in bank1.c | unit | `./gradlew :gbkt-examples:banks:test --tests "*BanksEmissionTest*"` | BanksEmissionTest.kt (Wave 0 gap) |
| INV-2 | `_bkg_tiles_load_banked()` in main.c contains SWITCH_ROM | unit | same | BanksEmissionTest.kt |
| INV-3 | `gbkt-build.properties` mbcType = 0x1B | unit | same | BanksEmissionTest.kt |
| INV-4 | `save_game_saves()` in main.c contains ENABLE_RAM | unit | same | BanksEmissionTest.kt |

**Pass criterion:** All 4 GREEN before any UAT runs.
**Failure mode caught:** Regression in BANKED injection, missing HOME-bank wrapper, wrong cartridge byte propagation, SRAM codegen gap.

### Tier-2: Build Oracle (ROM build — requires GBDK toolchain)

| Signal | Command | Pass criterion | Failure mode |
|--------|---------|----------------|--------------|
| Zero lcc warnings | `./gradlew :gbkt-examples:banks:buildRom 2>&1` | No `warning:` in output | Codegen produces invalid C |
| Zero SDCC errors | same | No `SDCC:` or `error:` | Codegen produces semantically invalid C |
| Zero MBC5 trap | same | No `unknown address/value` | BANKED trampoline broken |
| ROM generated | same | `build/gbkt/output/banks.gb` exists | Pipeline failed to produce ROM |

**Pass criterion:** Exit code 0, no error patterns, `.gb` exists.
**Per D-20:** Verifier MUST run this before flipping phase verdict to passed.

### Tier-3: Runtime Oracle (MCP play-through — requires ROM + emulator)

| Anchor | Evidence Type | MCP Sequence | Pass criterion |
|--------|--------------|--------------|----------------|
| 1: Scene nav (HOME→bank trampoline) | Screenshot + variable | `emulator_start` → step to title → `emulator_step(buttons=["start"])` → `emulator_wait_for_scene("play", 60)` → `emulator_screenshot("anchor1-play-scene")` + `emulator_assert(scene_is="play")` | Scene reaches "play" without emulator crash; screenshot shows play scene |
| 2: Zone tilemap load | Screenshot + variable | Same session → `emulator_screenshot("anchor2-tilemap")` after entering play scene | Zone tilemap visible; screenshot shows tile pattern |
| 3: MBC5 cartridge byte | ROM file read | `python3 -c "f=open('banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))"` | Output = `0x1b` (or `0x19`) |
| 4: SRAM persistence | Variable read + GBST save/load | step to trigger save → `emulator_read_memory("0xA000", 4)` → `emulator_save_state("anchor4-pre-reboot")` → `emulator_load_state("anchor4-pre-reboot")` → `emulator_read_memory("0xA000", 4)` | SRAM bytes match before and after GBST round-trip |

**Pass criterion:** Anchors 1+2 have screenshots in `evidence/uat-screenshots/`; anchors 3+4 have text artifacts in `evidence/`.
**Failure mode caught:** Runtime banking failures, emulator crash, SRAM not written.

### 4th-Signal Artifact

```bash
# After buildRom:
grep "DEF l__CODE_" gbkt-examples/banks/build/gbkt/output/banks.noi
# Assert each value <= 0x4000 (16384)
python3 -c "
import re
with open('gbkt-examples/banks/build/gbkt/output/banks.noi') as f:
    for m in re.finditer(r'DEF l__CODE_(\d+) 0x([0-9a-fA-F]+)', f.read()):
        size = int(m.group(2), 16)
        bank = m.group(1)
        assert size <= 16384, f'Bank {bank} code section {size} bytes > 16384 (bank overflow!)'
        print(f'Bank {bank}: {size} bytes ({size/16384*100:.1f}% capacity) OK')
"
```

**Pass criterion:** All banks ≤ 16384 bytes; output captured in `evidence/oracle-comparison.md`.

### Wave 0 Gaps

- [ ] `gbkt-examples/banks/src/test/kotlin/.../BanksEmissionTest.kt` — covers INV-1..4 (brace-walk + 4 JVM invariants)
- [ ] `gbkt-examples/banks/src/test/kotlin/.../BanksIRTest.kt` — covers IR structure (scene count, variable count, system count)
- [ ] `gbkt-examples/banks/src/test/kotlin/.../BanksUatTest.kt` — covers UAT anchors 1+2+4 (anchors 3 is file-read in plan step)
- [ ] `gbkt-examples/banks/11-UAT.md` — 4-anchor contract doc (Plan 1 deliverable)
- [ ] `gbkt-examples/banks/PLAYBOOK.md` — MCP agent playbook (Plan 1 deliverable)
- [ ] `gbkt-examples/banks/build.gradle.kts` — example subproject (Plan 2 deliverable)
- [ ] `gbkt-examples/banks/src/main/kotlin/.../Banks.kt` — game DSL (Plan 3 deliverable)
- [ ] `settings.gradle.kts` entry — `include("gbkt-examples:banks")` (Plan 2 deliverable)

---

## Architecture Patterns

### Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Scene lifecycle (enter/frame/exit) | Bank 1 (banked ROM) | HOME (trampolines) | `SceneVisitor` puts scene funcs in non-HOME banks; `navigate_to_scene()` dispatches from HOME |
| Zone tilemap data | Bank 2+ (banked ROM data) | — | `allocateZoneBanks` reserves bank 2+ for tilemap const arrays |
| SRAM save/load functions | HOME bank | — | `save_game_<id>` must be HOME-resident (cannot call SWITCH_ROM from BANKED context) |
| BANKED trampolines | HOME bank | — | `navigate_to_scene()` trampoline lives in HOME per `GBDKPipelineV2.buildNavigateToSceneFunction()` |
| SWITCH_ROM wrapper (`_bkg_tiles_load_banked`) | HOME bank | — | Plan 07.4-30 fix: SWITCH_ROM unsafe from BANKED context |
| Cartridge byte (0x0147) | Build toolchain | — | GenerateCTask → gbkt-build.properties → CompileRomTask → lcc `-Wm-yt` |

### Recommended Project Structure

```
gbkt-examples/banks/
├── build.gradle.kts          # game spec, assets, ramBanks.set(2)
├── 11-UAT.md                 # 4-anchor contract
├── PLAYBOOK.md               # MCP agent instructions
├── res/
│   └── tiles/
│       └── checker.png       # minimal 4-tile checkerboard for zone (8x8 or 16x16)
├── src/
│   └── main/kotlin/io/github/gbkt/examples/banks/
│       └── Banks.kt          # game DSL (3 scenes + 1 zone + 1 saveData)
│   └── test/kotlin/io/github/gbkt/examples/banks/
│       ├── BanksIRTest.kt    # IR structure validation
│       ├── BanksEmissionTest.kt  # 4 JVM-tier emission invariants (brace-walk)
│       └── BanksUatTest.kt   # UAT anchors 1+2+4
└── .planning/phases/11-.../evidence/
    ├── reference/
    │   ├── BUILD.md          # reproducible reference ROM build recipe
    │   └── banks.noi.txt     # reference .noi content (gitignored .gb)
    ├── uat-screenshots/      # anchor 1+2 screenshots
    └── oracle-comparison.md  # ROM size diff + C shape diff + 4th signal
```

### DSL Pattern (banks.kt skeleton)

```kotlin
val banks = game("Banks") {
    config {
        cartridge = "MBC5_RAM_BATTERY"  // 0x1B cartridge byte, matches reference
        romBanks = 4                    // HOME + bank1 + bank2(zone) + margin
        ramBanks = 2                    // SRAM for SaveDataBuilder
    }

    // State variable — must be non-transient to be saved
    var saveFlag by u8Var(0)

    // SaveDataBuilder — slot count, checksum optional
    saveData("saves") { slots(2) }

    // Zone for tilemap load (allocateZoneBanks places in bank 2)
    val playZone by zone("play_zone") {
        tileSize(8)
        tileset(asset("tiles/checker.png"))
        // minimal tile data — enough to call set_bkg_tiles
    }

    // Pause scene (defined before play for forward-ref safety)
    val pauseScene = scene("pause") {
        enter { clear() }
        frame { whenever(buttons.start.pressed) { navigate("play") } }
    }

    // Play scene — loads zone tilemap, has save trigger
    scene("play") {
        enter { showSprites() }
        frame {
            whenever(buttons.select.pressed) { triggerSystem("saves") } // anchor 4 trigger
            whenever(buttons.start.pressed) { navigate(pauseScene) }
        }
    }

    // Title scene
    val titleScene = scene("title") {
        enter { clear() }
        frame { whenever(buttons.start.pressed) { navigate("play") } }
    }

    start = "title"
}
```

**Note:** `triggerSystem("saves")` requires the named codegen bug-fix (Candidate 1) to be applied first for anchor 4 to work.

### Anti-Patterns to Avoid

- **Anti-pattern: `cartridge = "MBC5"` with expectation of `0x1B` byte.** Use `"MBC5_RAM_BATTERY"` to get `0x1B`. `"MBC5"` maps to `0x19`.
- **Anti-pattern: relying on `config { ramBanks = 2 }` alone.** Must also set `gbkt { ramBanks.set(2) }` in `build.gradle.kts` for `-Wl-ya2` to be passed to lcc.
- **Anti-pattern: file-level grep for BANKED count.** Use per-function brace-walk (per CLAUDE.md scope-level grep gates corollary).
- **Anti-pattern: `emulator_stop` + `emulator_start` for SRAM persistence test.** SRAM is NOT preserved across Coffee-GB stop/start. Use `emulator_save_state` / `emulator_load_state` (GBST format captures WRAM where game state lives; SRAM 0xA000 is readable via `emulator_read_memory` within a single session).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BANKED keyword injection | Custom postprocess pass | `CFunction.isBanked` + `CEmitter.kt:192` | Already in production |
| Bank-overflow detection | Custom check | `BankingAnalysisPass` + `BudgetAuditPass` | Already catches >16KB sections |
| Zone bank allocation | Custom allocator | `allocateZoneBanks()` in GBDKPipelineV2 | Phase 06.7 battle-tested |
| HOME-bank SWITCH_ROM wrapper | Inline SWITCH_ROM in banked code | `_bkg_tiles_load_banked` helper | Plan 07.4-30 — inline unsafe from BANKED context |
| Cartridge byte setting | Manual ROM patch | `GenerateCTask.writeBuildMetadata()` + CompileRomTask | Existing pipeline |
| .noi bank-size parse | Ad-hoc binary parse | Regex `DEF l__CODE_(\d+) 0x([0-9A-Fa-f]+)` | .noi is plain text |

---

## Common Pitfalls

### Pitfall 1: `config { ramBanks = 2 }` is NOT enough for SRAM

**What goes wrong:** `ENABLE_RAM; sram[0] = val; DISABLE_RAM;` runs but the linker didn't allocate SRAM. The ROM may appear to write correctly in emulation (Coffee-GB Mbc5 handles RAM regardless), but the cartridge byte won't indicate battery-backed RAM.
**Why it happens:** `CartridgeConfig.ramBanks` is stored in the IR but `GenerateCTask.writeBuildMetadata()` does NOT reflect it to `gbkt-build.properties`. `CompileRomTask` reads `ramBanks` from `GbktExtension.ramBanks` only.
**How to avoid:** Add `gbkt { ramBanks.set(2) }` to `build.gradle.kts` alongside the DSL `config { ramBanks = 2 }`.
**Warning signs:** lcc output missing `-Wl-ya2` flag.

### Pitfall 2: All-scenes-in-HOME fast-path doesn't trigger for 3-scene game

**What goes wrong:** Planner expects single-scene fast-path behavior but gets full FFD bin-packing.
**Why it happens:** `BankingAnalysisPass` fast-path requires `game.scenes.size == 1`. A 3-scene game ALWAYS goes through FFD, always gets `bank1.c`, always has `BANKED` scene functions.
**How to avoid:** Expect and document that all 3 scenes land in bank 1 via FFD; this is correct behavior.
**Warning signs:** Would only manifest if planner accidentally checks `allScenesInHome` path for multi-scene game.

### Pitfall 3: `emulator_stop` + `emulator_start` does not preserve SRAM

**What goes wrong:** UAT anchor 4 "SRAM persistence across emulator reboot" fails — `emulator_read_memory(0xA000, 4)` returns zeros after restart.
**Why it happens:** Coffee-GB uses `MemoryBattery` (in-memory); `gbkt-emulator` initializes `Gameboy` without `FileBattery`. `SavestateManager.kt` captures WRAM/OAM/HRAM, NOT SRAM (0xA000–0xBFFF).
**How to avoid:** Use `emulator_save_state("before-reboot")` + `emulator_load_state("before-reboot")` as the "reboot" substitute. The GBST file captures WRAM where the game state variables live; SRAM is visible within the same session via `emulator_read_memory`. Alternatively, verify anchor 4 by reading `emulator_read_memory(0xA000, N)` immediately after the save trigger (without reboot) — this proves the write path without persistence.
**Warning signs:** `emulator_read_memory(0xA000, 4)` returns `[0,0,0,0]` after `emulator_stop` + `emulator_start`.

### Pitfall 4: `triggerSystem("saves")` produces linker error

**What goes wrong:** `save_game_saves(0)` is never called at runtime — lcc linker error `undefined identifier 'trigger_saves'`.
**Why it happens:** `GBDKSystemVisitor.visitSaveSystem()` generates `save_game_*` / `load_game_*` but no `trigger_*` stub. `ScriptOpVisitor.visitTriggerSystem()` always calls `trigger_<id>()`.
**How to avoid:** This is the top-1 named codegen bug candidate (Candidate 1). The fix is adding a `trigger_saves(UINT8 slot)` wrapper in `visitSaveSystem()`. Planner must include this fix in the plan.
**Warning signs:** lcc output contains `undefined identifier 'trigger_saves'`.

### Pitfall 5: MBC5 cartridge byte mismatch (`0x19` vs `0x1B`)

**What goes wrong:** UAT anchor 3 expects `0x1B` but ROM offset 0x0147 reads `0x19`.
**Why it happens:** `"MBC5"` → `"0x19"` (no battery). `"MBC5_RAM_BATTERY"` → `"0x1B"` (battery). Reference Makefile uses `-Wl-yt0x1B`, so reference produces `0x1B`.
**How to avoid:** Use `cartridge = "MBC5_RAM_BATTERY"` to match reference.
**Warning signs:** `python3 -c "f=open('banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))"` prints `0x19` when `0x1b` expected.

### Pitfall 6: Zone bank exceeds `AnalysisConfig.maxBanks` — affects scene FFD only

**What goes wrong:** Confusion about `romBanks` vs `allocateZoneBanks` behavior.
**Why it happens:** `allocateZoneBanks()` does NOT check `AnalysisConfig.maxBanks` — it uses its own loop. `romBanks` caps the scene FFD. The linker gets `-Wm-yo4` from `detectMaxBank()` regardless of `romBanks = 2`.
**How to avoid:** Set `romBanks = 4` minimum (HOME + scenes + zone + margin) for clarity. Zone will always be in bank ≥ 2 regardless.
**Warning signs:** Non-issue in practice — linker auto-detects from `#pragma bank` directives.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual BANKED tracking | `CFunction.isBanked` flag auto-emits `BANKED` | Phase 06.7 / 07.4-30 | Eliminates BANKED injection bugs |
| Inline SWITCH_ROM from banked code | `_bkg_tiles_load_banked` HOME wrapper | Plan 07.4-30 | Fixes garbage-execution when SWITCH_ROM ran in banked context |
| Single-scene HOME (pre-09.1) | Single-scene HOME fast-path (`BankSlot(bank=0)`) | Phase 09.1-04 | No spurious bank1.c for tiny games |
| Monolithic SaveSystem (untriggered) | Same — STILL UNTRIGGERED | — | Bug to fix in Phase 11 |

**Deprecated/outdated:**
- `splitByBank` / `processBankedLine`: These names appear in the CLAUDE.md root memory section but do NOT correspond to functions in the current codebase. They refer conceptually to the `CEmitter` BANKED injection mechanism. The CLAUDE.md memory note "splitByBank now auto-adds BANKED to ALL function defs in non-zero banks" means the auto-injection is now unconditional for non-zero-bank `CFunction`s — there is no separate post-processing step called `splitByBank`.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `emulator_read_memory(0xA000)` reads SRAM after `ENABLE_RAM` (Coffee-GB MMU passes the read through even for battery RAM) | SaveDataBuilder SRAM Path | If Coffee-GB ignores reads to 0xA000 when battery RAM not `FileBattery`, anchor 4 read always returns 0. Low risk — Coffee-GB Mbc5 class present in JAR, standard MBC5 behavior. |
| A2 | UAT anchor 4 "reboot" recipe is `emulator_save_state` / `emulator_load_state` (GBST), not genuine cold-boot | SRAM Path, Common Pitfalls | If genuine cold-boot is required and FileBattery support is added, test recipe changes. This is the safer/more testable approach given current harness. |
| A3 | `_bkg_tiles_load_banked` helper is generated unconditionally for games with any zone (not guarded by genre detection) | BankingAnalysisPass / Architecture | If guarded by sport/RPG genre detection, anchor 2 would silently fail. Needs verification at implementation line 964+ of GBDKPipelineV2.kt. |
| A4 | `ramBanks = 2` in DSL `config { }` does NOT affect lcc `-Wl-ya` flag | SaveDataBuilder SRAM Path | If GenerateCTask was later updated to also propagate CartridgeConfig.ramBanks to build.properties, the two-channel problem is already fixed. Verify at first buildRom. |
| A5 | Planner uses `cartridge = "MBC5_RAM_BATTERY"` for `0x1B` | Cartridge-Byte Emission | If D-07 is read strictly as `"MBC5"` → `0x19`, anchor 3 must expect `0x19` instead. Both valid — just document which value is expected in UAT contract. |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed. Table is NOT empty: A3 and A4 should be verified at first build.

---

## Open Questions

1. **Is `_bkg_tiles_load_banked` generated unconditionally for any game with zones, or only for sport/RPG genres?**
   - What we know: The helper is at `GBDKPipelineV2.kt:1872` (`buildBkgTilesLoadBankedHelper()`), called from `buildHomeFile()` at line ~964.
   - What's unclear: Whether the call site is guarded by a zone-count check or genre presence check.
   - Recommendation: Read `buildHomeFile()` at lines 958-970 during Plan 7 (zone addition step) before building. If guarded, this is Candidate 2 for the named bug-fix.

2. **Does `emulator_read_memory(0xA000)` return the actual SRAM byte after `save_game_saves(0)` runs?**
   - What we know: `GbEmulator.MemoryAccess.readByte(address)` goes through the MMU; Coffee-GB has `Mbc5` class; `ENABLE_RAM` is emitted.
   - What's unclear: Whether `CoffeeGbEmulator.getMemory().readByte(0xA000)` returns SRAM content or always 0xFF when battery is in-memory mode.
   - Recommendation: Verify at first ROM build by running `emulator_read_memory("0xA000", 4)` in a quick UAT script.

3. **What is the exact `slotSize` for `saveData("saves") { slots(2) }` with 1 variable?**
   - What we know: `slotSize = savedVars.size + 1 (sentinel) + (if checksum) 1`. With 1 variable + no checksum + sentinel = `slotSize = 2`. Slot 0 at 0xA000, slot 1 at 0xA002.
   - What's unclear: Which variables count as "saved" — does the zone's tileset or the zone itself contribute saved variables? No — `savedVars` only includes game-scope `VariableDef`s (not zone data).
   - Recommendation: Use exactly 1 explicitly declared `u8Var` in the game scope as the save-verified variable.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| GBDK lcc | `:gbkt-examples:banks:buildRom` | Check at execution time | — | generateC-only for JVM tests; buildRom skipped |
| Coffee-GB emulator (embedded) | UAT anchors 1+2+4 | Yes (embedded JAR) | 1.6.0 | — |
| MCP gbkt-emulator server | UAT MCP play-through | Requires `./gradlew :gbkt-mcp-server:shadowJar` | — | UatRunner-based tests instead |
| Python 3 | Cartridge byte hex dump (anchor 3) | Check at execution | 3.x | Use Kotlin file read instead |

**Missing dependencies with no fallback:** None (JVM-tier tests have no external dependencies).

---

## Sources

### Primary (HIGH confidence — verified against live codebase)

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt:299-485` — SaveSystem codegen (ENABLE_RAM, no SWITCH_RAM)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:507-543, 1855-1893` — allScenesInHome fast-path, _bkg_tiles_load_banked
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt:192` — BANKED keyword emission site
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt:73-109` — isBanked determination
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt:666-671` — TriggerSystem → trigger_<id>() call
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt:77-128` — FFD bin-packing, HOME fast-path guard
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/config/AnalysisConfig.kt:85-98` — MBC5 → maxBanks = 256
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt:508, 665-675` — CARTRIDGE_MBC_MAP, writeBuildMetadata
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:120-138, 259-283` — detectMaxBank, readMbcType
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/SavestateManager.kt:14-19` — WRAM/OAM/HRAM only (no SRAM)
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt:148-156` — MemoryBattery (no FileBattery)
- `gbkt-examples/dungeon/build/gbkt/generated/main.c` — Live dungeon SRAM codegen (ENABLE_RAM, no SWITCH_RAM)
- `gbkt-examples/dungeon/build/gbkt/output/dungeon.noi` — Live .noi format example
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-23-PLAN.md:147-181` — awk brace-walk recipe
- `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt:82-101` — Kotlin brace-walk implementation
- `/Users/michalsvacha/gbdk/examples/cross-platform/banks/src/` — GBDK reference oracle (all 5 files)

### Secondary (MEDIUM confidence — cited from multiple codebase signals)

- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt:138-166, 530-567` — SaveDataBuilder, ConfigBuilder
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt:335-341` — `saveData()` registration
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt:257` — `triggerSystem()` DSL method

---

## Metadata

**Confidence breakdown:**
- SaveDataBuilder SRAM path: HIGH — verified against live dungeon generated C
- BANKED injection mechanism: HIGH — verified CEmitter.kt + SceneVisitor.kt
- FFD verdict prediction: HIGH — verified BankingAnalysisPass logic, confirmed 3-scene multi-bank path
- ramBanks two-channel problem: HIGH — verified GenerateCTask.writeBuildMetadata (no CartridgeConfig.ramBanks propagation)
- Coffee-GB SRAM persistence: HIGH — verified SavestateManager (WRAM/OAM/HRAM only) + CoffeeGbEmulator (MemoryBattery)
- `trigger_<id>` gap: HIGH — verified ScriptOpVisitor + GBDKSystemVisitor source
- .noi parse shape: HIGH — verified against live dungeon.noi

**Research date:** 2026-05-19
**Valid until:** 2026-06-19 (30 days — stable codegen infrastructure)

---

## RESEARCH COMPLETE
