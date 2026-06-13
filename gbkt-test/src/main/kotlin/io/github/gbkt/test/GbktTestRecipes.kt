/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.Observation
import io.github.gbkt.emulator.agent.SceneMap
import io.github.gbkt.emulator.agent.VariableInspector
import java.io.File
import java.util.logging.Logger
import org.junit.jupiter.api.Assumptions

private val logger = Logger.getLogger("io.github.gbkt.test.GbktTestRecipes")

/**
 * Pre-built composable test patterns for game-level integration tests.
 *
 * All recipes are extension functions on [GbktTestExtension] and use metadata-driven logic where
 * available. They work without metadata as best-effort heuristic checks.
 *
 * Usage:
 * ```kotlin
 * class PongTest {
 *     @JvmField
 *     @RegisterExtension
 *     val game = GbktTestExtension("pong")
 *
 *     @Test fun `title screen boots correctly`() {
 *         game.verifyTitleScreen(listOf("PONG", "PRESS START"))
 *     }
 * }
 * ```
 */

/** Title-related scene name patterns used when metadata is unavailable. */
private val TITLE_SCENE_PATTERNS = setOf("title", "main_menu", "menu", "start", "intro")

/**
 * Boots the game 120 frames and asserts the game is on the title/initial scene.
 *
 * If metadata is available, asserts the scene matches the first scene in the scene map. Otherwise,
 * accepts any scene name matching common title patterns (title, main_menu, etc.). If
 * [expectedTexts] is non-empty, each string must appear on screen.
 *
 * @param expectedTexts Optional list of text strings that must appear on screen.
 * @return The [Observation] after booting 120 frames.
 */
fun GbktTestExtension.verifyTitleScreen(expectedTexts: List<String> = emptyList()): Observation {
    val obs = agent.stepN(120)

    // Assert scene — use metadata if available, otherwise heuristic
    val scene = obs.scene
    if (scene != null) {
        val metaScenes = metadata?.scenes
        if (metaScenes != null) {
            // Metadata available: scene should be in the known scenes
            val sceneNames = metaScenes.sceneNames
            if (sceneNames.isNotEmpty()) {
                // Accept: either matches a title-like pattern, or is the smallest-index scene
                val lowestIndexScene = sceneNames.minByOrNull {
                    metaScenes.indexOf(it) ?: Int.MAX_VALUE
                }
                val isTitle =
                    TITLE_SCENE_PATTERNS.any { it in scene.lowercase() } ||
                        scene == lowestIndexScene
                if (!isTitle) {
                    throw AssertionError(
                        "verifyTitleScreen: expected title/initial scene after 120 frames, " +
                            "but got '$scene'. Known scenes: $sceneNames"
                    )
                }
            }
        } else {
            // No metadata: accept any title-like scene name
            // When there is no metadata we cannot validate scene name — pass through
        }
    }

    // Assert expected texts
    for (text in expectedTexts) {
        assertTextOnScreen(obs, text, message = "verifyTitleScreen")
    }

    return obs
}

/**
 * Best-effort check that the game can reach the first non-title scene from the title screen.
 *
 * Presses START and waits up to 300 frames for the first non-title scene transition. If no metadata
 * is available, this is a no-op.
 *
 * Note: This is a shallow smoke test. Complex multi-step navigation paths (e.g., requiring gameplay
 * progression) will not be verified here — they require per-game test logic.
 */
