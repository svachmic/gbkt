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
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile

/**
 * Quick fix that creates a new character definition.
 *
 * When an undefined character reference is detected, this quick fix offers to create a new
 * character definition at the appropriate location in the file.
 */
class CreateCharacterQuickFix(private val characterName: String) : LocalQuickFix {

    override fun getName(): String = "Create character '$characterName'"

    override fun getFamilyName(): String = "gbkt DSL quick fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as? KtFile ?: return

        val defaults = ClampValueQuickFix.Companion.Defaults
        val template =
            """

val $characterName by character {
    name("${characterName.replaceFirstChar { it.uppercase() }}")
    stats {
        hp(${defaults.DEFAULT_HP})
        sp(${defaults.DEFAULT_SP})
        atk(${defaults.DEFAULT_ATK})
        def(${defaults.DEFAULT_DEF})
        matk(${defaults.DEFAULT_MATK})
        mdef(${defaults.DEFAULT_MDEF})
        agl(${defaults.DEFAULT_AGL})
    }
    level(1, maxLevel = 99)
    onLevelUp {
        // TODO: Configure level up bonuses
        stats.hp += 10
        stats.atk += 2
    }
}
"""

        WriteCommandAction.runWriteCommandAction(project) {
            val insertionPoint = findInsertionPoint(file)
            val document =
                PsiDocumentManager.getInstance(project).getDocument(file)
                    ?: return@runWriteCommandAction

            val insertOffset: Int
            if (insertionPoint != null) {
                insertOffset = insertionPoint.textRange.endOffset
                document.insertString(insertOffset, template)
            } else {
                insertOffset = document.textLength
                document.insertString(insertOffset, template)
            }

            PsiDocumentManager.getInstance(project).commitDocument(document)

            // Position cursor at the name string
            val nameOffset = insertOffset + template.indexOf("name(\"") + "name(\"".length
            positionCursor(project, file, nameOffset)
        }
    }

    private fun positionCursor(project: Project, file: KtFile, offset: Int) {
        val virtualFile = file.virtualFile ?: return
        val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
        val textEditor = fileEditor as? TextEditor ?: return
        val editor = textEditor.editor

        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun findInsertionPoint(file: KtFile): org.jetbrains.kotlin.psi.KtDeclaration? {
        val declarations = file.declarations

        // Find the last character definition
        val lastCharacter =
            declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>().lastOrNull {
                property ->
                val delegate = property.delegateExpression
                delegate is org.jetbrains.kotlin.psi.KtCallExpression &&
                    delegate.calleeExpression?.text == "character"
            }

        if (lastCharacter != null) return lastCharacter

        return declarations.lastOrNull()
    }
}
