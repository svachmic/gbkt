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
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix that clamps an out-of-range value to valid bounds.
 *
 * When a Game Boy constraint violation is detected (e.g., coordinate > 255),
 * this quick fix offers to clamp the value to the valid range.
 */
class ClampValueQuickFix(
    private val currentValue: Int,
    private val min: Int,
    private val max: Int,
    private val parameterName: String,
) : LocalQuickFix {

    private val clampedValue: Int = currentValue.coerceIn(min, max)

    override fun getName(): String = "Clamp $parameterName to $clampedValue"

    override fun getFamilyName(): String = "gbkt constraint quick fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val psiFactory = KtPsiFactory(project)

        WriteCommandAction.runWriteCommandAction(project) {
            val newExpression = psiFactory.createExpression(clampedValue.toString())
            element.replace(newExpression)
        }
    }

    companion object {
        /** Common Game Boy value constraints. */
        object Constraints {
            val U8_RANGE = 0..255
            val U16_RANGE = 0..65535
            val I8_RANGE = -128..127
            val I16_RANGE = -32768..32767
            val PALETTE_INDEX = 0..7
            val SPRITE_SIZE = listOf(8, 16)

            /**
             * Screen-space X coordinate (visible area).
             * Game Boy screen is 160 pixels wide.
             */
            val SCREEN_X = 0..159

            /**
             * Screen-space Y coordinate (visible area).
             * Game Boy screen is 144 pixels tall.
             */
            val SCREEN_Y = 0..143

            /**
             * World-space coordinates (can extend beyond screen).
             * Used for world position, scrolling backgrounds, etc.
             */
            val WORLD_X = 0..255
            val WORLD_Y = 0..255
        }

        /** Default values for quick fix templates. */
        object Defaults {
            /** Default entity position (center of screen). */
            const val ENTITY_X = 80
            const val ENTITY_Y = 72

            /** Default sprite size. */
            const val SPRITE_WIDTH = 8
            const val SPRITE_HEIGHT = 8

            /** Default array size for new arrays. */
            const val ARRAY_SIZE = 10

            /** Default stats for RPG characters. */
            const val DEFAULT_HP = 100
            const val DEFAULT_SP = 50
            const val DEFAULT_ATK = 10
            const val DEFAULT_DEF = 10
            const val DEFAULT_MATK = 10
            const val DEFAULT_MDEF = 10
            const val DEFAULT_AGL = 10

            /** Default monster stats. */
            const val DEFAULT_MONSTER_HP = 30
            const val DEFAULT_MONSTER_EXP = 10
        }
    }
}
