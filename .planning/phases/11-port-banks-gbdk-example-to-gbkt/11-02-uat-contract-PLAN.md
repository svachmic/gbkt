---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 02
type: execute
wave: 0
depends_on: []
files_modified:
  - gbkt-examples/banks/11-UAT.md
autonomous: true
requirements:
  - BANK-01   # UAT anchor 1 (cross-bank scene nav HOME→bank trampoline) — per CONTEXT D-08(1)
  - BANK-02   # UAT anchor 2 (cross-bank zone tilemap load) — per CONTEXT D-08(2)
  - BANK-03   # UAT anchor 3 (MBC5 cartridge byte 0x0147) — per CONTEXT D-08(3)
  - BANK-04   # UAT anchor 4 (SRAM save persistence via GBST round-trip) — per CONTEXT D-08(4)
user_setup: []
must_haves:
  truths:
    - "`11-UAT.md` documents 4 anchor behaviors with screenshot evidence reserved for anchors 1+2 and variable evidence for anchors 3+4"
    - "Visual Evidence Rule is quoted verbatim from CLAUDE.md"
    - "MCP scripts include the GBST save-state/load-state pattern for anchor 4 (per RESEARCH §Pitfall 3)"
  artifacts:
    - path: "gbkt-examples/banks/11-UAT.md"
      provides: "UAT contract doc, 4 anchors, evidence paths reserved"
      contains: "anchor1-play-scene.png"
  key_links:
    - from: "11-UAT.md anchor 4 mcp_script"
      to: "RESEARCH §Pitfall 3 (SRAM persistence via emulator_save_state/load_state)"
      via: "explicit comment in script body"
      pattern: "emulator_save_state\\(.*anchor4-pre-reboot"
---

<objective>
Lock the 4-anchor UAT contract BEFORE any DSL is written (per CONTEXT D-11). This file is the binding evidence-shape contract that Plans 11-11/12/13 must satisfy.

Purpose: Make the verification target concrete — every later plan checks its row against this contract. The 4-anchor cap is a ONE-TIME EXCEPTION per D-09; no 5th anchor.

