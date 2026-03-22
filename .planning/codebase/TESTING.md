# Testing Patterns

**Analysis Date:** 2026-02-17

## Test Framework

**Runner:**
- Kotlin Test (kotlin.test) - lightweight, no external dependencies
- Supported by Gradle :test task
- Config: `build.gradle.kts` in each module

**Test Dependencies:**
- `kotlin("test")` - assertions and @Test annotation
- `libs.kotest.property` - property-based testing (generators)
- `libs.coroutines.test` - async testing utilities

**Run Commands:**
```bash
./gradlew :gbkt-core:test              # Run all tests in gbkt-core
./gradlew :gbkt-core:test --continue   # Don't fail fast
./gradlew build                         # Build and test all modules
./gradlew :gbkt-backend-gbdk:test      # Backend tests (codegen)
```

## Test File Organization

**Location:**
- Co-located with source: `src/test/kotlin/io/github/gbkt/core/{package}/`
- Mirrors source package structure exactly
- Test files in `src/test/`, source in `src/main/`

**Naming:**
- `{ClassName}Test.kt` - unit tests for a specific class
- `{Package}Test.kt` - integration tests for a package
- `Generators.kt` - shared property-based test generators

**Example Structure:**
```
gbkt-core/src/test/kotlin/io/github/gbkt/core/
├── InputTest.kt                  # Tests for input system
├── LogicBlockTest.kt             # Tests for DSL logic blocks
├── rpg/
│   ├── BattleTest.kt            # Battle system tests
│   ├── DamageTest.kt            # Damage calculation tests
│   └── CharacterStatsTest.kt    # Character stats tests
├── Generators.kt                 # Shared property generators
└── ...
```

## Test Structure

**Typical Unit Test:**
```kotlin
class InputTest {

    @Test
    fun `button A held generates correct mask check`() {
        val game = gbGame("test") {
            var pressed by u8Var(0)
            start = scene("main") {
                every.frame { whenever(buttons.a.held) { pressed set 1 } }
            }
        }

        val code = game.compileForTest()

        assertTrue(
            code.contains("0x10") || code.contains("16"),
            "Should check A button mask (0x10)",
        )
    }

    @Test
    fun `button released generates falling edge detection`() {
        val game = gbGame("test") {
            var justReleased by u8Var(0)
            start = scene("main") {
                every.frame { whenever(buttons.a.released) { justReleased set 1 } }
            }
        }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_joypad") && code.contains("_joypad_prev"),
            "Should compare current and previous joypad state",
        )
    }
}
```

**Patterns:**

- **Setup:** Create game using `gbGame()` DSL, configure variables and scenes
- **Execute:** Call `game.compileForTest()` to generate C code
- **Assert:** Check generated code for expected patterns or behavior
- **Section organization:** Group related tests with `// =========================================================================` headers

## Test Structure - Assertion Organization

**Assertion library:** Kotlin Test built-in assertions

**Common assertions:**
```kotlin
assertTrue(condition, "message")      // Assert true
assertFalse(condition, "message")     // Assert false
assertEquals(expected, actual)         // Equality check
assertNotNull(value)                  // Non-null check
assertNull(value)                     // Null check
assertThrows<Exception> { block() }   // Exception expected
assertFails { block() }               // Any exception expected
```

**Pattern with descriptive messages:**
```kotlin
assertTrue(
    code.contains("0x10") || code.contains("16"),
    "Should check A button mask (0x10)",
)
```

## Mocking

**Framework:** Kotlin Test has no built-in mocks - use manual stubs or test doubles

**Custom Test Doubles:**

Located in `gbkt-core/src/main/kotlin/io/github/gbkt/core/services/`:

