---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 11
type: execute
wave: 5
depends_on: ["11-02", "11-03", "11-10"]
files_modified:
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png
autonomous: false
requirements:
  - BANK-01   # UAT anchor 1 (cross-bank scene nav visual)
  - BANK-02   # UAT anchor 2 (cross-bank zone tilemap load visual)
user_setup: []
must_haves:
  truths:
    - "UAT anchor 1: after pressing Start on title scene, scene transitions to `play`; screenshot captured"
    - "UAT anchor 2: within play scene, zone tilemap is visible on screen; screenshot captured"
    - "Both screenshots exist at the paths reserved in 11-UAT.md"
    - "Per CLAUDE.md Visual Evidence Rule, variable-state assertions ALONE are insufficient — the .png artifacts are the binding evidence"
  artifacts:
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt"
      provides: "Two @Test methods covering anchors 1 + 2"
      contains: "anchor1-play-scene.png"
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png"
      provides: "Anchor 1 visual evidence — play scene rendered"
      contains: "PNG signature"
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png"
      provides: "Anchor 2 visual evidence — zone tilemap rendered"
      contains: "PNG signature"
  key_links:
    - from: "BanksUatTest"
      to: "build/gbkt/output/banks.gb (built ROM)"
      via: "StepAgent + AgentSessionConfig"
      pattern: "AgentSessionConfig\\.discoverFiles"
    - from: "Anchor screenshots"
      to: "11-UAT.md reserved paths"
      via: "EVIDENCE_DIR resolution"
      pattern: "anchor[12]-.*\\.png"
---

<objective>
Implement UAT anchors 1 (cross-bank scene navigation) and 2 (banked zone tilemap load) as JVM tests in `BanksUatTest.kt`, producing screenshot evidence per CLAUDE.md Visual Evidence Rule.

Purpose: Anchors 1 + 2 are visual truths. RESEARCH §"Validation Architecture" Tier-3 + 11-UAT.md (Plan 11-02) require runtime screenshots, not variable-state-only assertions. Per memory `feedback_visual_evidence_for_visual_truths.md`: codegen GREEN (Plans 11-07/08/10) is necessary but never sufficient — the runtime screenshot is the binding evidence.

Output: 2 `@Test` methods in `BanksUatTest.kt` + 2 PNG files under `evidence/uat-screenshots/`.

