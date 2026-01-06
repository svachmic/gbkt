/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import io.github.gbkt.core.services.DefaultGameServices
import io.github.gbkt.core.services.GameServices

/**
 * Main entry point for the gbkt testing framework.
 *
 * Creates a test harness that allows testing game logic without ROM/emulator.
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `player moves right`() = testGame("movement") {
 *     var playerX by u8Var(80)
 *
 *     scene("gameplay") {
 *         every.frame { playerX += dpad.x * 2 }
 *     }
 *     start = scene("gameplay")
 *
 *     test {
 *         press(dpad.right) { advanceFrames(5) }
 *         expect("playerX").toEqual(90)
 *     }
 * }
 * ```
 *
 * ## With Mock Services
 *
 * ```kotlin
 * @Test
 * fun `sprites registered correctly`() {
 *     val mockServices = TestGameServices()
 *
 *     testGame("test", mockServices) {
 *         sprite(SpriteAsset("player.png")) { size = 8 x 16 }
 *         test {
 *             assertEquals(1, mockServices.sprites.getSprites().size)
 *         }
 *     }
 * }
 * ```
 *
 * @param name The test game name
 * @param services Optional injectable services for mocking (defaults to TestGameServices)
 * @param init The test game builder block
 */
fun testGame(
    name: String,
    services: GameServices = DefaultGameServices(),
    init: TestGameBuilder.() -> Unit,
): Unit {
    val builder = TestGameBuilder(name, services)
    GameScopeContext.withScope(builder) {
        builder.init()
        builder.executeTests()
    }
}

/**
 * Builder for test games - wraps GameBuilder with test execution.
 *
 * @param name The test game name
 * @param services Injectable services for DI testing
 */
@TestDsl
class TestGameBuilder(
    name: String,
    /** Injected services for DI testing and mocking. */
    val services: GameServices = DefaultGameServices(),
) : GameScope() {
    private val gameBuilder = GameBuilder(name, services)
    private var testBlock: (TestScope.() -> Unit)? = null
    private var _start: SceneRef? = null

    // Delegate to GameBuilder for DSL features
    fun config(init: ConfigBuilder.() -> Unit) = gameBuilder.config(init)

    fun sprite(
        asset: io.github.gbkt.core.assets.SpriteAsset,
        init: SpriteBuilder.() -> Unit = {},
    ): Sprite = gameBuilder.sprite(asset, init)

    fun scene(name: String, init: SceneBuilder.() -> Unit): SceneRef {
        return gameBuilder.scene(name, init)
    }

    fun pool(name: String, size: Int, init: PoolBuilder.() -> Unit): Pool {
        return gameBuilder.pool(name, size, init)
    }

    var start: SceneRef
        get() = _start ?: error("Start scene not set")
        set(value) {
            _start = value
            gameBuilder.start = value
        }

    // Variable delegates - forward registration
    fun u8Var(initial: Int = 0): U8Delegate {
        val delegate = U8Delegate(initial)
        return delegate
    }

    fun u16Var(initial: Int = 0): U16Delegate {
        val delegate = U16Delegate(initial)
        return delegate
    }

    // Note: i8Var and i16Var use u8/u16 internally but track signed semantics
    fun i8Var(initial: Int = 0): U8Delegate = U8Delegate(initial)

    fun i16Var(initial: Int = 0): U16Delegate = U16Delegate(initial)

    /**
     * Define the test block to execute.
     *
     * This block has access to frame control, input simulation, and assertions.
     */
    fun test(block: TestScope.() -> Unit) {
        testBlock = block
    }

    /** Execute the tests after the game is built. */
    internal fun executeTests() {
        // Build the game
        val game = gameBuilder.build()

        // Create simulation context
        val simulation = SimulationContext(game)

        // Create input provider
        val input = MockInputProvider()

        // Create test scope
        val scope = TestScope(simulation, input)

        // Execute test block
        testBlock?.invoke(scope)
            ?: error("No test block defined. Use test { ... } to define assertions.")
    }
}

// =============================================================================
// CONVENIENCE EXTENSIONS
// =============================================================================

