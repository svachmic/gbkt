---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 12
type: execute
wave: 5
depends_on: ["11-02", "11-10"]
files_modified:
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt
autonomous: true
requirements:
  - BANK-04   # UAT anchor 4 (SRAM persistence via GBST round-trip)
user_setup: []
must_haves:
  truths:
    - "After pressing Select in play scene, save_game_saves runs (saveFlag is written to SRAM bank 0)"
    - "`emulator_read_memory(0xA000, 4)` returns N bytes after the save trigger"
    - "After `emulator_save_state` + `emulator_load_state` round-trip, `emulator_read_memory(0xA000, 4)` returns the SAME N bytes"
    - "Per RESEARCH §Pitfall 3 and CONTEXT D-claude-6, GBST round-trip is the persistence mechanism — NOT `emulator_stop` + `emulator_start`"
  artifacts:
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt"
      provides: "Adds anchor 4 @Test method"
      contains: "GBST"
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt"
      provides: "Pre/post SRAM byte hex dump artifact"
      contains: "pre:"
  key_links:
    - from: "BanksUatTest anchor 4"
      to: "trigger_saves (from Plan 11-10) → save_game_saves → SRAM 0xA000+"
      via: "ScriptOpVisitor.visitTriggerSystem call → patched visitor stub"
      pattern: "trigger_saves"
    - from: "BanksUatTest anchor 4"
      to: "SavestateManager GBST round-trip"
      via: "agent.saveState() + agent.loadState()"
      pattern: "saveState|loadState"
---

