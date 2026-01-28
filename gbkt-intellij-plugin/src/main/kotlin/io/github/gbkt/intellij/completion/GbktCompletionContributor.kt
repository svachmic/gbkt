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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns

/**
 * Code completion contributor for gbkt DSL.
 *
 * Provides context-aware code completion for:
 * - Top-level DSL functions (gbGame, scene, entity, etc.)
 * - Builder methods (position, sprite, combat, etc.)
 * - Input keywords (dpad, buttons, etc.)
 * - Condition operators (isEqualTo, collidesWith, etc.)
 *
 * Since gbkt is a Kotlin DSL, this contributor works alongside the Kotlin plugin's completion to
 * add DSL-specific suggestions.
 */
class GbktCompletionContributor : CompletionContributor() {

    init {
        // Register keyword completion for .gbkt.kts files
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), GbktKeywordCompletionProvider())

        // Register builder method completion
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), GbktBuilderCompletionProvider())

        // Register type-aware completion for enum values
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), GbktTypeAwareCompletionProvider())

        // Register property chain completion for dot expressions
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), GbktPropertyChainCompletionProvider())
    }
}
