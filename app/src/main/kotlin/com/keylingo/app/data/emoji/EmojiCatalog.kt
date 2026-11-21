package com.keylingo.app.data.emoji

import com.keylingo.app.domain.model.EmojiCategory
import java.text.Normalizer

/** One emoji from assets/emoji/emoji.tsv. Keywords are lowercase words from its CLDR name and tags. */
data class EmojiEntry(
    val emoji: String,
    val category: EmojiCategory,
    val version: Double,
    val keywordsEn: List<String>,
    val keywordsEs: List<String>,
)

/**
 * Immutable, in-memory emoji catalogue: category lists, keyword search and
 * smart suggestions for a typed word. Pure Kotlin (no Android types), built once
 * on a background thread; every lookup is a sorted-array range scan.
 */
class EmojiCatalog(entries: List<EmojiEntry>) {

    val entries: List<EmojiEntry> = entries
    private val available: Set<String> = entries.mapTo(HashSet()) { it.emoji }

    val byCategory: Map<EmojiCategory, List<String>> =
        entries.groupBy({ it.category }, { it.emoji }).let { grouped ->
            EmojiCategory.entries.filter { it != EmojiCategory.RECENT }.associateWith { grouped[it].orEmpty() }
        }

    /** (folded keyword, entry index) pairs sorted by keyword then catalogue order, for prefix search. */
    private val keys: Array<String>
    private val owners: IntArray
    /** Exact folded keyword -> entry indices in catalogue order, per language. */
    private val exactEn: Map<String, List<Int>>
    private val exactEs: Map<String, List<Int>>
    /** Folded keywords per entry, English then Spanish, in file order (the CLDR name's words come first). */
    private val folded: Array<List<String>> = Array(entries.size) { i -> (entries[i].keywordsEn + entries[i].keywordsEs).map(::fold) }

    init {
        val pairs = ArrayList<Pair<String, Int>>()
        val en = HashMap<String, MutableList<Int>>()
        val es = HashMap<String, MutableList<Int>>()
        entries.forEachIndexed { i, e ->
            val words = LinkedHashSet<String>()
            e.keywordsEn.forEach { k -> fold(k).also { words += it; en.getOrPut(it) { mutableListOf() }.add(i) } }
            e.keywordsEs.forEach { k -> fold(k).also { words += it; es.getOrPut(it) { mutableListOf() }.add(i) } }
            words.forEach { pairs += it to i }
        }
        pairs.sortWith(compareBy<Pair<String, Int>>({ it.first }, { it.second }))
        keys = Array(pairs.size) { pairs[it].first }
        owners = IntArray(pairs.size) { pairs[it].second }
        exactEn = en; exactEs = es
    }

    /**
     * Emoji whose keywords start with every word of [query] ("red hea" -> ❤️), English or Spanish,
     * accent-insensitive. Ranked by how early the typed words appear among the emoji's keywords (its
     * name comes first: "red heart" beats "hot face" for "red hea"), then exact matches, then catalogue order.
     * A single everyday word with a curated list ("heart") shows that list first.
     */
    fun search(query: String, limit: Int = 60): List<String> {
        val tokens = fold(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        var matched: Set<Int>? = null
        val exact = HashSet<Int>()
        for (token in tokens) {
            val hits = HashSet<Int>()
            var i = lowerBound(token)
            while (i < keys.size && keys[i].startsWith(token)) {
                hits += owners[i]
                if (keys[i] == token) exact += owners[i]
                i++
            }
            matched = matched?.intersect(hits) ?: hits
            if (matched.isEmpty()) return emptyList()
        }
        val position = matched!!.associateWith { i -> tokens.sumOf { t -> folded[i].indexOfFirst { it.startsWith(t) }.coerceAtLeast(0) } }
        val ranked = matched.sortedWith(compareBy({ position.getValue(it) }, { if (it in exact) 0 else 1 }, { it })).map { entries[it].emoji }
        // A single everyday word ("heart", "gracias") leads with the same emoji the suggestion row offers (❤️ first).
        val curated = if (tokens.size == 1) (CURATED_EN[tokens[0]] ?: CURATED_ES[tokens[0]]).orEmpty().filter { it in available } else emptyList()
        return (curated + ranked).distinct().take(limit)
    }

    /**
     * Emoji for a word the user typed. A curated list for common words first (so "heart",
     * "love", "happy"... give the expected emoji in the expected order, in English or Spanish),
     * otherwise emoji with that exact keyword. Function words never trigger suggestions.
     */
    fun suggestionsFor(word: String, languageCode: String, limit: Int = 6): List<String> {
        val w = fold(word.trim().trim('\'', '’', '.', ',', '!', '?', '¡', '¿'))
        if (w.length < 2) return emptyList()
        val curatedList = (if (languageCode == "es") CURATED_ES[w] ?: CURATED_EN[w] else CURATED_EN[w])
        if (curatedList != null) return curatedList.filter { it in available }.take(limit)
        val stop = if (languageCode == "es") STOP_ES else STOP_EN
        if (w in stop) return emptyList()
        val index = if (languageCode == "es") exactEs else exactEn
        return index[w].orEmpty().take(limit).map { entries[it].emoji }
    }

    private fun lowerBound(key: String): Int {
        var lo = 0; var hi = keys.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (keys[mid] < key) lo = mid + 1 else hi = mid }
        return lo
    }