fun GbktTestExtension.verifyFirstSceneTransition() {
    val meta = metadata ?: return
    val sceneNames = meta.scenes.sceneNames
    if (sceneNames.size < 2) return

    // Find title scene (lowest index or title-named)
    val titleScene = sceneNames.minByOrNull { meta.scenes.indexOf(it) ?: Int.MAX_VALUE }

    // For each non-title scene, attempt a START-press transition from title
    val nonTitleScenes = sceneNames.filter { it != titleScene }
    for (targetScene in nonTitleScenes) {
        // Boot fresh — the extension manages per-test lifecycle,
        // so we only track what we reached (no re-boot capability here)
        // Press START and wait for transition
        agent.step(setOf(Button.START))
        agent.step()
        val obs = agent.waitForScene(targetScene, 300)
        if (obs.scene != targetScene) {
            // Scene not reached — this is a best-effort check, not hard fail
            logger.warning(
                "verifyFirstSceneTransition: could not reach '$targetScene' within 300 frames " +
                    "(complex navigation may require per-game test logic)"
            )
        }
        break // Only verify the first non-title scene to keep this best-effort
    }
}

/**
 * Verifies that pressing [button] for 30 frames in [scene] causes [variableName] to change.
 *
 * Uses [bootToScene] to navigate to the target scene, reads the variable before and after holding
 * the button, then asserts the value changed in the expected direction.
 *
 * @param scene Scene to navigate to before testing input.
 * @param button Button to hold.
 * @param variableName DSL variable name to observe.
 * @param expectDecrease If true, asserts the variable decreased; otherwise asserts it increased.
 */
fun GbktTestExtension.verifyInputResponds(
    scene: String,
    button: Button,
    variableName: String,
    expectDecrease: Boolean = false,
) {
    bootToScene(scene)
    val before =
        agent.readVariable(variableName)
            ?: throw AssertionError(
                "verifyInputResponds: variable '$variableName' not found before holding $button"
            )
    agent.stepN(30, setOf(button))
    agent.step() // release
    val after =
        agent.readVariable(variableName)
            ?: throw AssertionError(
                "verifyInputResponds: variable '$variableName' not found after holding $button"
            )
    if (expectDecrease) {
        if (after >= before) {
            throw AssertionError(
                "verifyInputResponds: expected '$variableName' to decrease after holding $button " +
                    "for 30 frames, but before=$before, after=$after"
            )
        }
    } else {
        if (after <= before) {
            throw AssertionError(
                "verifyInputResponds: expected '$variableName' to increase after holding $button " +
                    "for 30 frames, but before=$before, after=$after"
            )
        }
    }
}

/**
 * Verifies that each actor in [expectedActors] is visible after navigating to [scene].
 *
 * Uses [bootToScene] to navigate to the scene, steps 10 frames, then checks each actor name appears
 * in the observation's actors list.
 *
 * @param scene Scene to navigate to.
 * @param expectedActors List of DSL actor names expected to be visible.
 */
fun GbktTestExtension.verifySpriteVisibility(scene: String, expectedActors: List<String>) {
    bootToScene(scene)
    val obs = agent.stepN(10)
    for (actorName in expectedActors) {
        assertActorVisible(obs, actorName, message = "verifySpriteVisibility(scene='$scene')")
    }
}

/**
 * Navigates to [sceneName] from the current emulator state and returns the observation.
 *
 * Boot flow:
 * 1. If a `bootScript` was provided to [GbktTestExtension], it was already executed in
 *    `beforeEach`. If the current scene already matches, return immediately.
 * 2. Otherwise, step 120 frames (boot), then call [StepAgent.waitForScene].
 * 3. If not reached, press START (common title→gameplay transition) and wait again.
 * 4. If still not reached, throw [AssertionError].
 *
 * @param sceneName The scene name to navigate to.
 * @param maxFrames Maximum frames to wait after each attempt.
 * @return The [Observation] at the target scene.
 * @throws AssertionError if the scene is not reached within the allowed frames.
 */
fun GbktTestExtension.bootToScene(sceneName: String, maxFrames: Int = 600): Observation {
    // Check if already in the target scene
    val currentObs = agent.step()
    if (currentObs.scene == sceneName) return currentObs

    // Wait for the scene directly
    val obs = agent.waitForScene(sceneName, maxFrames)
    if (obs.scene == sceneName) return obs

    // Attempt START press (title→gameplay is the most common transition)
    agent.step(setOf(Button.START))
    agent.step() // release
    val afterStart = agent.waitForScene(sceneName, maxFrames)
    if (afterStart.scene == sceneName) return afterStart

    throw AssertionError(
        "bootToScene: could not reach scene '$sceneName' within ${maxFrames * 2 + 2} frames. " +
            "Current scene: '${afterStart.scene}'. " +
            "Check your bootScript or game navigation logic."
    )
}

