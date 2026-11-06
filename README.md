# KeyLingo

An Android keyboard for everyday English typing that teaches Spanish as you
type — fully offline. Type "I am going to call you tomorrow" and the message
stays exactly that; the learning row above the keys shows
**Te voy a llamar mañana** plus word hints such as `llamar · infinitive`.
Type in Spanish and you get Spanish suggestions (with accents restored) and
English glosses instead.

- English autocomplete over a 61.5k-word lexicon, keyboard-aware typo
  correction, conservative autocorrect with one-tap Backspace undo
- Next-word prediction that learns your phrasing (bigrams + trigrams), with a cold-start model
- Personal vocabulary learned on device, never from password/incognito fields
- Offline Spanish conjugation engine and rule-based sentence translation
- Long-press accents (á é í ó ú ñ ü ¿ ¡), emoji, number row, dark mode, haptics

## Build

Requirements: Android Studio (Koala or newer), JDK 17+, Android SDK Platform 34.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Install it, open
KeyLingo, and follow the onboarding to enable and switch to the keyboard.

## Project documents

| File | Contents |
|---|---|
| `PROJECT_STATE.md` | Current state, build/test results, what is verified |
| `ARCHITECTURE.md` | Layers, data flow, suggestion/translation engines |
| `FEATURE_IMPLEMENTATION_STATUS.md` | Feature-by-feature status |
| `DATABASE_AND_DICTIONARY_INVENTORY.md` | Assets, formats, licences, Room schemas |
| `TESTING.md` | Test suites and the device verification checklist |
| `CHANGELOG.md` | What changed |
| `KNOWN_ISSUES_AND_BACKLOG.md` | Limitations and prioritised roadmap |

Regenerating the English lexicon: see `tools/build_english_lexicon.py`
(needs the `wordlist-english` npm package's SCOWL lists).
