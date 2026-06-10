---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 03
type: execute
wave: 0
depends_on: []
files_modified:
  - gbkt-examples/banks/PLAYBOOK.md
autonomous: true
requirements:
  - BANK-PLAYBOOK   # MCP playbook required for /gbkt-play-game banks and UAT anchors (CONTEXT D-11, RESEARCH §Tier-3)
user_setup: []
must_haves:
  truths:
    - "MCP agent can boot the banks game using the playbook"
    - "All 4 anchors have keyed MCP input scripts"
    - "Controls table is unambiguous (Start vs Select; per-scene)"
  artifacts:
    - path: "gbkt-examples/banks/PLAYBOOK.md"
      provides: "MCP agent playbook covering boot, scene flow, controls, anchor scripts"
      contains: "## MCP Input Scripts"
  key_links:
    - from: "PLAYBOOK.md anchor scripts"
      to: "11-UAT.md anchor names"
      via: "matching anchor IDs"
      pattern: "anchor[1-4]"
---

<objective>
Create the MCP agent playbook for `gbkt-examples/banks/` so `/gbkt-play-game banks` and `/gbkt-test-game banks` can run.

Purpose: The MCP server (`gbkt-emulator_get_playbook`) returns this file's contents to the agent. Without it, an agent driving the banks ROM is blind to the scene graph, control mapping, and the save trigger.

Output: `gbkt-examples/banks/PLAYBOOK.md` modeled on `gbkt-examples/simple-physics/PLAYBOOK.md`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/simple-physics/PLAYBOOK.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Author PLAYBOOK.md for banks example</name>
  <read_first>
    - gbkt-examples/simple-physics/PLAYBOOK.md (full file — analog structure to copy)
    - 11-PATTERNS.md §"gbkt-examples/banks/PLAYBOOK.md" (lines 391–415 — what to change)
    - 11-RESEARCH.md §"Validation Architecture" Tier-3 (MCP sequence per anchor — the same scripts go in PLAYBOOK)
    - 11-RESEARCH.md §"DSL Pattern (banks.kt skeleton)" — confirms scene names: title, play, pause
    - 11-CONTEXT.md D-claude-2 (scene names locked as title/play/pause via this plan's discretion)
  </read_first>
  <files>gbkt-examples/banks/PLAYBOOK.md</files>
  <action>
    Copy structure from `gbkt-examples/simple-physics/PLAYBOOK.md`. Replace contents per 11-PATTERNS.md §PLAYBOOK.md.

    Required sections (in order):

    1. `# Banks`
    2. `## Overview` — one paragraph: "Multi-bank ROM banking demonstration. 3 scenes (title, play, pause) over a multi-bank ROM. Exercises: cross-bank scene navigation (HOME→bank-1 BANKED trampoline), banked zone tilemap load (SWITCH_ROM-from-HOME wrapper), MBC5+RAM+BATT cartridge byte, SRAM save slot via SaveDataBuilder."
    3. `## How to Play` — bullet list: boot, press Start on title to enter play scene, press Select in play to trigger save, press Start in play to enter pause, press Start in pause to return to play.
    4. `## Controls` — markdown table with columns `| Scene | Button | Effect |`, exactly 4 rows:
       - `| title | START | Navigate to play scene (cross-bank trampoline: anchor 1) |`
       - `| play | SELECT | Trigger save slot 0 (SRAM write: anchor 4) |`
       - `| play | START | Navigate to pause scene |`
       - `| pause | START | Navigate back to play scene |`
    5. `## Scene Flow` — text description: `title → play → pause → play (loop)`. No gameover.
    6. `## Win / Lose Conditions` — single line: "None — this is a codegen-exercise example, not a playable game. Success criterion is anchor evidence captured, not gameplay completion."
    7. `## Known Quirks` — bullet list:
       - "`triggerSystem(\"saves\")` requires the named codegen bug fix (Plan 11-10) — adds `trigger_saves()` stub in `GBDKSystemVisitor.visitSaveSystem()`. Without it, lcc reports `undefined identifier 'trigger_saves'`."
       - "SRAM persistence across GBST save_state/load_state round-trip ONLY — Coffee-GB uses `MemoryBattery` (in-memory); `emulator_stop` + `emulator_start` does NOT preserve SRAM. Per RESEARCH §Pitfall 3."
       - "MBC5 cartridge byte requires `cartridge = \"MBC5_RAM_BATTERY\"` in DSL config to get `0x1b` byte. `\"MBC5\"` alone maps to `0x19` (without battery)."
    8. `## Variables Reference` — markdown table `| Name | Type | Initial | Purpose |`, one row: `| saveFlag | UINT8 | 0 | Persisted via SaveDataBuilder slot 0 for SRAM round-trip verification (anchor 4) |`.
    9. `## MCP Input Scripts` — 4 fenced code blocks keyed by anchor ID:
       - `### Anchor 1 — Cross-bank scene navigation` (mcp script same as in 11-UAT.md anchor 1 block)
       - `### Anchor 2 — Cross-bank zone tilemap load` (same as anchor 2 mcp script)
       - `### Anchor 3 — MBC5 cartridge byte` (the shell `python3 -c "..."` ROM-byte read recipe — not an MCP script; mark with a comment "ROM-file read, no emulator session needed")
       - `### Anchor 4 — SRAM persistence via GBST round-trip` (mcp script with `emulator_save_state` + `emulator_load_state`)

    Copy mcp_script bodies from 11-PATTERNS.md §"4-anchor mcp_script skeleton" (lines 363–387 there).

    Do NOT include any platformer/RPG/exploration controls. The Controls table has exactly 4 rows.
  </action>
  <verify>
    <automated>test -f gbkt-examples/banks/PLAYBOOK.md && grep -c "^### Anchor [1-4]" gbkt-examples/banks/PLAYBOOK.md | grep -qE "^4$"</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/PLAYBOOK.md` exists
    - Exactly 4 occurrences of `### Anchor 1 —`, `### Anchor 2 —`, `### Anchor 3 —`, `### Anchor 4 —` (level-3 headings under `## MCP Input Scripts`)
    - File contains the literal string `| title | START | Navigate to play scene` (controls table row)
    - File contains the literal string `| play | SELECT | Trigger save slot 0` (anchor 4 trigger row)
    - File contains the literal string `trigger_saves` (Known Quirks bug-fix note)
    - File contains the literal string `MBC5_RAM_BATTERY`
    - File does NOT contain references to `gameover`, `battle`, or `dialog` scenes (banks has none)
  </acceptance_criteria>
  <done>MCP agent can boot the banks ROM with this playbook and reach all 4 anchors.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Playbook doc → MCP agent | Agent treats playbook as truth for control mapping; mis-mapped buttons would yield false-positive UAT |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-05 | Tampering | Controls table | mitigate | Acceptance gate asserts literal table rows; cross-checked against Banks.kt frame handlers in Plan 11-05 |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs in this plan |
</threat_model>

<verification>
  - Acceptance grep gate passes (4 anchor headings; controls rows present; bug-fix note present).
  - Manual scan: no leftover simple-physics references (no mention of "physics", "ball", "spring").
</verification>

<success_criteria>
  - PLAYBOOK.md committed.
  - 4 anchor scripts keyed.
  - Controls table matches Banks.kt frame-handler intent (will be verified again in Plan 11-05 acceptance).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-03-SUMMARY.md` listing: 1 file created, line count, 4-anchor heading grep output.
</output>
