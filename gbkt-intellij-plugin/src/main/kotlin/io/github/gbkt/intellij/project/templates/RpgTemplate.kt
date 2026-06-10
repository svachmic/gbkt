/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.project.templates

/**
 * RPG template - a turn-based RPG starter.
 *
 * Creates a project structure with:
 * - Multi-file organization (rpg/, scenes/, world/)
 * - Character and monster definitions
 * - Battle system
 * - Dungeon exploration
 */
object RpgTemplate : GameTemplate {
    override val displayName: String = "RPG (Turn-Based)"
    override val description: String = "Turn-based RPG with battles, items, and dungeons"

    override fun generateMainSource(gameName: String, packageName: String): String =
        """
        |/*
        | * $gameName - Created with gbkt
        | */
        |package $packageName
        |
        |import io.github.gbkt.core.*
        |import io.github.gbkt.core.dsl.*
        |import io.github.gbkt.core.flow.*
        |import io.github.gbkt.core.rpg.*
        |import $packageName.rpg.*
        |import $packageName.scenes.*
        |
        |fun main() {
        |    val game = gbGame("$gameName") {
        |        // Register RPG systems
        |        registerCharacters()
        |        registerMonsters()
        |        registerAbilities()
        |        registerItems()
        |        registerBattleSystem()
        |
        |        // Register scenes
        |        val titleScene = registerTitleScene()
        |        val gameplayScene = registerGameplayScene()
        |        val battleScene = registerBattleScene()
        |        val gameOverScene = registerGameOverScene()
        |
        |        // Game flow
        |        gameFlow {
        |            titleScreen(titleScene)
        |            gameplay(gameplayScene)
        |            battle(battleScene)
        |            gameOver(gameOverScene)
        |        }
        |
        |        start = titleScene
        |    }
        |
        |    game.build()
        |}
    """
            .trimMargin()

    override fun getAdditionalSources(gameName: String, packageName: String): Map<String, String> =
        mapOf(
            "rpg/Characters.kt" to generateCharactersFile(packageName),
            "rpg/Monsters.kt" to generateMonstersFile(packageName),
            "rpg/Abilities.kt" to generateAbilitiesFile(packageName),
            "rpg/Items.kt" to generateItemsFile(packageName),
            "rpg/BattleSystem.kt" to generateBattleSystemFile(packageName),
            "scenes/TitleScene.kt" to generateTitleSceneFile(packageName),
            "scenes/GameplayScene.kt" to generateGameplaySceneFile(packageName),
            "scenes/BattleScene.kt" to generateBattleSceneFile(packageName),
            "scenes/GameOverScene.kt" to generateGameOverSceneFile(packageName),
        )

    override fun getSampleAssets(): Map<String, AssetDescription> =
        mapOf(
            "sprites/hero.png" to
                AssetDescription.Placeholder("16x16 hero sprite sheet (4 directions)"),
            "sprites/monsters.png" to AssetDescription.Placeholder("Monster sprite sheet"),
            "tilemaps/dungeon.tmx" to
                AssetDescription.Tilemap(32, 32, "Dungeon tilemap (Tiled format)"),
            "tilemaps/dungeon_tiles.png" to AssetDescription.Placeholder("8x8 dungeon tileset"),
        )

