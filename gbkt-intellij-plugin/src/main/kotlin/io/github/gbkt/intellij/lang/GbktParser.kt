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
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Parser for gbkt files.
 *
 * Creates a minimal AST structure. The actual Kotlin PSI analysis is performed by the
 * GbktDslVisitor which leverages the Kotlin plugin's PSI infrastructure.
 *
 * Note: Full Kotlin parsing is handled by the Kotlin plugin when files are opened. This parser just
 * provides the basic file structure needed by the IntelliJ Platform.
 */
class GbktParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        // Consume all tokens - actual PSI structure comes from Kotlin plugin
        while (!builder.eof()) {
            builder.advanceLexer()
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }
}
