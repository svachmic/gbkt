/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.flow

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.dsl.GbktDsl

// =============================================================================
// GAME FLOW SYSTEM
// Orchestrates standard game state sequences (title → gameplay → gameover)
// =============================================================================

/** Standard game scenes that can be registered with the game flow. */
enum class GameFlowScene {
    TITLE,
    CHARACTER_SELECT,
    GAMEPLAY,
    BATTLE,
    PAUSE,
    GAME_OVER,
    VICTORY,
    CREDITS,
}

/**
 * Configuration for the game flow system.
 *
 * Defines the standard scenes and transitions for the game. This enables:
 * - Automatic scene routing (gotoGameplay(), gotoGameOver(), etc.)
 * - Development shortcuts (startAt scene for testing)
 * - Consistent scene management across the game
 */
data class GameFlowConfig(
    /** Title screen scene */
    val titleScene: SceneRef? = null,

    /** Character selection scene (optional) */
    val characterSelectScene: SceneRef? = null,

    /** Main gameplay scene */
    val gameplayScene: SceneRef? = null,

    /** Turn-based battle scene (optional) */
    val battleScene: SceneRef? = null,

    /** Pause menu scene (optional, can be overlay) */
    val pauseScene: SceneRef? = null,

    /** Game over scene */
    val gameOverScene: SceneRef? = null,

    /** Victory/credits scene (optional) */
    val victoryScene: SceneRef? = null,

    /** Credits scene (optional) */
    val creditsScene: SceneRef? = null,

    /** Scene to start at in development mode (bypasses title) */
    val devStartScene: SceneRef? = null,

    /** Whether development mode is enabled */
    val devModeEnabled: Boolean = false,
)

/**
 * Builder for game flow configuration.
 *
 * Usage:
 * ```kotlin
 * val game = gbGame("MyRPG") {
 *     gameFlow {
 *         titleScreen(titleScene)
 *         characterSelect(selectScene)  // Optional
 *         gameplay(gameplayScene)
 *         battle(battleScene)
 *         gameOver(gameOverScene)
 *         victory(victoryScene)
 *         credits(creditsScene)
 *
 *         devMode {
 *             startAt(gameplayScene)  // Skip title during dev
 *         }
 *     }
 * }
 * ```
 */
@GbktDsl
class GameFlowBuilder {
    private var titleScene: SceneRef? = null
    private var characterSelectScene: SceneRef? = null
    private var gameplayScene: SceneRef? = null
    private var battleScene: SceneRef? = null
    private var pauseScene: SceneRef? = null
    private var gameOverScene: SceneRef? = null
    private var victoryScene: SceneRef? = null
    private var creditsScene: SceneRef? = null
    private var devStartScene: SceneRef? = null
    private var devModeEnabled: Boolean = false

    // =========================================================================
    // SCENE REGISTRATION
    // =========================================================================

    /**
     * Register the title screen scene.
     *
     * This is typically the first scene shown when the game starts.
     */
    fun titleScreen(scene: SceneRef) {
        titleScene = scene
    }

    /**
     * Register the character selection scene (optional).
     *
     * Shown after title, before gameplay, if character selection is needed.
     */
    fun characterSelect(scene: SceneRef) {
        characterSelectScene = scene
    }

    /**
     * Register the main gameplay scene.
     *
     * This is where the core game loop happens.
     */
    fun gameplay(scene: SceneRef) {
        gameplayScene = scene
    }

    /**
     * Register the battle scene (optional).
     *
     * For games with separate battle screens (like JRPGs).
     */
    fun battle(scene: SceneRef) {
        battleScene = scene
    }

    /**
     * Register the pause menu scene (optional).
     *
     * Can be a separate scene or an overlay.
     */
    fun pause(scene: SceneRef) {
        pauseScene = scene
    }

    /**
     * Register the game over scene.
     *
     * Shown when the player loses.
     */
    fun gameOver(scene: SceneRef) {
        gameOverScene = scene
    }

    /**
     * Register the victory scene (optional).
     *
     * Shown when the player wins the game.
     */
    fun victory(scene: SceneRef) {
        victoryScene = scene
    }

