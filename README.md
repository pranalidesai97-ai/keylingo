# KeyLingo

An Android keyboard for everyday English typing that teaches Spanish as you
type — fully offline. Type "I am going to call you tomorrow" and the message
stays exactly that; the learning row above the keys shows
**Te voy a llamar mañana** plus word hints such as `llamar · infinitive`.
Type in Spanish and you get Spanish suggestions (with accents restored),
English glosses and an English sentence translation instead.

- English autocomplete over a 111.8k-word lexicon, keyboard-aware typo
  correction, curated common misspellings, conservative autocorrect with one-tap Backspace undo
- Grammar suggestions you tap to apply (never automatic)
- Next-word prediction that learns your phrasing (bigrams + trigrams), with a cold-start model
- Personal vocabulary learned on device, never from password/incognito fields
- Offline Spanish conjugation engine and rule-based sentence translation in both directions
- Six keyboard themes (Sunlight Yellow by default) plus a custom colour, light/dark
- Long-press accents (á é í ó ú ñ ü ¿ ¡), emoji, number row; vibration and key sound (both off by default)
- Learned-words manager; incidental typing history expires automatically

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

Regenerating the lexicons: English = `tools/build_english_lexicon.py`
(SCOWL lists from the `wordlist-english` npm package) → `tools/rerank_english_by_frequency.py`
(wordfreq data) → `tools/sort_lexicon_by_key.py … en`; Spanish =
`tools/build_spanish_lexicon.py` → `tools/sort_lexicon_by_key.py … es`.

Third-party data: the two lexicon files contain material adapted from wordfreq
(CC BY-SA 4.0). See `DATABASE_AND_DICTIONARY_INVENTORY.md` §5 and the bundled
`app/src/main/assets/licenses/` before distributing the APK.
