---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 05
type: execute
wave: 1
depends_on: ["11-01", "11-04"]
files_modified:
  - gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt
autonomous: true
requirements:
  - BANK-DSL-SCENES   # 3 scenes title/play/pause (D-01, D-03, D-claude-2)
  - BANK-DSL-ZONE     # 1 banked zone (D-05)
  - BANK-DSL-SAVE     # 1 SaveDataBuilder slot (D-06)
  - BANK-DSL-CART     # MBC5_RAM_BATTERY config (D-07 + RESEARCH §Cartridge-Byte Emission)
user_setup: []
must_haves:
  truths:
    - "`./gradlew :gbkt-examples:banks:generateC` exits 0 (DSL compiles + IR builds + C emits)"
    - "Generated `bank1.c` exists and declares `play_enter`, `play_frame`, `play_exit` with the `BANKED` keyword"
    - "Generated `main.c` declares `save_game_saves` (and after Plan 11-10, `trigger_saves`)"
    - "Cartridge config = `MBC5_RAM_BATTERY` (NOT `\"MBC5\"`) so cartridge byte resolves to 0x1b per RESEARCH"
  artifacts:
    - path: "gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt"
      provides: "Idiomatic gbkt DSL: 3 scenes, 1 zone, 1 saveData slot, MBC5_RAM_BATTERY"
      contains: "saveData(\"saves\")"
    - path: "gbkt-examples/banks/build/gbkt/generated/main.c"
      provides: "Generated HOME-bank C source (created by generateC)"
      contains: "save_game_saves"
    - path: "gbkt-examples/banks/build/gbkt/generated/bank1.c"
      provides: "Generated bank-1 C source with BANKED scene functions"
      contains: "void play_enter(void) BANKED"
  key_links:
    - from: "Banks.kt config { cartridge = \"MBC5_RAM_BATTERY\" }"
      to: "gbkt-build.properties mbcType=0x1B"
      via: "GenerateCTask.writeBuildMetadata via CARTRIDGE_MBC_MAP"
      pattern: "cartridge\\s*=\\s*\"MBC5_RAM_BATTERY\""
    - from: "Banks.kt saveData(\"saves\") { slots(2) }"
      to: "GBDKSystemVisitor.visitSaveSystem → save_game_saves in main.c"
      via: "SystemIR.SaveSystem → CFunction"
      pattern: "saveData\\(\"saves\"\\)"
    - from: "Banks.kt zone(\"play_zone\") { tileset(asset(\"tiles/checker.png\")) }"
      to: "allocateZoneBanks → bank 2 const arrays + _bkg_tiles_load_banked wrapper"
      via: "GBDKPipelineV2.allocateZoneBanks + buildBkgTilesLoadBankedHelper"
      pattern: "zone\\(\"play_zone\"\\)"
---

<objective>
Write the gbkt DSL for the banks port: 3 scenes (title/play/pause), 1 banked zone, 1 SaveDataBuilder slot, MBC5_RAM_BATTERY cartridge with romBanks=4, ramBanks=2. This is the substrate that ALL later plans verify against.

Purpose: Anti-overfitting D-overfitting-1/2/3 inherited from Phase 9/10 — the DSL produces the right codegen surface declaratively, NOT by exposing manual banking (REQUIREMENTS.md forbidden). Reference's BANKED/SWITCH_RAM/bo<N> shapes are codegen ORACLES, not DSL targets.

