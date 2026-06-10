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
package io.github.gbkt.intellij.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix that creates a new variable definition.
 *
 * When an undefined variable reference is detected, this quick fix offers to create a new variable
 * definition using u8Var, u16Var, etc.
 */
class CreateVariableQuickFix(
    private val variableName: String,
    private val variableType: VariableType = VariableType.U8,
) : LocalQuickFix {

    enum class VariableType(
        val dslFunction: String,
        val defaultValue: String,
        val isArray: Boolean = false,
    ) {
        // Scalar types
        U8("u8Var", "0"),
        U16("u16Var", "0"),
        I8("i8Var", "0"),
        I16("i16Var", "0"),
        // Array types
        U8_ARRAY("u8Array", "10, 0", isArray = true),
        U16_ARRAY("u16Array", "10, 0", isArray = true),
        I8_ARRAY("i8Array", "10, 0", isArray = true),
        I16_ARRAY("i16Array", "10, 0", isArray = true),
    }

    override fun getName(): String = "Create ${variableType.dslFunction} '$variableName'"

    override fun getFamilyName(): String = "gbkt DSL quick fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as? KtFile ?: return
        val psiFactory = KtPsiFactory(project)

        // Template for new variable
        val template =
            "\nvar $variableName by ${variableType.dslFunction}(${variableType.defaultValue})\n"

        WriteCommandAction.runWriteCommandAction(project) {
            // Find a good insertion point - after other variable definitions or near top
            val insertionPoint = findInsertionPoint(file)

            if (insertionPoint != null) {
                val newProperty = psiFactory.createProperty(template.trim())
                val newLine = psiFactory.createNewLine()

                // Insert after the insertion point
                val parent = insertionPoint.parent
                parent.addAfter(newLine, insertionPoint)
                parent.addAfter(newProperty, insertionPoint)
            } else {
                // Insert at the beginning of the file (after imports if any)
                val document = PsiDocumentManager.getInstance(project).getDocument(file)
                if (document != null) {
                    val insertOffset = findTopInsertionOffset(file)
                    document.insertString(insertOffset, template)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            }
        }
    }

    private fun findInsertionPoint(file: KtFile): org.jetbrains.kotlin.psi.KtDeclaration? {
        val declarations = file.declarations

        // Find the last variable definition to insert after it
        val lastVariable =
            declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>().lastOrNull {
                property ->
                val delegate = property.delegateExpression
                delegate is org.jetbrains.kotlin.psi.KtCallExpression &&
                    delegate.calleeExpression?.text in VARIABLE_FUNCTIONS
            }

        return lastVariable
    }

    private fun findTopInsertionOffset(file: KtFile): Int {
        // Insert after package statement and imports
        val packageDirective = file.packageDirective
        val imports = file.importDirectives

        return when {
            imports.isNotEmpty() -> imports.last().textRange.endOffset
            packageDirective != null -> packageDirective.textRange.endOffset
            else -> 0
        }
    }

    companion object {
        private val VARIABLE_FUNCTIONS =
            setOf(
                "u8Var",
                "u16Var",
                "i8Var",
                "i16Var",
                "u8Array",
                "u16Array",
                "i8Array",
                "i16Array",
            )
    }
}
