package com.keylingo.app.data.emoji

import com.keylingo.app.data.emoji.BundledEmojiProvider.Companion.maxEmojiVersion
import com.keylingo.app.domain.model.EmojiCategory
import com.keylingo.app.testing.realEmojiCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled emoji catalogue (assets/emoji/emoji.tsv): categories, keyword search and smart suggestions. */
class EmojiCatalogTest {

    private val catalog = realEmojiCatalog()

    @Test fun everyPanelCategoryIsFilledAndNothingIsListedTwice() {
        val panel = EmojiCategory.entries.filter { it != EmojiCategory.RECENT }
        assertEquals(panel.toSet(), catalog.byCategory.keys)
        for (c in panel) assertTrue("$c is empty", catalog.byCategory.getValue(c).size >= 50)
        val all = catalog.byCategory.values.flatten()
        assertEquals("duplicates", all.size, all.toSet().size)
        assertTrue("${all.size} emoji", all.size > 1800)
        assertTrue("🐶" in catalog.byCategory.getValue(EmojiCategory.ANIMALS))
        assertTrue("🍕" in catalog.byCategory.getValue(EmojiCategory.FOOD))
        assertTrue("🇪🇸" in catalog.byCategory.getValue(EmojiCategory.FLAGS))
        assertEquals("😀", catalog.byCategory.getValue(EmojiCategory.SMILEYS).first())
    }

    @Test fun olderPhonesOnlyGetEmojiTheirFontCanDraw() {
        assertEquals(5.0, maxEmojiVersion(26), 0.0) // Android 8
        assertEquals(11.0, maxEmojiVersion(28), 0.0)
        val oreo = realEmojiCatalog(maxEmojiVersion(26))
        val all = oreo.byCategory.values.flatten()
        assertTrue("🥰 (Emoji 11) on Android 8", "🥰" !in all)
        assertTrue("😂" in all && "❤️" in all)
        // Suggestions skip what the phone cannot draw, keeping the order of the rest.
        assertEquals(listOf("❤️", "😘", "😍", "💕", "💖"), oreo.suggestionsFor("love", "en"))
    }

    @Test fun searchFindsEmojiByEnglishOrSpanishKeyword() {
        assertTrue(catalog.search("heart").contains("❤️"))
        assertEquals("❤️", catalog.search("red hea").first())
        assertTrue(catalog.search("corazón").contains("❤️"))
        assertTrue("accent-insensitive", catalog.search("corazon").contains("❤️"))
        assertTrue(catalog.search("dog").take(3).contains("🐶"))
        assertTrue(catalog.search("pizza").contains("🍕"))
        assertTrue(catalog.search("perro").contains("🐶"))
        assertTrue(catalog.search("").isEmpty())
        assertTrue(catalog.search("zzqqxx").isEmpty())
    }

    @Test fun smartSuggestionsForEverydayWords() {
        val expected = mapOf(
            "heart" to listOf("❤️", "💕", "💖"), "love" to listOf("❤️", "🥰", "😘"), "smile" to listOf("😊", "😄", "🙂"),
            "happy" to listOf("😊", "😍", "🥳"), "birthday" to listOf("🎂", "🎉", "🎈"), "laugh" to listOf("😂", "🤣", "😆"),
            "sad" to listOf("😢", "😭", "🥺"), "thanks" to listOf("🙏", "❤️"),
        )
        for ((word, first) in expected) assertEquals(word, first, catalog.suggestionsFor(word, "en").take(first.size))
        // Case and trailing punctuation do not matter.
        assertEquals(listOf("❤️", "🥰", "😘"), catalog.suggestionsFor("Love!", "en").take(3))
        // Spanish words, with or without accents.
        assertEquals("🙏", catalog.suggestionsFor("gracias", "es").first())
        assertEquals("🎂", catalog.suggestionsFor("cumpleaños", "es").first())
        assertEquals("❤️", catalog.suggestionsFor("corazon", "es").first())
        // Other emoji-related words come from the keywords.
        assertTrue(catalog.suggestionsFor("dog", "en").contains("🐶"))
        // Not emoji-related: nothing.
        for (w in listOf("the", "with", "of", "and", "a", "I")) assertTrue(w, catalog.suggestionsFor(w, "en").isEmpty())
        for (w in listOf("de", "con", "la")) assertTrue(w, catalog.suggestionsFor(w, "es").isEmpty())
    }

    @Test fun curatedListsOnlyNameEmojiThatExist() {
        val known = realEmojiCatalog(99.0).entries.map { it.emoji }.toSet()
        val bad = (EmojiCatalog.CURATED_EN + EmojiCatalog.CURATED_ES).flatMap { (w, l) -> l.filter { it !in known }.map { "$w:$it" } }
        assertTrue("not in the catalogue: $bad", bad.isEmpty())
        // Keys are stored folded, as lookups fold the typed word.
        val unfolded = (EmojiCatalog.CURATED_EN.keys + EmojiCatalog.CURATED_ES.keys).filter { EmojiCatalog.fold(it) != it }
        assertTrue(unfolded.toString(), unfolded.isEmpty())
    }

    @Test fun searchAndSuggestionsStayFast() {
        repeat(3) { catalog.search("hea"); catalog.suggestionsFor("happy", "en") } // warm-up
        val start = System.nanoTime()
        val queries = listOf("h", "he", "hea", "heart", "s", "sm", "smi", "smile", "c", "ca", "cat", "f", "fl", "flag", "red heart")
        repeat(20) { queries.forEach { q -> catalog.search(q); catalog.suggestionsFor(q, "en") } }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / (20 * queries.size)
        println("EMOJI search+suggest per keystroke: %.3f ms (desktop JVM)".format(perCall))
        assertTrue("$perCall ms", perCall < 5.0)
    }
}
