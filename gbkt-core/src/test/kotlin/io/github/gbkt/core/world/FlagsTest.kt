/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagRefTest {
    @Test
    fun `flag ref calculates global index`() {
        // Create a flag with page index 0
        val flag = FlagRef("flag1", pageIndex = 0, indexInPage = 5)

        assertEquals("flag1", flag.name)
        assertEquals(5, flag.indexInPage)
        assertEquals(5, flag.globalIndex) // page 0, index 5 = 5
    }

    @Test
    fun `flag ref calculates global index across pages`() {
        val flag = FlagRef("flag1", pageIndex = 2, indexInPage = 10)

        // Page 2, index 10 = 2 * 32 + 10 = 74
        assertEquals(74, flag.globalIndex)
    }

    @Test
    fun `flag ref calculates byte offset and bit mask`() {
        // Flag at index 0 should be byte 0, bit 0
        val flag0 = FlagRef("flag0", pageIndex = 0, indexInPage = 0)
        assertEquals(0, flag0.byteOffset)
        assertEquals(1, flag0.bitMask)

        // Flag at index 7 should be byte 0, bit 7
        val flag7 = FlagRef("flag7", pageIndex = 0, indexInPage = 7)
        assertEquals(0, flag7.byteOffset)
        assertEquals(128, flag7.bitMask)

        // Flag at index 8 should be byte 1, bit 0
        val flag8 = FlagRef("flag8", pageIndex = 0, indexInPage = 8)
        assertEquals(1, flag8.byteOffset)
        assertEquals(1, flag8.bitMask)

        // Flag at index 15 should be byte 1, bit 7
        val flag15 = FlagRef("flag15", pageIndex = 0, indexInPage = 15)
        assertEquals(1, flag15.byteOffset)
        assertEquals(128, flag15.bitMask)
    }

    @Test
    fun `flag ref byte offset works across pages`() {
        val flag = FlagRef("flag", pageIndex = 1, indexInPage = 0)

        // Page 1, index 0 = global index 32, byte offset = 4
        assertEquals(32, flag.globalIndex)
        assertEquals(4, flag.byteOffset)
        assertEquals(1, flag.bitMask)
    }

    @Test
    fun `flag ref page binding works`() {
        val flag = FlagRef("testFlag", pageIndex = 0, indexInPage = 3)
        assertNull(flag.page) // Not bound yet

        val page = FlagPage("testPage", 0, listOf(flag))
        flag.bindToPage(page)

        assertNotNull(flag.page)
        assertEquals("testPage", flag.page!!.name)
        assertEquals(0, flag.page!!.pageIndex)
    }
}

class FlagPageTest {
    @Test
    fun `flag page can be created`() {
        val page = FlagPage("chests", 0, emptyList())

        assertEquals("chests", page.name)
        assertEquals(0, page.pageIndex)
        assertTrue(page.flags.isEmpty())
    }

    @Test
    fun `flag page validates page index`() {
        // Valid page indices (0-7)
        for (i in 0..7) {
            FlagPage("test$i", i, emptyList()) // Should not throw
        }
    }
}

class GlobalFlagsTest {
    @Test
    fun `global flags can be created`() {
        val page1 = FlagPage("page1", 0, emptyList())
        val page2 = FlagPage("page2", 1, emptyList())
        val flags = GlobalFlags(listOf(page1, page2))

        assertEquals(2, flags.pages.size)
        assertEquals(0, flags.totalFlags)
        assertEquals(32, flags.sizeInBytes)
    }

    @Test
    fun `global flags can get page by name`() {
        val page1 = FlagPage("chests", 0, emptyList())
        val page2 = FlagPage("story", 1, emptyList())
        val flags = GlobalFlags(listOf(page1, page2))

        assertNotNull(flags.getPage("chests"))
        assertNotNull(flags.getPage("story"))
        assertNull(flags.getPage("nonexistent"))
    }

    @Test
    fun `global flags can get flag by name`() {
        val flag = FlagRef("myFlag", pageIndex = 0, indexInPage = 0)
        val pageWithFlags = FlagPage("test", 0, listOf(flag))
        val flags = GlobalFlags(listOf(pageWithFlags))

        val found = flags.getFlag("myFlag")
        assertNotNull(found)
        assertEquals("myFlag", found.name)

        assertNull(flags.getFlag("nonexistent"))
    }
}

class GlobalFlagsBuilderTest {
    @Test
    fun `builder creates empty flags system`() {
        val builder = GlobalFlagsBuilder()
        val flags = builder.build()

        assertTrue(flags.pages.isEmpty())
        assertEquals(0, flags.totalFlags)
    }