Note: BanksUatTest uses `Assumptions.assumeTrue(ROM_FILE.exists())` (already in the Plan 11-01 stub) — if the ROM is not built, tests auto-skip rather than fail. The buildRom prerequisite is enforced by Plan 11-14's BLOCKING gate.
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
@gbkt-examples/banks/PLAYBOOK.md
@gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt
@CLAUDE.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add anchor 1 @Test (cross-bank scene navigation)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt</files>
  <read_first>
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt full file (analog — newAgent / step / screenshot / assert pattern)
    - 11-PATTERNS.md §"BanksUatTest.kt" (lines 273–317 — what to copy/change)
    - 11-UAT.md anchor 1 mcp_script (Plan 11-02 deliverable; contains the exact MCP sequence)
    - 11-RESEARCH.md §Tier-3 anchor 1 row (line 497)
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt API surface (use `find_symbol` to locate methods: `step(buttons: Set<Button>)`, `screenshot(path: File)`, `waitForScene(scene: String, timeout: Int)`, `assertScene(scene: String)`)
    - CLAUDE.md §Visual Evidence Rule
  </read_first>
  <behavior>
    Test method `anchor 1 cross-bank scene navigation`:
    1. `agent = newAgent()` (skip if `banks.gb` missing — Assumptions.assumeTrue gate already in the helper)
    2. `agent.stepN(10)` to boot through title
    3. Verify title scene reached (via metadata-driven scene assertion if `_current_scene` is exposed; otherwise rely on the wait-for-scene call in step 4)
    4. `agent.step(setOf(Button.START))` — single press
    5. `agent.waitForScene("play", timeout = 60)` — must succeed within 60 frames
    6. `agent.screenshot(File(EVIDENCE_DIR, "anchor1-play-scene.png"))` — write screenshot to evidence path reserved in 11-UAT.md
    7. `assertEquals("play", agent.currentScene())` (or whatever the StepAgent API exposes for current-scene readback)
    8. `agent.close()` (or `use { }` block)
  </behavior>
  <action>
    Open `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` and append the anchor-1 @Test method. The companion object (with `EVIDENCE_DIR`, `ROM_FILE`, `METADATA_FILE`) and the `newAgent()` helper already exist from Plan 11-01.

    Test method:

    ```kotlin
    @Test
    fun `anchor 1 cross-bank scene navigation`() {
        newAgent().use { agent ->
            // Boot through title scene; metadata.transitions tracks the expected start
            agent.stepN(10)

            // Press Start on title — HOME→bank-1 BANKED trampoline fires
            agent.step(setOf(io.github.gbkt.emulator.input.Button.START))

            // Wait for scene transition (cross-bank BANKED play_enter() call)
            agent.waitForScene("play", timeoutFrames = 60)

            // Visual evidence per CLAUDE.md Visual Evidence Rule
            EVIDENCE_DIR.mkdirs()
            val screenshotPath = File(EVIDENCE_DIR, "anchor1-play-scene.png")
            agent.screenshot(screenshotPath)
            assertTrue(screenshotPath.exists(), "anchor1 screenshot must exist after capture")
            assertTrue(screenshotPath.length() > 100, "anchor1 screenshot must be a real PNG, not empty")

            // Variable evidence (secondary; screenshot is primary per Visual Evidence Rule)
            assertEquals("play", agent.currentScene(),
                "After Start on title, current scene must be 'play' (cross-bank BANKED trampoline contract)")
        }
    }
    ```

    Adapt method names if the StepAgent API differs from the speculative names above. The `find_symbol` lookup in `<read_first>` resolves any discrepancy — match the actual API surface in `gbkt-emulator/.../StepAgent.kt`. For instance, if the method is `agent.waitForScene("play")` without a timeout param, use that; if it's `agent.assertScene("play")`, use that.

    Imports needed (add to file imports section if missing): `io.github.gbkt.emulator.input.Button`, `java.io.File`, `kotlin.test.assertEquals`, `kotlin.test.assertTrue`.

    The `use { }` block ensures agent cleanup (StepAgent is AutoCloseable per simple-physics test pattern).
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 1*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 1*" --quiet` exits 0 (if ROM is built) OR test is auto-skipped (if ROM missing — Assumptions.assumeTrue triggered)
    - If ROM is built: file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png` exists, has PNG signature, and is > 100 bytes
    - File `BanksUatTest.kt` contains literal `anchor1-play-scene.png` (matches 11-UAT.md reserved path)
    - File contains literal `waitForScene("play"` AND `Button.START`
  </acceptance_criteria>
  <done>Anchor 1 verified: visual + variable evidence; cross-bank scene nav contract observed at runtime.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Add anchor 2 @Test (banked zone tilemap visible)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt</files>
  <read_first>
    - 11-PATTERNS.md §"BanksUatTest.kt" — anchor 2 pattern
    - 11-UAT.md anchor 2 mcp_script (Plan 11-02 deliverable)
    - 11-RESEARCH.md §Tier-3 anchor 2 row (line 498)
    - CLAUDE.md §Visual Evidence Rule (anchor 2 is the canonical "tilemap visible" case — per CLAUDE.md, variable assertion of `_current_tileset_id` is INSUFFICIENT; screenshot is mandatory)
    - The anchor 1 method written in Task 1 (same boot/setup; the difference is the additional step + screenshot timing)
  </read_first>
  <behavior>
    Test method `anchor 2 banked zone tilemap visible`:
    1. Same boot/title setup as anchor 1
    2. Press Start → wait for play scene (same as anchor 1)
    3. Step additional frames (e.g., 30) to ensure `_bkg_tiles_load_banked` has fired and tilemap pixels reached VRAM
    4. `agent.screenshot(File(EVIDENCE_DIR, "anchor2-tilemap.png"))`
    5. Assert screenshot has non-trivial content (file size > 200 bytes — a blank checkerboard PNG is > 70 bytes; this is a sanity floor, not a pixel-content check)

    Anchor 2 does NOT separately assert `_current_tileset_id` or any zone variable — per Visual Evidence Rule, that's the explicit anti-pattern. The screenshot IS the evidence.
  </behavior>
  <action>
    Append a second @Test method to `BanksUatTest.kt`:

    ```kotlin
    @Test
    fun `anchor 2 banked zone tilemap visible`() {
        newAgent().use { agent ->
            agent.stepN(10)
            agent.step(setOf(io.github.gbkt.emulator.input.Button.START))
            agent.waitForScene("play", timeoutFrames = 60)

            // Step additional frames so SWITCH_ROM-from-HOME wrapper (_bkg_tiles_load_banked)
            // has completed and tilemap pixels are in VRAM
            agent.stepN(30)

            // Visual evidence — per CLAUDE.md Visual Evidence Rule, variable-state assertions
            // alone (e.g., _current_tileset_id) are INSUFFICIENT for "tilemap is visible" truths.
            EVIDENCE_DIR.mkdirs()
            val screenshotPath = File(EVIDENCE_DIR, "anchor2-tilemap.png")
            agent.screenshot(screenshotPath)
            assertTrue(screenshotPath.exists(), "anchor2 screenshot must exist")
            assertTrue(
                screenshotPath.length() > 200,
                "anchor2 screenshot must show real tilemap pixels (not blank — checker pattern from play_zone tileset)"
            )
        }
    }
    ```

    Same import set as Task 1; no additions.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 2*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksUatTest.anchor 2*" --quiet` exits 0 (or skips on missing ROM)
    - If ROM is built: file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png` exists, has PNG signature, and is > 200 bytes
    - File `BanksUatTest.kt` contains literal `anchor2-tilemap.png`
    - File contains literal `tilemap pixels` (in the assertion message — proves the test was written with visual-evidence intent, not variable-state-intent)
    - Total `@Test` count in `BanksUatTest.kt` is now 2 (one anchor 4 test will be added by Plan 11-12)
  </acceptance_criteria>
  <done>Anchor 2 verified: zone tilemap visible in screenshot — Plan 07.4-30 regression check intact for the banks port.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Human gate — review the two screenshots (per Visual Evidence Rule)</name>
  <what-built>
    Tasks 1 + 2 produced two PNG screenshots:
    - `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png`
    - `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png`
  </what-built>
  <how-to-verify>
    Open each PNG and confirm visually:
    1. **anchor1-play-scene.png** — Does the image show the play scene (not the title scene)? The play scene has `showSprites()` called in `enter` per Plan 11-05 Banks.kt; the title has `clear()` only. A blank Game Boy screen is suspect.
    2. **anchor2-tilemap.png** — Does the image show the checkerboard pattern from `res/tiles/checker.png` rendered on the background layer? A blank screen or all-white tiles indicates the SWITCH_ROM-from-HOME wrapper didn't fire OR the tileset asset reference is broken.

    Per memory `feedback_visual_evidence_for_visual_truths.md`: variable-state assertions in code are NOT sufficient — only the developer's eye on the actual pixels closes the loop.
  </how-to-verify>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png, .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png (read-only)</files>
  <action>Human opens both PNG files in an image viewer and confirms anchor 1 shows the play scene (not title) and anchor 2 shows the checker tilemap pattern (not blank). On approval, update gbkt-examples/banks/11-UAT.md anchor 1 + 2 Result fields from `pending` to `passed`.</action>
  <verify><human-check>Both PNGs visually match anchor intent per CLAUDE.md Visual Evidence Rule; 11-UAT.md Result fields updated.</human-check></verify>
  <done>Both anchors visually confirmed; 11-UAT.md updated.</done>
  <resume-signal>
    Type `approved` to confirm both anchors are visually valid, OR describe which anchor's screenshot is wrong and propose a remediation (e.g., "anchor 2 screenshot is blank — re-run with stepN(60) instead of 30", or "anchor 1 is on title screen — wait-for-scene timeout too short").
    On approval, mark anchors 1 + 2 GREEN in 11-UAT.md (update the `Result:` field from `pending` to `passed` for both anchors).
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Emulator (Coffee-GB embedded) → host filesystem | Screenshots written under evidence/; no untrusted input |
| Test → ROM | Test reads ROM only; ROM is local artifact |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-22 | Repudiation | Variable-state-only "verification" | mitigate | Per Visual Evidence Rule, screenshot is mandatory; human gate (Task 3) cannot be auto-approved |
| T-11-23 | Tampering | Pre-generated screenshot | mitigate | Tests write screenshots IN-LINE; `agent.screenshot()` is the only path — manual PNG injection would fail the size + content gate |
| T-11-24 | Information disclosure | Screenshot content | accept | Game Boy frame is non-sensitive |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - Both PNG files exist with valid signatures.
  - Both tests GREEN in CI (if ROM built) or skip on missing ROM.
  - Human-eye check confirms image content matches anchor intent.
</verification>

<success_criteria>
  - 2 GREEN @Test methods in BanksUatTest.
  - 2 PNG evidence files at the paths reserved in 11-UAT.md.
  - 11-UAT.md anchor-1 and anchor-2 Result fields updated to `passed`.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-11-SUMMARY.md` with: 2 tests, 2 PNGs (paths + byte sizes), human-gate outcome.
</output>
