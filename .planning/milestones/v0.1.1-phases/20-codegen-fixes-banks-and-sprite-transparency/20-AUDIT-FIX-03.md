# 20-AUDIT-FIX-03 — FIX-03 Banks Trio Emission-Guard Audit

## Status

**Confirmation-only phase.** All 3 banks seeds (SEED-014/015/016) are already guarded
by named JVM emission assertions. Zero new guards were authored (D-03 no-duplicate-coverage
decision). This document maps each seed to its pre-existing guard and the reverted-fix
scenario it catches.

**Verification run result (2026-06-14):** BUILD SUCCESSFUL — 8 BanksEmissionTest tests
(1 expected skip) + 3 BanksUatTest tests, 0 failures, 0 errors. Confirmed GREEN at HEAD
(`chore/hardening_0_1_0`). D-02 ordering gate satisfied (Plan 01 ran before this audit).

Run commands (D-02 gate — executed in Plan 01):

```bash
# BanksEmissionTest INV-2 (SEED-014)
./gradlew :gbkt-examples:banks:test \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-2*"

# BanksEmissionTest INV-5 (SEED-015)
./gradlew :gbkt-examples:banks:test \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-5*"

# BanksEmissionTest INV-6 (SEED-014, companion)
./gradlew :gbkt-examples:banks:test \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-6*"

# Full BanksEmissionTest suite (all 8, confirms aggregate GREEN)
./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"

# BanksUatTest Anchor 4 (SEED-016) — requires GBDK_HOME and ROM build
./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom \
  :gbkt-examples:banks:test --tests "*.BanksUatTest"
```

Evidence files: `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-03/`

---

## 1:1 Seed → Guard Mapping

| SEED | Title | Guarding test file (module-relative path) | Assertion name(s) | Existing or newly authored | Reverted-fix scenario |
|------|-------|------------------------------------------|-------------------|----------------------------|-----------------------|
| SEED-014 | bkg_tiles_load_banked gating incomplete | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` | `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence` (line 200), `INV-6 play_enter in bank1 calls _bkg_tiles_load_banked for playZone` (line 463) | existing | Reverting `hasZoneSceneBinder` guard at `GBDKPipeline.kt:1428` to sport-racing-only causes INV-2 to fail: `_bkg_tiles_load_banked` wrapper absent from main.c (SWITCH_ROM sequence not emitted); INV-6 fails: play_enter has no zone-load call in bank1.c |
| SEED-015 | Banks trampoline body inheritance wrong | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` | `INV-5 title_enter_trampoline section comment retains title_enter name (SEED-015)` (line 408) | existing | Re-allowing comment-line rewrites in `FunctionDeduplicationPass` causes INV-5 to fail: comment preceding `title_enter_trampoline` reads "pause_enter" instead of "title_enter" — deduplication regex over-matches section header comments |
| SEED-016 | Banks Anchor 4 SRAM test not executed | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` | `anchor 4 SRAM persistence via GBST round-trip` (line 291) | existing | Removing the `trigger_saves` trampoline stub from `GBDKSystemVisitor.visitSaveSystem` causes Anchor 4 to fail at the SRAM round-trip mid-mutation sentinel (preBytes assertion does not match after reload) |

---

## Per-Seed Run Commands (Traceability)

```bash
# SEED-014 — gbkt-examples:banks (emission tests, full GBDK pipeline)
./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest" \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-2*"

# SEED-014 (companion guard INV-6) — same module
./gradlew :gbkt-examples:banks:test \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-6*"

# SEED-015 — gbkt-examples:banks (emission tests)
./gradlew :gbkt-examples:banks:test \
  --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-5*"

# SEED-016 — gbkt-examples:banks (UAT test — requires ROM build, GBDK_HOME)
# RESEARCH Pitfall 1: Anchor 4 is in BanksUatTest.kt which requires a real ROM.
# GBDK must be installed and GBDK_HOME set. Without GBDK, test auto-skips.
./gradlew :gbkt-examples:banks:buildRom \
  :gbkt-examples:banks:test --tests "*.BanksUatTest"
