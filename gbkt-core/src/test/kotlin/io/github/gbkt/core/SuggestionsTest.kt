/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/** Tests for the fuzzy matching and suggestion utilities. */
class SuggestionsTest {

    // =========================================================================
    // Levenshtein Distance Tests
    // =========================================================================

    @Test
    fun `levenshtein distance of identical strings is zero`() {
        assertEquals(0, Suggestions.levenshteinDistance("player", "player"))
        assertEquals(0, Suggestions.levenshteinDistance("", ""))
        assertEquals(0, Suggestions.levenshteinDistance("a", "a"))
    }

    @Test
    fun `levenshtein distance of empty string is length of other string`() {
        assertEquals(5, Suggestions.levenshteinDistance("", "hello"))
        assertEquals(5, Suggestions.levenshteinDistance("hello", ""))
    }

    @Test
    fun `levenshtein distance for single character difference`() {
        // Substitution
        assertEquals(1, Suggestions.levenshteinDistance("cat", "bat"))
        assertEquals(1, Suggestions.levenshteinDistance("cat", "cut"))
        assertEquals(1, Suggestions.levenshteinDistance("cat", "car"))

        // Insertion
        assertEquals(1, Suggestions.levenshteinDistance("cat", "cats"))
        assertEquals(1, Suggestions.levenshteinDistance("cat", "scat"))

        // Deletion
        assertEquals(1, Suggestions.levenshteinDistance("cats", "cat"))
        assertEquals(1, Suggestions.levenshteinDistance("scat", "cat"))
    }

    @Test
    fun `levenshtein distance for multiple differences`() {
        assertEquals(
            1,
            Suggestions.levenshteinDistance("playr", "player"),
        ) // Missing 'e' = 1 insertion
        assertEquals(
            1,
            Suggestions.levenshteinDistance("player", "plaer"),
        ) // Missing 'y' = 1 deletion
        assertEquals(3, Suggestions.levenshteinDistance("kitten", "sitting"))
        assertEquals(3, Suggestions.levenshteinDistance("saturday", "sunday"))
    }

    @Test
    fun `levenshtein distance is case insensitive`() {
        assertEquals(0, Suggestions.levenshteinDistance("Player", "player"))
        assertEquals(0, Suggestions.levenshteinDistance("PLAYER", "player"))
        assertEquals(1, Suggestions.levenshteinDistance("Playr", "player"))
    }

    // =========================================================================
    // Find Suggestions Tests
    // =========================================================================

    @Test
    fun `findSuggestions returns empty list for empty input`() {
        val candidates = listOf("player", "enemy", "bullet")
        assertEquals(emptyList(), Suggestions.findSuggestions("", candidates))
    }

    @Test
    fun `findSuggestions returns empty list for empty candidates`() {
        assertEquals(emptyList(), Suggestions.findSuggestions("player", emptyList()))
    }

    @Test
    fun `findSuggestions finds close matches`() {
        val candidates = listOf("player", "enemy", "bullet", "platform")

        // One character typo
        val suggestions = Suggestions.findSuggestions("playr", candidates)
        assertTrue(suggestions.contains("player"), "Should suggest 'player' for 'playr'")
    }

    @Test
    fun `findSuggestions does not return exact matches`() {
        val candidates = listOf("player", "enemy", "bullet")
        val suggestions = Suggestions.findSuggestions("player", candidates)
        assertFalse(suggestions.contains("player"), "Should not return exact match")
    }

    @Test
    fun `findSuggestions respects maxDistance`() {
        val candidates = listOf("player", "enemy", "bullet")

        // "enemyy" is distance 1 from "enemy" (1 extra 'y')
        val suggestions1 = Suggestions.findSuggestions("enemyy", candidates, maxDistance = 1)
        assertTrue(suggestions1.contains("enemy"), "Distance 1 should match with maxDistance=1")

        // "enemyyy" is distance 2 from "enemy" (2 extra 'y's)
        val suggestions2 = Suggestions.findSuggestions("enemyyy", candidates, maxDistance = 1)
        assertFalse(
            suggestions2.contains("enemy"),
            "Distance 2 should not match with maxDistance=1",
        )

        val suggestions3 = Suggestions.findSuggestions("enemyyy", candidates, maxDistance = 2)
        assertTrue(suggestions3.contains("enemy"), "Distance 2 should match with maxDistance=2")
    }

    @Test
    fun `findSuggestions sorts by distance then alphabetically`() {
        val candidates = listOf("player", "playe", "players", "play")

        val suggestions = Suggestions.findSuggestions("playr", candidates, maxDistance = 3)

        // Distances from "playr":
        // - "play" = 1 (delete 'r')
        // - "playe" = 1 (substitute 'r' → 'e')
        // - "player" = 1 (insert 'e')
        // - "players" = 2 (insert 'e' and 's')
        // All distance 1 sorted alphabetically: play < playe < player
        assertTrue(suggestions.isNotEmpty())
        assertEquals(
            "play",
            suggestions[0],
            "Closest alphabetically should be first among equal distances",
        )
        assertEquals("playe", suggestions[1])
        assertEquals("player", suggestions[2])
    }

    @Test
    fun `findSuggestions handles typical typos`() {
        val candidates = listOf("idle", "walking", "running", "jumping", "attacking")

        // Common typos
        assertEquals(listOf("idle"), Suggestions.findSuggestions("idel", candidates))
        assertEquals(listOf("walking"), Suggestions.findSuggestions("walkign", candidates))
        assertEquals(listOf("running"), Suggestions.findSuggestions("runnign", candidates))
    }

