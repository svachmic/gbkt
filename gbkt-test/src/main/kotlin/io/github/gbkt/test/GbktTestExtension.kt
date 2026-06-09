/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.MetadataParseException
import io.github.gbkt.emulator.agent.Observation
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import java.util.logging.Logger
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler

/**
 * JUnit5 extension that manages a [StepAgent] lifecycle for game-level integration tests.
 *
 * Eliminates 50+ lines of boilerplate per test class by:
 * - Auto-discovering ROM, sym, and metadata files via [GameDiscovery]
 * - Auto-skipping tests when the ROM has not been built (`buildRom` not yet run)
 * - Auto-capturing a screenshot and variable dump JSON on test failure
 * - Closing the emulator session after each test
 *
 * Usage:
 * ```kotlin
 * class PongTest {
 *     @JvmField
 *     @RegisterExtension
 *     val game = GbktTestExtension("pong")
 *
 *     @Test
 *     fun `title screen shows PONG text`() {
 *         val obs = game.stepN(120)
 *         assertTextOnScreen(obs, "PONG")
 *     }
 * }
 * ```
 *
 * @param gameName Game name matching the ROM file base name (e.g., `"pong"`).
 * @param customRomFile Optional explicit ROM file path. Overrides convention-based discovery.
 * @param bootScript Optional lambda to run after [agent] starts, useful for skipping to a specific
 *   scene (e.g., pressing START to skip the title screen).
 * @param gbcMode When true, configures the emulator for Game Boy Color mode. Use for ROMs compiled
 *   with GBC_COMPATIBLE or GBC_ONLY target. Defaults to false.
 * @param stubEmulatorFactory Optional factory for injecting a stub [GbEmulator] in unit tests. When
 *   non-null, the [StepAgent] uses this factory instead of creating a real emulator.
 */
class GbktTestExtension(
    val gameName: String,
    private val customRomFile: File? = null,
    private val bootScript: ((StepAgent) -> Unit)? = null,
    private val gbcMode: Boolean = false,
    internal val stubEmulatorFactory: (() -> GbEmulator)? = null,
) : BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

    private val logger = Logger.getLogger(GbktTestExtension::class.java.name)

    /** The active [StepAgent] for this test. Available in test methods after [beforeEach]. */
    lateinit var agent: StepAgent
        private set

    /** Game metadata loaded from `game_metadata.json`, or null if the file was not found. */
    var metadata: GameMetadata? = null
        private set

    /** The screenshot directory used for failure captures. */
    private val screenshotDir: File
        get() = File("build/gbkt/test-failures")

    private var agentInitialized = false

    // ── JUnit5 lifecycle callbacks ────────────────────────────────────────────

    override fun beforeEach(context: ExtensionContext) {
        val config = resolveConfig()
        if (config == null) {
            Assumptions.assumeTrue(false, "ROM not found for game '$gameName' — run buildRom first")
            return
        }

        // Load metadata if available
        metadata =
            config.metadataFile
                ?.takeIf { it.exists() }
                ?.let { file ->
                    try {
                        GameMetadata.fromJsonFile(file)
                    } catch (e: MetadataParseException) {
                        logger.warning("Failed to parse metadata: ${e.message}")
                        null
                    }
                }

        agent = StepAgent(config, metadata, stubEmulatorFactory = stubEmulatorFactory)
        agentInitialized = true
        agent.start()
        bootScript?.invoke(agent)
    }

    override fun afterEach(context: ExtensionContext) {
        if (agentInitialized) {
            agent.close()
            agentInitialized = false
        }
    }

    override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
        if (agentInitialized) {
            val testClass = context.testClass.map { it.simpleName }.orElse("Unknown")
            val testName = context.displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val frame = agent.frameCount
            val label = "failure_${testClass}_${testName}_frame${frame}"

            // Capture screenshot — broad catch is intentional: failure-reporting code must not
            // throw new exceptions that would mask the original test failure.
            @Suppress("TooGenericExceptionCaught")
            try {
                agent.captureScreenshot(label)
            } catch (e: Exception) {
                logger.warning("Failed to capture screenshot: ${e.message}")
            }

            // Dump variables to JSON sidecar — same rationale as the screenshot catch above.
            @Suppress("TooGenericExceptionCaught")
            try {
                screenshotDir.mkdirs()
                val jsonFile = File(screenshotDir, "${label}.json")
                jsonFile.writeText(buildFailureJson(frame))
            } catch (e: Exception) {
                logger.warning("Failed to write failure dump: ${e.message}")
            }
        }
        throw throwable
    }

    // ── Convenience delegation methods ────────────────────────────────────────

    /** @see StepAgent.step */
    fun step(buttons: Set<Button> = emptySet()): Observation = agent.step(buttons)

    /** @see StepAgent.stepN */
    fun stepN(n: Int, buttons: Set<Button> = emptySet()): Observation = agent.stepN(n, buttons)

    /** @see StepAgent.readVariable */
    fun readVariable(name: String): Int? = agent.readVariable(name)

    /** @see StepAgent.writeVariable */
    fun writeVariable(name: String, value: Int): Boolean = agent.writeVariable(name, value)

    /** @see StepAgent.readMemory */
    fun readMemory(address: Int): Int = agent.readMemory(address)

    /**
     * Reads a raw byte from the emulator's address space. Alias for [readMemory] that matches the
     * [io.github.gbkt.emulator.MemoryAccess] naming convention.
     *
     * @param address Hardware address in the range 0x0000–0xFFFF.
     * @return Byte value in range 0–255.
     */
    fun readByte(address: Int): Int = agent.readMemory(address)

    /** @see StepAgent.waitForScene */
    fun waitForScene(name: String, maxFrames: Int = 600): Observation =
        agent.waitForScene(name, maxFrames)

    /** @see StepAgent.captureScreenshot */
    fun captureScreenshot(label: String): File = agent.captureScreenshot(label)

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun resolveConfig(): AgentSessionConfig? {
        val base =
            if (customRomFile != null) {
                if (!customRomFile.exists()) return null
                AgentSessionConfig.discoverFiles(customRomFile, screenshotDir)
            } else {
                GameDiscovery.configForGame(gameName, screenshotDir) ?: return null
            }
        return if (gbcMode) base.copy(gbcMode = true) else base
    }

    @Suppress("TooGenericExceptionCaught")
    private fun buildFailureJson(frame: Int): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"frame\": $frame,\n")
        sb.append("  \"gameName\": \"$gameName\",\n")
        sb.append("  \"variables\": {\n")
        try {
            val varEntries =
                agent.listVariables().mapNotNull { name ->
                    val value = agent.readVariable(name) ?: return@mapNotNull null
                    "    \"$name\": $value"
                }
            sb.append(varEntries.joinToString(",\n"))
            if (varEntries.isNotEmpty()) sb.append("\n")
        } catch (e: Exception) {
            // Broad catch is intentional: variable-dump code embeds error into the JSON sidecar
            // rather than throwing — preserves the dump as evidence even on partial failure.
            // (@Suppress on the enclosing function handles the detekt rule.)
            sb.append("    \"_error\": \"${e.message?.replace("\"", "'")}\"")
            sb.append("\n")
        }
        sb.append("  }\n")
        sb.append("}\n")
        return sb.toString()
    }
}