    private fun generateCharactersFile(packageName: String): String =
        """
        |/*
        | * Character definitions
        | */
        |package $packageName.rpg
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerCharacters() {
        |    val hero by character {
        |        name("Hero")
        |        stats {
        |            hp(100)
        |            sp(30)
        |            atk(12)
        |            def(10)
        |            matk(8)
        |            mdef(8)
        |            agl(10)
        |        }
        |        level(1, maxLevel = 50, expCurve = ExpCurve.STANDARD)
        |        onLevelUp {
        |            stats.hp += 8
        |            stats.sp += 3
        |            stats.atk += 2
        |            stats.def += 1
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateMonstersFile(packageName: String): String =
        """
        |/*
        | * Monster definitions
        | */
        |package $packageName.rpg
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerMonsters() {
        |    val slime by monster {
        |        name("Slime")
        |        tier(MonsterTier.COMMON)
        |        baseStats {
        |            hp(20)
        |            atk(5)
        |            def(3)
        |            agl(8)
        |        }
        |        exp(10)
        |        drops {
        |            gold(5..15)
        |        }
        |    }
        |
        |    val goblin by monster {
        |        name("Goblin")
        |        tier(MonsterTier.COMMON)
        |        baseStats {
        |            hp(35)
        |            atk(10)
        |            def(5)
        |            agl(12)
        |        }
        |        exp(20)
        |        drops {
        |            gold(10..25)
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateAbilitiesFile(packageName: String): String =
        """
        |/*
        | * Ability definitions
        | */
        |package $packageName.rpg
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerAbilities() {
        |    val attack by ability {
        |        name("Attack")
        |        targeting(TargetingMode.SINGLE_ENEMY)
        |        execute {
        |            target.damage(caster.atk, Aspect.PHYSICAL)
        |        }
        |    }
        |
        |    val heal by ability {
        |        name("Heal")
        |        cost(sp = 5)
        |        targeting(TargetingMode.SINGLE_ALLY)
        |        execute {
        |            target.heal(20)
        |        }
        |    }
        |
        |    val fireball by ability {
        |        name("Fireball")
        |        cost(sp = 10)
        |        targeting(TargetingMode.SINGLE_ENEMY)
        |        aspect(Aspect.FIRE)
        |        execute {
        |            target.damage(caster.matk * 2, Aspect.FIRE)
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateItemsFile(packageName: String): String =
        """
        |/*
        | * Item definitions
        | */
        |package $packageName.rpg
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerItems() {
        |    val potion by item {
        |        name("Potion")
        |        category(ItemCategory.CONSUMABLE)
        |        maxStack(10)
        |        buyPrice(50)
        |        onUse {
        |            target.heal(30)
        |        }
        |    }
        |
        |    val ether by item {
        |        name("Ether")
        |        category(ItemCategory.CONSUMABLE)
        |        maxStack(10)
        |        buyPrice(100)
        |        onUse {
        |            target.restoreSp(20)
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateBattleSystemFile(packageName: String): String =
        """
        |/*
        | * Battle system configuration
        | */
        |package $packageName.rpg
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerBattleSystem() {
        |    val combat by battle("combat") {
        |        maxPartySize(4)
        |        maxEnemies(3)
        |        turnOrder(TurnOrderStrategy.SPEED_BASED)
        |
        |        presentation {
        |            damageNumbers(true)
        |            screenShakeOnCrit(4, 8)
        |            actionMessages(true)
        |        }
        |
        |        onState(BattleState.VICTORY) {
        |            awardExp()
        |            awardDrops()
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateTitleSceneFile(packageName: String): String =
        """
        |/*
        | * Title screen scene
        | */
        |package $packageName.scenes
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.scene.*
        |import io.github.gbkt.core.ui.*
        |
        |fun GameBuilder.registerTitleScene(): SceneRef {
        |    return scene("title") {
        |        val titleMenu = menu("titleMenu") {
        |            item("NEW GAME") { /* Start new game */ }
        |            item("CONTINUE") { /* Load save */ }
        |        }
        |
        |        enter {
        |            titleMenu.show()
        |        }
        |
        |        every.frame {
        |            titleMenu.update()
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateGameplaySceneFile(packageName: String): String =
        """
        |/*
        | * Main gameplay/exploration scene
        | */
        |package $packageName.scenes
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.dsl.*
        |import io.github.gbkt.core.scene.*
        |
        |fun GameBuilder.registerGameplayScene(): SceneRef {
        |    return scene("gameplay") {
        |        enter {
        |            screen.showSprites()
        |        }
        |
        |        every.frame {
        |            // Movement and exploration logic
        |            whenever(dpad.right) { /* Move right */ }
        |            whenever(dpad.left) { /* Move left */ }
        |            whenever(dpad.up) { /* Move up */ }
        |            whenever(dpad.down) { /* Move down */ }
        |
        |            // Pause menu on start
        |            whenever(buttons.start.pressed) {
        |                /* Show pause menu */
        |            }
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateBattleSceneFile(packageName: String): String =
        """
        |/*
        | * Battle scene
        | */
        |package $packageName.scenes
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.scene.*
        |import io.github.gbkt.core.rpg.*
        |
        |fun GameBuilder.registerBattleScene(): SceneRef {
        |    return scene("battle") {
        |        enter {
        |            // Initialize battle UI
        |        }
        |
        |        every.frame {
        |            // Battle state machine handles turn flow
        |        }
        |
        |        exit {
        |            // Clean up battle state
        |        }
        |    }
        |}
    """
            .trimMargin()

    private fun generateGameOverSceneFile(packageName: String): String =
        """
        |/*
        | * Game over scene
        | */
        |package $packageName.scenes
        |
        |import io.github.gbkt.core.builder.GameBuilder
        |import io.github.gbkt.core.scene.*
        |import io.github.gbkt.core.ui.*
        |
        |fun GameBuilder.registerGameOverScene(): SceneRef {
        |    return scene("gameover") {
        |        val gameOverMenu = menu("gameOverMenu") {
        |            item("CONTINUE") { /* Load last save */ }
        |            item("TITLE") { /* Return to title */ }
        |        }
        |
        |        enter {
        |            gameOverMenu.show()
        |        }
        |
        |        every.frame {
        |            gameOverMenu.update()
        |        }
        |    }
        |}
    """
            .trimMargin()
}