Output: `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` overwrites the Plan 11-01 placeholder with the full DSL. After this plan, `:gbkt-examples:banks:generateC` MUST succeed; `:buildRom` is the smoke test for Plan 11-09.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/dungeon/src/main/kotlin/io/github/gbkt/examples/dungeon/Dungeon.kt
@gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Replace Banks.kt placeholder with full DSL</name>
  <files>gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt</files>
  <read_first>
    - gbkt-examples/dungeon/src/main/kotlin/io/github/gbkt/examples/dungeon/Dungeon.kt lines 35–145 (config + zone + saveData + scene shape — closest analog per 11-PATTERNS.md)
    - 11-PATTERNS.md §"gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt" (lines 84–146 — full DSL skeleton with annotations)
    - 11-RESEARCH.md §"Cartridge-Byte Emission" (lines 340–370 — `"MBC5_RAM_BATTERY"` → 0x1b; `"MBC5"` alone → 0x19)
    - 11-RESEARCH.md §"BankingAnalysisPass on Small Games" (lines 236–264 — romBanks=4 minimum)
    - 11-RESEARCH.md §"SaveDataBuilder SRAM Path" (lines 154–189 — slotSize math; 1 variable + sentinel = 2 bytes per slot)
    - 11-RESEARCH.md §"DSL Pattern (banks.kt skeleton)" (lines 575–622 — reference skeleton)
    - 11-RESEARCH.md §Pitfall 4 (triggerSystem("saves") needs Plan 11-10 fix — DOCUMENT this in a comment in Banks.kt)
    - 11-CONTEXT.md D-01, D-03, D-05, D-06, D-07, D-claude-2, D-claude-3
  </read_first>
  <behavior>
    After Plan 11-05, `banks.build()` produces a GameIR with:
    - 3 scenes named `title`, `play`, `pause`
    - `startScene == "title"`
    - exactly 1 zone with id `play_zone`
    - exactly 1 SaveSystem in `ir.systems`
    - exactly 1 variable `saveFlag` of type `VarType.U8`
    - `ir.cartridgeConfig.cartridge == "MBC5_RAM_BATTERY"`
    - `ir.cartridgeConfig.romBanks == 4`
    - `ir.cartridgeConfig.ramBanks == 2`

    Behavior verified by Plan 11-06 (BanksIRTest).
  </behavior>
  <action>
    OVERWRITE `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt`. Replace the Plan 11-01 stub with the full DSL.

    File contents (in order):
    1. MPL 2.0 header (verbatim from 11-PATTERNS.md §"MPL 2.0 File Header").
    2. Top-level KDoc comment: "Banks — GBDK banks reference port. Demonstrates: multi-bank ROM (MBC5_RAM_BATTERY), BANKED calling convention via cross-bank scene navigation, banked zone tilemap load via SWITCH_ROM-from-HOME wrapper, SRAM persistence via SaveDataBuilder. See `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md` for full design rationale (decisions D-01 through D-20)."
    3. `package io.github.gbkt.examples.banks`
    4. `import io.github.gbkt.core.dsl.*`
    5. `val banks = game("Banks") { ... }` with the body containing, in this order:

       a. **config block** — `config { cartridge = "MBC5_RAM_BATTERY"; romBanks = 4; ramBanks = 2 }`. Per RESEARCH §Cartridge-Byte Emission, `"MBC5_RAM_BATTERY"` (not `"MBC5"`) maps to the 0x1b cartridge byte. Per RESEARCH §BankingAnalysisPass on Small Games, `romBanks = 4` gives HOME(0) + scenes(1) + zone(2) + margin(3). Add an inline comment citing both research sections.

       b. **State variable** — `var saveFlag by u8Var(0)`. Comment: "// Non-transient u8; written by SaveDataBuilder into SRAM slot offset 0 per RESEARCH §SaveDataBuilder SRAM Path."

       c. **Save system** — `saveData("saves") { slots(2) }`. Comment: "// 2 slots × (1 byte saveFlag + 1 sentinel byte) = 4 bytes total SRAM footprint." Add a second comment: "// WARNING: triggerSystem(\"saves\") in the play frame below requires the codegen fix in Plan 11-10 (adds `trigger_saves` stub in GBDKSystemVisitor.visitSaveSystem). Until then, generateC succeeds but the linker reports `undefined identifier 'trigger_saves'` — that's the named bug per RESEARCH §Pitfall 4."

       d. **Zone** — declared OUTSIDE the play scene as `val playZone = zone("play_zone") { tileset(asset("tiles/checker.png")) }`. (If `zone(...)` requires being inside a scene per gbkt-engine surface, place it inside `play` scene's `enter { }`; verify by reading the dungeon analog at `Dungeon.kt:85-94` — the dungeon places zone declarations at the game scope level, so do the same here.) Comment: "// allocateZoneBanks places zone tilemap const arrays in bank 2 per RESEARCH §BankingAnalysisPass."

       e. **Pause scene** (declared first because forward reference): `val pauseScene = scene("pause") { enter { clear() }; frame { whenever(buttons.start.pressed) { navigate("play") } } }`.

       f. **Play scene**: `scene("play") { enter { showSprites() }; frame { whenever(buttons.select.pressed) { triggerSystem("saves") }; whenever(buttons.start.pressed) { navigate(pauseScene) } } }`. The `triggerSystem("saves")` line is the anchor-4 trigger AND the named-bug trigger.

       g. **Title scene**: `scene("title") { enter { clear() }; frame { whenever(buttons.start.pressed) { navigate("play") } } }`.

       h. `start = "title"` at the end of the game block.

    Do NOT add: actors, sound effects, HUD, dialog, exploration system, flags, camera, or any RPG/platformer/genre constructs. Banks.kt uses only `io.github.gbkt.core.dsl.*` — no genre imports (per 11-PATTERNS.md "What to change" note).

    Do NOT add Phase 13 typed-Cartridge enum (per CONTEXT D-17 + D-claude-5 — magic string is correct for Phase 11).

    Do NOT add `bank(N) { }` / `bankedFunction(N) { }` / SRAM-bank assignment DSL — REQUIREMENTS.md forbids and CONTEXT.md §Out-of-scope rejects.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:generateC --quiet && test -f gbkt-examples/banks/build/gbkt/generated/main.c && test -f gbkt-examples/banks/build/gbkt/generated/bank1.c</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` contains literal `cartridge = "MBC5_RAM_BATTERY"`
    - File contains literal `romBanks = 4` AND `ramBanks = 2`
    - File contains literal `saveData("saves") { slots(2) }`
    - File contains literal `zone("play_zone")` and literal `tileset(asset("tiles/checker.png"))`
    - File contains exactly 3 `scene(` declarations (title, play, pause)
    - File contains `triggerSystem("saves")` exactly once
    - File contains `start = "title"` exactly once
    - File does NOT contain the literal `bank(` (no manual-banking DSL) — `grep -v '^[[:space:]]*//' gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt | grep -c "bank(" | grep -qE "^0$"` exits 0
    - File does NOT contain the literal `import io.github.gbkt.rpg` (no genre imports)
    - `./gradlew :gbkt-examples:banks:generateC --quiet` exits 0
    - Generated `main.c` contains literal `save_game_saves` (proves SaveDataBuilder emission ran)
    - Generated `bank1.c` contains literal `void play_enter(void) BANKED` (proves SceneVisitor + CEmitter BANKED injection ran)
  </acceptance_criteria>
  <done>generateC succeeds; main.c + bank1.c are emitted with the expected shape contracts.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| DSL source → IR → generated C | Banks.kt encodes the locked cartridge/SRAM config; an incorrect string (e.g., `"MBC5"`) silently breaks anchor 3 |
| Asset reference → pipeline | `asset("tiles/checker.png")` resolves relative to `gbkt { assets("res") }`; mis-typo breaks anchor 2 generateC |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-07 | Tampering | Cartridge magic-string | mitigate | Acceptance grep enforces `"MBC5_RAM_BATTERY"` literal; Plan 11-08 INV-3 re-verifies via gbkt-build.properties |
| T-11-08 | Information disclosure | Asset path | accept | No PII in asset; checker.png is public-domain checkerboard |
| T-11-09 | Denial of service | romBanks too low | mitigate | romBanks=4 documented per RESEARCH §BankingAnalysisPass; lower values would crash allocateZoneBanks |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs in this plan (Kotlin source only; no transitive dependencies introduced) |
</threat_model>

<verification>
  - generateC succeeds and emits both main.c and bank1.c.
  - `grep -c "void play_enter(void) BANKED" gbkt-examples/banks/build/gbkt/generated/bank1.c` ≥ 1.
  - `grep -c "save_game_saves" gbkt-examples/banks/build/gbkt/generated/main.c` ≥ 1.
  - Cross-check: `grep -c "trigger_saves" gbkt-examples/banks/build/gbkt/generated/main.c` is currently 0 (proves named bug from Plan 11-10 is still latent — fixed in 11-10).
</verification>

<success_criteria>
  - All `<acceptance_criteria>` grep gates and Gradle commands pass.
  - generateC produces the 4 expected codegen surfaces (BANKED scene functions, SaveDataBuilder save_game_saves, zone bank2 tilemap data, MBC5_RAM_BATTERY mbcType).
  - File does not exceed ~75 lines (sanity: matches Dungeon.kt's compactness ratio for non-RPG content).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-05-SUMMARY.md` listing: Banks.kt line count, scene/zone/system counts confirmed from `banks.build()`, generated file list (`main.c`, `bank1.c`, `game.h`, `gbkt-build.properties`, any `zone_bank*.c`).
</output>
