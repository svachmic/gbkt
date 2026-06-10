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
package io.github.gbkt.intellij.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.PsiElement
import io.github.gbkt.intellij.highlighting.GbktKeywords

/**
 * Provides quick documentation for gbkt DSL elements.
 *
 * Shows documentation when hovering over DSL keywords or pressing the documentation shortcut
 * (Ctrl+Q / F1).
 */
class GbktDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val text = originalElement?.text ?: element?.text ?: return null

        // Check if we're in a .gbkt.kts file
        val file = element?.containingFile ?: originalElement?.containingFile
        if (file != null && !file.name.endsWith(".gbkt.kts")) return null

        return getDocumentation(text)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val text = originalElement?.text ?: element?.text ?: return null
        return getQuickInfo(text)
    }

    private fun getDocumentation(keyword: String): String? {
        val doc = DOCUMENTATION[keyword] ?: return null
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$keyword</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append(doc.description)
            append(DocumentationMarkup.CONTENT_END)
            if (doc.example != null) {
                append(DocumentationMarkup.SECTIONS_START)
                append(DocumentationMarkup.SECTION_HEADER_START)
                append("Example:")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append("<pre><code>")
                append(doc.example)
                append("</code></pre>")
                append(DocumentationMarkup.SECTION_END)
                append(DocumentationMarkup.SECTIONS_END)
            }
            if (doc.seeAlso.isNotEmpty()) {
                append(DocumentationMarkup.SECTIONS_START)
                append(DocumentationMarkup.SECTION_HEADER_START)
                append("See also:")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append(doc.seeAlso.joinToString(", "))
                append(DocumentationMarkup.SECTION_END)
                append(DocumentationMarkup.SECTIONS_END)
            }
        }
    }

    private fun getQuickInfo(keyword: String): String? {
        return when {
            keyword in GbktKeywords.TOP_LEVEL_FUNCTIONS -> "gbkt DSL function"
            keyword in GbktKeywords.CONTROL_FLOW -> "gbkt control flow"
            keyword in GbktKeywords.BUILDER_METHODS -> "gbkt builder method"
            keyword in GbktKeywords.INPUT -> "gbkt input"
            keyword in GbktKeywords.CONDITIONS -> "gbkt condition"
            keyword in GbktKeywords.LIFECYCLE -> "gbkt lifecycle callback"
            keyword in GbktKeywords.TYPES -> "gbkt type"
            else -> null
        }
    }

    companion object {
        private data class DocEntry(
            val description: String,
            val example: String? = null,
            val seeAlso: List<String> = emptyList(),
        )

        private val DOCUMENTATION =
            mapOf(
                // Top-level functions
                "gbGame" to
                    DocEntry(
                        "Defines the root game configuration block.",
                        """
                        gbGame {
                            title = "My Game"
                            start = mainScene
                        }
                        """
                            .trimIndent(),
                        listOf("scene", "entity"),
                    ),
                "scene" to
                    DocEntry(
                        "Defines a game scene with lifecycle callbacks. " +
                            "Scenes contain game logic and can transition between each other.",
                        """
                        val mainScene by scene("main") {
                            enter { screen.showSprites() }
                            every.frame { updatePlayer() }
                            exit { screen.hideSprites() }
                        }
                        """
                            .trimIndent(),
                        listOf("enter", "exit", "every"),
                    ),
                "entity" to
                    DocEntry(
                        "Defines a game entity with components like position, sprite, and hitbox.",
                        """
                        val player by entity {
                            position(80, 72)
                            sprite(playerSprite) {
                                size = 8 x 16
                                hitbox(0, 0, 8, 16)
                            }
                        }
                        """
                            .trimIndent(),
                        listOf("position", "sprite", "hitbox", "combat", "states"),
                    ),
                "dialog" to
                    DocEntry(
                        "Defines a dialog box configuration for text display.",
                        """
                        dialog("intro") {
                            box {
                                position(0, 104)
                                size(160, 40)
                            }
                            textSpeed(2)
                        }
                        """
                            .trimIndent(),
                        listOf("box", "portrait", "textSpeed"),
                    ),
                "camera" to
                    DocEntry(
                        "Defines camera settings for viewport control.",
                        """
                        val camera = camera("main") {
                            smoothing = 0.15f
                            deadzone(16, 16)
                            bounds(0, 0, 256, 256)
                        }
                        camera.follow(player)
                        """
                            .trimIndent(),
                        listOf("follow", "shake", "fadeOut", "fadeIn"),
                    ),
                "stats" to
                    DocEntry(
                        "Defines character statistics for RPG systems.",
                        """
                        stats {
                            hp(100, max = 999)
                            sp(50, max = 99)
                            atk(10); def(8); agl(12)
                        }
                        """
                            .trimIndent(),
                        listOf("hp", "sp", "atk", "def", "agl"),
                    ),
                "flags" to
                    DocEntry(
                        "Defines global game flags for state persistence. Supports up to 256 flags organized in pages.",
                        """
                        flags {
                            page("chests") {
                                flag("chest1Opened")
                                flag("chest2Opened")
                            }
                            page("story") {
                                flag("talkedToKing")
                            }
                        }
                        """
                            .trimIndent(),
                        listOf("page", "flag"),
                    ),

                // Variables
                "u8Var" to
                    DocEntry(
                        "Creates an unsigned 8-bit variable (0-255).",
                        "var score by u8Var(0)",
                        listOf("u16Var", "i8Var", "i16Var"),
                    ),
                "u16Var" to
                    DocEntry(
                        "Creates an unsigned 16-bit variable (0-65535).",
                        "var gold by u16Var(0)",
                        listOf("u8Var", "i8Var", "i16Var"),
                    ),
                "i8Var" to
                    DocEntry(
                        "Creates a signed 8-bit variable (-128 to 127).",
                        "var velocity by i8Var(0)",
                        listOf("u8Var", "u16Var", "i16Var"),
                    ),
                "i16Var" to
                    DocEntry(
                        "Creates a signed 16-bit variable (-32768 to 32767).",
                        "var positionX by i16Var(0)",
                        listOf("u8Var", "u16Var", "i8Var"),
                    ),
                "u8Array" to
                    DocEntry(
                        "Creates an array of unsigned 8-bit values.",
                        "val inventory by u8Array(8, 0)",
                        listOf("u16Array", "i8Array", "i16Array"),
                    ),
                "u16Array" to
                    DocEntry(
                        "Creates an array of unsigned 16-bit values.",
                        "val highScores by u16Array(5, 0)",
                        listOf("u8Array", "i8Array", "i16Array"),
                    ),

                // RPG systems
                "monster" to
                    DocEntry(
                        "Defines a monster with stats, AI, and drops.",
                        """
                        val kobold by monster {
                            name("Kobold")
                            tier(Tier.C)
                            baseStats { hp(20); atk(5); def(3) }
                            ai { turn -> basicAttack(randomTarget()) }
                            exp(15)
                        }
                        """
                            .trimIndent(),
                        listOf("ability", "item", "encounterTable"),
                    ),
                "ability" to
                    DocEntry(
                        "Defines a combat ability with targeting and effects.",
                        """
                        val fireball by ability {
                            name("Fireball")
                            targeting(Targeting.ALL_ENEMIES)
                            cost(10.sp)
                            aspect(Aspect.FIRE)
                            execute { caster, targets -> dealDamage(targets, caster.matk * 2) }
                        }
                        """
                            .trimIndent(),
                        listOf("monster", "combat"),
                    ),
                "item" to
                    DocEntry(
                        "Defines an item with properties and use effects.",
                        """
                        val potion by item {
                            name("Potion")
                            category(ItemCategory.CONSUMABLE)
                            maxStack(99)
                            onUse { target -> target.hp += 30 }
                        }
                        """
                            .trimIndent(),
                        listOf("monster", "ability"),
                    ),
                "floor" to
                    DocEntry(
                        "Defines a dungeon floor with maps and objects.",
                        """
                        val floor1 by floor {
                            defaultPosition(5, 5)
                            map("entrance") { tileset(dungeonTiles) }
                            exits { exit(from = "entrance" at 10 x 5, to = "hallway" at 0 x 5) }
                        }
                        """
                            .trimIndent(),
                        listOf("encounterTable", "flags"),
                    ),
                "encounterTable" to
                    DocEntry(
                        "Defines random encounter probabilities.",
                        """
                        encounterTable("dungeon") {
                            safeSteps(10)
                            initialChance(5)
                            entry(weight = 30) { kobold() }
                            entry(weight = 20) { goblin() }
                        }
                        """
                            .trimIndent(),
                        listOf("monster", "floor"),
                    ),

                // Control flow
                "whenever" to
                    DocEntry(
                        "Executes a block when a condition is true.",
                        """
                        whenever(dpad.right) { player.x += 2 }
                        whenever(buttons.a.pressed) { jump() }
                        whenever(player collidesWith enemy) { takeDamage() }
                        """
                            .trimIndent(),
                        listOf("branch", "repeat"),
                    ),
                "branch" to
                    DocEntry(
                        "Conditional branching with multiple conditions.",
                        """
                        branch {
                            whenever(score isAtLeast 100) { showWin() }
                            whenever(health isAtMost 0) { showGameOver() }
                            otherwise { continueGame() }
                        }
                        """
                            .trimIndent(),
                        listOf("whenever", "then", "otherwise"),
                    ),
                "repeat" to
                    DocEntry(
                        "Repeats a block a specified number of times.",
                        "repeat(10) { spawnEnemy() }",
                        listOf("repeatWhile", "repeatIndexed"),
                    ),
                "repeatWhile" to
                    DocEntry(
                        "Repeats a block while a condition is true.",
                        "repeatWhile(enemiesAlive isAbove 0) { updateBattle() }",
                        listOf("repeat", "repeatIndexed"),
                    ),
                "repeatIndexed" to
                    DocEntry(
                        "Repeats with access to the current iteration index.",
                        "repeatIndexed(8) { i -> inventory[i] = 0 }",
                        listOf("repeat", "repeatWhile"),
                    ),

                // Builder methods
                "position" to
                    DocEntry(
                        "Sets the x,y position of an entity.",
                        "position(80, 72)",
                        listOf("velocity", "entity"),
                    ),
                "velocity" to
                    DocEntry(
                        "Adds velocity component for movement.",
                        "velocity(0, 0)",
                        listOf("position", "entity"),
                    ),
                "sprite" to
                    DocEntry(
                        "Adds a sprite component with visual properties.",
                        """
                        sprite(playerSprite) {
                            size = 8 x 16
                            paletteIndex = 0
                            hitbox(0, 0, 8, 16)
                        }
                        """
                            .trimIndent(),
                        listOf("hitbox", "animations", "regions"),
                    ),
                "hitbox" to
                    DocEntry(
                        "Defines collision bounds for an entity or sprite.",
                        "hitbox(0, 0, 8, 16)  // x, y, width, height",
                        listOf("sprite", "collidesWith"),
                    ),
                "combat" to
                    DocEntry(
                        "Adds combat component with HP and damage properties.",
                        """
                        combat {
                            maxHp(100)
                            attackPower(10)
                            defense(5)
                            team(Team.PLAYER)
                        }
                        """
                            .trimIndent(),
                        listOf("states", "entity"),
                    ),
                "states" to
                    DocEntry(
                        "Adds a state machine for entity behavior.",
                        """
                        states {
                            state("idle") {
                                enter { playAnimation("idle") }
                                on(enemyNearby) { goto("alert") }
                            }
                            state("alert") {
                                tick { chasePlayer() }
                            }
                        }
                        """
                            .trimIndent(),
                        listOf("state", "enter", "exit", "tick", "on", "goto"),
                    ),
                "state" to
                    DocEntry(
                        "Defines a single state within a state machine.",
                        """
                        state("walking") {
                            enter { playAnimation("walk") }
                            tick { moveForward() }
                            exit { stopAnimation() }
                            on(hitWall) { goto("idle") }
                        }
                        """
                            .trimIndent(),
                        listOf("states", "enter", "exit", "tick", "on"),
                    ),

                // Lifecycle callbacks
                "enter" to
                    DocEntry(
                        "Called when entering a scene or state.",
                        """
                        enter {
                            screen.showSprites()
                            playMusic(bgm)
                        }
                        """
                            .trimIndent(),
                        listOf("exit", "every", "tick"),
                    ),
                "exit" to
                    DocEntry(
                        "Called when exiting a scene or state.",
                        """
                        exit {
                            screen.hideSprites()
                            stopMusic()
                        }
                        """
                            .trimIndent(),
                        listOf("enter", "every", "tick"),
                    ),
                "every" to
                    DocEntry(
                        "Creates periodic callbacks. Use with .frame, .second, etc.",
                        """
                        every.frame { updatePlayer() }
                        every.second { spawnEnemy() }
                        """
                            .trimIndent(),
                        listOf("enter", "exit", "frame"),
                    ),
                "frame" to
                    DocEntry(
                        "Specifies per-frame execution. Used with 'every'.",
                        "every.frame { updatePlayer() }",
                        listOf("every", "tick"),
                    ),
                "tick" to
                    DocEntry(
                        "Called every frame while in a state.",
                        """
                        tick {
                            moveTowardPlayer()
                            checkCollisions()
                        }
                        """
                            .trimIndent(),
                        listOf("enter", "exit", "on"),
                    ),
                "on" to
                    DocEntry(
                        "Defines a condition-triggered transition in a state machine.",
                        "on(playerNearby) { goto(\"attack\") }",
                        listOf("goto", "state", "states"),
                    ),
                "goto" to
                    DocEntry(
                        "Transitions to another state in a state machine.",
                        "on(healthLow) { goto(\"flee\") }",
                        listOf("on", "state", "states"),
                    ),

                // Input
                "dpad" to
                    DocEntry(
                        "D-pad input. Access directional states directly.",
                        """
                        whenever(dpad.right) { player.x += 2 }
                        whenever(dpad.up) { player.y -= 2 }
                        """
                            .trimIndent(),
                        listOf("buttons", "pressed", "held"),
                    ),
                "buttons" to
                    DocEntry(
                        "Button input. Use with .pressed, .released, or .held.",
                        """
                        whenever(buttons.a.pressed) { jump() }
                        whenever(buttons.b.held) { charge() }
                        whenever(buttons.start.pressed) { pause() }
                        """
                            .trimIndent(),
                        listOf("dpad", "pressed", "released", "held"),
                    ),
                "pressed" to
                    DocEntry(
                        "True on the frame a button is first pressed.",
                        "whenever(buttons.a.pressed) { jump() }",
                        listOf("released", "held", "buttons"),
                    ),
                "released" to
                    DocEntry(
                        "True on the frame a button is released.",
                        "whenever(buttons.b.released) { releaseCharge() }",
                        listOf("pressed", "held", "buttons"),
                    ),
                "held" to
                    DocEntry(
                        "True while a button is being held down.",
                        "whenever(buttons.b.held) { charge() }",
                        listOf("pressed", "released", "buttons"),
                    ),

                // Conditions
                "isEqualTo" to
                    DocEntry(
                        "Checks if a value equals another value.",
                        "whenever(score isEqualTo 100) { showBonus() }",
                        listOf("isNotEqualTo", "isAtLeast", "isAtMost"),
                    ),
                "isNotEqualTo" to
                    DocEntry(
                        "Checks if a value does not equal another value.",
                        "whenever(state isNotEqualTo PAUSED) { update() }",
                        listOf("isEqualTo", "isAtLeast", "isAtMost"),
                    ),
                "isGreaterThan" to
                    DocEntry(
                        "Checks if a value is strictly greater than another.",
                        "whenever(score isGreaterThan highScore) { newRecord() }",
                        listOf("isLessThan", "isAtLeast", "isAtMost"),
                    ),
                "isLessThan" to
                    DocEntry(
                        "Checks if a value is strictly less than another.",
                        "whenever(health isLessThan 10) { showWarning() }",
                        listOf("isGreaterThan", "isAtLeast", "isAtMost"),
                    ),
                "isAtLeast" to
                    DocEntry(
                        "Checks if a value is greater than or equal to another (>=).",
                        "whenever(score isAtLeast 100) { win() }",
                        listOf("isAtMost", "isGreaterThan", "isAbove"),
                    ),
                "isAtMost" to
                    DocEntry(
                        "Checks if a value is less than or equal to another (<=).",
                        "whenever(health isAtMost 0) { gameOver() }",
                        listOf("isAtLeast", "isLessThan", "isBelow"),
                    ),
                "isAbove" to
                    DocEntry(
                        "Alias for isGreaterThan. Checks if value > other.",
                        "whenever(player.x isAbove 160) { wrapAround() }",
                        listOf("isBelow", "isAtLeast", "isGreaterThan"),
                    ),
                "isBelow" to
                    DocEntry(
                        "Alias for isLessThan. Checks if value < other.",
                        "whenever(player.y isBelow 0) { clampPosition() }",
                        listOf("isAbove", "isAtMost", "isLessThan"),
                    ),
                "collidesWith" to
                    DocEntry(
                        "Checks if two entities' hitboxes overlap.",
                        "whenever(player collidesWith enemy) { takeDamage() }",
                        listOf("overlaps", "hitbox"),
                    ),
                "overlaps" to
                    DocEntry(
                        "Checks if two rectangular areas overlap.",
                        "whenever(playerRect overlaps dangerZone) { triggerTrap() }",
                        listOf("collidesWith", "hitbox"),
                    ),

                // Other useful keywords
                "tag" to
                    DocEntry(
                        "Assigns a tag to an entity for grouping/identification.",
                        """
                        val player by entity {
                            tag(Tags.PLAYER)
                        }
                        // Later: whenever(bullet collidesWith tag(Tags.ENEMY)) { ... }
                        """
                            .trimIndent(),
                        listOf("entity"),
                    ),
                "navGrid" to
                    DocEntry(
                        "Defines a navigation grid for pathfinding.",
                        """
                        navGrid {
                            cellSize = 8
                            walkable(GROUND, GRASS)
                            blocked(WALL, WATER)
                        }
                        """
                            .trimIndent(),
                        listOf("pathfind"),
                    ),
                "tween" to
                    DocEntry(
                        "Animates a value over time with easing.",
                        """
                        tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)
                        tween(alpha, from = 255, to = 0, duration = 30.frames) { fadeComplete() }
                        """
                            .trimIndent(),
                        listOf("Easing"),
                    ),
                "save" to
                    DocEntry(
                        "Configures game save functionality.",
                        """
                        save {
                            slot(0) {
                                include(playerStats, inventory, flags)
                            }
                        }
                        """
                            .trimIndent(),
                        listOf("flags"),
                    ),
            )
    }
}
