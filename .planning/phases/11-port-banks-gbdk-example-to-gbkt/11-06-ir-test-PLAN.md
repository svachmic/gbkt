---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 06
type: execute
wave: 2
depends_on: ["11-05"]
files_modified:
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt
autonomous: true
requirements:
  - BANK-IR-STRUCTURE   # IR sanity: scene count, start scene, variable count, zone presence, save system presence
user_setup: []
must_haves:
  truths:
    - "`./gradlew :gbkt-examples:banks:test --tests BanksIRTest` exits 0"
    - "All 8 IR structure tests are GREEN"
    - "BanksIRTest detects any regression in scene count, zone presence, or save-system presence"
  artifacts:
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt"
      provides: "8 @Test methods locking the IR shape of banks.build()"
      contains: "private val ir = banks.build()"
  key_links:
    - from: "BanksIRTest"
      to: "banks.build() in Banks.kt"
      via: "direct symbol reference"
      pattern: "banks\\.build\\(\\)"
---

<objective>
Fill the Plan 11-01 stub of `BanksIRTest.kt` with 8 IR-shape tests locking the contract Banks.kt established in Plan 11-05.

Purpose: Tier-1 oracle for IR structure. JVM-tier, no ROM. Catches regressions in the DSL or in IR construction (e.g., a future refactor that drops scenes silently).

Output: 8 GREEN `@Test` methods covering scene count, start scene, variable count + type, zone presence + id, save system presence, full scene ID set.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/dungeon/src/test/kotlin/io/github/gbkt/examples/dungeon/DungeonIRTest.kt
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add 8 IR-shape @Test methods to BanksIRTest</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt</files>
  <read_first>
    - gbkt-examples/dungeon/src/test/kotlin/io/github/gbkt/examples/dungeon/DungeonIRTest.kt (analog — same patterns: `ir.scenes.size`, `ir.startScene`, `ir.variables`, `ir.zones`, `ir.systems`)
    - 11-PATTERNS.md §"gbkt-examples/banks/src/test/kotlin/.../BanksIRTest.kt" (lines 150–198 — the core IR test pattern with exact assertion shapes)
    - 11-CONTEXT.md D-01 (substrate = 3 scenes + 1 zone + 1 saveData)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SystemIR.kt line 67 (`data class SaveSystem`) — confirms `SaveSystem` import path
    - The Plan 11-01 stub file (already contains companion init `private val ir = banks.build()` and the imports)
  </read_first>
  <behavior>
    Test list (each becomes one `@Test fun \`...\``):
    1. `has 3 scenes` — `assertEquals(3, ir.scenes.size)`
    2. `start scene is title` — `assertEquals("title", ir.startScene)`
    3. `scenes include title play pause` — `ir.scenes.map { it.id }.toSet().containsAll(setOf("title","play","pause"))`
    4. `has 1 variable` — `assertEquals(1, ir.variables.size)`
    5. `saveFlag is U8` — `ir.variables.any { it.name == "saveFlag" && it.type == VarType.U8 }`
    6. `has zone definitions` — `ir.zones.isNotEmpty()`
    7. `has play_zone zone` — `ir.zones.any { it.id == "play_zone" }`
    8. `has save system` — `ir.systems.any { it is SaveSystem }`
  </behavior>
  <action>
    Open `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` (created as a stub in Plan 11-01) and add the 8 `@Test` methods listed in `<behavior>`. The class header and `private val ir = banks.build()` line already exist; add methods after that line.

    Each test method body is one or two lines. Use exactly the assertion shapes from 11-PATTERNS.md §BanksIRTest.kt (lines 178–197) — copy them verbatim:

    ```kotlin
    @Test fun `has 3 scenes`() { assertEquals(3, ir.scenes.size) }
    @Test fun `start scene is title`() { assertEquals("title", ir.startScene) }
    @Test fun `scenes include title play pause`() {
        val ids = ir.scenes.map { it.id }.toSet()
        assertTrue(ids.contains("title"))
        assertTrue(ids.contains("play"))
        assertTrue(ids.contains("pause"))
    }
    @Test fun `has 1 variable`() { assertEquals(1, ir.variables.size) }
    @Test fun `saveFlag is U8`() {
        assertTrue(ir.variables.any { it.name == "saveFlag" && it.type == VarType.U8 })
    }
    @Test fun `has zone definitions`() { assertTrue(ir.zones.isNotEmpty()) }
    @Test fun `has play_zone zone`() { assertTrue(ir.zones.any { it.id == "play_zone" }) }
    @Test fun `has save system`() { assertTrue(ir.systems.any { it is SaveSystem }) }
    ```

    If any import is missing (e.g., `SaveSystem`, `VarType`), add it to the imports block — these were already present in the Plan 11-01 stub per the read_first reference.

    Do NOT add tests for actors (none exist), flags (none exist), camera (none exists), or RPG types. Do NOT add `@BeforeEach` / `@AfterEach` setup — `ir` is constructed once at field init.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksIRTest" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksIRTest"` exits 0
    - Test report at `gbkt-examples/banks/build/test-results/test/TEST-io.github.gbkt.examples.banks.BanksIRTest.xml` reports `tests=8 failures=0 errors=0`
    - File contains exactly 8 occurrences of `@Test fun ` (one per method)
    - All 8 expected method names are present (grep each: `has 3 scenes`, `start scene is title`, `scenes include title play pause`, `has 1 variable`, `saveFlag is U8`, `has zone definitions`, `has play_zone zone`, `has save system`)
  </acceptance_criteria>
  <done>BanksIRTest: 8/8 GREEN; IR shape contract locked at JVM tier.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test → IR | Read-only access to banks.build(); no mutation |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-10 | Tampering | IR assertions | mitigate | Acceptance criteria require exact test count + named methods |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - Test report exists; 8 tests GREEN.
  - `grep -c "@Test fun" gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` returns 8.
</verification>

<success_criteria>
  - All 8 IR tests pass.
  - No flakiness — IR construction is deterministic.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-06-SUMMARY.md` with: test count, pass count, file diff stat.
</output>
