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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Go-to-definition handler for gbkt DSL elements.
 *
 * Provides navigation from entity/scene references to their definitions. For example, clicking on a
 * reference to `player` entity will navigate to where `val player by entity { ... }` is defined.
 *
 * Also handles dot expressions like `player.x` - navigating to the entity definition.
 */
class GbktGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val file = sourceElement.containingFile
        if (!GbktDslVisitor.isGbktFile(file)) return null

        // Find the reference expression at the cursor
        val reference =
            PsiTreeUtil.getParentOfType(sourceElement, KtNameReferenceExpression::class.java)
                ?: return null

        // Check if this is part of a dot-qualified expression (e.g., player.x or hero.stats.hp)
        val dotExpression =
            PsiTreeUtil.getParentOfType(reference, KtDotQualifiedExpression::class.java)
        if (dotExpression != null) {
            // Use nested property navigator for chain resolution
            val chainResults = GbktNestedPropertyNavigator.resolvePropertyChain(dotExpression, file)
            if (chainResults.isNotEmpty()) {
                return chainResults.toTypedArray()
            }

            // Fall back to receiver name
            val receiverName = extractReceiverName(dotExpression)
            if (receiverName != null) {
                val definitions = findDefinitionsInProject(file, receiverName)
                if (definitions.isNotEmpty()) {
                    return definitions.toTypedArray()
                }
            }
        }

        // Standard resolution for simple references
        val referenceName = reference.getReferencedName()
        val definitions = findDefinitionsInProject(file, referenceName)

        return if (definitions.isNotEmpty()) {
            definitions.toTypedArray()
        } else {
            null
        }
    }

    /**
     * Extracts the receiver name from a dot-qualified expression. For "player.x", returns "player".
     * For "player.stats.hp", returns "player".
     */
    private fun extractReceiverName(dotExpression: KtDotQualifiedExpression): String? {
        var receiver = dotExpression.receiverExpression

        // Handle chained expressions like player.stats.hp - go to the root
        while (receiver is KtDotQualifiedExpression) {
            receiver = receiver.receiverExpression
        }

        return if (receiver is KtNameReferenceExpression) {
            receiver.getReferencedName()
        } else {
            null
        }
    }

    private fun findDefinitionsInProject(file: PsiFile, name: String): List<PsiElement> =
        buildList {
            // First, search in the current file
            addAll(findDefinitionsInFile(file, name))

            // Then search in all gbkt files in the project
            val project = file.project
            val virtualFiles =
                com.intellij.psi.search.FileTypeIndex.getFiles(
                    io.github.gbkt.intellij.GbktFileType,
                    com.intellij.psi.search.GlobalSearchScope.projectScope(project),
                )

            for (virtualFile in virtualFiles) {
                if (virtualFile == file.virtualFile) continue // Skip current file

                val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null) {
                    addAll(findDefinitionsInFile(psiFile, name))
                }
            }
        }

    private fun findDefinitionsInFile(file: PsiFile, name: String): List<PsiElement> {
        if (file !is KtFile) return emptyList()

        val visitor = GbktDslVisitor.analyze(file)

        return buildList {
            // Check entities
            visitor.entities.filter { it.name == name }.forEach { add(it.element) }

            // Check scenes
            visitor.scenes.filter { it.name == name }.forEach { add(it.element) }

            // Check dialogs
            visitor.dialogs.filter { it.name == name }.forEach { add(it.element) }

            // Check cameras
            visitor.cameras.filter { it.name == name }.forEach { add(it.element) }

            // Check variables
            visitor.variables.filter { it.name == name }.forEach { add(it.element) }

            // Check flags
            visitor.flags.filter { it.name == name }.forEach { add(it.element) }

            // Check RPG definitions
            visitor.characters.filter { it.name == name }.forEach { add(it.element) }
            visitor.monsters.filter { it.name == name }.forEach { add(it.element) }
            visitor.abilities.filter { it.name == name }.forEach { add(it.element) }
            visitor.items.filter { it.name == name }.forEach { add(it.element) }
            visitor.floors.filter { it.name == name }.forEach { add(it.element) }
            visitor.battles.filter { it.name == name }.forEach { add(it.element) }
            visitor.inventories.filter { it.name == name }.forEach { add(it.element) }
            visitor.statusEffects.filter { it.name == name }.forEach { add(it.element) }
        }
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? {
        return "Go to gbkt Definition"
    }
}
