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
package io.github.gbkt.intellij.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import io.github.gbkt.intellij.GbktFileType
import io.github.gbkt.intellij.lang.GbktDslVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Rename processor for gbkt DSL elements.
 *
 * Handles renaming of:
 * - Entity definitions (val player by entity { ... })
 * - Scene definitions (val gameScene by scene { ... })
 * - Variable definitions (var score by u8Var(0))
 * - Dialog definitions
 * - Camera definitions
 *
 * Finds all references across all .gbkt.kts files and updates them together.
 */
class GbktRenamePsiElementProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        // Only handle property declarations in gbkt files
        if (element !is KtProperty) return false

        // KtProperty.containingFile is always non-null
        if (!element.containingFile.name.endsWith(".gbkt.kts")) return false

        // Check if it's a DSL definition (has a delegate expression with known DSL function)
        val delegateExpression = element.delegateExpression ?: return false
        val callee =
            (delegateExpression as? org.jetbrains.kotlin.psi.KtCallExpression)
                ?.calleeExpression
                ?.text

        return callee in DSL_DEFINITION_FUNCTIONS
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): MutableCollection<PsiReference> {
        val references = mutableListOf<PsiReference>()

        if (element !is KtProperty) return references

        val elementName = element.name ?: return references
        val project = element.project

        // Convert to GlobalSearchScope if possible
        val globalScope =
            when (searchScope) {
                is GlobalSearchScope -> searchScope
                else -> GlobalSearchScope.projectScope(project)
            }

        // Search in all gbkt files
        val gbktFiles = FileTypeIndex.getFiles(GbktFileType, globalScope)

        for (virtualFile in gbktFiles) {
            val psiFile =
                PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue

            // Find all references to this element
            val fileReferences = findReferencesInFile(psiFile, elementName, element)
            references.addAll(fileReferences)
        }

        return references
    }

    private fun findReferencesInFile(
        file: KtFile,
        name: String,
        originalElement: PsiElement,
    ): List<PsiReference> {
        val references = mutableListOf<PsiReference>()
        val analysis = GbktDslVisitor.analyze(file)

        // Check all references
        for (reference in analysis.references) {
            if (reference.name == name && reference.element != originalElement) {
                // Don't include the original definition itself
                val referenceElement = reference.element
                if (!isPartOfProperty(referenceElement, originalElement)) {
                    // Get the PsiReference from the element
                    referenceElement.reference?.let { references.add(it) }
                }
            }
        }

        return references
    }

    private fun isPartOfProperty(element: PsiElement, property: PsiElement): Boolean {
        var current = element.parent
        while (current != null) {
            if (current == property) return true
            current = current.parent
        }
        return false
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
    ) {
        super.prepareRenaming(element, newName, allRenames)

        // Find all references and add them to allRenames for batch renaming
        if (element !is KtProperty) return

        val elementName = element.name ?: return
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val gbktFiles = FileTypeIndex.getFiles(GbktFileType, scope)

        for (virtualFile in gbktFiles) {
            val psiFile =
                PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue

            val analysis = GbktDslVisitor.analyze(psiFile)

            for (reference in analysis.references) {
                if (
                    reference.name == elementName && !isPartOfProperty(reference.element, element)
                ) {
                    // Use the name identifier element, not the whole reference expression.
                    // This ensures the rename processor correctly identifies what to rename.
                    val nameElement = reference.element.getReferencedNameElement()
                    allRenames[nameElement] = newName
                }
            }
        }
    }

    companion object {
        /** DSL functions that define named elements. */
        private val DSL_DEFINITION_FUNCTIONS =
            setOf(
                "entity",
                "scene",
                "dialog",
                "camera",
                "u8Var",
                "u16Var",
                "i8Var",
                "i16Var",
                "u8Array",
                "u16Array",
                "flags",
                "character",
                "monster",
                "ability",
                "item",
                "floor",
                "battle",
                "inventory",
                "statusEffect",
            )
    }
}