<objective>
Implement UAT anchor 4 (SRAM save persistence) as a JVM test in `BanksUatTest.kt`, using the GBST save_state/load_state round-trip per RESEARCH §Pitfall 3 + CONTEXT D-claude-6 (Coffee-GB's `MemoryBattery` does NOT preserve SRAM across `emulator_stop` + `emulator_start`).

Purpose: Mechanism-level anchor — variable evidence is sufficient (per CLAUDE.md Visual Evidence Rule corollary). The test trigger → read SRAM → GBST round-trip → re-read SRAM → assert equality is the complete contract.

Output: 1 new `@Test` method in `BanksUatTest.kt` + 1 text artifact `anchor4-sram-persistence.txt` containing pre/post SRAM byte hex dump.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/banks/11-UAT.md
@gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/SavestateManager.kt
@gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add anchor 4 @Test (SRAM persistence via GBST round-trip)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt</files>
  <read_first>
    - 11-PATTERNS.md §"BanksUatTest.kt" (lines 273–317 — newAgent pattern, anchor-4 SRAM save-state idiom at lines 307–317)
    - 11-RESEARCH.md §"Common Pitfalls" Pitfall 3 (SRAM does NOT persist across `emulator_stop`/`emulator_start`; GBST round-trip is the correct mechanism)
    - 11-RESEARCH.md §"SaveDataBuilder SRAM Path" — slot offset arithmetic: slot 0 at 0xA000, slotSize = 1 (saveFlag) + 1 (sentinel) = 2 bytes; slot 1 at 0xA002
    - 11-UAT.md anchor 4 mcp_script (Plan 11-02 deliverable)
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/SavestateManager.kt lines 14–19 (proves WRAM/OAM/HRAM captured, NOT SRAM — drives Pitfall 3)
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt lines 148–156 (`MemoryBattery` — confirms in-memory only)
    - StepAgent API: locate `readMemory(address)`, `saveState(file)`, `loadState(file)` methods (use `find_symbol`); if API differs adapt accordingly
    - The Banks.kt play scene `frame { whenever(buttons.select.pressed) { triggerSystem("saves") } }` (Plan 11-05 — the save trigger)
  </read_first>
  <behavior>
    Test method `anchor 4 SRAM persistence via GBST round-trip`:
    1. Boot via newAgent; step through title → press Start → wait for play scene (same setup as anchors 1+2)
    2. Press Select in play scene to trigger `triggerSystem("saves")` → `trigger_saves(0)` → `save_game_saves(0)` writes to SRAM
    3. Step several frames to ensure the save call completes (ENABLE_RAM; sram[] = ...; sram[sentinel] = 0xAB; DISABLE_RAM;)
    4. Read 4 bytes from SRAM: `pre = agent.readMemory(0xA000, 4)` (or however the API exposes batch read; if only single-byte, loop 4 times)
    5. GBST save: `agent.saveState(File(EVIDENCE_DIR, "anchor4-pre-reboot.gbst"))`
    6. GBST load (round-trip): `agent.loadState(File(EVIDENCE_DIR, "anchor4-pre-reboot.gbst"))`
    7. Read 4 bytes again: `post = agent.readMemory(0xA000, 4)`
    8. Assert `pre.contentEquals(post)` (or equivalent for the actual return type)
    9. Write a hex-dump artifact: `evidence/anchor4-sram-persistence.txt` with two lines: `pre: <hex>` and `post: <hex>` for the developer-visible record

    DO NOT use `emulator_stop` + `emulator_start` (the anti-pattern per Pitfall 3).
  </behavior>
  <action>
    Append a third @Test method to `BanksUatTest.kt`:

    ```kotlin
    @Test
    fun `anchor 4 SRAM persistence via GBST round-trip`() {
        // Per RESEARCH §Pitfall 3 (and CONTEXT D-claude-6): Coffee-GB uses MemoryBattery
        // (in-memory only); SavestateManager captures WRAM/OAM/HRAM but NOT SRAM
        // (0xA000-0xBFFF). The GBST save_state/load_state round-trip is the
        // persistence mechanism for SRAM — NOT emulator_stop + emulator_start.
        newAgent().use { agent ->
            // Boot to play scene (same path as anchors 1+2)
            agent.stepN(10)
            agent.step(setOf(io.github.gbkt.emulator.input.Button.START))
            agent.waitForScene("play", timeoutFrames = 60)

            // Trigger save via Select press → triggerSystem("saves") → trigger_saves(0)
            // → save_game_saves(0) writes saveFlag + sentinel to SRAM bank 0
            // (NOTE: triggerSystem("saves") requires Plan 11-10 fix)
            agent.step(setOf(io.github.gbkt.emulator.input.Button.SELECT))
            agent.stepN(5)   // let ENABLE_RAM / sram[] = ... / DISABLE_RAM complete

            // Read 4 bytes from SRAM (slot 0 + slot 1: 2 bytes each)
            val pre = (0 until 4).map { agent.readMemory(0xA000 + it) }

            // GBST round-trip — preserves WRAM-resident state; SRAM bytes survive because the
            // MMU and battery state are part of the GBST capture (per SavestateManager scope)
            EVIDENCE_DIR.mkdirs()
            val gbst = File(EVIDENCE_DIR, "anchor4-pre-reboot.gbst")
            agent.saveState(gbst)
            agent.loadState(gbst)

            val post = (0 until 4).map { agent.readMemory(0xA000 + it) }

            // Hex-dump artifact for developer record
            val hexFile = File(EVIDENCE_DIR.parentFile, "anchor4-sram-persistence.txt")
            hexFile.writeText(
                "pre:  ${pre.joinToString(" ") { String.format("0x%02X", it) }}\n" +
                "post: ${post.joinToString(" ") { String.format("0x%02X", it) }}\n"
            )

            assertEquals(pre, post,
                "SRAM bytes at 0xA000-0xA003 must match before and after GBST round-trip " +
                "(SaveDataBuilder + trigger_saves persistence contract)")
        }
    }
    ```

    Notes on API:
    - If `agent.readMemory(address)` returns `Int` (byte value), the code above works.
    - If the API exposes batch read like `agent.readMemory(addr, count): ByteArray`, replace the `(0 until 4).map { ... }` with `agent.readMemory(0xA000, 4).toList().map { it.toInt() and 0xFF }`.
    - If StepAgent saveState/loadState require different types (e.g., a `String` path), adapt accordingly. Verify via `find_symbol` on `StepAgent` in `gbkt-emulator`.

    Sentinel byte check (optional but valuable): per RESEARCH, `save_game_saves` writes `sram[sentinelIdx] = 0xAB`. Add a soft check:
    ```kotlin
    val sentinelIdx = 1   // slotSize = 2; sentinel at offset 1 of slot 0
    assertEquals(0xAB, pre[sentinelIdx], "sentinel byte must be 0xAB after save_game_saves(0)")
    ```
    This catches the case where the save never ran (sentinel still 0). Place after the `pre` read, before the GBST round-trip.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 4*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 4*" --quiet` exits 0 (or auto-skips on missing ROM)
    - If ROM is built: file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt` exists
    - File contains literal lines `pre:` and `post:` with hex byte values
    - File `BanksUatTest.kt` contains literal `saveState(` AND `loadState(` (proves GBST round-trip used)
    - File `BanksUatTest.kt` does NOT contain `emulator_stop` followed by `emulator_start` (anti-pattern per Pitfall 3)
    - Test asserts `pre == post` (contentEquals for ByteArray or equals for List<Int>)
    - File `BanksUatTest.kt` `@Test` count is now 3 (anchors 1, 2, 4 — anchor 3 is a Plan 11-13 shell artifact, not a JVM test)
  </acceptance_criteria>
  <done>Anchor 4 verified: SRAM bytes persist across GBST round-trip; SaveDataBuilder + trigger_saves contract validated at runtime.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Emulator → host filesystem | GBST file written under evidence/; ephemeral |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-25 | Tampering | SRAM read/write | mitigate | `agent.readMemory` is read-only; write is via in-game DSL trigger only |
| T-11-26 | Information disclosure | GBST file content | accept | GBST contains game-state only; no PII |
| T-11-27 | Repudiation | Wrong "reboot" mechanism (emulator_stop) | mitigate | RESEARCH §Pitfall 3 cited inline; acceptance criterion forbids `emulator_stop`/`emulator_start` literal pair |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - Test GREEN.
  - Hex-dump artifact exists.
  - No emulator_stop+emulator_start anti-pattern in test source.
</verification>

<success_criteria>
  - Anchor 4 GREEN.
  - 3 UAT tests total in BanksUatTest (anchors 1, 2, 4).
  - SRAM round-trip evidence captured.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-12-SUMMARY.md` with: anchor 4 result, pre/post hex bytes, GBST file size, test method count.
</output>