Output: `gbkt-examples/banks/11-UAT.md` with 4 named anchors, reserved screenshot paths, MCP script skeletons, and a verbatim quote of the Visual Evidence Rule from CLAUDE.md.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md
@CLAUDE.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Author 11-UAT.md with 4 anchor behaviors</name>
  <read_first>
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md (lines 1–33 for the frontmatter + Visual Evidence Rule quote pattern; entire file for the Tests section shape)
    - 11-PATTERNS.md §"gbkt-examples/banks/11-UAT.md" (lines 322–388 — full 4-anchor mcp_script skeleton)
    - 11-CONTEXT.md §"Decisions" D-08 through D-11 (anchor IDs, cap, visual vs mechanism split)
    - 11-RESEARCH.md §"Validation Architecture" §Tier-3 table (MCP sequence per anchor)
    - 11-RESEARCH.md §Pitfall 3 (SRAM persistence — anchor 4 MUST use GBST save_state, NOT emulator_stop/start)
    - CLAUDE.md §"Verification Methodology — Visual Evidence Rule" (quote it verbatim in the doc)
  </read_first>
  <files>gbkt-examples/banks/11-UAT.md</files>
  <action>
    Write `gbkt-examples/banks/11-UAT.md`. Structure (mirrors `10-UAT.md`):

    1. **Frontmatter** (YAML block):
       - `status: draft`
       - `phase: 11-port-banks-gbdk-example-to-gbkt`
       - `source: [11-CONTEXT.md, 11-RESEARCH.md, 11-PATTERNS.md]`
       - `started: 2026-05-19`
       - `updated: 2026-05-19`

    2. **`## Visual Evidence Rule`** section — quote verbatim from CLAUDE.md §"Verification Methodology — Visual Evidence Rule" (the paragraph starting "For verification truths shaped..."). Add note: "Anchors 1+2 are visual truths; anchors 3+4 are mechanism truths and need variable/file evidence only."

    3. **`## Tests`** section — 4 numbered subsections, ONE per anchor. Each subsection has these exact fields (markdown headings or labeled lines):
       - `### Anchor N: <name>` (e.g., `### Anchor 1: Cross-bank scene navigation (HOME→bank-1 BANKED trampoline)`)
       - `**Behavior:** <one sentence describing the truth being tested>`
       - `**Evidence type:** screenshot` (anchors 1+2) OR `**Evidence type:** variable/file` (anchors 3+4)
       - `**Evidence path:** evidence/uat-screenshots/<slug>.png` (anchors 1+2) OR `**Evidence path:** evidence/<slug>.txt` (anchors 3+4)
       - ```` ```mcp_script ```` fenced block with the MCP sequence (use the 4-anchor skeleton from 11-PATTERNS.md §11-UAT.md, lines 362–387)
       - `**Expected:** <concrete success criterion>` (e.g., for anchor 3: `python3 read of ROM offset 0x0147 prints 0x1b`)
       - `**Result:** pending`

    4. **Anchor-specific contents:**
       - **Anchor 1** — name "Cross-bank scene navigation", evidence_path `evidence/uat-screenshots/anchor1-play-scene.png`, expected: "After Start press on title scene, scene transitions to `play` without MBC5 trap; play scene visible in screenshot."
       - **Anchor 2** — name "Cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper)", evidence_path `evidence/uat-screenshots/anchor2-tilemap.png`, expected: "Within play scene, banked zone tilemap visible (checker pattern); proves `_bkg_tiles_load_banked` HOME wrapper fired."
       - **Anchor 3** — name "MBC5 cartridge byte at ROM offset 0x0147", evidence_path `evidence/anchor3-cartridge-byte.txt`, expected: "`python3 -c \"f=open('build/gbkt/output/banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))\"` prints `0x1b` (MBC5+RAM+BATT — matches reference `-Wl-yt0x1B`). Per RESEARCH §"Cartridge-Byte Emission", DSL must use `cartridge = \"MBC5_RAM_BATTERY\"` (NOT `\"MBC5\"`) to get `0x1b`."
       - **Anchor 4** — name "SRAM save persistence via GBST round-trip", evidence_path `evidence/anchor4-sram-persistence.txt`, expected: "After Select press (save trigger), `emulator_read_memory(0xA000, 4)` returns 4 bytes; after `emulator_save_state` + `emulator_load_state` round-trip, `emulator_read_memory(0xA000, 4)` returns the SAME 4 bytes. Per RESEARCH §Pitfall 3: use GBST round-trip, NOT `emulator_stop` + `emulator_start` (Coffee-GB MemoryBattery does not persist SRAM)."

    5. **`## Anti-overfitting note`** (final section, one paragraph): reference D-overfitting-1/2/3 from CONTEXT.md — UAT verifies BANKED contract, not GBDK reference text-rendering shape. No DSL features added to make screenshots pretty.

    Do NOT include any `actual:` / `evidence:` lines yet — those are filled in by Plans 11-11/12/13 after the UAT runs. The contract is the *expected* state.
  </action>
  <verify>
    <automated>test -f gbkt-examples/banks/11-UAT.md && grep -c "Anchor [1-4]:" gbkt-examples/banks/11-UAT.md | grep -qE "^4$"</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/11-UAT.md` exists
    - Exactly 4 occurrences of `Anchor 1:`, `Anchor 2:`, `Anchor 3:`, `Anchor 4:` headings (one each)
    - File contains the literal strings `evidence/uat-screenshots/anchor1-play-scene.png` and `evidence/uat-screenshots/anchor2-tilemap.png`
    - File contains the literal string `emulator_save_state(` AND `emulator_load_state(` (anchor 4 GBST round-trip)
    - File contains the literal string `MBC5_RAM_BATTERY` AND `0x1b` (or `0x1B`)
    - File contains the literal string `Visual Evidence Rule` (CLAUDE.md quote present)
    - File does NOT contain `emulator_stop()` followed by `emulator_start()` as the anchor 4 mechanism (RESEARCH §Pitfall 3 violation)
  </acceptance_criteria>
  <done>11-UAT.md committed as the binding contract; later UAT plans (11-11/12/13) reference its anchor names verbatim.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Doc → planner agents | UAT.md feeds later plan generation; tampering would mis-direct verification |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-03 | Tampering | UAT contract content | mitigate | Verify acceptance_criteria grep matches before commit; `git diff` review before push |
| T-11-04 | Repudiation | Anchor 4 evidence shape mismatch with harness | mitigate | Explicit RESEARCH §Pitfall 3 citation in doc — agents reading 11-UAT.md cannot ambiguously interpret "reboot" |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs in this plan (markdown only) |
</threat_model>

<verification>
  - Acceptance grep gate passes (4 anchor headings, GBST mechanism, MBC5_RAM_BATTERY, no emulator_stop/start as anchor-4 mechanism).
  - Manual scan: every anchor has all 5 labeled fields (Behavior / Evidence type / Evidence path / mcp_script / Expected / Result).
</verification>

<success_criteria>
  - 11-UAT.md exists with 4 anchors, evidence paths reserved, MCP scripts skeleton-complete.
  - Visual Evidence Rule quoted from CLAUDE.md.
  - GBST round-trip is the only anchor-4 mechanism named.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-02-SUMMARY.md` listing: 1 file created, 4 anchors named, acceptance grep output.
</output>
