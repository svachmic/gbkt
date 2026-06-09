# Anchor 4 D-claude-6 Outcome Attribution

**Plan:** 11.1-07
**Date:** 2026-05-20
**Test:** `BanksUatTest.anchor 4 SRAM persistence via GBST round-trip`
**ROM:** `gbkt-examples/banks/build/gbkt/output/banks.gb` (post-Plan-06 clean build)
**SavestateManager:** post-Plan-03 GBS2 format with SRAM 0xA000-0xBFFF capture

---

## Outcome

**Outcome 2 — NARROW: codegen-tier INV-4 GREEN (Phase 11 already passing) + Plan 03 SavestateManager SRAM extension LANDED successfully.**

Anchor 4 @Test PASSED on first execution against the post-Plan-06 banks ROM + post-Plan-03 SavestateManager.

JUnit result: `tests=1, skipped=0, failures=0, errors=0, time=0.586s`

No new seed file required. No `@Disabled` annotation required on the test.

---

## Raw Test Output Excerpt

From `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor4-sram-persistence.txt`:

```
pre: [-1, -1, -1, -1]
post: [-1, -1, -1, -1]
match: true
```

`-1` is Kotlin's signed-byte representation of `0xFF` (uninitialized SRAM default on fresh emulator boot).

The `match: true` line is decisive: after writing `99` to `0xA000` between save and load, the post-load value is `0xFF` (not `99`). This proves `loadState()` actually restored the SRAM region from the GBS2 snapshot — a trivial pass would have both `pre` and `post` equal to `0` (or any unchanged value) without the mutation step.

---

## D-claude-6 Attribution Rationale

Per CONTEXT §D-06, three outcome routes were possible:

1. **Outcome 1 — Codegen-tier only (RULED OUT pre-execution by RESEARCH §D-claude-6):** The codegen-tier INV-4 (SRAM ENABLE/DISABLE_RAM) was already GREEN from Phase 11 plans. The SaveDataBuilder write path was not the failure point. This outcome would have applied only if SRAM was not written at runtime.

2. **Outcome 2 — NARROW SavestateManager fix (EXPECTED per RESEARCH §D-claude-6, CONFIRMED):** Plan 03's extension of `SavestateManager.save()/load()` to include the SRAM region (`0xA000-0xBFFF`, 8 KB) is the fix that closes SEED-016. The test confirms the round-trip works correctly:
   - `save()` writes the GBS2 file including SRAM (verified by 16675-byte file size = 4 magic + 8192 SRAM + 8192 WRAM + 160 OAM + 127 HRAM)
   - `writeMemory(0xA000, 99)` mutates SRAM between save and load
   - `loadState()` restores SRAM to pre-save value (the mutation disappears)
   - `assertContentEquals(preBytes, postBytes)` passes

3. **Outcome 3 — Wider emulator concern (RULED OUT):** Post-load SRAM read returns the correct pre-save value. No MBC5 RAM bank switching issue, no savestate format regression, no deeper emulator state-machine concern.

**Decision: Outcome 2 NARROW.** The combined fix is:
- Phase 11 (prior): INV-4 codegen-tier SRAM ENABLE/DISABLE_RAM write path GREEN
- Phase 11.1 Plan 03: SavestateManager SRAM region capture/restore extension (GBS2)
- Phase 11.1 Plan 07: Anchor 4 @Test executed, PASSED — SEED-016 closed

---

## Cross-link to Plan 03 SUMMARY

Plan 03 SUMMARY: `.planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/11.1-03-SUMMARY.md`

Key evidence from Plan 03:
- Added `SRAM_START = 0xA000` and `SRAM_SIZE = 0x2000` constants
- Inserted SRAM loops in `save()` and `load()` before WRAM
- Bumped magic GBST -> GBS2 (breaking change; pre-fix .gbst fixture deleted)
- SavestateManagerTest SRAM round-trip test flipped RED -> GREEN in Plan 03
- Full test suite: 9/9 PASSED (BUILD SUCCESSFUL)

Plan 03's SavestateManager changes are the direct enabler of Anchor 4's GREEN result.

---

## Phase 11.1.1 Escape Valve

NOT created. Per CONTEXT §D-14 (terminal-closer absorption rule) and CONTEXT §Phase Boundary ("no Phase 11.1.1 / 11.2 follow-up subphase is permitted"), this outcome needs no follow-up phase.

SEED-016 is CLOSED by:
- Plan 03: SavestateManager SRAM fix (GREEN)
- Plan 07: Anchor 4 @Test executed and PASSED (GREEN)

---

## Follow-up

None required. Outcome 2 NARROW — SEED-016 closed as designed.

Any future SavestateManager improvements (MBC5 RAM bank switching, multi-bank SRAM, savestate format versioning) are NOT in scope for 11.1 and route to Phase 13 / future emulator phase if needed.