    /**
     * Register the credits scene (optional).
     *
     * Shown after victory or accessible from title.
     */
    fun credits(scene: SceneRef) {
        creditsScene = scene
    }

    // =========================================================================
    // DEVELOPMENT MODE
    // =========================================================================

    /**
     * Configure development mode settings.
     *
     * Dev mode allows skipping scenes for faster iteration.
     */
    fun devMode(init: DevModeBuilder.() -> Unit) {
        val builder = DevModeBuilder()
        builder.init()
        devModeEnabled = true
        devStartScene = builder.startScene
    }

    internal fun build(): GameFlowConfig =
        GameFlowConfig(
            titleScene = titleScene,
            characterSelectScene = characterSelectScene,
            gameplayScene = gameplayScene,
            battleScene = battleScene,
            pauseScene = pauseScene,
            gameOverScene = gameOverScene,
            victoryScene = victoryScene,
            creditsScene = creditsScene,
            devStartScene = devStartScene,
            devModeEnabled = devModeEnabled,
        )
}

/** Builder for development mode settings. */
@GbktDsl
class DevModeBuilder {
    internal var startScene: SceneRef? = null

    /**
     * Skip directly to this scene when starting in dev mode.
     *
     * Useful for testing specific scenes without going through title/menus.
     */
    fun startAt(scene: SceneRef) {
        startScene = scene
    }
}

/**
 * Handle for runtime game flow operations.
 *
 * This provides type-safe methods to navigate between standard game scenes.
 *
 * Usage in scene logic:
 * ```kotlin
 * scene("title") {
 *     enter { mainMenu.show() }
 *     every.frame {
 *         mainMenu.tick()
 *         whenever(mainMenu.selectedIndex isEqualTo 0) {
 *             // Start selected
 *             flow.gotoCharacterSelect()
 *         }
 *     }
 * }
 * ```
 */
class GameFlowHandle internal constructor(private val config: GameFlowConfig) {
    /** Get the scene to start at (respects dev mode if enabled). */
    fun getStartScene(): SceneRef? {
        if (config.devModeEnabled && config.devStartScene != null) {
            return config.devStartScene
        }
        return config.titleScene ?: config.gameplayScene
    }

    /** Check if a standard scene is registered. */
    fun hasScene(scene: GameFlowScene): Boolean =
        when (scene) {
            GameFlowScene.TITLE -> config.titleScene != null
            GameFlowScene.CHARACTER_SELECT -> config.characterSelectScene != null
            GameFlowScene.GAMEPLAY -> config.gameplayScene != null
            GameFlowScene.BATTLE -> config.battleScene != null
            GameFlowScene.PAUSE -> config.pauseScene != null
            GameFlowScene.GAME_OVER -> config.gameOverScene != null
            GameFlowScene.VICTORY -> config.victoryScene != null
            GameFlowScene.CREDITS -> config.creditsScene != null
        }

    /** Get a standard scene reference. */
    fun getScene(scene: GameFlowScene): SceneRef? =
        when (scene) {
            GameFlowScene.TITLE -> config.titleScene
            GameFlowScene.CHARACTER_SELECT -> config.characterSelectScene
            GameFlowScene.GAMEPLAY -> config.gameplayScene
            GameFlowScene.BATTLE -> config.battleScene
            GameFlowScene.PAUSE -> config.pauseScene
            GameFlowScene.GAME_OVER -> config.gameOverScene
            GameFlowScene.VICTORY -> config.victoryScene
            GameFlowScene.CREDITS -> config.creditsScene
        }

    /** Whether dev mode is enabled. */
    val isDevMode: Boolean
        get() = config.devModeEnabled
}

/**
 * Create a game flow configuration.
 *
 * @param init Builder initialization block
 * @return The configured game flow handle
 */
fun gameFlow(init: GameFlowBuilder.() -> Unit): GameFlowHandle {
    val builder = GameFlowBuilder()
    builder.init()
    return GameFlowHandle(builder.build())
}