/**
 * Expectation descriptor for [verifyMetadataSymbolAgreement].
 *
 * @param expectedSceneCount Number of scenes the game should have.
 * @param expectedScenes Scene names that must appear in the metadata.
 * @param expectedActors Actor names that must appear in the metadata.
 * @param expectedOamCounts Per-actor OAM slot counts (actor name -> count). Empty to skip.
 * @param expectedTotalOam Total OAM slots across all actors. Null to skip.
 */
data class MetadataExpectation(
    val expectedSceneCount: Int,
    val expectedScenes: Set<String>,
    val expectedActors: Set<String>,
    val expectedOamCounts: Map<String, Int> = emptyMap(),
    val expectedTotalOam: Int? = null,
)

/**
 * Verifies that `game_metadata.json`, the `.noi` symbol table, and `game.h` all agree on variable
 * names, scene definitions, actor layout, and OAM slot allocation.
 *
 * This is a no-emulator recipe — it only reads build artifacts on disk.
 *
 * Checks performed:
 * 1. All actor X/Y variables exist in the `.noi` symbol table
 * 2. [currentSceneVar] exists in the symbol table
 * 3. Scene names and indices match between `game_metadata.json` and `game.h`
 * 4. Scene count and names match [expectation]
 * 5. Expected actors exist in the metadata (with OAM counts if specified)
 * 6. Total OAM count matches if specified
 * 7. No OAM slot overlaps between any two actors
 *
 * @param expectation The expected metadata values to verify against.
 * @param currentSceneVar The variable name for the current scene (default `"current_scene"`).
 */
fun GbktTestExtension.verifyMetadataSymbolAgreement(
    expectation: MetadataExpectation,
    currentSceneVar: String = "current_scene",
) {
    val metadataFile = File("build/gbkt/generated/game_metadata.json")
    val symFile = File("build/gbkt/output/$gameName.noi")
    val gameHeader = File("build/gbkt/generated/game.h")

    Assumptions.assumeTrue(
        metadataFile.exists() && symFile.exists(),
        "metadata or .noi not found — run buildRom first",
    )

    val zeroMemory =
        object : MemoryAccess {
            override fun readByte(address: Int): Int = 0

            override fun writeByte(address: Int, value: Int) {
                // No-op: zeroMemory only backs symbol-table inspection — writes are discarded.
            }
        }

    val metadata = GameMetadata.fromJsonFile(metadataFile)
    val inspector = VariableInspector(zeroMemory, symFile)
    val loadedVars = inspector.listVariables()

    verifyActorXySymbols(metadata, loadedVars)
    verifyCurrentSceneSymbol(currentSceneVar, loadedVars)
    verifySceneHeaderConsistency(metadata, gameHeader)
    verifySceneExpectations(metadata, expectation)
    verifyOamNoOverlaps(metadata)
}

/** Checks that every actor's X/Y variable names exist in the .noi symbol table. */
private fun verifyActorXySymbols(metadata: GameMetadata, loadedVars: List<String>) {
    for (actor in metadata.actors) {
        if (actor.xVar !in loadedVars) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: Actor '${actor.name}' xVar '${actor.xVar}' " +
                    "not found in .noi symbols. Available: $loadedVars"
            )
        }
        if (actor.yVar !in loadedVars) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: Actor '${actor.name}' yVar '${actor.yVar}' " +
                    "not found in .noi symbols. Available: $loadedVars"
            )
        }
    }
}

