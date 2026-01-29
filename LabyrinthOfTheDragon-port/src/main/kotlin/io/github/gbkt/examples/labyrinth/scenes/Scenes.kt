/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SaveDataHandle
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.rpg.BattleSystem
import io.github.gbkt.core.rpg.CombatFormulas
import io.github.gbkt.core.ui.StatusBarHandle
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.Sounds
import io.github.gbkt.examples.labyrinth.StatusIcons
import io.github.gbkt.examples.labyrinth.rpg.Monsters

/**
 * Scene coordinator for Labyrinth of the Dragon.
 *
 * This class coordinates scene initialization using the individual scene files:
 * - [TitleScene.kt][initTitleScene] - Main menu
 * - [HeroSelectScene.kt][initHeroSelectScene] - Character class selection
 * - [GameplayScene.kt][initGameplayScene] - Dungeon exploration
 * - [PauseScene.kt][initPauseScene] - In-game pause menu
 * - [BattleScene.kt][initBattleScene] - Turn-based combat
 * - [GameOverScene.kt][initGameOverScene] - Defeat screen
 * - [VictoryScene.kt][initVictoryScene] - Win screen
 * - [CreditsScene.kt][initCreditsScene] - Credits roll
 * - [SettingsScene.kt][initSettingsScene] - Audio settings
 *
 * Scene structure:
 * - Title: Main menu with New Game / Continue / Options
 * - HeroSelect: Choose character class (Druid, Fighter, Monk, Sorcerer)
 * - Gameplay: Dungeon exploration mode
 * - Pause: Save/Load/Quit menu
 * - Battle: Turn-based combat encounters
 * - GameOver: Defeat screen
 * - Victory: Win screen after defeating the Dragon
 * - Credits: Story and credits roll
 * - Settings: Volume and SFX configuration
 */
@Suppress("LongParameterList")
class Scenes(
    private val builder: GameBuilder,
    private val state: GameState,
    private val battleState: BattleSceneState,
    private val creditsState: CreditsSceneState,
    private val settingsState: SettingsSceneState,
    private val combatSystem: BattleSystem,
    private val combatFormulas: CombatFormulas,
    private val saveData: SaveDataHandle,
    private val monsters: Monsters,
    private val gameFlags: GlobalFlags,
    private val sounds: Sounds,
    private val camera: Camera,
    private val monsterPalette: Palette,
    private val monster1HpBar: StatusBarHandle,
    private val monster2HpBar: StatusBarHandle,
    private val monster3HpBar: StatusBarHandle,
    private val statusIcons: StatusIcons,
) {

    // Pre-initialize SceneRefs with their names to handle circular references.
    // The actual scene content is built in initAll() but the refs are already valid.
    var title: SceneRef = SceneRef("title")
    var heroSelect: SceneRef = SceneRef("hero_select")
    var gameplay: SceneRef = SceneRef("gameplay")
    var pause: SceneRef = SceneRef("pause")
    var battle: SceneRef = SceneRef("battle")
    var gameOver: SceneRef = SceneRef("gameover")
    var victory: SceneRef = SceneRef("victory")
    var credits: SceneRef = SceneRef("credits")
    var settings: SceneRef = SceneRef("settings")

    /**
     * Initialize all scenes.
     *
     * Must be called after construction. Order matters for forward references:
     * 1. Gameplay (referenced by heroSelect, battle, pause)
     * 2. Pause (referenced by gameplay)
     * 3. HeroSelect (referenced by title)
     * 4. Settings (referenced by title)
     * 5. Title (referenced by gameOver, credits, heroSelect, pause, settings)
     * 6. Battle, GameOver, Victory, Credits
     */
    fun initAll() {
        // Initialize scenes in dependency order using the individual scene files
        // Note: gameplay needs battle ref for boss encounters, so we pass the pre-initialized ref
        gameplay = builder.initGameplayScene(state, pause, battle, monsters, gameFlags)
        pause = builder.initPauseScene(state, saveData, title, gameplay)
        heroSelect = builder.initHeroSelectScene(state, title, gameplay)
        settings = builder.initSettingsScene(settingsState, sounds, title)
        title = builder.initTitleScene(state, sounds, heroSelect, settings)
        battle =
            builder.initBattleScene(
                state,
                battleState,
                combatSystem,
                sounds,
                gameplay,
                camera,
                monsterPalette,
                monster1HpBar,
                monster2HpBar,
                monster3HpBar,
                statusIcons,
            )
        gameOver = builder.initGameOverScene(title)
        credits = builder.initCreditsScene(creditsState, title, camera)
        victory = builder.initVictoryScene(credits) // Victory now goes to credits
    }
}
