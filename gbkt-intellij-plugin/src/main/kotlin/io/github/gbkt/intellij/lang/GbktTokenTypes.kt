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

import com.intellij.psi.tree.IElementType
import io.github.gbkt.intellij.GbktLanguage

/**
 * Token types for gbkt language.
 *
 * Currently minimal - actual tokenization is handled by Kotlin plugin. These tokens are used for
 * gbkt-specific constructs.
 */
object GbktTokenTypes {
    /** Generic code token - represents Kotlin code within gbkt files. */
    val CODE = IElementType("GBKT_CODE", GbktLanguage)

    /**
     * DSL block token - represents gbkt-specific DSL constructs. Used for: scene {}, entity {},
     * dialog {}, etc.
     */
    val DSL_BLOCK = IElementType("GBKT_DSL_BLOCK", GbktLanguage)

    /**
     * DSL keyword token - represents gbkt DSL keywords. Used for: scene, entity, sprite, position,
     * etc.
     */
    val DSL_KEYWORD = IElementType("GBKT_DSL_KEYWORD", GbktLanguage)
}
