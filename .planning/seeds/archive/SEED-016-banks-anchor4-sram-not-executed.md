# SEED-016: Phase 11 Anchor 4 (SRAM GBST round-trip) — not executed

> **Triage:** VERIFIED-ALREADY-FIXED — [TRIAGE.md#SEED-016](.planning/phases/16-seed-triage/TRIAGE.md#SEED-016) · 2026-06-12

**Surfaced by:** Phase 11 (banks port close — Plan 11-14, by way of Plan 11-12 skip)
**Evidence:** `gbkt-examples/banks/11-UAT.md` §"Phase 11 UAT Outcome" line "Anchor 4 (SRAM persistence): Not executed — Plan 11-12 skipped pending Phase 11.1 resolution"
**Symptom:** Phase 11's 4-anchor UAT contract has Anchor 4 (SRAM save persistence via GBST `save_state`/`load_state` round-trip) pending. The mechanism path (SaveDataBuilder → `save_game_<id>` → `ENABLE_RAM` + `SWITCH_RAM(N)` → SRAM write) is provably emitted in `bank1.c` (INV-4 GREEN locks the codegen contract) AND the named-bug fix in Plan 11-10 added the `trigger_<id>` trampoline so `play_frame` can fire the write. The SRAM write path does NOT visually depend on the play scene rendering, so a UAT could in theory verify it without screenshots — but Plan 11-12 was orchestrator-skipped because it queues behind 11-11 (which RED'd on visual evidence) on the same `BanksUatTest` source file.
**Hypothesis:** Three possible outcomes when Anchor 4 is finally exercised:
1. **GREEN as-is.** The `trigger_saves()` trampoline + `save_game_saves()` + `ENABLE_RAM` + `SWITCH_RAM(0)` + byte write are all in `bank1.c` per INV-4. GBST `emulator_save_state` should capture the SRAM region. Phase 11.1 just adds the @Test method and re-runs.
2. **GBST does NOT capture SRAM region.** Per RESEARCH §Pitfall 3, Coffee-GB `MemoryBattery` is in-memory; `SavestateManager` historically captures WRAM/OAM/HRAM but the SRAM (0xA000–0xBFFF) capture has not been runtime-verified for banks. If GBST round-trip drops SRAM, that's a separate emulator/MCP defect — not a banks-port codegen issue.
3. **SaveDataBuilder write path doesn't actually run.** Even with INV-4 GREEN at codegen time, the Select button path may not reach `save_game_saves()` at runtime — could be a frame-input bug, a sym-resolution issue, or a banked-call-from-banked-context fault that the bank trampoline doesn't fix.

**Blast radius:** **UNKNOWN** until executed. Worst case (outcome 2) involves `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/.../SavestateManager.kt` (capture SRAM region in GBST). Outcome 3 lives in pipeline / bank routing. Outcome 1 is just a doc-update.
**Routing:** **Phase 11.1** (terminal closer subphase). Bundle with SEED-014 + SEED-015 because Phase 11.1 will re-run UAT against banks anyway once SEED-014 unblocks visual evidence — and Anchor 4 can ride on the same UAT @Test sweep at zero additional cost.

**Files in play:**
- `gbkt-examples/banks/src/test/kotlin/.../BanksUatTest.kt` — add @Test for Anchor 4
- `gbkt-examples/banks/11-UAT.md` Anchor 4 mcp_script block — already specifies the recipe
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt` — output artifact path is fixed
- `gbkt-emulator/.../SavestateManager.kt` — verify SRAM 0xA000–0xBFFF is in capture range
- `gbkt-backend-gbdk/.../GBDKSystemVisitor.kt:visitSaveSystem` — confirm INV-4 emission shape is what runs

**Workaround in Phase 11 banks port:** None — Anchor 4 is the only deferred anchor; ship Phase 11 with 3/4 anchors verified (3 GREEN, 1 deferred) and 2/4 visual anchors RED routed to SEED-014.
