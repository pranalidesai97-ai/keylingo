package com.keylingo.app.data.grammar

import com.keylingo.app.domain.model.GrammarIssueType
import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishGrammarCheckerTest {
    private val checker = TestData.bundled.grammarChecker

    private fun corrected(s: String, complete: Boolean = true) = checker.check(s, complete)?.corrected

    private fun expectFixes(vararg cases: Pair<String, String>) {
        val wrong = cases.mapNotNull { (input, want) -> corrected(input).takeIf { it != want }?.let { "\"$input\" -> \"$it\" (want \"$want\")" } }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun briefExamples() = expectFixes(
        "I love my my family." to "I love my family.",
        "She go to office every day." to "She goes to the office every day.",
        "Yesterday I go to the office." to "Yesterday I went to the office.",
    )

    @Test fun subjectVerbAgreement() = expectFixes(
        "He have a car." to "He has a car.",
        "She don't like coffee." to "She doesn't like coffee.",
        "They goes to school." to "They go to school.",
        "We was late." to "We were late.",
        "I is happy." to "I am happy.",
        "My sister live in Madrid." to "My sister lives in Madrid.",
        "The kids plays outside." to "The kids play outside.",
        "He always forget his keys." to "He always forgets his keys.",
    )

    @Test fun pastTimeMarkers() = expectFixes(
        "I eat pizza last night." to "I ate pizza last night.",
        "We are at the beach yesterday." to "We were at the beach yesterday.",
        "She buy a phone two days ago." to "She bought a phone two days ago.",
        "I don't see him yesterday." to "I didn't see him yesterday.",
    )

    @Test fun articlesAndWordChoice() = expectFixes(
        "I ate a apple." to "I ate an apple.",
        "She is an teacher." to "She is a teacher.",
        "It takes an hour." to "It takes an hour.",
        "I am teacher." to "I am a teacher.",
        "I could of called." to "I could have called.",
        "Their is a problem." to "There is a problem.",
        "This is better then that." to "This is better than that.",
        "Its a good day." to "It's a good day.",
    )

    @Test fun capitalisationAndSpacing() = expectFixes(
        "hello there , how are you?" to "Hello there, how are you?",
        "i think so." to "I think so.",
        "Yes,sure." to "Yes, sure.",
    )

    @Test fun correctAndInformalEnglishIsLeftAlone() {
        val fine = listOf(
            "She goes to school every day.", "Does she go to the gym?", "I saw her go home.", "If it were me, I would stay.",
            "I had had enough.", "The bus stop is near my house.", "Tomorrow I go to Madrid.", "I'm gonna grab some food",
            "lol that's so funny", "Can he come with us?", "They play soccer on Sundays.", "My friends are coming tonight.",
            "I think that that is fine.", "We went to the beach yesterday.", "Let him go.", "I want to go home.",
            "She is a teacher.", "I go to work by bus.", "He has been working all day.", "Thank you very very much!",
            "hey how are you", "ok ok", "I am at home", "What do you think?", "The news is good.", "Everyone was there.",
        )
        val flagged = fine.mapNotNull { s -> checker.check(s, true)?.let { "\"$s\" -> \"${it.corrected}\" ${it.issues.map { i -> i.type }}" } }
        assertTrue(flagged.joinToString("\n"), flagged.isEmpty())
    }

    @Test fun theWordBeingTypedIsIgnored() {
        // "go" is still being typed: no verdict yet.
        assertNull(checker.check("She go", complete = false))
        assertEquals("She goes to", corrected("She go to", complete = false))
    }

    @Test fun issuesExplainThemselves() {
        val s = checker.check("Yesterday I go to the office.", true)!!
        assertEquals(GrammarIssueType.VERB_TENSE, s.issues.single().type)
        assertTrue(s.issues.single().message.contains("went"))
    }
}