```

---

## Guard Details

### SEED-014 — bkg_tiles_load_banked gating incomplete

- **Fix location:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt:1428`
  (`hasZoneSceneBinder` guard that gates emission of `_bkg_tiles_load_banked` wrapper to any
  game with a zone-scene binder, not only sport-racing games)
- **Guard module:** `gbkt-examples:banks` (emission test exercises the full GBDK pipeline stack
  including `BankingAnalysisPass` and `GBDKPipeline`)
- **Two companion assertions:** INV-2 verifies the HOME-bank wrapper exists in main.c and contains
  the `SWITCH_ROM` sequence; INV-6 verifies `play_enter` in bank1.c calls `_bkg_tiles_load_banked(2u,...)`
  — bank arg 2 confirms playZone was allocated to bank 2 by `BankingAnalysisPass`
- **Scope-level grep gate (INV-6):** `extractFunctionBody(bank1C, "play_enter")` — asserts ONLY
  inside `play_enter`, not against other bank1.c functions
- **Test count:** 2 (INV-2 + INV-6) — both existing, no new guards

### SEED-015 — Banks trampoline body inheritance wrong

- **Fix location:** `gbkt-backend-gbdk/.../codegen/postprocess/FunctionDeduplicationPass.kt`
  — callsite-rewrite loop now skips comment lines; prevents regex from rewriting section header
  comments such as `// Trampoline: title_enter (bank 1)` to `// Trampoline: pause_enter (bank 1)`
- **Guard module:** `gbkt-examples:banks` (INV-5 requires the full `GBDKBackend` pipeline with
  `BankingAnalysisPass` + `FunctionDeduplicationPass` active — NOT raw `GBDKPipeline` alone)
- **Class header documents:** pre-fix over-match root cause (deduplication regex treated comment
  body as a callee name), fix strategy (skip lines starting with `//`)
- **Test count:** 1 (INV-5) — existing, no new guard

### SEED-016 — Banks Anchor 4 SRAM test not executed

- **Fix location:** `gbkt-backend-gbdk/.../visitor/GBDKSystemVisitor.kt` —
  `visitSaveSystem()` emits `trigger_saves` trampoline stub enabling SRAM persistence calls to be
  routed correctly across banks; without this stub, the MBC5 save round-trip does not complete
- **Guard module:** `gbkt-examples:banks` (BanksUatTest.kt — UAT tier; requires real ROM and GBDK)
- **RESEARCH Pitfall 1:** Anchor 4 requires a ROM build (`buildRom`) and `GBDK_HOME` to be set.
  Without GBDK, the test auto-skips (`Assumptions.assumeTrue(ROM_FILE.exists(), ...)`). Evidence
  file records GBDK_HOME path used: `/Users/michalsvacha/gbdk`
- **Non-tautological round-trip:** test writes sentinel value 99 to SRAM at 0xA000, saves
  emulator state, then verifies that restoring state reverts the sentinel — `postBytes[0] != 99`
  is the non-tautological gate
- **Test count:** 1 (Anchor 4) — existing, no new guard

---

## Zero New Guards Authored

RESEARCH established full existing coverage before this plan ran. Audit confirmed:

- SEED-014 → INV-2 + INV-6 (both existing, both GREEN 2026-06-14)
- SEED-015 → INV-5 (existing, GREEN 2026-06-14)
- SEED-016 → Anchor 4 (existing, GREEN 2026-06-14)

`git status --porcelain` after this plan shows zero modified production `.kt` files.
The only new file is this audit document. D-03 no-duplicate-coverage decision satisfied.

---

## Decisions Captured

| Decision | Outcome |
|----------|---------|
| D-01 (standalone audit doc) | This document; kept separate from VERIFICATION.md |
| D-02 (ordering gate) | BanksEmissionTest INV-2/INV-5/INV-6 run to GREEN FIRST (Plan 01) before authoring this audit; D-02 gate confirmed satisfied |
| D-03 (audit-first, no duplicate coverage) | 0 of 3 seeds needed new guards; all pre-existed at HEAD — full existing coverage confirmed |
| D-07 (single-commit audit) | This doc committed together with 20-02-SUMMARY.md; no production Kotlin modified |