// Note: Use gbkt.core.Button enum directly in tests, e.g. Button.A, Button.LEFT
// The core library's dpad and buttons objects are available for DSL recording

// =============================================================================
// ALTERNATE ENTRY POINTS
// =============================================================================

/**
 * Test a scene in isolation without defining a full game.
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `gameplay scene works`() = testScene("gameplay") {
 *     var counter by u8Var(0)
 *
 *     every.frame { counter += 1 }
 *
 *     test {
 *         advanceFrames(10)
 *         expect("counter").toEqual(10)
 *     }
 * }
 * ```
 */
fun testScene(name: String, init: TestSceneBuilder.() -> Unit): Unit {
    val builder = TestSceneBuilder(name)
    GameScopeContext.withScope(builder) {
        builder.init()
        builder.executeTests()
    }
}

/** Builder for testing a single scene. */
@TestDsl
class TestSceneBuilder(private val sceneName: String) : GameScope() {
    private var onFrame: List<IRStatement> = emptyList()
    private var testBlock: (TestScope.() -> Unit)? = null

    // Timing DSL
    val every: TimingScope
        get() = TimingScope()

    inner class TimingScope {
        val frame: FrameRecorder
            get() = FrameRecorder()
    }

    inner class FrameRecorder {
        operator fun invoke(block: FrameScope.() -> Unit) {
            val recorder = StatementRecorder()
            RecordingContext.record(recorder) { FrameScope(sceneName).block() }
            onFrame = recorder.statements
        }
    }

    // Variable delegates
    fun u8Var(initial: Int = 0) = U8Delegate(initial)

    fun u16Var(initial: Int = 0) = U16Delegate(initial)

    fun i8Var(initial: Int = 0) = U8Delegate(initial) // Use u8 internally

    fun i16Var(initial: Int = 0) = U16Delegate(initial) // Use u16 internally

    fun test(block: TestScope.() -> Unit) {
        testBlock = block
    }

    internal fun executeTests() {
        // Build minimal game with just this scene
        val scene = Scene(sceneName, emptyList(), onFrame, emptyList())
        val game =
            Game(
                name = "test",
                config = GameConfig(),
                variables = variables,
                sprites = emptyList(),
                scenes = mapOf(sceneName to scene),
                startScene = sceneName,
            )

        val simulation = SimulationContext(game)
        val input = MockInputProvider()
        val scope = TestScope(simulation, input)

        testBlock?.invoke(scope)
            ?: error("No test block defined. Use test { ... } to define assertions.")
    }
}

// =============================================================================
// IR VERIFICATION (Advanced)
// =============================================================================

/**
 * Record IR statements for verification.
 *
 * ## Example
 *
 * ```kotlin
 * val ir = recordIR {
 *     playerX += 1
 * }
 * assertTrue(ir.any { it is IRAssign && it.target == "playerX" })
 * ```
 */
inline fun recordIR(crossinline block: () -> Unit): List<IRStatement> {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { block() }
    return recorder.statements
}

/** Check if IR contains a specific statement type. */
inline fun <reified T : IRStatement> List<IRStatement>.containsType(): Boolean {
    return any { it is T }
}

/** Find all statements of a specific type. */
inline fun <reified T : IRStatement> List<IRStatement>.filterType(): List<T> {
    return filterIsInstance<T>()
}

// =============================================================================
// RE-EXPORTS FOR DISCOVERABILITY
// =============================================================================

/**
 * Lightweight testing DSL for unit-testing isolated game logic.
 *
 * This is a re-export of [io.github.gbkt.core.test.testLogic] for discoverability. Unlike
 * [testGame] and [testScene], this allows testing individual logic blocks without building a full
 * game or scene.
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `damage calculation works`() = testLogic {
 *     var health by u8Var(100)
 *     var damage by u8Var(10)
 *
 *     record { health -= damage }
 *         .assertEmitted<IRAssign> { it.target == "health" }
 *
 *     execute()
 *     expect("health").toEqual(90)
 * }
 * ```
 *
 * @see testLogic
 */
@Suppress("unused") val testLogicRef = ::testLogic