```kotlin
// Mock services for testing
class MockAssetService : AssetService {
    override fun registerAsset(path: String) { /* stub */ }
    override fun getAssetPaths(): List<String> { /* return test data */ }
}

class MockEntityService : EntityService {
    override fun registerEntity(entity: Entity) { /* track registrations */ }
    val registeredEntities = mutableListOf<Entity>()
}

// Aggregated test doubles
class TestGameServices {
    val assetService = MockAssetService()
    val entityService = MockEntityService()
    val spriteService = MockSpriteService()
    fun reset() { /* reset all services */ }
}
```

**Test-specific classes in same module:**

```kotlin
// gbkt-core/src/main/kotlin/io/github/gbkt/core/services/
data class TestEntity(val x: u8, val y: u8, val vx: i8, val vy: i8, val width: Int, val height: Int)

// Creation for testing
private fun createEntity(name: String, tags: Set<String>? = null) =
    Entity(
        name = name,
        positionComponent = null,
        spriteComponent = null,
        // ... minimal setup for test
    )
```

**Pattern - Service Injection for Tests:**
```kotlin
val defaultServices = DefaultGameServices()
val testServices = TestGameServices()  // All mocks

// Tests inject mocks
val game = GameBuilder("test", services = testServices)
// Now game uses mock services instead of defaults
```

**What to Mock:**
- Dependency services (AssetService, SpriteService, etc.)
- I/O operations (file reading, emulator interaction)
- External systems (only in backend tests)

**What NOT to Mock:**
- Core DSL types (should test actual behavior)
- IR nodes (should test actual transformation)
- Game logic (defeats the purpose of the test)

## Fixtures and Factories

**Test Data Location:**
- `Generators.kt` - shared property generators and factory functions
- Test helper functions in same test file for isolated tests
- Common test entities: `TestEntity`, `TestCharacter` in `Generators.kt`

**Factory Pattern:**

```kotlin
// In Generators.kt
private fun createU8Var(name: String, initial: Int = 0) =
    GBVar(name, initial, GBVar.VarType.U8)

private fun createEntity(name: String, tags: Set<String>? = null) =
    Entity(
        name = name,
        positionComponent = null,
        velocityComponent = null,
        // ... standard test values
    )

// In test file
@Test
fun `entity stores tags correctly`() {
    val entity = createEntity("player", setOf("controllable", "visible"))
    assertTrue(entity.hasTag("controllable"))
}
```

**Property-Based Generators (Kotest):**

```kotlin
// Tier 1: Domain-specific realistic values
fun Arb.Companion.screenX(): Arb<u8> =
    int(0, GBConstants.SCREEN_WIDTH - 1).map { u8(it) }

fun Arb.Companion.velocity(): Arb<i8> =
    int(-8, 8).map { i8(it) }

// Tier 2: Edge case boundaries
fun Arb.Companion.u8Edge(): Arb<u8> =
    element(u8(0), u8(1), u8(127), u8(128), u8(254), u8(255))

// Tier 3: Composite game scenarios
fun Arb.Companion.collidingPair(): Arb<Pair<TestEntity, TestEntity>> =
    bind(
        int(20, GBConstants.SCREEN_WIDTH - 40),
        int(20, GBConstants.SCREEN_HEIGHT - 40),
        element(8, 16),
    ) { x, y, size ->
        val e1 = TestEntity(u8(x), u8(y), i8(0), i8(0), size, size)
        val e2 = TestEntity(u8(x + size / 2), u8(y + size / 2), i8(0), i8(0), size, size)
        e1 to e2
    }

// Tier 4: Full-range exhaustive testing
fun Arb.Companion.u8Full(): Arb<u8> =
    int(0, 255).map { u8(it) }
```

## Coverage

**Requirements:** No explicit coverage targets enforced by build

**Coverage Tracking:**
- JaCoCo/Kover integration available via Gradle plugin
- Reports generated to `build/reports/kover/`
- SonarQube integration: `sonar.coverage.jacoco.xmlReportPaths`

**View Coverage:**
```bash
./gradlew :gbkt-core:koverHtmlReport
# Opens build/reports/kover/html/index.html
```

