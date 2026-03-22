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
package io.github.gbkt.intellij.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Provides completion for property chains in gbkt DSL.
 *
 * Handles dot expressions like:
 * - `hero.` → stats, position, sprite, combat (entity properties)
 * - `hero.stats.` → hp, sp, atk, def, matk, mdef, agl (stat properties)
 * - `player.x` / `player.y` → coordinate properties
 * - `camera.` → x, y, shake, fadeIn, fadeOut, follow (camera methods)
 */
class GbktPropertyChainCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".gbkt.kts")) return

        val position = parameters.position

        // Skip completion inside strings and comments for performance
        if (PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) != null)
            return
        if (PsiTreeUtil.getParentOfType(position, PsiComment::class.java) != null) return

        // Check if we're after a dot (property access)
        val dotExpr = findDotExpression(position) ?: return

        // Analyze the chain to determine what type we're accessing
        val chain = extractChain(dotExpr)
        if (chain.isEmpty()) return

        val ktFile = file as? KtFile ?: return
        val analysis = GbktDslVisitor.analyze(ktFile)

        // Determine the base type from the first element
        val firstElement = chain.first()
        val baseType =
            if (firstElement.isMethodCall) {
                // Method call at root - check return type
                METHOD_RETURN_TYPES[firstElement.name] ?: BaseType.UNKNOWN
            } else {
                determineBaseType(firstElement.name, analysis)
            }

        // Get properties based on the chain
        val properties = resolveChainProperties(baseType, chain.drop(1))

        // Add suggestions
        for (prop in properties) {
            result.addElement(
                LookupElementBuilder.create(prop.name)
                    .withIcon(getIconForProperty(prop))
                    .withTypeText(prop.type)
                    .withTailText(prop.tailText, true)
            )
        }
    }

    private fun findDotExpression(position: PsiElement): KtDotQualifiedExpression? {
        return PsiTreeUtil.getParentOfType(position, KtDotQualifiedExpression::class.java)
    }

    /**
     * Represents an element in a property chain. Can be either a property access or a method call.
     */
    private data class ChainElement(val name: String, val isMethodCall: Boolean = false)

    private fun extractChain(dotExpr: KtDotQualifiedExpression): List<ChainElement> {
        val chain = mutableListOf<ChainElement>()

        fun traverse(expr: org.jetbrains.kotlin.psi.KtExpression) {
            when (expr) {
                is KtDotQualifiedExpression -> {
                    traverse(expr.receiverExpression)
                    val selector = expr.selectorExpression
                    when (selector) {
                        is KtNameReferenceExpression -> {
                            // Property access: hero.stats
                            val name = selector.getReferencedName()
                            // Don't add the placeholder (IntelliJIdeaRulezzz)
                            if (!name.contains("IntelliJ")) {
                                chain.add(ChainElement(name, isMethodCall = false))
                            }
                        }
                        is KtCallExpression -> {
                            // Method call: hero.heal()
                            val methodName = selector.calleeExpression?.text
                            if (methodName != null && !methodName.contains("IntelliJ")) {
                                chain.add(ChainElement(methodName, isMethodCall = true))
                            }
                        }
                    }
                }
                is KtNameReferenceExpression -> {
                    chain.add(ChainElement(expr.getReferencedName(), isMethodCall = false))
                }
                is KtCallExpression -> {
                    val methodName = expr.calleeExpression?.text
                    if (methodName != null) {
                        chain.add(ChainElement(methodName, isMethodCall = true))
                    }
                }
            }
        }

        traverse(dotExpr)
        return chain
    }

    private fun determineBaseType(name: String, analysis: GbktDslVisitor): BaseType {
        return findTypeFromAnalysis(name, analysis) ?: inferTypeFromName(name)
    }

    /** Find base type by checking analysis definition lists. */
    private fun findTypeFromAnalysis(name: String, analysis: GbktDslVisitor): BaseType? {
        val typeChecks =
            listOf(
                analysis.entities to BaseType.ENTITY,
                analysis.scenes to BaseType.SCENE,
                analysis.cameras to BaseType.CAMERA,
                analysis.dialogs to BaseType.DIALOG,
                analysis.characters to BaseType.CHARACTER,
                analysis.monsters to BaseType.MONSTER,
                analysis.abilities to BaseType.ABILITY,
                analysis.items to BaseType.ITEM,
                analysis.floors to BaseType.FLOOR,
                analysis.battles to BaseType.BATTLE,
                analysis.inventories to BaseType.INVENTORY,
                analysis.statusEffects to BaseType.STATUS_EFFECT,
                analysis.variables to BaseType.VARIABLE,
            )

        for ((definitions, type) in typeChecks) {
            if (definitions.any { it.name == name }) return type
        }
        return null
    }

    /** Infer base type from naming conventions (fallback heuristics). */
    private fun inferTypeFromName(name: String): BaseType {
        val lowerName = name.lowercase()
        return when {
            "player" in lowerName || "enemy" in lowerName -> BaseType.ENTITY
            "hero" in lowerName -> BaseType.CHARACTER
            "camera" in lowerName -> BaseType.CAMERA
            "dpad" in lowerName -> BaseType.DPAD
            "buttons" in lowerName -> BaseType.BUTTONS
            "screen" in lowerName -> BaseType.SCREEN
            else -> BaseType.UNKNOWN
        }
    }

    private fun resolveChainProperties(
        baseType: BaseType,
        remainingChain: List<ChainElement>,
    ): List<PropertySuggestion> {
        if (remainingChain.isEmpty()) {
            // Return properties for the base type
            return BASE_TYPE_PROPERTIES[baseType] ?: emptyList()
        }

        // Navigate through the chain
        val nextElement = remainingChain.first()
        val nestedType =
            if (nextElement.isMethodCall) {
                // Check method return type
                METHOD_RETURN_TYPES[nextElement.name]
            } else {
                // Check property type
                PROPERTY_TYPES[nextElement.name]
            }

        return if (nestedType != null) {
            if (remainingChain.size == 1) {
                // We're at the end of the chain, return properties for this type
                BASE_TYPE_PROPERTIES[nestedType] ?: emptyList()
            } else {
                // Continue traversing the chain
                resolveChainProperties(nestedType, remainingChain.drop(1))
            }
        } else {
            // Unknown type in chain - try to continue if there's more
            emptyList()
        }
    }

    private fun getIconForProperty(prop: PropertySuggestion): javax.swing.Icon {
        return when (prop.kind) {
            PropertyKind.FIELD -> AllIcons.Nodes.Field
            PropertyKind.METHOD -> AllIcons.Nodes.Method
            PropertyKind.PROPERTY -> AllIcons.Nodes.Property
        }
    }

    data class PropertySuggestion(
        val name: String,
        val type: String,
        val tailText: String = "",
        val kind: PropertyKind = PropertyKind.PROPERTY,
    )

    enum class PropertyKind {
        FIELD,
        METHOD,
        PROPERTY,
    }

    enum class BaseType {
        ENTITY,
        CHARACTER,
        MONSTER,
        ABILITY,
        ITEM,
        FLOOR,
        BATTLE,
        INVENTORY,
        STATUS_EFFECT,
        SCENE,
        CAMERA,
        DIALOG,
        VARIABLE,
        STATS,
        COMBAT,
        SPRITE,
        DPAD,
        DPAD_BUTTON,
        BUTTONS,
        BUTTON,
        SCREEN,
        UNKNOWN,
    }

    companion object {
        /** Maps property names to their nested types. */
        private val PROPERTY_TYPES =
            mapOf(
                "stats" to BaseType.STATS,
                "combat" to BaseType.COMBAT,
                "sprite" to BaseType.SPRITE,
                "up" to BaseType.DPAD_BUTTON,
                "down" to BaseType.DPAD_BUTTON,
                "left" to BaseType.DPAD_BUTTON,
                "right" to BaseType.DPAD_BUTTON,
                "a" to BaseType.BUTTON,
                "b" to BaseType.BUTTON,
                "start" to BaseType.BUTTON,
                "select" to BaseType.BUTTON,
            )

        /**
         * Maps method names to their return types. Used for completion after method calls like
         * `entity.heal().`
         */
        private val METHOD_RETURN_TYPES =
            mapOf(
                // Entity methods that return the entity (for chaining)
                "spawn" to BaseType.ENTITY,
                "hide" to BaseType.ENTITY,
                "show" to BaseType.ENTITY,
                // Camera methods that return camera (for chaining)
                "follow" to BaseType.CAMERA,
                "shake" to BaseType.CAMERA,
                "pan" to BaseType.CAMERA,
                "reset" to BaseType.CAMERA,
                // Sprite methods
                "play" to BaseType.SPRITE,
                "stop" to BaseType.SPRITE,
            )

        /** Properties available for each base type. */
        private val BASE_TYPE_PROPERTIES: Map<BaseType, List<PropertySuggestion>> =
            mapOf(
                BaseType.ENTITY to
                    listOf(
                        PropertySuggestion("x", "Int", " - X coordinate"),
                        PropertySuggestion("y", "Int", " - Y coordinate"),
                        PropertySuggestion("position", "Position", " - Position vector"),
                        PropertySuggestion("velocity", "Velocity", " - Velocity vector"),
                        PropertySuggestion("sprite", "Sprite", " - Sprite component"),
                        PropertySuggestion("combat", "Combat", " - Combat component"),
                        PropertySuggestion("states", "StateMachine", " - State machine"),
                        PropertySuggestion("visible", "Boolean", " - Visibility flag"),
                        PropertySuggestion("active", "Boolean", " - Active flag"),
                        PropertySuggestion("spawn", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("destroy", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("hide", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("show", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.CHARACTER to
                    listOf(
                        PropertySuggestion("name", "String", " - Character name"),
                        PropertySuggestion("stats", "Stats", " - Character stats"),
                        PropertySuggestion("level", "Int", " - Current level"),
                        PropertySuggestion("exp", "Int", " - Experience points"),
                        PropertySuggestion("hp", "Int", " - Current HP"),
                        PropertySuggestion("maxHp", "Int", " - Maximum HP"),
                        PropertySuggestion("sp", "Int", " - Current SP"),
                        PropertySuggestion("maxSp", "Int", " - Maximum SP"),
                        PropertySuggestion("isAlive", "Boolean", " - Is character alive"),
                        PropertySuggestion("levelUp", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("heal", "Unit", "(amount: Int)", PropertyKind.METHOD),
                        PropertySuggestion("damage", "Unit", "(amount: Int)", PropertyKind.METHOD),
                    ),
                BaseType.MONSTER to
                    listOf(
                        PropertySuggestion("name", "String", " - Monster name"),
                        PropertySuggestion("tier", "MonsterTier", " - Monster tier"),
                        PropertySuggestion("stats", "Stats", " - Base stats"),
                        PropertySuggestion("hp", "Int", " - Current HP"),
                        PropertySuggestion("maxHp", "Int", " - Maximum HP"),
                        PropertySuggestion("isAlive", "Boolean", " - Is monster alive"),
                        PropertySuggestion("exp", "Int", " - Experience reward"),
                        PropertySuggestion("damage", "Unit", "(amount: Int)", PropertyKind.METHOD),
                        PropertySuggestion("flee", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.ABILITY to
                    listOf(
                        PropertySuggestion("name", "String", " - Ability name"),
                        PropertySuggestion("cost", "Int", " - SP cost"),
                        PropertySuggestion("targeting", "TargetingMode", " - Targeting mode"),
                        PropertySuggestion("aspect", "Aspect", " - Element/aspect"),
                        PropertySuggestion("basePower", "Int", " - Base damage/heal power"),
                        PropertySuggestion("canUse", "Boolean", "(caster)", PropertyKind.METHOD),
                        PropertySuggestion(
                            "execute",
                            "Unit",
                            "(caster, target)",
                            PropertyKind.METHOD,
                        ),
                    ),
                BaseType.ITEM to
                    listOf(
                        PropertySuggestion("name", "String", " - Item name"),
                        PropertySuggestion("category", "ItemCategory", " - Item category"),
                        PropertySuggestion("maxStack", "Int", " - Max stack size"),
                        PropertySuggestion("buyPrice", "Int", " - Buy price"),
                        PropertySuggestion("sellPrice", "Int", " - Sell price"),
                        PropertySuggestion("slot", "EquipSlot", " - Equipment slot"),
                        PropertySuggestion("isConsumable", "Boolean", " - Is consumable"),
                        PropertySuggestion("isEquippable", "Boolean", " - Is equippable"),
                        PropertySuggestion("use", "Unit", "(target)", PropertyKind.METHOD),
                    ),
                BaseType.FLOOR to
                    listOf(
                        PropertySuggestion("name", "String", " - Floor name"),
                        PropertySuggestion("defaultX", "Int", " - Default spawn X"),
                        PropertySuggestion("defaultY", "Int", " - Default spawn Y"),
                        PropertySuggestion("encounters", "EncounterTable", " - Encounter table"),
                        PropertySuggestion("safeSteps", "Int", " - Steps before encounters"),
                    ),
                BaseType.BATTLE to
                    listOf(
                        PropertySuggestion("state", "BattleState", " - Current battle state"),
                        PropertySuggestion("turn", "Int", " - Current turn number"),
                        PropertySuggestion("party", "List<Character>", " - Party members"),
                        PropertySuggestion("enemies", "List<Monster>", " - Enemy monsters"),
                        PropertySuggestion("currentActor", "Combatant", " - Current acting unit"),
                        PropertySuggestion("isVictory", "Boolean", " - Has player won"),
                        PropertySuggestion("isDefeat", "Boolean", " - Has player lost"),
                        PropertySuggestion("start", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("end", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("flee", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.INVENTORY to
                    listOf(
                        PropertySuggestion("maxSlots", "Int", " - Maximum inventory slots"),
                        PropertySuggestion("usedSlots", "Int", " - Used slots"),
                        PropertySuggestion("isEmpty", "Boolean", " - Is inventory empty"),
                        PropertySuggestion("isFull", "Boolean", " - Is inventory full"),
                        PropertySuggestion("add", "Boolean", "(item, count)", PropertyKind.METHOD),
                        PropertySuggestion(
                            "remove",
                            "Boolean",
                            "(item, count)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion("contains", "Boolean", "(item)", PropertyKind.METHOD),
                        PropertySuggestion("count", "Int", "(item)", PropertyKind.METHOD),
                    ),
                BaseType.STATUS_EFFECT to
                    listOf(
                        PropertySuggestion("name", "String", " - Effect name"),
                        PropertySuggestion("duration", "Int", " - Duration in turns"),
                        PropertySuggestion("remaining", "Int", " - Remaining turns"),
                        PropertySuggestion("stackMode", "StackMode", " - Stacking behavior"),
                        PropertySuggestion("isDebuff", "Boolean", " - Is debuff"),
                        PropertySuggestion("isBuff", "Boolean", " - Is buff"),
                        PropertySuggestion("apply", "Unit", "(target)", PropertyKind.METHOD),
                        PropertySuggestion("remove", "Unit", "(target)", PropertyKind.METHOD),
                    ),
                BaseType.STATS to
                    listOf(
                        PropertySuggestion("hp", "Int", " - Health points"),
                        PropertySuggestion("sp", "Int", " - Skill points"),
                        PropertySuggestion("atk", "Int", " - Attack"),
                        PropertySuggestion("def", "Int", " - Defense"),
                        PropertySuggestion("matk", "Int", " - Magic attack"),
                        PropertySuggestion("mdef", "Int", " - Magic defense"),
                        PropertySuggestion("agl", "Int", " - Agility"),
                        PropertySuggestion("acc", "Int", " - Accuracy"),
                        PropertySuggestion("eva", "Int", " - Evasion"),
                        PropertySuggestion("luk", "Int", " - Luck"),
                    ),
                BaseType.COMBAT to
                    listOf(
                        PropertySuggestion("maxHp", "Int", " - Maximum HP"),
                        PropertySuggestion("hp", "Int", " - Current HP"),
                        PropertySuggestion("attackPower", "Int", " - Attack power"),
                        PropertySuggestion("defense", "Int", " - Defense value"),
                        PropertySuggestion("team", "Team", " - Combat team"),
                        PropertySuggestion("isInvincible", "Boolean", " - Invincibility status"),
                        PropertySuggestion(
                            "takeDamage",
                            "Unit",
                            "(amount: Int)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion("heal", "Unit", "(amount: Int)", PropertyKind.METHOD),
                    ),
                BaseType.CAMERA to
                    listOf(
                        PropertySuggestion("x", "Int", " - Camera X position"),
                        PropertySuggestion("y", "Int", " - Camera Y position"),
                        PropertySuggestion("follow", "Unit", "(entity)", PropertyKind.METHOD),
                        PropertySuggestion(
                            "shake",
                            "Unit",
                            "(intensity, duration)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion(
                            "fadeIn",
                            "Unit",
                            "(duration, callback?)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion(
                            "fadeOut",
                            "Unit",
                            "(duration, callback?)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion("pan", "Unit", "(x, y, duration)", PropertyKind.METHOD),
                        PropertySuggestion("reset", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.SPRITE to
                    listOf(
                        PropertySuggestion("visible", "Boolean", " - Visibility"),
                        PropertySuggestion("flipX", "Boolean", " - Horizontal flip"),
                        PropertySuggestion("flipY", "Boolean", " - Vertical flip"),
                        PropertySuggestion("palette", "Int", " - Palette index"),
                        PropertySuggestion("frame", "Int", " - Current frame"),
                        PropertySuggestion("animation", "String", " - Current animation"),
                        PropertySuggestion("play", "Unit", "(animationName)", PropertyKind.METHOD),
                        PropertySuggestion("stop", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.DPAD to
                    listOf(
                        PropertySuggestion("up", "DpadButton", " - Up button"),
                        PropertySuggestion("down", "DpadButton", " - Down button"),
                        PropertySuggestion("left", "DpadButton", " - Left button"),
                        PropertySuggestion("right", "DpadButton", " - Right button"),
                        PropertySuggestion("x", "Int", " - Horizontal axis (-1, 0, 1)"),
                        PropertySuggestion("y", "Int", " - Vertical axis (-1, 0, 1)"),
                        PropertySuggestion("any", "Boolean", " - Any direction pressed"),
                        PropertySuggestion("none", "Boolean", " - No direction pressed"),
                    ),
                BaseType.BUTTONS to
                    listOf(
                        PropertySuggestion("a", "Button", " - A button"),
                        PropertySuggestion("b", "Button", " - B button"),
                        PropertySuggestion("start", "Button", " - Start button"),
                        PropertySuggestion("select", "Button", " - Select button"),
                    ),

                // Individual button properties (dpad.up., buttons.a., etc.)
                BaseType.DPAD_BUTTON to
                    listOf(
                        PropertySuggestion("held", "Boolean", " - Button is held down"),
                        PropertySuggestion("pressed", "Boolean", " - Button was just pressed"),
                        PropertySuggestion("released", "Boolean", " - Button was just released"),
                    ),
                BaseType.BUTTON to
                    listOf(
                        PropertySuggestion("held", "Boolean", " - Button is held down"),
                        PropertySuggestion("pressed", "Boolean", " - Button was just pressed"),
                        PropertySuggestion("released", "Boolean", " - Button was just released"),
                    ),
                BaseType.SCREEN to
                    listOf(
                        PropertySuggestion("width", "Int", " - Screen width (160)"),
                        PropertySuggestion("height", "Int", " - Screen height (144)"),
                        PropertySuggestion("showSprites", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("hideSprites", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("showBackground", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("hideBackground", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("showWindow", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("hideWindow", "Unit", "()", PropertyKind.METHOD),
                    ),
                BaseType.SCENE to
                    listOf(
                        PropertySuggestion("name", "String", " - Scene name"),
                        PropertySuggestion("isActive", "Boolean", " - Is scene active"),
                    ),
                BaseType.DIALOG to
                    listOf(
                        PropertySuggestion("show", "Unit", "(text)", PropertyKind.METHOD),
                        PropertySuggestion(
                            "showWithPortrait",
                            "Unit",
                            "(text, portrait)",
                            PropertyKind.METHOD,
                        ),
                        PropertySuggestion("close", "Unit", "()", PropertyKind.METHOD),
                        PropertySuggestion("isOpen", "Boolean", " - Dialog visibility"),
                    ),
            )
    }
}
