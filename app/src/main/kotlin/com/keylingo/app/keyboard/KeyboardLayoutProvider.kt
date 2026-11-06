package com.keylingo.app.keyboard

/** What kind of action a key performs when pressed. */
sealed class KeyAction {
    data class Character(val lower: String, val upper: String) : KeyAction()
    object Shift : KeyAction()
    object Backspace : KeyAction()
    object Enter : KeyAction()
    object Space : KeyAction()
    object SwitchToSymbols : KeyAction()
    object SwitchToLetters : KeyAction()
    object SwitchToNumbers : KeyAction()
    object Emoji : KeyAction()
    data class Symbol(val value: String) : KeyAction()
}

/** A single key, with a relative width weight so rows can lay out proportionally. */
data class KeyDefinition(
    val action: KeyAction,
    val weight: Float = 1f
)

enum class KeyboardMode { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI }

/**
 * Static QWERTY / symbol / emoji layouts. Kept as plain data so the
 * Compose keyboard surface can be a dumb renderer over rows of keys.
 */
object KeyboardLayoutProvider {

    fun rowsFor(mode: KeyboardMode, showNumberRow: Boolean = true): List<List<KeyDefinition>> = when (mode) {
        KeyboardMode.LETTERS -> letterRows(showNumberRow)
        KeyboardMode.SYMBOLS_1 -> symbolRows1()
        KeyboardMode.SYMBOLS_2 -> symbolRows2()
        KeyboardMode.EMOJI -> emojiRows()
    }

    private fun charKey(c: Char): KeyDefinition =
        KeyDefinition(KeyAction.Character(c.lowercaseChar().toString(), c.uppercaseChar().toString()))

    private fun letterRows(showNumberRow: Boolean): List<List<KeyDefinition>> {
        val row0 = "1234567890".map { KeyDefinition(KeyAction.Symbol(it.toString())) }
        val row1 = "qwertyuiop".map { charKey(it) }
        val row2 = "asdfghjkl".map { charKey(it) }
        val row3 = mutableListOf<KeyDefinition>()
        row3.add(KeyDefinition(KeyAction.Shift, weight = 1.5f))
        row3.addAll("zxcvbnm".map { charKey(it) })
        row3.add(KeyDefinition(KeyAction.Backspace, weight = 1.5f))

        val row4 = listOf(
            KeyDefinition(KeyAction.SwitchToNumbers, weight = 1.5f),
            KeyDefinition(KeyAction.Emoji, weight = 1f),
            KeyDefinition(KeyAction.Space, weight = 4f),
            KeyDefinition(KeyAction.Symbol("."), weight = 1f),
            KeyDefinition(KeyAction.Enter, weight = 1.5f)
        )
        val rows = mutableListOf<List<KeyDefinition>>()
        if (showNumberRow) rows.add(row0)
        rows.add(row1)
        rows.add(row2)
        rows.add(row3)
        rows.add(row4)
        return rows
    }

    private fun symbolRows1(): List<List<KeyDefinition>> {
        val row1 = "1234567890".map { KeyDefinition(KeyAction.Symbol(it.toString())) }
        val row2 = "@#\$_&-+()/".map { KeyDefinition(KeyAction.Symbol(it.toString())) }
        val row3 = mutableListOf<KeyDefinition>()
        row3.add(KeyDefinition(KeyAction.SwitchToSymbols, weight = 1.5f))
        row3.addAll("*\"':;!?".map { KeyDefinition(KeyAction.Symbol(it.toString())) })
        row3.add(KeyDefinition(KeyAction.Backspace, weight = 1.5f))
        val row4 = listOf(
            KeyDefinition(KeyAction.SwitchToLetters, weight = 1.5f),
            KeyDefinition(KeyAction.Emoji, weight = 1f),
            KeyDefinition(KeyAction.Space, weight = 4f),
            KeyDefinition(KeyAction.Symbol(","), weight = 1f),
            KeyDefinition(KeyAction.Enter, weight = 1.5f)
        )
        return listOf(row1, row2, row3, row4)
    }

    private fun symbolRows2(): List<List<KeyDefinition>> {
        val row1 = "~`|•√π÷×§".map { KeyDefinition(KeyAction.Symbol(it.toString())) }
        val row2 = "£¢€¥^°={}".map { KeyDefinition(KeyAction.Symbol(it.toString())) }
        val row3 = mutableListOf<KeyDefinition>()
        row3.add(KeyDefinition(KeyAction.SwitchToSymbols, weight = 1.5f))
        row3.addAll("\\©®™%[]<>".map { KeyDefinition(KeyAction.Symbol(it.toString())) })
        row3.add(KeyDefinition(KeyAction.Backspace, weight = 1.5f))
        val row4 = listOf(
            KeyDefinition(KeyAction.SwitchToLetters, weight = 1.5f),
            KeyDefinition(KeyAction.Emoji, weight = 1f),
            KeyDefinition(KeyAction.Space, weight = 4f),
            KeyDefinition(KeyAction.Symbol(","), weight = 1f),
            KeyDefinition(KeyAction.Enter, weight = 1.5f)
        )
        return listOf(row1, row2, row3, row4)
    }

    private fun emojiRows(): List<List<KeyDefinition>> {
        val emojis = listOf(
            "😀","😁","😂","🤣","😊","😍","😘","😜","🤔","😎",
            "😢","😭","😡","👍","👎","👏","🙏","💪","🔥","🎉",
            "❤️","💔","⭐","☀️","🌙","🍎","🍕","☕","🐶","🐱"
        )
        val chunked = emojis.chunked(10).map { row -> row.map { KeyDefinition(KeyAction.Symbol(it)) } }
        val bottomRow = listOf(
            KeyDefinition(KeyAction.SwitchToLetters, weight = 2f),
            KeyDefinition(KeyAction.Space, weight = 5f),
            KeyDefinition(KeyAction.Backspace, weight = 2f)
        )
        return chunked + listOf(bottomRow)
    }
}