**Coverage Gaps Identified (High Priority):**
- Exploration system (new feature) - minimal test coverage
- Skill system callbacks - untested edge cases
- Zone system interactions - limited integration tests
- Multi-bank codegen edge cases - property-based testing would help

## Test Types

**Unit Tests (Most Common):**
- Location: `src/test/kotlin/io/github/gbkt/core/{package}/`
- Scope: Single class/function in isolation
- Approach: `gbGame()` DSL + code generation + assertions
- Example: `InputTest.kt` tests input DSL compilation
- Speed: Fast (milliseconds)

**Example Unit Test:**
```kotlin
@Test
fun `logicBlock records statements`() {
    val game = gbGame("LogicBlockRecordTest") {
        var counter by u8Var(0)
        val incrementCounter = logicBlock("increment") { counter += 1 }

        assertEquals(1, incrementCounter.size, "Logic block should have 1 statement")
        assertFalse(incrementCounter.isEmpty, "Logic block should not be empty")

        start = scene("main") { every.frame {} }
    }

    assertNotNull(game, "Game should build successfully")
}
```

**Integration Tests:**
- Location: `src/test/kotlin/io/github/gbkt/backend/gbdk/`
- Scope: End-to-end DSL → IR → C code generation
- Approach: Build game, generate C, verify output patterns
- Example: `ExpressionCodegenTest.kt` verifies full expression compilation
- Speed: Moderate (seconds per test)

**Example Integration Test:**
```kotlin
@Test
fun `positive integer literal generates unsigned suffix`() {
    val game = gbGame("test") {
        var result by u8Var(0)
        start = scene("main") { enter { result set 42 } }
    }

    val code = GBDKCodeGenerator(game).generate()

    assertTrue(code.contains("42u"), "Positive integer should have 'u' suffix")
}
```

**Property-Based Tests (Less Common, Strategic):**
- Framework: Kotest property testing
- Scope: Testing correctness across many input combinations
- Approach: Define property using generators, assertion holds for all
- Location: Ad-hoc in tests needing exhaustive validation
- Speed: Slower (many iterations)

**Simulation Tests:**
- Framework: SimulationContext DSL (in `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/`)
- Scope: Execute game logic in-memory without ROM
- Approach: `SimulationContext` runs IR, simulates frames, checks state
- Example: Collision detection, entity movement, scene transitions
- Speed: Very fast (in-memory, no codegen)

**Example Simulation Test:**
```kotlin
@Test
fun `collision triggers scene change`() {
    val game = createTestGame()
    val sim = SimulationContext(game)

    // Setup overlapping positions
    sim.setVariable("playerX", 100)
    sim.setVariable("playerY", 50)
    sim.setVariable("enemyX", 102)
    sim.setVariable("enemyY", 52)

    // Run frame
    sim.executeFrame()

    // Verify scene changed
    assertEquals("gameover", sim.currentScene)
}
```

**E2E Tests:**
- Not used in this project
- Full ROM generation + emulator execution requires GBDK toolchain
- Manual testing via `./gradlew buildRom` and mGBA

## Common Patterns

**Async Testing:**
Not heavily used (Game Boy is synchronous), but coroutines available via `libs.coroutines.test`

**Error Testing:**
```kotlin
@Test
fun `require validates precondition`() {
    val exception = assertThrows<IllegalArgumentException> {
        createStatusBar {
            require(count > 0) { "Segment count must be positive" }
        }
    }
    assertTrue(exception.message?.contains("Segment count") ?: false)
}
```

**Variable State Testing:**
```kotlin
@Test
fun `variable tracks assignment changes`() {
    val game = gbGame("test") {
        var health by u8Var(100)

        start = scene("main") {
            enter { health set 50 }
        }
    }

    val code = game.compileForTest()
    assertTrue(code.contains("health"), "Generated code should contain health reference")
}
```

**Code Generation Verification:**
Most tests follow this pattern:

