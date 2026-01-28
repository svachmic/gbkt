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
package io.github.gbkt.intellij.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import io.github.gbkt.intellij.GbktFileType
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Navigator for nested property chains in gbkt DSL.
 *
 * Resolves chains like:
 * - `hero.stats.hp` → navigates to the character definition
 * - `player.combat.maxHp` → navigates to the entity's combat block
 * - `enemy.sprite.size` → navigates to the entity's sprite definition
 *
 * This complements the basic go-to-definition by understanding the gbkt type system.
 */
object GbktNestedPropertyNavigator {

    /**
     * Attempts to resolve a property chain to its definition.
     *
     * Searches all `.gbkt.kts` files in the project, not just the current file.
     *
     * @param dotExpression The dot-qualified expression (e.g., hero.stats.hp)
     * @param file The containing file
     * @return List of navigation targets (definition elements)
     */
    fun resolvePropertyChain(
        dotExpression: KtDotQualifiedExpression,
        file: PsiFile,
    ): List<PsiElement> {
        val chain = extractPropertyChain(dotExpression)
        if (chain.isEmpty()) return emptyList()

        // The first element is the root definition
        val rootName = chain.first()

        // Find the root definition across all project files
        val project = file.project
        val rootDefinition = findRootDefinitionInProject(rootName, project)
            ?: return emptyList()

        // If only one element in chain, return the root
        if (chain.size == 1) {
            return listOf(rootDefinition.element)
        }

        // Navigate into the nested blocks
        val results = mutableListOf<PsiElement>()
        results.add(rootDefinition.element)

        // Try to find nested blocks
        val nestedTarget = findNestedBlock(rootDefinition.callExpression, chain.drop(1))
        if (nestedTarget != null) {
            results.add(0, nestedTarget) // Prefer the more specific match
        }

        return results
    }

    /**
     * Searches all `.gbkt.kts` files in the project for a definition with the given name.
     */
    private fun findRootDefinitionInProject(
        name: String,
        project: Project,
    ): GbktDslVisitor.DslDefinition? {
        val gbktFiles = FileTypeIndex.getFiles(GbktFileType, GlobalSearchScope.projectScope(project))
        val psiManager = PsiManager.getInstance(project)

        for (virtualFile in gbktFiles) {
            val psiFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
            val analysis = GbktDslVisitor.analyze(psiFile)
            val definition = findRootDefinition(name, analysis)
            if (definition != null) {
                return definition
            }
        }

        return null
    }

    /**
     * Extracts the property chain as a list of names.
     * "hero.stats.hp" → ["hero", "stats", "hp"]
     */
    private fun extractPropertyChain(dotExpression: KtDotQualifiedExpression): List<String> {
        val chain = mutableListOf<String>()

        fun traverse(expr: org.jetbrains.kotlin.psi.KtExpression) {
            when (expr) {
                is KtDotQualifiedExpression -> {
                    traverse(expr.receiverExpression)
                    val selector = expr.selectorExpression
                    if (selector is KtNameReferenceExpression) {
                        chain.add(selector.getReferencedName())
                    } else if (selector is KtCallExpression) {
                        selector.calleeExpression?.text?.let { chain.add(it) }
                    }
                }
                is KtNameReferenceExpression -> {
                    chain.add(expr.getReferencedName())
                }
            }
        }

        traverse(dotExpression)
        return chain
    }

    /**
     * Finds the root definition (entity, character, scene, etc.).
     */
    private fun findRootDefinition(
        name: String,
        analysis: GbktDslVisitor,
    ): GbktDslVisitor.DslDefinition? {
        // Check entities first (most common)
        analysis.entities.find { it.name == name }?.let { return it }

        // Check scenes
        analysis.scenes.find { it.name == name }?.let { return it }

        // Check dialogs
        analysis.dialogs.find { it.name == name }?.let { return it }

        // Check cameras
        analysis.cameras.find { it.name == name }?.let { return it }

        // Check variables
        analysis.variables.find { it.name == name }?.let { return it }

        // Check RPG definitions
        analysis.characters.find { it.name == name }?.let { return it }
        analysis.monsters.find { it.name == name }?.let { return it }
        analysis.abilities.find { it.name == name }?.let { return it }
        analysis.items.find { it.name == name }?.let { return it }
        analysis.floors.find { it.name == name }?.let { return it }
        analysis.battles.find { it.name == name }?.let { return it }
        analysis.inventories.find { it.name == name }?.let { return it }
        analysis.statusEffects.find { it.name == name }?.let { return it }

        return null
    }

