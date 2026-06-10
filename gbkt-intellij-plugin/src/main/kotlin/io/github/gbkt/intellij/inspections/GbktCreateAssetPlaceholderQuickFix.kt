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

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Quick-fix that creates a minimal 1x1 transparent placeholder PNG at the referenced asset path.
 *
 * This allows developers to quickly scaffold missing assets so the DSL file compiles while they
 * work on the real asset. The placeholder is a 1x1 pixel transparent PNG.
 *
 * @param absoluteTargetPath Full absolute path where the placeholder PNG will be created
 * @param displayPath Relative path shown in the quick-fix description
 */
class GbktCreateAssetPlaceholderQuickFix(
    private val absoluteTargetPath: String,
    private val displayPath: String,
) : LocalQuickFix {

    override fun getName(): String = "Create placeholder PNG for '$displayPath'"

    override fun getFamilyName(): String = "gbkt: Create asset placeholder"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val targetFile = File(absoluteTargetPath)

        // Create parent directories as needed
        targetFile.parentFile?.mkdirs()

        // Write a minimal 1x1 transparent ARGB PNG
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        // Pixel (0,0) is already transparent (alpha=0) by default for TYPE_INT_ARGB

        try {
            ImageIO.write(image, "PNG", targetFile)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // If ImageIO fails, write a minimal valid PNG byte sequence manually as fallback
            targetFile.writeBytes(MINIMAL_1X1_TRANSPARENT_PNG)
        }

        // Notify VFS to pick up the new file
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
    }

    companion object {
        /**
         * Minimal valid 1x1 transparent PNG (33 bytes). This is a hard-coded fallback if ImageIO is
         * unavailable.
         */
        private val MINIMAL_1X1_TRANSPARENT_PNG: ByteArray =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A, // PNG signature
                0x00,
                0x00,
                0x00,
                0x0D, // IHDR length
                0x49,
                0x48,
                0x44,
                0x52, // "IHDR"
                0x00,
                0x00,
                0x00,
                0x01, // width=1
                0x00,
                0x00,
                0x00,
                0x01, // height=1
                0x08,
                0x06, // bit depth=8, color type=6 (RGBA)
                0x00,
                0x00,
                0x00, // compression, filter, interlace
                0x1F.toByte(),
                0x15,
                0xC4.toByte(),
                0x89.toByte(), // CRC
            )
    }
}
