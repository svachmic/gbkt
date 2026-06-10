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
package io.github.gbkt.intellij.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.gbkt.intellij.GbktIcons
import javax.swing.Icon

/**
 * Color settings page for gbkt syntax highlighting.
 *
 * Allows users to customize colors for DSL elements in Settings > Editor > Color Scheme > gbkt.
 */
class GbktColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = GbktIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = GbktSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        return ADDITIONAL_TAGS
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "gbkt"

    companion object {
        private val DESCRIPTORS =
            arrayOf(
                AttributesDescriptor("DSL function", GbktSyntaxHighlighter.DSL_FUNCTION),
                AttributesDescriptor("Control flow", GbktSyntaxHighlighter.DSL_CONTROL_FLOW),
                AttributesDescriptor("Builder method", GbktSyntaxHighlighter.DSL_BUILDER_METHOD),
                AttributesDescriptor("Input", GbktSyntaxHighlighter.DSL_INPUT),
                AttributesDescriptor("Condition", GbktSyntaxHighlighter.DSL_CONDITION),
                AttributesDescriptor("Lifecycle callback", GbktSyntaxHighlighter.DSL_LIFECYCLE),
                AttributesDescriptor("Type reference", GbktSyntaxHighlighter.DSL_TYPE),
            )

        private val ADDITIONAL_TAGS =
            mapOf(
                "dsl_function" to GbktSyntaxHighlighter.DSL_FUNCTION,
                "control_flow" to GbktSyntaxHighlighter.DSL_CONTROL_FLOW,
                "builder" to GbktSyntaxHighlighter.DSL_BUILDER_METHOD,
                "input" to GbktSyntaxHighlighter.DSL_INPUT,
                "condition" to GbktSyntaxHighlighter.DSL_CONDITION,
                "lifecycle" to GbktSyntaxHighlighter.DSL_LIFECYCLE,
                "type" to GbktSyntaxHighlighter.DSL_TYPE,
            )

        private val DEMO_TEXT =
            """
            // gbkt Game Boy DSL Example

            val player by <dsl_function>entity</dsl_function> {
                <builder>position</builder>(80, 72)
                <builder>sprite</builder>(playerSprite) {
                    <builder>size</builder> = 8 x 16
                    <builder>hitbox</builder>(0, 0, 8, 16)
                }
                <builder>combat</builder> {
                    <builder>maxHp</builder>(100)
                    <builder>attackPower</builder>(10)
                }
            }

            val mainScene by <dsl_function>scene</dsl_function>("main") {
                <lifecycle>enter</lifecycle> {
                    screen.showSprites()
                }

                <lifecycle>every</lifecycle>.<lifecycle>frame</lifecycle> {
                    <control_flow>whenever</control_flow>(<input>dpad</input>.<input>right</input>) {
                        player.x += 2
                    }
                    <control_flow>whenever</control_flow>(<input>buttons</input>.<input>a</input>.<input>pressed</input>) {
                        attack()
                    }
                    <control_flow>whenever</control_flow>(player.<condition>collidesWith</condition>(enemy)) {
                        takeDamage()
                    }
                }
            }

            var score by <dsl_function>u8Var</dsl_function>(0)

            <control_flow>whenever</control_flow>(score <condition>isAtLeast</condition> 100) {
                win()
            }
            """
                .trimIndent()
    }
}
