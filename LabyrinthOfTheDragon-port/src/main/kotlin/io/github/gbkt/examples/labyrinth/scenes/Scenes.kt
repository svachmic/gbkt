/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.LabyrinthSounds
import io.github.gbkt.examples.labyrinth.rpg.LabyrinthCombatSystem

/**
 * Scene coordinator for Labyrinth of the Dragon.
 *
 * Provides forward-declared [SceneRef] objects for all game scenes. These typed refs allow any
 * scene to navigate to any other scene without depending on string literals or scene declaration
 * order.
 *
 * ## Usage
 *
 * All scene navigation uses `Scenes.*` refs:
 * ```kotlin
 * navigate(Scenes.heroSelectRef)   // type-safe, zero magic strings
 * ```
 *
 * ## Registration
 *
 * Call [register] inside the `game { }` block to register all non-gameplay scenes:
 * ```kotlin
 * game("LabyrinthDragon") {
 *     val sounds = defineSounds()
 *     Scenes.register(this, sounds)
 * }
 * ```
 *
 * Gameplay-side scenes (GameplayScene, BattleScene, PauseScene) are wired in plans 12–16.
 *
 * Original reference: `main.c` — `GameState` enum and scene transition logic.
 */
object Scenes {

    // -------------------------------------------------------------------------
    // Forward-declared typed scene refs — cross-scene navigation without string literals
    // @source main.c — typedef enum GameState { GAME_STATE_TITLE, GAME_STATE_HERO_SELECT, etc. }
    // -------------------------------------------------------------------------

    /** Ref to the title screen scene. @source title_screen.c — `init_title_screen()` */
    val titleRef: SceneRef = SceneRef("title")

    /** Ref to the hero selection scene. @source hero_select.c — `init_hero_select()` */
    val heroSelectRef: SceneRef = SceneRef("hero_select")

    /** Ref to the main dungeon exploration scene. @source map.c — `init_world_map()` */
    val gameplayRef: SceneRef = SceneRef("gameplay")

    /** Ref to the turn-based battle scene. @source battle.c — battle state machine */
    val battleRef: SceneRef = SceneRef("battle")

    /** Ref to the pause/menu overlay scene. @source map.c — MAP_MENU_OPEN state */
    val pauseRef: SceneRef = SceneRef("pause")

    /** Ref to the game over scene. @source main.c — GAME_STATE_CREDITS (defeat path) */
    val gameOverRef: SceneRef = SceneRef("gameover")

    /** Ref to the victory/credits scene. @source credits.c — `init_credits()` */
    val victoryRef: SceneRef = SceneRef("victory")

    /**
     * Registers all game scenes into the [GameBuilder].
     *
     * Scene registration order follows reverse-navigation convention: gameover/victory → battle →
     * pause → gameplay → heroSelect → title.
     *
     * @param builder The active [GameBuilder] — must be called inside a `game { }` lambda.
     * @param sounds Typed sound refs from [GameBuilder.defineSounds] for SFX wiring.
     * @param combatSystem Typed [LabyrinthCombatSystem] for battle scene wiring.
     * @param state Typed [GameState] for runtime variable refs in battle scene.
     */
    fun register(
        builder: GameBuilder,
        sounds: LabyrinthSounds,
        combatSystem: LabyrinthCombatSystem,
        state: GameState,
    ) {
        builder.apply {
            // Non-gameplay scenes (registered first — navigated to from gameplay scenes)
            GameOverScene.register(this, sounds)
            VictoryScene.register(this, sounds)

            // Battle scene (Plan 12) — uses BattleRef from combatSystem
            BattleScene.register(
                this,
                combat = combatSystem.combat,
                sounds = sounds,
                state = state,
                gameplayRef = gameplayRef,
                gameOverRef = gameOverRef,
            )

            // Pause scene (Plan 12b) — save/load/return menu
            PauseScene.register(this, sounds)

            // Gameplay scene (Plan 12b) — core exploration loop
            GameplayScene.register(this, sounds, combatSystem)

            // Hero select scene (Plan 11)
            HeroSelectScene.register(this, sounds)

            // Title scene (Plan 11) — registered last (navigates only forward)
            TitleScene.register(this, sounds)
        }
    }
}