    /**
     * Finds a nested block within a definition by traversing the lambda body.
     *
     * For example, given an entity definition and ["combat", "maxHp"], this finds
     * the combat { } block inside the entity.
     */
    private fun findNestedBlock(
        callExpression: KtCallExpression,
        path: List<String>,
    ): PsiElement? {
        if (path.isEmpty()) return callExpression

        val targetName = path.first()

        // Find the lambda argument
        val lambda = PsiTreeUtil.findChildOfType(callExpression, KtLambdaExpression::class.java)
            ?: return null

        // Search for a call expression with the target name
        val nestedCalls = PsiTreeUtil.findChildrenOfType(lambda, KtCallExpression::class.java)

        for (nestedCall in nestedCalls) {
            val calleeName = nestedCall.calleeExpression?.text
            if (calleeName == targetName) {
                // Found it - if more path elements, recurse
                return if (path.size > 1) {
                    findNestedBlock(nestedCall, path.drop(1)) ?: nestedCall
                } else {
                    nestedCall
                }
            }
        }

        return null
    }

    /**
     * Gets the type of a DSL definition for better navigation context.
     */
    fun getDefinitionType(definition: GbktDslVisitor.DslDefinition): DefinitionType {
        return when (definition.type) {
            GbktDslVisitor.DslType.ENTITY -> DefinitionType.ENTITY
            GbktDslVisitor.DslType.SCENE -> DefinitionType.SCENE
            GbktDslVisitor.DslType.DIALOG -> DefinitionType.DIALOG
            GbktDslVisitor.DslType.CAMERA -> DefinitionType.CAMERA
            GbktDslVisitor.DslType.VARIABLE -> DefinitionType.VARIABLE
            GbktDslVisitor.DslType.ARRAY -> DefinitionType.ARRAY
            GbktDslVisitor.DslType.FLAGS -> DefinitionType.FLAGS
            GbktDslVisitor.DslType.CHARACTER -> DefinitionType.CHARACTER
            GbktDslVisitor.DslType.MONSTER -> DefinitionType.MONSTER
            GbktDslVisitor.DslType.ABILITY -> DefinitionType.ABILITY
            GbktDslVisitor.DslType.ITEM -> DefinitionType.ITEM
            GbktDslVisitor.DslType.FLOOR -> DefinitionType.FLOOR
            GbktDslVisitor.DslType.BATTLE -> DefinitionType.BATTLE
            GbktDslVisitor.DslType.INVENTORY -> DefinitionType.INVENTORY
            GbktDslVisitor.DslType.STATUS_EFFECT -> DefinitionType.STATUS_EFFECT
        }
    }

    /** Types of definitions for navigation hints. */
    enum class DefinitionType(val displayName: String) {
        ENTITY("Entity"),
        SCENE("Scene"),
        DIALOG("Dialog"),
        CAMERA("Camera"),
        VARIABLE("Variable"),
        ARRAY("Array"),
        FLAGS("Flags"),
        CHARACTER("Character"),
        MONSTER("Monster"),
        ABILITY("Ability"),
        ITEM("Item"),
        FLOOR("Floor"),
        BATTLE("Battle"),
        INVENTORY("Inventory"),
        STATUS_EFFECT("Status Effect"),
    }

    /** Known nested block types and their properties. */
    val NESTED_BLOCKS = mapOf(
        "entity" to listOf("position", "velocity", "sprite", "hitbox", "combat", "states", "physics"),
        "sprite" to listOf("size", "palette", "regions", "animations"),
        "combat" to listOf("maxHp", "attackPower", "defense", "team", "invincibilityFrames"),
        "states" to listOf("state"),
        "state" to listOf("enter", "exit", "tick", "on"),
        "character" to listOf("stats", "level", "onLevelUp"),
        "monster" to listOf("baseStats", "ai", "drops"),
        "ability" to listOf("cost", "targeting", "aspect", "execute"),
        "item" to listOf("category", "slot", "stats", "onUse"),
    )
}
