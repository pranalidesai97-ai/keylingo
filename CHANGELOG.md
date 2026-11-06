# Changelog — Production-Polish Session

Continuing on the real, verified source (post KLDebug-removal, word-list
expansion, and dictionary-collision-fix session). All changes below are
applied to actual files and verified by static analysis/simulation.

## 1. Backspace hold-to-repeat (Priority 1.1)

`keyboard/ui/KeyboardKey.kt`: Backspace now uses a custom pointer-gesture
handler instead of plain `.clickable`. On press: deletes one character
immediately (same as a tap), then after a 350ms initial delay, repeats
deletion every 55ms, accelerating to 40ms after 15 repeats, stopping
immediately on release or gesture cancellation. Press/release/cancel
interactions are emitted manually to the same `MutableInteractionSource`
so the existing press-scale animation keeps working identically for this
key. Every other key's `.clickable` behavior is completely untouched —
this was scoped specifically to Backspace, not a general press-handling
rewrite.

## 2. Auto-capitalization (Priority 8)

`keyboard/KeyboardViewModel.kt`: new `maybeAutoCapitalize()`, called
after every text-committing action (character, symbol, space, backspace,
enter). Arms a one-shot shift (reusing the existing
`ShiftState.SHIFTED_ONCE` + `consumeOneShotShift()` machinery — no new
state machine) when the field is empty, ends in a newline, or the last
non-space character is `.`/`!`/`?`. Only ever upgrades from `OFF`; never
overrides a manually-set `SHIFTED_ONCE` or `CAPS_LOCK`, so it can't fight
a deliberate manual choice.

## 3. Double-space-to-period (Priority 8)

Same file: tracks whether the previous key press was also Space and how
long ago. A second Space press within 500ms, when the field genuinely
ends in a single space preceded by a non-whitespace character, deletes
that space and commits `". "` instead of a second space. Reset
appropriately so autocomplete-selection (which also commits a trailing
space) can't cause a false trigger.

## 4. Number row: made configurable (Priority 8, closes an earlier TODO)

- `data/settings/SettingsDataStore.kt`: new `showNumberRow: Boolean = true`
  field on `KeyLingoSettings`, persisted via a new DataStore key, same
  pattern as every other setting.
- `settings/SettingsViewModel.kt` / `settings/ui/SettingsScreen.kt`: new
  `setShowNumberRow()` + a `NumberRowToggleSection` composable, an exact
  mirror of the existing `LearningToggleSection` pattern, plus matching
  string resources.
- `keyboard/KeyboardLayoutProvider.kt`: `rowsFor()`/`letterRows()` now
  take the setting and conditionally include the digit row (added last
  session as always-on; default here is still `true`, so existing
  behavior is preserved unless the user explicitly turns it off).
- `keyboard/ui/KeyboardScreen.kt`: call site updated to pass
  `uiState.settings.showNumberRow` through.

## What was NOT attempted this session, and why

Explicitly not silently skipped — each of these is a substantial
standalone effort that would require either fabricating scale/quality I
can't verify, or producing exactly the kind of low-effort algorithmic
output (garbled inflections, unverified "thousands of entries") this
project's own history already flagged as unacceptable:

- **Production-quality autocorrect** (edit distance, keyboard-neighbor
  confusion, transposition, missing/duplicated letters) — a real
  algorithm plus a curated confusion-matrix dataset is its own project,
  not a bolt-on. Not started.
- **Dictionary expansion to "many thousands" of entries** — the existing
  727-entry dictionary and 1,369-word autocomplete list were already
  hand-curated, one at a time, specifically to avoid generating fake
  words. Scaling to thousands more with the same care is a large,
  separate content-authoring effort. Not started.
- **Bigram/next-word prediction, context-aware ranking** — needs either a
  real n-gram corpus or usage-based learning, neither of which exists in
  this codebase yet. Not started.
- **Spanish accented-character long-press popups** — genuinely
  implementable, but was deprioritized this pass in favor of the
  Priority-1/8 items actually completed above; a reasonable next-session
  target given it's well-scoped.
- **Learning/persistence system** (frequent/custom/recent words, names,
  slang, cross-session memory) — needs a new persistence layer and
  product decisions about what "the keyboard learns" means. Not started.
- **Full animation/haptic/sound/theme polish, dynamic theme** — haptic
  and sound need explicit user-facing settings and platform APIs neither
  wired nor tested here; "dynamic theme" (Material You) needs Android 12+
  API usage not currently in the codebase. Not started.
- **Real performance profiling** — categorically requires a running app
  on a device/profiler. See `PERFORMANCE_NOTES.md` for what can honestly
  be said about this without one.
- **Cursor behavior improvements** (tap positioning, spacebar-swipe
  cursor move, selection handling) — real `InputConnection` gesture work,
  not started this pass.
- **Undo accidental autocorrect, emoji suggestions, clipboard
  suggestions** — depend on autocorrect/emoji-pipeline work that doesn't
  exist yet. Not started.
