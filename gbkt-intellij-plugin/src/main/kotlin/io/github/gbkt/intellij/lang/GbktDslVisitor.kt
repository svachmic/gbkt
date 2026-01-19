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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Visitor that walks Kotlin PSI to identify gbkt DSL constructs.
 *
 * This visitor identifies:
 * - Scene definitions (scene("name") { ... })
 * - Entity definitions (entity { ... } or val name by entity { ... })
 * - Variable definitions (u8Var, u16Var, etc.)
 * - Dialog definitions
 * - Camera configurations
 * - Flag definitions
 *
 * Use this to build an index of DSL elements for navigation and validation.
 */
class GbktDslVisitor : PsiRecursiveElementVisitor() {

    /** Scene definitions found in the file. */
    val scenes = mutableListOf<DslDefinition>()

    /** Entity definitions found in the file. */
    val entities = mutableListOf<DslDefinition>()

    /** Variable definitions (u8Var, u16Var, etc.). */
    val variables = mutableListOf<DslDefinition>()

    /** Dialog definitions. */
    val dialogs = mutableListOf<DslDefinition>()

    /** Camera configurations. */
    val cameras = mutableListOf<DslDefinition>()

    /** Flag definitions. */
    val flags = mutableListOf<DslDefinition>()

    /** All DSL call expressions (for validation). */
    val allDslCalls = mutableListOf<DslCall>()

    /** Entity and scene references (for go-to-definition). */
    val references = mutableListOf<DslReference>()

    override fun visitElement(element: PsiElement) {
        when (element) {
            is KtProperty -> visitProperty(element)
            is KtCallExpression -> visitCallExpression(element)
            is KtNameReferenceExpression -> visitNameReference(element)
        }
        super.visitElement(element)
    }

    private fun visitProperty(property: KtProperty) {
        val name = property.name ?: return
        val delegateExpression = property.delegateExpression

        // Check for "val x by entity { ... }" pattern
        if (delegateExpression is KtCallExpression) {
            val callee = delegateExpression.calleeExpression?.text
            when (callee) {
                "entity" ->
                    entities.add(DslDefinition(name, DslType.ENTITY, property, delegateExpression))
                "scene" ->
                    scenes.add(DslDefinition(name, DslType.SCENE, property, delegateExpression))
                "dialog" ->
                    dialogs.add(DslDefinition(name, DslType.DIALOG, property, delegateExpression))
                "camera" ->
                    cameras.add(DslDefinition(name, DslType.CAMERA, property, delegateExpression))
                "u8Var",
                "u16Var",
                "i8Var",
                "i16Var" -> {
                    variables.add(
                        DslDefinition(name, DslType.VARIABLE, property, delegateExpression)
                    )
                }
                "u8Array",
                "u16Array" -> {
                    variables.add(DslDefinition(name, DslType.ARRAY, property, delegateExpression))
                }
            }
        }
    }

    private fun visitCallExpression(call: KtCallExpression) {
        val callee = call.calleeExpression?.text ?: return

        // Track all DSL calls for validation
        if (callee in DSL_FUNCTIONS) {
            allDslCalls.add(DslCall(callee, call))
        }

        // Capture top-level definitions without delegate pattern
        when (callee) {
            "scene" -> {
                val nameArg = getFirstStringArg(call)
                if (nameArg != null) {
                    scenes.add(DslDefinition(nameArg, DslType.SCENE, call, call))
                }
            }
            "gbGame" -> {
                // Root game definition
                allDslCalls.add(DslCall("gbGame", call))
            }
            "flags" -> {
                val nameArg = getFirstStringArg(call) ?: "flags"
                flags.add(DslDefinition(nameArg, DslType.FLAGS, call, call))
            }
        }
    }

    private fun visitNameReference(reference: KtNameReferenceExpression) {
        val name = reference.getReferencedName()

        // Check if this might be a reference to an entity or scene
        // Skip if it's the definition itself (in delegate pattern)
        val parent = reference.parent
        if (parent is KtProperty && parent.nameIdentifier == reference) {
            return
        }

        // Could be a reference to entity/scene
        references.add(DslReference(name, reference))
    }

    private fun getFirstStringArg(call: KtCallExpression): String? {
        val args = call.valueArguments
        if (args.isEmpty()) return null

        val firstArg = args[0].getArgumentExpression()
        val text = firstArg?.text ?: return null

        // Remove quotes from string literal
        return if (text.startsWith("\"") && text.endsWith("\"")) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }

    /** Types of DSL definitions. */
    enum class DslType {
        SCENE,
        ENTITY,
        DIALOG,
        CAMERA,
        VARIABLE,
        ARRAY,
        FLAGS,
    }

    /** A DSL definition (entity, scene, variable, etc.). */
    data class DslDefinition(
        val name: String,
        val type: DslType,
        val element: PsiElement,
        val callExpression: KtCallExpression,
    )

    /** A DSL function call. */
    data class DslCall(val functionName: String, val element: KtCallExpression)

    /** A potential reference to a DSL definition. */
    data class DslReference(val name: String, val element: KtNameReferenceExpression)

    companion object {
        /** All known gbkt DSL functions. */
        val DSL_FUNCTIONS =
            setOf(
                // Top-level
                "gbGame",
                "scene",
                "entity",
                "dialog",
                "camera",
                "flags",
                // Variables
                "u8Var",
                "u16Var",
                "i8Var",
                "i16Var",
                "u8Array",
                "u16Array",
                // Scene lifecycle
                "enter",
                "exit",
                "every",
                // Entity configuration
                "position",
                "velocity",
                "sprite",
                "hitbox",
                "combat",
                "states",
                "tag",
                // Control flow
                "whenever",
                "branch",
                "repeat",
                "repeatWhile",
                "repeatIndexed",
                // State machine
                "state",
                "tick",
                "on",
                "goto",
                // Sprite configuration
                "size",
                "palette",
                "regions",
                "animations",
                // Combat
                "maxHp",
                "attackPower",
                "defense",
                "team",
                "invincibilityFrames",
                // Dialog
                "box",
                "portrait",
                "textSpeed",
                "speaker",
                // Input
                "dpad",
                "buttons",
                "pressed",
                "released",
                "held",
                // Conditions
                "isEqualTo",
                "isGreaterThan",
                "isLessThan",
                "collidesWith",
                "overlaps",
            )

        /** DSL functions that require specific parent contexts. */
        val CONTEXT_REQUIREMENTS =
            mapOf(
                "enter" to setOf("scene"),
                "exit" to setOf("scene"),
                "position" to setOf("entity"),
                "velocity" to setOf("entity"),
                "sprite" to setOf("entity"),
                "hitbox" to setOf("entity", "sprite"),
                "combat" to setOf("entity"),
                "states" to setOf("entity"),
                "state" to setOf("states"),
                "tick" to setOf("state"),
                "on" to setOf("state"),
                "goto" to setOf("state"),
            )

        /** Creates a visitor and visits the given file. */
        fun analyze(file: PsiFile): GbktDslVisitor {
            val visitor = GbktDslVisitor()
            if (file is KtFile) {
                file.accept(visitor)
            }
            return visitor
        }

        /** Checks if this is a gbkt file. */
        fun isGbktFile(file: PsiFile): Boolean {
            return file.name.endsWith(".gbkt.kts")
        }
    }
}