    companion object {
        const val ASSET = "emoji/emoji.tsv"

        /** Lowercase without accents ("Corazón" -> "corazon"), so either spelling matches. */
        fun fold(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

        private val COMBINING_MARKS = Regex("\\p{M}+")

        /** Parses emoji.tsv, keeping entries [isAvailable] accepts (the device can draw them). */
        fun parse(lines: Sequence<String>, isAvailable: (emoji: String, version: Double) -> Boolean): EmojiCatalog {
            val out = ArrayList<EmojiEntry>(2000)
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val c = line.split('\t')
                if (c.size < 3) continue
                val category = EmojiCategory.fromCode(c[1]) ?: continue
                val version = c[2].toDoubleOrNull() ?: 0.0
                if (!isAvailable(c[0], version)) continue
                out += EmojiEntry(c[0], category, version, c.getOrElse(3) { "" }.split(' ').filter { it.isNotEmpty() },
                    c.getOrElse(4) { "" }.split(' ').filter { it.isNotEmpty() })
            }
            return EmojiCatalog(out)
        }

        /** Common words and the emoji people expect for them, best first. Keys are folded (lowercase, no accents). */
        internal val CURATED_EN: Map<String, List<String>> = mapOf(
            "heart" to listOf("❤️", "💕", "💖", "💗", "💓", "💞"),
            "hearts" to listOf("💕", "❤️", "💖", "💞", "💗"),
            "love" to listOf("❤️", "🥰", "😘", "😍", "💕", "💖"),
            "loved" to listOf("❤️", "🥰", "😍", "💕"),
            "loving" to listOf("🥰", "❤️", "😍", "💕"),
            "lovely" to listOf("🥰", "😍", "💖", "✨"),
            "smile" to listOf("😊", "😄", "🙂", "😁", "😀"),
            "smiling" to listOf("😊", "😄", "🙂", "😁"),
            "happy" to listOf("😊", "😍", "🥳", "😄", "😁"),
            "happiness" to listOf("😊", "🥰", "✨"),
            "birthday" to listOf("🎂", "🎉", "🎈", "🥳", "🎁"),
            "bday" to listOf("🎂", "🎉", "🎈", "🥳"),
            "laugh" to listOf("😂", "🤣", "😆", "😹"),
            "laughing" to listOf("😂", "🤣", "😆"),
            "lol" to listOf("😂", "🤣", "😆"),
            "haha" to listOf("😂", "🤣", "😆"),
            "hahaha" to listOf("😂", "🤣", "😆"),
            "funny" to listOf("😂", "🤣", "😆", "😜"),
            "sad" to listOf("😢", "😭", "🥺", "😔", "😞"),
            "cry" to listOf("😢", "😭", "🥺"),
            "crying" to listOf("😭", "😢", "🥺"),
            "sorry" to listOf("🙏", "😔", "🥺", "😞"),
            "thanks" to listOf("🙏", "❤️", "😊", "🤗"),
            "thank" to listOf("🙏", "❤️", "😊", "🤗"),
            "thx" to listOf("🙏", "❤️", "😊"),
            "please" to listOf("🙏", "🥺"),
            "party" to listOf("🎉", "🥳", "🎊", "🍾"),
            "congrats" to listOf("🎉", "🥳", "👏", "🎊"),
            "congratulations" to listOf("🎉", "🥳", "👏", "🎊"),
            "celebrate" to listOf("🎉", "🥳", "🍾", "🎊"),
            "kiss" to listOf("😘", "💋", "😗", "😚"),
            "kisses" to listOf("😘", "💋", "😚"),
            "hug" to listOf("🤗", "🫂", "❤️"),
            "hugs" to listOf("🤗", "🫂", "❤️"),
            "cool" to listOf("😎", "👍", "🆒"),
            "fire" to listOf("🔥", "🚒"),
            "ok" to listOf("👌", "👍", "🆗"),
            "okay" to listOf("👌", "👍", "🆗"),
            "yes" to listOf("👍", "✅", "👌"),
            "good" to listOf("👍", "👌", "😊"),
            "great" to listOf("👍", "🙌", "😄", "💯"),
            "awesome" to listOf("🤩", "🙌", "💯", "🔥"),
            "wow" to listOf("😮", "🤩", "😲"),
            "omg" to listOf("😱", "😮", "😲"),
            "angry" to listOf("😠", "😡", "🤬"),
            "mad" to listOf("😠", "😡", "🤬"),
            "tired" to listOf("😴", "🥱", "😩"),
            "sleep" to listOf("😴", "💤", "🛌"),
            "sleepy" to listOf("😴", "🥱", "💤"),
            "hello" to listOf("👋", "😊", "🙂"),
            "hi" to listOf("👋", "😊", "🙂"),
            "bye" to listOf("👋", "😘", "🙋"),
            "night" to listOf("🌙", "😴", "🌃", "✨"),
            "morning" to listOf("☀️", "🌅", "☕"),
            "coffee" to listOf("☕", "🥤"),
            "pizza" to listOf("🍕"),
            "cake" to listOf("🍰", "🎂", "🧁"),
            "beer" to listOf("🍺", "🍻"),
            "wine" to listOf("🍷", "🥂"),
            "dog" to listOf("🐶", "🐕", "🦮"),
            "cat" to listOf("🐱", "🐈", "😺"),
            "sun" to listOf("☀️", "🌞", "😎"),
            "rain" to listOf("🌧️", "☔", "🌦️"),
            "snow" to listOf("❄️", "☃️", "🌨️"),
            "music" to listOf("🎵", "🎶", "🎧"),
            "money" to listOf("💰", "💵", "🤑"),
            "gift" to listOf("🎁", "🎀"),
            "christmas" to listOf("🎄", "🎅", "🎁"),
            "beach" to listOf("🏖️", "🌴", "🌊"),
            "baby" to listOf("👶", "🍼", "🐣"),
            "win" to listOf("🏆", "🥇", "🎉"),
            "hot" to listOf("🔥", "🥵", "☀️"),
            "cold" to listOf("🥶", "❄️", "🧊"),
            "sick" to listOf("🤒", "🤧", "😷"),
            "strong" to listOf("💪", "🔥"),
            "pray" to listOf("🙏", "🛐"),
            "star" to listOf("⭐", "🌟", "✨"),
        )

        internal val CURATED_ES: Map<String, List<String>> = mapOf(
            "corazon" to listOf("❤️", "💕", "💖", "💗"),
            "amor" to listOf("❤️", "🥰", "😘", "😍", "💕"),
            "quiero" to listOf("❤️", "🥰", "😘"),
            "feliz" to listOf("😊", "😍", "🥳", "😄"),
            "felicidades" to listOf("🎉", "🥳", "👏", "🎊"),
            "cumpleanos" to listOf("🎂", "🎉", "🎈", "🥳"),
            "sonrisa" to listOf("😊", "😄", "🙂"),
            "risa" to listOf("😂", "🤣", "😆"),
            "jaja" to listOf("😂", "🤣", "😆"),
            "jajaja" to listOf("😂", "🤣", "😆"),
            "triste" to listOf("😢", "😭", "🥺", "😔"),
            "llorar" to listOf("😭", "😢", "🥺"),
            "gracias" to listOf("🙏", "❤️", "😊", "🤗"),
            "perdon" to listOf("🙏", "😔", "🥺"),
            "fiesta" to listOf("🎉", "🥳", "🎊"),
            "beso" to listOf("😘", "💋", "😚"),
            "besos" to listOf("😘", "💋", "😚"),
            "abrazo" to listOf("🤗", "🫂", "❤️"),
            "abrazos" to listOf("🤗", "🫂", "❤️"),
            "hola" to listOf("👋", "😊", "🙂"),
            "adios" to listOf("👋", "😘"),
            "noche" to listOf("🌙", "😴", "✨"),
            "cafe" to listOf("☕"),
            "perro" to listOf("🐶", "🐕"),
            "gato" to listOf("🐱", "🐈"),
            "sol" to listOf("☀️", "🌞", "😎"),
            "lluvia" to listOf("🌧️", "☔"),
            "musica" to listOf("🎵", "🎶", "🎧"),
            "dinero" to listOf("💰", "💵", "🤑"),
            "regalo" to listOf("🎁", "🎀"),
            "navidad" to listOf("🎄", "🎅", "🎁"),
            "playa" to listOf("🏖️", "🌴", "🌊"),
            "genial" to listOf("👍", "🙌", "😄", "💯"),
            "bien" to listOf("👍", "👌", "😊"),
            "cansado" to listOf("😴", "🥱", "😩"),
            "cansada" to listOf("😴", "🥱", "😩"),
            "enojado" to listOf("😠", "😡", "🤬"),
            "fuego" to listOf("🔥"),
        )

        /** Words in emoji names that are not about any emoji ("face with tears of joy"). */
        private val STOP_EN = setOf("with", "of", "the", "and", "in", "on", "at", "for", "to", "by", "from", "an", "or", "no", "up",
            "out", "over", "off", "it", "is", "my", "me", "you", "we", "he", "she", "they", "am", "are", "be", "not", "all", "one",
            "two", "three", "new", "top", "back", "end", "soon", "this", "that", "so", "do", "go", "get", "but", "if", "as", "us")
        private val STOP_ES = setOf("de", "con", "la", "el", "en", "y", "a", "los", "las", "del", "por", "un", "una", "que", "se",
            "al", "para", "sin", "o", "su", "es", "lo", "le", "mi", "tu", "no", "si", "me", "te", "nos", "ya", "mas", "muy")
    }
}