1. **Build DSL game** with feature to test
2. **Compile with backend** - `game.compileForTest()` or `GBDKCodeGenerator(game).generate()`
3. **Verify generated C** - assert code contains expected patterns, operators, function calls

```kotlin
@Test
fun `dpad x axis generates ternary expression`() {
    val game = gbGame("test") {
        var playerX by u8Var(80)
        start = scene("main") { every.frame { playerX set (playerX + dpad.x) } }
    }

    val code = game.compileForTest()

    // dpad.x generates: left ? -1 : (right ? 1 : 0)
    assertTrue(code.contains("?") && code.contains(":"), "Should generate ternary for dpad.x")
}
```

**Iteration Testing with Logic Blocks:**
```kotlin
@Test
fun `logicBlock can be expanded multiple times`() {
    val game = gbGame("LogicBlockMultiExpandTest") {
        var counter by u8Var(0)
        val incrementCounter = logicBlock("increment") { counter += 1 }

        start = scene("main") {
            every.frame {
                incrementCounter()
                incrementCounter()
                incrementCounter()
            }
        }
    }

    // Should compile successfully with multiple expansions
    val code = game.compileForTest()
    assertNotNull(code, "Should generate valid code")
}
```

## Test Organization Best Practices

**Test Sections:**
- Group related tests with section headers (80-char line of `=`)
- One section per logical feature area
- Example:

```kotlin
class InputTest {
    // =========================================================================
    // SINGLE BUTTON CHECK TESTS
    // =========================================================================

    @Test
    fun `button A held generates correct mask check`() { ... }

    @Test
    fun `button B held generates correct mask check`() { ... }

    // =========================================================================
    // BUTTON COMBINATIONS TESTS
    // =========================================================================

    @Test
    fun `two buttons combined with and generates both checks`() { ... }
}
```

**Test Method Naming:**
- Backtick format for readability: `` `button A held generates correct mask check`() ``
- Read as sentence: "button A held generates correct mask check"
- Include both action and expected result
- Not camelCase - backticks allow spaces

**Assertion Messages:**
Always include descriptive message as second parameter:

```kotlin
assertTrue(
    code.contains("0x10") || code.contains("16"),
    "Should check A button mask (0x10)",  // ← Message is critical
)
```

## Test Suite Maintenance

**Baseline Tracking:**
- `detekt-baseline.xml` tracks existing violations
- New code should not introduce new violations
- Baseline updated during cleanup phases

**Before Submitting PR:**
```bash
# Run all tests
./gradlew build

# Check code quality
./gradlew detekt

# Verify formatting
./gradlew spotlessCheck

# No test-specific coverage requirement, but review test output
```

**Test Execution:**
- All tests run on each commit (enforced by pre-commit hooks)
- Detekt and Spotless must pass
- Test failures block merge

## Key Test Locations

| Component | Test File | Coverage |
|-----------|-----------|----------|
| Input DSL | `InputTest.kt` | Button masks, combinations, pressed/held/released, axes |
| Logic Blocks | `LogicBlockTest.kt` | Recording, expansion, parameters, nesting |
| Expressions | `ExpressionCodegenTest.kt` | Literals, variables, operators, ternary, arrays |
| Statements | `StatementCodegenTest.kt` | Assignments, conditionals, loops, scene changes |
| Animations | `AnimationCodegenTest.kt` | Sprite animation compilation, callbacks |
| RPG System | `rpg/BattleTest.kt`, `DamageTest.kt`, etc. | Stats, damage calculation, status effects, leveling |
| Input Service | `InputTest.kt` | Joypad state tracking, edge detection |
| Entity Pool | `entity/PoolTest.kt` | Spawn, despawn, iteration |
| Collision | `TilemapCollisionTest.kt` | Tile collision, AABB detection |
| Camera | `CameraTest.kt` | Positioning, smoothing, shake, transitions |

---

*Testing analysis: 2026-02-17*
