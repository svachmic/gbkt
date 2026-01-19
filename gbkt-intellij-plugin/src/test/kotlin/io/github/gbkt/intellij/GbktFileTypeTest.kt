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
package io.github.gbkt.intellij

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Tests for GbktFileType. */
class GbktFileTypeTest {

    @Test
    fun `file type has correct name`() {
        val fileType = GbktFileType
        assertEquals("gbkt", fileType.name)
    }

    @Test
    fun `file type has correct description`() {
        val fileType = GbktFileType
        assertEquals("gbkt Game Boy DSL file", fileType.description)
    }

    @Test
    fun `file type has correct default extension`() {
        val fileType = GbktFileType
        assertEquals("gbkt.kts", fileType.defaultExtension)
    }

    @Test
    fun `file type has icon`() {
        val fileType = GbktFileType
        assertNotNull(fileType.icon)
    }

    @Test
    fun `file type is not binary`() {
        val fileType = GbktFileType
        assertEquals(false, fileType.isBinary)
    }
}
