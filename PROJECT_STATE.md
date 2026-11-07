# KeyLingo — Project State

_Last updated: 2026-11-06. Describes the code on branch `claude/eager-dijkstra-jsufzj`._

Verification levels used in all KeyLingo docs:

| Label | Meaning |
|---|---|
| **Built + tested** | Compiles in the real Gradle/AGP build and is covered by passing JVM tests (unit, Robolectric SQLite, Robolectric Compose UI). |
| **Built** | Compiles and is packaged in the APK, but has no automated test. |
| **Rendered (JVM)** | Compose UI drawn by Robolectric into a PNG and reviewed by eye. **Not** a phone screenshot. |
| **Device: not verified** | Nothing in this project has been run on a phone or emulator yet. |

## 1. What KeyLingo is

An Android keyboard (`InputMethodService`, Jetpack Compose UI) for everyday
English typing that teaches Spanish as you type. It is fully offline:
- no network permission and no network code;
- personal learning data stays in on-device Room databases and is excluded from cloud backup and device transfer.

## 2. Current state in one paragraph

The project **builds** (`assembleDebug`, minified `assembleRelease`,
`lintDebug` with 0 errors) and has **209 passing tests**. What it has:
- **Input:** a 111.8k-word English lexicon; keyboard-aware typo correction plus a curated misspelling list (`becuase`, `definately`, `teh`…); conservative autocorrect with one-tap undo; next-word prediction.
- **Learning row:** an offline English→Spanish sentence translator, and now a Spanish→English one.
- **Grammar:** suggestions are offered as a tappable chip and never applied automatically.
- **Appearance:** six keyboard themes plus a custom colour.
- **Feedback:** vibration and key sound are both off by default.
- **Settings:** a grouped screen, with a manager for learned words and a retention policy for incidental typing history.

**No physical-device or emulator testing has happened** (see §6).

## 3. Build and test results (this session)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ 209 tests, 0 failures (27 suites, see TESTING.md) |
| `tools/android-regex-check/run.sh` (ICU4C 74.2) | ✅ 31 production patterns compile; 283 match checks equal the JVM |
| `./gradlew :app:assembleDebug` | ✅ `app-debug.apk` 18.6 MB |
| `./gradlew :app:assembleRelease` | ✅ `app-release-unsigned.apk` 2.5 MB (R8 minified; never run) |
| `./gradlew :app:lintDebug` | ✅ 0 errors, 21 warnings (almost all "newer dependency version available") |

**Build environment caveat.** This cloud container has no Android SDK. The build used:
- AGP/AndroidX from Google Maven and Maven Central;
- the `android-34` `android.jar` from the public `Sable/android-platforms` mirror;
- a `core-for-system-modules.jar` derived from it;
- stub build-tools.

Nothing in the repo depends on that setup. **Build release artifacts in Android Studio with a real SDK.**

## 4. Work in this improvement phase (details in CHANGELOG.md)

| Phase | Result |
|---|---|
| 1. Feedback | Vibration **off by default** and a separate sound toggle (off), both from a single source (`KeyFeedback`). Visual pressed state on every key. Backspace repeat stops on release (gesture test). `VIBRATE` permission removed. |
| 2. Vocabulary | English lexicon grew from 61,547 to **111,795** words (77,137 suggestable, 34,658 recognised-only). 48,652 inflected forms, 108 curated misspellings, proper nouns. Typo search re-indexed so it stays fast. |
| 3. Grammar | Rules: repeated words, subject–verb agreement, past tense with time markers, a/an, missing articles, common confusables, capitalisation, spacing. Shown as a suggestion chip, applied only when tapped, checked only after a finished word. |
| 4. Translation | New Spanish→English sentence translator (112 regression sentences), with grammar notes. English→Spanish translates the grammar-corrected sentence and says so. "Where are you going?" → "¿Adónde vas?". |
| 5. Personal learning | Retention: incidental words are forgotten after 30 days and single-use word pairs after 60 days, and stored sequences are capped. Forgetting a word also removes its sequences. |
| 6. Themes | Sunlight Yellow (default), Lavender Purple, Dark Violet, Mint Green, Ocean Blue, Blush Pink and a custom colour. WCAG-checked text contrast; light/dark; persisted; geometry unchanged. |
| 7. Settings | Grouped sections; typing language (Auto/EN/ES), grammar and sentence-translation toggles; learned-words screen (filter, delete one, delete all). |
| 8. Platform | Verified no INTERNET permission in the merged manifest. Personal data excluded from backup and transfer. Per-sentence latency test added (all < 1 ms on the build JVM). |

## 4b. First device result (2026-11-07)

On a vivo phone, build 493dd6e crashed while the keyboard service started: a `(?U)` regex is rejected by Android's ICU engine. Fixed (see CHANGELOG 2026-11-07). The fix is built and tested on the JVM and against ICU4C. Confirmed on the device: build a2e98e6 launches.

Second device result: WhatsApp shows the translation, but Chrome and YouTube search fields do not. Their field types (`textUri`, `textFilter`…) were excluded from language help. Fixed, and the suggestion strip slimmed (see CHANGELOG). Confirmed on device: build c4aa430 shows translations in Chrome, YouTube and WhatsApp.

Third round: the empty-field word row was blank (data still loading) and themes were hard to find. Fixed with immediate starter words and a Keyboard theme screen (see CHANGELOG).

Fourth round: five gradient themes (Ocean Mist kept exactly), EN/ES starter phrases in empty fields, and a new logo (see CHANGELOG). **On-device confirmation of this build is pending.**

## 5. Decisions that need the owner's approval

1. **Backups off.** `allowBackup="false"` plus data-extraction rules keep learned words off Google backup and device-to-device transfer. A side effect is that settings don't move to a new phone either. Revert if cross-device restore matters more.
2. **Frequency data licence.** Ranking outside the ~1,900 curated words still uses SCOWL tiers, because no corpus licence was accepted (see KNOWN_ISSUES K6).
3. **Release build.** R8 output has never run; sign and test it in Android Studio before distributing.

## 6. Outstanding device verification (must happen next)

Nothing here can be closed without a phone or emulator. The checklist is in TESTING.md §4:
- enabling and switching to the keyboard;
- Compose rendering inside the IME window;
- key and suggestion latency;
- the accent long-press overlay;
- vibration and sound feel;
- backspace repeat;
- autocorrect undo and grammar-chip apply in real apps;
- password fields;
- themes on real screens;
- landscape;
- R8 release behaviour;
- Room upgrade.

## 7. Next milestone

Run the device checklist on a real phone and fix what it finds. Then work through the top of KNOWN_ISSUES_AND_BACKLOG.md:
- a speaker-gender setting;
- dictionary coverage;
- frequency-data licensing;
- a composing-text model.