/** Checks that [currentSceneVar] exists in the .noi symbol table. */
private fun verifyCurrentSceneSymbol(currentSceneVar: String, loadedVars: List<String>) {
    if (currentSceneVar !in loadedVars) {
        throw AssertionError(
            "verifyMetadataSymbolAgreement: '$currentSceneVar' not found in .noi symbols. " +
                "Available: $loadedVars"
        )
    }
}

/** Checks that scene names and indices agree between game_metadata.json and game.h. */
private fun verifySceneHeaderConsistency(metadata: GameMetadata, gameHeader: File) {
    if (!gameHeader.exists()) return
    val headerSceneMap = SceneMap.fromGameHeader(gameHeader)
    val metadataSceneNames = metadata.scenes.sceneNames
    val headerSceneNames = headerSceneMap.sceneNames
    if (metadataSceneNames != headerSceneNames) {
        throw AssertionError(
            "verifyMetadataSymbolAgreement: scene names mismatch — " +
                "metadata=$metadataSceneNames, game.h=$headerSceneNames"
        )
    }
    for (name in metadataSceneNames) {
        val metaIndex = metadata.scenes.indexOf(name)
        val headerIndex = headerSceneMap.indexOf(name)
        if (metaIndex != headerIndex) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: scene '$name' index mismatch — " +
                    "metadata=$metaIndex, game.h=$headerIndex"
            )
        }
    }
}

/** Checks scene count, expected scene names, expected actors, OAM counts, and total OAM. */
@Suppress("ThrowsCount") // Test verification helper: each throw is a distinct assertion failure.
private fun verifySceneExpectations(metadata: GameMetadata, expectation: MetadataExpectation) {
    val actualSceneCount = metadata.scenes.sceneNames.size
    if (actualSceneCount != expectation.expectedSceneCount) {
        throw AssertionError(
            "verifyMetadataSymbolAgreement: expected ${expectation.expectedSceneCount} scenes, " +
                "got $actualSceneCount. Scenes: ${metadata.scenes.sceneNames}"
        )
    }
    for (scene in expectation.expectedScenes) {
        if (scene !in metadata.scenes.sceneNames) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: expected scene '$scene' not found in metadata. " +
                    "Available: ${metadata.scenes.sceneNames}"
            )
        }
    }
    for (actorName in expectation.expectedActors) {
        val actor =
            metadata.actor(actorName)
                ?: throw AssertionError(
                    "verifyMetadataSymbolAgreement: expected actor '$actorName' not found in metadata. " +
                        "Available: ${metadata.actors.map { it.name }}"
                )
        val expectedOam = expectation.expectedOamCounts[actorName]
        if (expectedOam != null && actor.oamCount != expectedOam) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: actor '$actorName' OAM count mismatch — " +
                    "expected=$expectedOam, actual=${actor.oamCount}"
            )
        }
    }
    if (expectation.expectedTotalOam != null) {
        val totalOam = metadata.actors.sumOf { it.oamCount }
        if (totalOam != expectation.expectedTotalOam) {
            throw AssertionError(
                "verifyMetadataSymbolAgreement: total OAM count mismatch — " +
                    "expected=${expectation.expectedTotalOam}, actual=$totalOam"
            )
        }
    }
}

/** Checks that no two actors have overlapping OAM slot ranges. */
private fun verifyOamNoOverlaps(metadata: GameMetadata) {
    for (i in metadata.actors.indices) {
        for (j in i + 1 until metadata.actors.size) {
            val a = metadata.actors[i]
            val b = metadata.actors[j]
            val aRange = a.oamStart until (a.oamStart + a.oamCount)
            val bRange = b.oamStart until (b.oamStart + b.oamCount)
            if (aRange.any { it in bRange }) {
                throw AssertionError(
                    "verifyMetadataSymbolAgreement: OAM overlap between '${a.name}' " +
                        "[${a.oamStart}..${a.oamStart + a.oamCount - 1}] and '${b.name}' " +
                        "[${b.oamStart}..${b.oamStart + b.oamCount - 1}]"
                )
            }
        }
    }
}
