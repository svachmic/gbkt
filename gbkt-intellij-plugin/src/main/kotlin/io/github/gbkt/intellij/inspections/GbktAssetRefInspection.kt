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
package io.github.gbkt.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import java.io.File
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that validates asset() references in gbkt DSL files.
 *
 * Checks that every `asset("path/to/file.png")` call refers to a file that actually exists on disk
 * relative to the project's asset directories. If the file is missing, a problem is registered with
 * a quick-fix to create a 1x1 placeholder PNG.
 *
 * Asset search order:
 * 1. src/main/resources/
 * 2. src/main/assets/
 * 3. assets/
 * 4. resources/
 */
class GbktAssetRefInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Missing asset reference"

    override fun getGroupDisplayName(): String = "gbkt"

    override fun isEnabledByDefault(): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val projectPath = holder.project.basePath ?: return PsiElementVisitor.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // Only inspect asset() calls
                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName != "asset") return

                // Get the first string argument
                val args = expression.valueArguments
                if (args.isEmpty()) return

                val firstArg = args[0].getArgumentExpression() ?: return
                if (firstArg !is KtStringTemplateExpression) return

                // Only handle plain (non-interpolated) string templates
                val entries = firstArg.entries
                if (entries.size != 1) return
                val literalEntry = entries[0]
                val assetPath = literalEntry.text ?: return
                if (assetPath.isBlank()) return

                // Resolve the asset relative to known asset directories
                val resolvedFile = resolveAssetFile(projectPath, assetPath)

                if (resolvedFile == null || !resolvedFile.exists()) {
                    // Determine the best candidate path for the quick-fix
                    val candidatePath = File(projectPath, "src/main/resources/$assetPath")
                    holder.registerProblem(
                        firstArg,
                        "Asset file not found: $assetPath",
                        ProblemHighlightType.GENERIC_ERROR,
                        GbktCreateAssetPlaceholderQuickFix(candidatePath.absolutePath, assetPath),
                    )
                }
            }
        }
    }

    companion object {
        /** Asset search root directories relative to the project base path. */
        private val ASSET_SEARCH_ROOTS =
            listOf("src/main/resources", "src/main/assets", "assets", "resources")

        /**
         * Try to resolve an asset path relative to the project's known asset directories.
         *
         * @param projectPath Absolute project base path
         * @param assetPath Relative asset path from the asset() DSL call
         * @return The first existing File, or null if not found
         */
        fun resolveAssetFile(projectPath: String, assetPath: String): File? {
            for (root in ASSET_SEARCH_ROOTS) {
                val candidate = File(projectPath, "$root/$assetPath")
                if (candidate.exists()) return candidate
            }
            return null
        }
    }
}
