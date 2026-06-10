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
package io.github.gbkt.intellij.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import io.github.gbkt.intellij.GbktLanguage
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Parser definition for gbkt files.
 *
 * Delegates to Kotlin's lexer and parser since gbkt files are valid Kotlin scripts. This provides
 * full Kotlin language support while allowing gbkt-specific extensions through the GbktDslVisitor
 * and related classes.
 */
class GbktParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer {
        return GbktLexer()
    }

    override fun createParser(project: Project): PsiParser {
        return GbktParser()
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement {
        return GbktPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return GbktFile(viewProvider)
    }

    companion object {
        val FILE = IFileElementType(GbktLanguage)
        val COMMENTS =
            TokenSet.create(
                KtTokens.EOL_COMMENT,
                KtTokens.BLOCK_COMMENT,
                KtTokens.DOC_COMMENT,
                KtTokens.SHEBANG_COMMENT,
            )
        val STRINGS =
            TokenSet.create(
                KtTokens.OPEN_QUOTE,
                KtTokens.CLOSING_QUOTE,
                KtTokens.REGULAR_STRING_PART,
            )
    }
}