    @Test
    fun `builder can add pages`() {
        val builder = GlobalFlagsBuilder()
        builder.page("chests") {}
        builder.page("story") {}

        val flags = builder.build()

        assertEquals(2, flags.pages.size)
        assertEquals("chests", flags.pages[0].name)
        assertEquals(0, flags.pages[0].pageIndex)
        assertEquals("story", flags.pages[1].name)
        assertEquals(1, flags.pages[1].pageIndex)
    }
}

class FlagPageBuilderTest {
    @Test
    fun `builder creates page with flags`() {
        val builder = FlagPageBuilder("test", 0)
        builder.flag("flag1")
        builder.flag("flag2")
        builder.flag("flag3")

        val page = builder.build()

        assertEquals("test", page.name)
        assertEquals(0, page.pageIndex)
        assertEquals(3, page.flags.size)
        assertEquals("flag1", page.flags[0].name)
        assertEquals("flag2", page.flags[1].name)
        assertEquals("flag3", page.flags[2].name)
    }

    @Test
    fun `builder assigns correct flag indices`() {
        val builder = FlagPageBuilder("test", 2)
        builder.flag("first")
        builder.flag("second")
        builder.flag("third")

        val page = builder.build()

        assertEquals(0, page.flags[0].indexInPage)
        assertEquals(1, page.flags[1].indexInPage)
        assertEquals(2, page.flags[2].indexInPage)

        // Global indices should account for page offset
        // Page 2 starts at index 64 (2 * 32)
        assertEquals(64, page.flags[0].globalIndex)
        assertEquals(65, page.flags[1].globalIndex)
        assertEquals(66, page.flags[2].globalIndex)
    }
}

class FlagsDslTest {
    @Test
    fun `flags DSL creates flags system`() {
        val gameFlags = flags {
            page("chests") {
                flag("chest1Opened")
                flag("chest2Opened")
            }
            page("story") {
                flag("talkedToKing")
                flag("defeatedBoss")
            }
        }

        assertEquals(2, gameFlags.pages.size)
        assertEquals(4, gameFlags.totalFlags)

        val chestPage = gameFlags.getPage("chests")
        assertNotNull(chestPage)
        assertEquals(2, chestPage.flags.size)

        val storyPage = gameFlags.getPage("story")
        assertNotNull(storyPage)
        assertEquals(2, storyPage.flags.size)

        // Verify flag lookup
        assertNotNull(gameFlags.getFlag("chest1Opened"))
        assertNotNull(gameFlags.getFlag("defeatedBoss"))
        assertNull(gameFlags.getFlag("nonexistent"))
    }

    @Test
    fun `flags DSL creates correct global indices`() {
        val gameFlags = flags {
            page("page0") {
                flag("p0_f0") // index 0
                flag("p0_f1") // index 1
            }
            page("page1") {
                flag("p1_f0") // index 32
                flag("p1_f1") // index 33
            }
        }

        val p0f0 = gameFlags.getFlag("p0_f0")
        assertNotNull(p0f0)
        assertEquals(0, p0f0.globalIndex)

        val p0f1 = gameFlags.getFlag("p0_f1")
        assertNotNull(p0f1)
        assertEquals(1, p0f1.globalIndex)

        val p1f0 = gameFlags.getFlag("p1_f0")
        assertNotNull(p1f0)
        assertEquals(32, p1f0.globalIndex)

        val p1f1 = gameFlags.getFlag("p1_f1")
        assertNotNull(p1f1)
        assertEquals(33, p1f1.globalIndex)
    }
}

class FlagOperationsTest {
    @Test
    fun `flag operations create IR nodes`() {
        val gameFlags = flags { page("test") { flag("testFlag") } }

        val ops = FlagOperations(gameFlags)
        val flag = gameFlags.getFlag("testFlag")!!

        val isSetIR = ops.isSet(flag)
        assertEquals(flag, isSetIR.flag)

        val setIR = ops.set(flag)
        assertEquals(flag, setIR.flag)

        val clearIR = ops.clear(flag)
        assertEquals(flag, clearIR.flag)

        val toggleIR = ops.toggle(flag)
        assertEquals(flag, toggleIR.flag)
    }

    @Test
    fun `flag operations can lookup by name`() {
        val gameFlags = flags { page("test") { flag("myFlag") } }

        val ops = FlagOperations(gameFlags)

        val isSet = ops.isSet("myFlag")
        assertNotNull(isSet)
        assertEquals("myFlag", isSet.flag.name)

        val notFound = ops.isSet("nonexistent")
        assertNull(notFound)
    }
}