    // =========================================================================
    // Format Suggestion Tests
    // =========================================================================

    @Test
    fun `formatSuggestion returns empty string when no matches`() {
        val candidates = listOf("player", "enemy")
        val result = Suggestions.formatSuggestion("xxxxxxx", candidates)
        assertEquals("", result)
    }

    @Test
    fun `formatSuggestion formats single suggestion`() {
        val candidates = listOf("player", "enemy", "bullet")
        val result = Suggestions.formatSuggestion("playr", candidates)
        assertEquals(" Did you mean 'player'?", result)
    }

    @Test
    fun `formatSuggestion formats two suggestions`() {
        val candidates = listOf("play", "player", "playa")
        val result = Suggestions.formatSuggestion("playr", candidates, maxDistance = 2)
        // "play" and "playa" are both distance 1, "player" is distance 1 too
        // All distance 1, sorted alphabetically: play, playa, player
        assertTrue(result.contains("Did you mean"))
        assertTrue(result.contains("'") && result.contains("'"))
    }

    // =========================================================================
    // Format Multiple Suggestions Tests
    // =========================================================================

    @Test
    fun `formatMultipleSuggestions returns empty string when no matches`() {
        val candidates = listOf("player", "enemy")
        val result = Suggestions.formatMultipleSuggestions("xxxxxxx", candidates)
        assertEquals("", result)
    }

    @Test
    fun `formatMultipleSuggestions formats single suggestion`() {
        val candidates = listOf("player", "enemy", "bullet")
        val result = Suggestions.formatMultipleSuggestions("playr", candidates)
        assertEquals(" Did you mean 'player'?", result)
    }

    @Test
    fun `formatMultipleSuggestions formats multiple suggestions`() {
        val candidates = listOf("main", "menu", "game", "gameover")
        val result = Suggestions.formatMultipleSuggestions("mein", candidates, maxSuggestions = 3)
        assertTrue(result.contains("Did you mean one of:"))
        assertTrue(result.contains("'main'"))
        assertTrue(result.contains("'menu'"))
    }

    @Test
    fun `formatMultipleSuggestions respects maxSuggestions`() {
        val candidates = listOf("aaa", "aab", "aac", "aad", "aae")
        val result = Suggestions.formatMultipleSuggestions("aax", candidates, maxSuggestions = 2)
        // Should only show 2 suggestions
        val quoteCount = result.count { it == '\'' }
        assertEquals(4, quoteCount, "Should have 2 suggestions (4 quotes)")
    }

    // =========================================================================
    // Extension Function Tests
    // =========================================================================

    @Test
    fun `suggestFrom extension function works`() {
        val candidates = listOf("player", "enemy", "bullet")
        val suggestions = "playr".suggestFrom(candidates)
        assertTrue(suggestions.contains("player"))
    }

    @Test
    fun `formatSuggestionFrom extension function works`() {
        val candidates = listOf("player", "enemy", "bullet")
        val result = "playr".formatSuggestionFrom(candidates)
        assertEquals(" Did you mean 'player'?", result)
    }

    // =========================================================================
    // Integration-Style Tests (realistic scenarios)
    // =========================================================================

    @Test
    fun `suggests sprite names for typos`() {
        val sprites = listOf("player", "enemy", "bullet", "powerup", "explosion")

        // Typo: "plyer" -> "player"
        val suggestion1 = Suggestions.formatSuggestion("plyer", sprites)
        assertEquals(" Did you mean 'player'?", suggestion1)

        // Typo: "enmy" -> "enemy"
        val suggestion2 = Suggestions.formatSuggestion("enmy", sprites)
        assertEquals(" Did you mean 'enemy'?", suggestion2)

        // Typo: "bullte" -> "bullet"
        val suggestion3 = Suggestions.formatSuggestion("bullte", sprites)
        assertEquals(" Did you mean 'bullet'?", suggestion3)
    }

    @Test
    fun `suggests animation names for typos`() {
        val animations = listOf("idle", "walk", "run", "jump", "attack", "die")

        // Typo: "idel" -> "idle"
        val suggestion1 = Suggestions.formatSuggestion("idel", animations)
        assertEquals(" Did you mean 'idle'?", suggestion1)

        // Typo: "wlak" -> "walk"
        val suggestion2 = Suggestions.formatSuggestion("wlak", animations)
        assertEquals(" Did you mean 'walk'?", suggestion2)
    }

    @Test
    fun `suggests state names for typos`() {
        val states = listOf("idle", "walking", "running", "jumping", "falling", "attacking")

        // Typo: "jumpnig" -> "jumping"
        val suggestion = Suggestions.formatSuggestion("jumpnig", states)
        assertEquals(" Did you mean 'jumping'?", suggestion)
    }

    @Test
    fun `suggests scene names for typos`() {
        val scenes = listOf("title", "gameplay", "gameover", "credits", "settings")

        // Typo: "gameply" -> "gameplay"
        val suggestion = Suggestions.formatSuggestion("gameply", scenes)
        assertEquals(" Did you mean 'gameplay'?", suggestion)
    }

    @Test
    fun `no suggestion when too different`() {
        val sprites = listOf("player", "enemy", "bullet")

        // Completely different name
        val suggestion = Suggestions.formatSuggestion("something_completely_different", sprites)
        assertEquals("", suggestion)
    }
}
