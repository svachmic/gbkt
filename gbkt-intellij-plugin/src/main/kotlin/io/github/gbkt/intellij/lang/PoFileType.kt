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

import com.intellij.openapi.fileTypes.FileType
import io.github.gbkt.intellij.GbktIcons
import javax.swing.Icon

/**
 * File type definition for GNU gettext PO files.
 *
 * PO files are the industry standard for localization. This file type enables:
 * - File icon in project tree
 * - PO editor with GB font preview
 * - Syntax highlighting (basic)
 * - Validation for gbkt string constraints
 */
object PoFileType : FileType {
    override fun getName(): String = "PO"

    override fun getDescription(): String = "GNU gettext translation file"

    override fun getDefaultExtension(): String = "po"

    override fun getIcon(): Icon = GbktIcons.FILE

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = false
}

/**
 * File type definition for GNU gettext POT template files.
 *
 * POT files are translation templates (source of truth for translators).
 */
object PotFileType : FileType {
    override fun getName(): String = "POT"

    override fun getDescription(): String = "GNU gettext template file"

    override fun getDefaultExtension(): String = "pot"

    override fun getIcon(): Icon = GbktIcons.FILE

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = false
}
