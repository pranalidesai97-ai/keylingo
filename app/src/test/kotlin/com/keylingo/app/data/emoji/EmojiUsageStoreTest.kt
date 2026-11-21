package com.keylingo.app.data.emoji

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Recently/frequently used emoji, stored on the device in their own DataStore file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiUsageStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun clear() = runBlocking { EmojiUsageStore(context).clear() }

    @Test fun mostUsedFirstThenMostRecent() = runBlocking {
        val store = EmojiUsageStore(context)
        store.record("😂", now = 1); store.record("❤️", now = 2); store.record("😂", now = 3); store.record("🎉", now = 4)
        assertEquals(listOf("😂", "🎉", "❤️"), store.usage.first().map { it.emoji })
        assertEquals(2, store.usage.first().first().count)
    }

    @Test fun survivesANewStoreInstance() = runBlocking {
        EmojiUsageStore(context).record("🙏", now = 10)
        EmojiUsageStore(context).record("🙏", now = 11)
        val reopened = EmojiUsageStore(context).usage.first()
        assertEquals("🙏", reopened.single().emoji)
        assertEquals(2, reopened.single().count)
        assertEquals(11L, reopened.single().lastUsedAt)
    }

    @Test fun keepsABoundedHistoryAndNeverDropsTheEmojiJustUsed() {
        var uses = emptyList<EmojiUse>()
        repeat(EmojiUsageStore.MAX_ENTRIES) { i -> uses = EmojiUsageStore.recordInto(uses, "e$i", i.toLong()); uses = EmojiUsageStore.recordInto(uses, "e$i", i.toLong()) }
        uses = EmojiUsageStore.recordInto(uses, "new", 1000)
        assertEquals(EmojiUsageStore.MAX_ENTRIES, uses.size)
        assertTrue("the emoji just used is kept", uses.any { it.emoji == "new" })
        // Encoding round-trips, including multi-codepoint emoji.
        val sample = listOf(EmojiUse("👨‍👩‍👧", 3, 5), EmojiUse("🇪🇸", 1, 7))
        assertEquals(EmojiUsageStore.ranked(sample), EmojiUsageStore.decode(EmojiUsageStore.encode(sample)))
    }
}
