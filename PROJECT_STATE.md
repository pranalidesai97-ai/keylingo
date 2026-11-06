# KeyLingo — Project State

_Last updated: 2026-11-06. Describes the code on branch `claude/eager-dijkstra-jsufzj`._

Verification levels used in all KeyLingo docs:

| Label | Meaning |
|---|---|
| **Built + tested** | Compiles in the real Gradle/AGP build and is covered by passing JVM unit tests (incl. Robolectric SQLite tests). |
| **Built** | Compiles and is packaged in the APK, but has no automated test (usually Compose UI). |
| **Device: not verified** | Nothing in this project has been run on a phone or emulator yet. |

## 1. What KeyLingo is

An Android keyboard (`InputMethodService`, Jetpack Compose UI) for everyday
English typing that teaches Spanish as you type. It is fully offline: no
network permission, no network code, personal learning data stored only in
on-device Room databases.

## 2. Current state in one paragraph

The project now **builds** (`assembleDebug`, minified `assembleRelease`,
`lintDebug` with 0 errors) and has **98 passing unit tests**. Before this
pass the code had never been compiled. The keyboard has a 61.5k-word English
lexicon with keyboard-aware typo correction and conservative autocorrect,
personal vocabulary and n-gram learning that is race-free and privacy-gated,
next-word prediction with a cold-start model, a deterministic Spanish
conjugation engine, and an offline rule-based English→Spanish sentence
translator whose word alignments drive the learning hints. Typing Spanish is
detected automatically and gets Spanish suggestions (with accent
restoration) plus English glosses. **No physical-device or emulator testing
has happened** — see §6.

## 3. Build and test results (this session)

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ 98 tests, 0 failures (11 suites, see TESTING.md) |
| `./gradlew :app:assembleDebug` | ✅ `app-debug.apk` 17.7 MB |
| `./gradlew :app:assembleRelease` | ✅ `app-release-unsigned.apk` 1.95 MB (R8 minified; was 7.0 MB before the keep-rule fix) |
| `./gradlew :app:lintDebug` | ✅ 0 errors, 22 warnings (19 are "newer dependency version available") |

**Build environment caveat.** This cloud container has no Android SDK, and
the SDK download host is blocked. The build used: AGP/AndroidX/Room/Hilt
from Google Maven and Maven Central (allowed by a dedicated network
environment), `aapt2` from Google Maven (AGP does this itself), the
`android-34` `android.jar` from the public `Sable/android-platforms`
mirror, a `core-for-system-modules.jar` derived from it, and stub
build-tools. The Gradle build itself is standard; nothing in the repo
depends on that SDK. **Build release artifacts in Android Studio with a real
SDK** before distributing.

## 4. What changed (summary — details in CHANGELOG.md)

1. **Build fixed**: missing `gradle-wrapper.jar` added; the hand-written `gradlew` was broken.
2. **Data repaired**: every Spanish entry had lost its accents (`año`→`ano`, `bebé`→`bebe`, `trabajó`→`trabajo`); dictionary migrated to a lemma-based, POS-aware, gender-tagged TSV (1,325 rows).
3. **Crash fixed**: concurrent learning writes violated a unique index inside fire-and-forget coroutines (IME crash) → atomic upserts + coroutine exception guard.
4. **Privacy fixed**: password/incognito fields were learned from → `InputFieldPolicy`.
5. **Input correctness**: cursor-relative context, grapheme-safe backspace, case-preserving suggestions, auto-shift that turns off, Enter follows the field action, `directBootAware` crash risk removed.
6. **New capabilities**: lexicon + typo correction + autocorrect with undo, n-gram prediction with cold start, conjugation engine, sentence translator, Spanish typing support, long-press accents, haptics, emoji grid, "delete learned data".

## 5. Product rules status

See FEATURE_IMPLEMENTATION_STATUS.md for the full matrix. Everything listed
in the product vision has at least a working, tested core except: no
device-level verification, no composing-text underline, no swipe typing, and
the dictionary still lacks ~800 of the 2,500 most frequent English words
(sentence translations are hidden whenever an unknown content word appears).

## 6. Outstanding device verification (must happen next)

Nothing here can be closed without a phone/emulator. Checklist in TESTING.md §4:
IME enable/switch flow, Compose rendering inside the IME window, key
latency and suggestion latency, accent long-press overlay, haptics,
autocorrect undo in real apps (WhatsApp, Gmail, Chrome), password fields,
Enter actions, rotation/landscape height, dark mode, first-launch data load
time, R8 release build behaviour, and Room migration on an upgraded install.

## 7. Next milestone

Run the device checklist on a real phone and fix what it finds; then the
top items of KNOWN_ISSUES_AND_BACKLOG.md (user gender for agreement,
dictionary coverage of the top 3,000 words, composing-text model).
