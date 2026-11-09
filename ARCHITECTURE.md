# KeyLingo — Architecture

Structure and data flow of the code as it exists now. Status lives in
PROJECT_STATE.md / FEATURE_IMPLEMENTATION_STATUS.md.

## 1. Layers

```
presentation   keyboard/ (IME service, KeyboardViewModel, TextContext, InputFieldPolicy, Compose UI)
               settings/ (SettingsScreen, ThemePickerSection, LearnedWordsScreen + ViewModels)
               ui/theme/ KeyLingoTheme, KeyboardColors/KeyboardThemes (keyboard palettes)
      │ calls use cases / interfaces only
domain         model/, repository/ (interfaces), usecase/   — pure Kotlin, no Android imports
      ▲ implemented by
data           bundled/   in-memory, read-only language data (assets)
               lexicon/   Lexicon + TypoDistance
               language/  EnglishMorphology, TypingLanguageDetector, spanish/ (conjugator, morphology, text)
               translation/ EnglishToSpanishTranslator, SpanishToEnglishTranslator, SpanishToEnglishGlosser
               grammar/   EnglishGrammarChecker (suggestions only)
               dictionary/ BundledDictionary
               hint/      LearningAssistant (HintProvider), LearningModePolicy
               learning/  Room: personal vocabulary      ┐ user data, never destructive
               context/   Room: personal bigrams/trigrams ┘
               settings/  DataStore preferences
```

**Two kinds of data, never mixed:**
- *Bundled* (read-only, shipped in `assets/`, parsed once into memory by
  `BundledLanguageDataSource`): English lexicon, EN→ES dictionary, idioms,
  cold-start next-word tables.
- *Personal* (Room, on device only): learned words (`keylingo_learning.db`)
  and word sequences (`keylingo_context.db`). Cleared from Settings.

Previously the bundled data was copied into two Room "cache" databases by
seeders; that caused a cold-start race and could not support typo search.
They were replaced by in-memory structures; the old cache files are deleted
on startup by `KeyLingoApplication` (user databases are untouched).

## 2. Dependency injection (Hilt, all `SingletonComponent`)

| Module | Binds / provides |
|---|---|
| `AppModule` | `BundledDataSupplier`→`BundledLanguageDataSource`, `AutocompleteProvider`→`BundledAutocompleteProvider`, `HintProvider`→`LearningAssistant`, `LanguageDetector`→`BundledLanguageDetector`, `LanguagePack`→`SpanishLanguagePack` |
| `LearningModule` / `LearningBindsModule` | `LearningDatabase`, `LearnedWordDao`, `LearnedVocabularyProvider`→`RoomLearnedVocabularyProvider` |
| `ContextModule` / `ContextBindsModule` | `ContextDatabase` (+ migrations), `BigramDao`, `ContextPredictionProvider`→`RoomContextPredictionProvider` |
| `AppDispatchers` (@Inject) | `compute = Default`, `io = IO`; tests inject inline dispatchers |

`KeyLingoInputMethodService` is `@AndroidEntryPoint`; it constructs
`KeyboardViewModel` itself (an IME has no `ViewModelStoreOwner`) and owns its
`CoroutineScope` (`SupervisorJob + Main.immediate + CoroutineExceptionHandler`).

## 3. Keyboard runtime flow

```
key press ─► KeyboardKey (Compose gesture: tap / long-press accents / backspace repeat; pressed tint + scale)
          ─► KeyFeedback.onKeyDown  (the only vibration/sound call site; each only if its setting is on)
          ─► KeyboardViewModel.onKeyAction
               ├─ edits via KeyboardTextConnection (commit / delete grapheme / batch edits)
               ├─ word boundary? → autocorrect (if pending + still valid) → learning (if policy allows)
               ├─ updateAutoShift()
               └─ scheduleRefresh():  suggestions after 20 ms, learning row after 220 ms (debounced, cancelled on next key)
refreshSuggestions:  TextContext(textBeforeCursor(240), textAfterCursor(4))
                     → LanguageDetector.detect(current sentence)
                     → GenerateAutocompleteSuggestionsUseCase(SuggestionRequest)
refreshHints:        current (or just-finished) sentence → GenerateHintsUseCase → LearningAssistant
                     (typing language from Settings or detected; grammar/sentence flags from Settings)
onGrammarSuggestionApplied: replaces the sentence before the cursor only if it is unchanged
```

`InputFieldPolicy` (from `EditorInfo`) gates everything per field: suggestions,
learning, autocorrect, learning hints, capitalisation mode, initial layer,
Enter action. Passwords: nothing analysed or stored. Incognito: nothing stored.

The gates are deliberately different, because apps describe similar fields very differently:
- **Read-only language help** (translation, word hints, grammar chips): every text field except passwords and e-mail addresses. That includes browser address bars (`textUri` + actionGo), filter-style search boxes (`textFilter`), web inputs (`textWebEditText`, with or without `NO_SUGGESTIONS`) and `TYPE_NULL` views. Text that is itself a URL or e-mail is skipped by content (`TextContext.containsWebAddressOrEmail`).
- **Autocorrect and auto-capitals**: prose fields only, never address or search bars.
- **Learning**: never in password, incognito, numeric, e-mail, URI or filter fields.

There is no per-app (package-name) logic.

Strip height: a 40 dp word row, plus a 46 dp learning row when `KeyboardUiState.showLearningRow` (field allows language help and the learning row or grammar is enabled). It is decided per field and settings, never by the chips shown, so keys don't move while typing.

Threading: the ViewModel runs on the main thread but does no heavy work;
lexicon search, translation and detection run on `AppDispatchers.compute`,
Room on `Dispatchers.IO`. Only a 240-char window before the cursor is read per
refresh (never the whole field).

## 4. Suggestion engine (`GenerateAutocompleteSuggestionsUseCase`)

Mid-word (prefix non-empty) candidates and their score components:
- bundled completions: `100 − 6·ln(rank+10)` (+25 if exactly the typed word)
- personal vocabulary (past threshold): bundled frequency or 55, + `9·ln(1 + organic + 3·accepted)`, +6 if used in the last 24 h
- spelling variants with identical letters (apostrophe/accent/case): frequency + 30
- keyboard-aware typo corrections: frequency − 18·distance (−30 if the typed word is itself known)
- context bonus (re-ranks only): personal bigram/trigram counts `10·ln(1+n)`, bundled cold-start list `8/(position+1)`

Autocorrect (applied on space/punctuation, undone by one Backspace) fires only
if the typed word is unknown, unlearned and not rejected this session, and:
an exact variant exists (`dont`→`don't`, `tambien`→`también`, `i`→`I`), or the
best whole-word correction is within the length-scaled distance limit
(≤0.6 for 3–4 letters, ≤1.0 up to 7, ≤1.5 beyond), ranks ≤ 25,000 and beats the
runner-up by a clear margin. Words with digits or inner capitals are never
corrected. The literal typed word is always offered next to a correction.

Between words: personal trigrams (30·ln) > bigrams (20·ln) > bundled cold-start
(10/(pos+1)); sentence start uses the `<s>` row.

`TypoDistance` is a weighted restricted Damerau–Levenshtein: adjacent key 0.45,
other key 1.0, transposition 0.55, doubled/dropped repeated letter 0.35,
insert/delete 0.9, accent-only 0.05. One DP pass per candidate yields both the
whole-word and prefix distance, banded (Ukkonen) to the cost limit. Candidates come from (first letter, length) buckets plus first-letter completion lists. Words longer than 6 letters only scan the typed first letter, and the typed second letter in the second position. Measured at 6–10 ms per lookup on the build VM's JVM.

Curated misspellings (`en_misspellings.tsv`) are offered first and preferred as the autocorrection when the typed word is not learned. Distant typo candidates (distance > 0.6) are suppressed when real completions exist. The best completion is guaranteed a visible slot.

Strip hygiene (2026-11-08):
- A different whole word is shown only within `maxShownDistance` (any single slip up to 3 letters, then `0.08 + 0.25·length`).
- With 1–2 typed letters, whole-word typo candidates take −12 so they never lead real completions ("pr" → probably).
- A two-letter word may not have both letters replaced (Lexicon cost cap 0.6).
- The exact typed word is never echoed, except as the LITERAL "keep what I typed" choice beside a pending autocorrection.

**Spanish phonetics.** The Spanish `Lexicon` is built with `spanishPhonetics = true`: s/c/z, b/v, g/j, y/l, c/k/q substitutions and a missing or extra `h` cost 0.5 (`TypoDistance.PHONETIC_COST`). The silent-h bucket is scanned only for words equal to "h" + the typed word. Real Spanish words are still never autocorrected (`ola` offers `hola` but keeps `ola`).

**Ranks and load.** `en_words.tsv` ranks come from wordfreq merged with curated chat ranks; `es_words.tsv` is wordfreq's Spanish top 50k (details and licence in DATABASE_AND_DICTIONARY_INVENTORY.md). Both files are stored in match-key order, so `Lexicon`'s one-time sort is a single linear pass; ranks and behaviour do not depend on row order. `BundledDataIntegrityTest` keeps them sorted.

## 5. Learning row (`LearningAssistant`)

1. `TypingLanguageDetector`: counts words that are common English
   (rank ≤ 15k) vs. in the Spanish lexicon, plus Spanish-only characters;
   English unless Spanish clearly wins.
2. English sentence → `EnglishToSpanishTranslator.translate()` → text +
   aligned `TranslationSegment`s. Segments that are content words become
   `HintWord`s (`hintText`, `formLabel` such as "gerund", "past · yo",
   "noun · f"); `LearningModePolicy` keeps 8/5/3 hints (Beginner/Intermediate/
   Advanced). The sentence chip is shown only if coverage is 100% and the
   sentence has ≥ 2 words.
3. English grammar: `EnglishGrammarChecker.check(text, complete)` runs on the
   current sentence (ignoring the word still being typed). A suggestion is shown
   as a chip; the sentence translation is made from the corrected text and
   labelled “from corrected sentence”. Nothing is applied without a tap.
4. Spanish sentence → `SpanishToEnglishGlosser` per word (conjugation-aware,
   accent restoration, ambiguity shown as "we were / we went") and
   `SpanishToEnglishTranslator` for the sentence chip (100% coverage, ≥ 2
   words), with up to two grammar notes matched on the Spanish text.

## 5b. Grammar checker (`EnglishGrammarChecker`)

Small, independent rules produce non-overlapping edits that are spliced into
one corrected sentence:
- repeated words, except allowed repeats;
- subject–verb agreement;
- past tense with past-time markers, blocked by future markers;
- a/an and missing articles;
- confusables;
- capitalisation, only for complete sentences;
- spacing.

Agreement finds the subject at clause starts. It skips frequency adverbs, detects compound nouns (`bus stop is`), and skips subjunctive and auxiliary contexts. Plurality comes from English morphology only, so a noun like `news` is not a false plural. The checker prefers missing an error to flagging correct or informal English.

## 5c. Spanish→English translator (`SpanishToEnglishTranslator`)

Recursive descent over Spanish tokens, in this order:
1. Set phrases and idioms (`por qué`, `mucho gusto`, `¿cuántos años…?`).
2. wh-word or wh-phrase (`cuántos hermanos`).
3. Clause-initial time adverbs.
4. Subject: a pronoun, or a noun phrase followed by a finite verb, telling article `la` from clitic `la`.
5. Negation and clitics.
6. Finite verb, read from the reverse index, preferring the subject's person.

Constructions then handled:
- gustar-type verbs and `doler` + body part;
- `llamarse`;
- `querer` + person → love;
- `tener`, `hacer` and `haber` idioms and ages;
- impersonal weather;
- `ser` with impersonal complements;
- verb chains: estar + gerund, haber + participle, ir a, acabar de, tener que, verb + infinitive with enclitics.

English is generated with do-support for negation and questions, inversion, postverbal subjects in wh-questions, and wh-as-subject (`What happened?`). Complements: prepositional idioms (`en casa`, `por la noche`, `en autobús` → by bus, `en la mesa` → on), personal `a` dropped, generic articles dropped where English omits them. Unknown words are kept and lower coverage, which hides the chip. Questions typed without accents get their wh-word and following verb restored (`Como estas?`).

## 5d. Themes (`ui/theme/KeyboardColors.kt`)

There are five presets plus a custom colour. `KeyLingoSettings.themePreset` is stored by enum name.

**Upgrades.** `KeyboardThemePreset.fromStored` maps names saved by earlier versions:
- `OCEAN_BLUE` → Ocean Mist;
- `SUNLIGHT_YELLOW` → Golden Dune;
- `BLUSH_PINK` → Sunset Bloom;
- `MINT_GREEN` → Aqua Breeze;
- `LAVENDER_PURPLE` and `DARK_VIOLET` → Slate Grey.

The default for new installs is Ocean Mist; a saved choice is never replaced.

**Ocean Mist** is the approved former Ocean Blue. Its colours are still derived by HSL rules from one accent colour, and `KeyboardThemesTest` pins every value. `oceanMistGradients` adds three-stop gradients whose middle stop is each approved colour. The custom colour uses the same derivation and stays flat.

**Sunset Bloom, Aqua Breeze, Golden Dune, Slate Grey** are hand-tuned three-stop gradients in `gradientThemes`, each with light and dark variants. Gradients are drawn on:
- the keyboard background: one diagonal brush behind the strip and the keys;
- special keys;
- word, phrase and hint chips;
- accent surfaces (active Shift/Enter, highlighted autocorrection, grammar chip) and the translation chip.

Letter keys stay a flat off-white tint with dark text. `KeyboardColors` keeps one representative colour per surface (the middle stop) for contrast checks, popups and small elements. `List<Color>.toBrush()` turns stops into a `SolidColor` (one stop) or a linear gradient.

**Contrast.** Every text colour reaches WCAG AA (4.5:1) against every stop it sits on (`KeyboardThemesTest`). `ThemeRenderingTest` checks the drawn pixels. Geometry is not theme-dependent.

## 5e. Starter phrases and branding

**Starter phrases.** In an empty field (no text before or after the cursor, no selection) that allows language help, the word row shows `StarterPhrases.ENGLISH` or `StarterPhrases.SPANISH` instead of word suggestions:
- The language is the typing-language setting, or, on Auto, the language last typed.
- They are constants, so they appear without waiting for the bundled data.
- `onStarterPhraseSelected` inserts "phrase " only while the field is still empty, so a double tap cannot insert it twice or replace text.
- Nothing is learned from phrases.
- Typing switches to normal autocomplete; deleting everything brings the phrases back.

**Branding.** The approved logo `tools/branding/keylingo-logo-master.webp` is the source of truth. `tools/branding/make_launcher_assets.py` generates:
- `mipmap-*/ic_launcher_background.png`: the whole artwork, square filling the visible 72 dp;
- `mipmap-*/ic_launcher_monochrome.png`: themed-icon silhouette;
- `drawable-nodpi/ic_keylingo_mark.png`: in-app mark;
- `ic_launcher-playstore.png`.

The adaptive icon's foreground is transparent. `ui/brand/KeyLingoWordmark` shows the mark with "Key" and "Lingo" (the latter in the logo's gradient).

**App theme.** `KeyLingoAppTheme` (Plus Jakarta Sans from `res/font`, app shapes, page colours) wraps the app screens. The keyboard keeps `KeyLingoTheme` with the system font, so keyboard rendering is independent of app typography. With a keyboard accent, the app's `primary` is the accent and tonal containers are a tint of it (14% light, 24% dark).

**App layout.** `ui/layout/AppLayout.kt` holds the measurements every companion-app screen shares:
- `AppSpacing`: page margin 20 dp, card inset 18/16 dp, card gap 14 dp, section gap 28 dp, max content width 600 dp;
- `Modifier.appContentWidth()`: full width up to 600 dp, centred beyond it;
- `AppCard`: the rounded, bordered card.

Alignment rule: section headers, card titles, radio circles and buttons start on the card text edge. Radio rows are pulled back 12 dp to cancel the RadioButton's touch-target inset. `AppLayoutAlignmentTest` measures this; `AppScreensRenderTest` renders all four screens in light and dark.

## 6. Translator design (`EnglishToSpanishTranslator`)

Deterministic recursive-descent over tokens (contractions expanded):
clause → [conjunction] [wh-word] (aux-inversion | subject) verb-group complements.
- Verb group: do-support, negation, will/would, can/could/should/must/might,
  be + -ing (estar + gerund), be going to (ir a), have + participle (haber),
  have/has been + -ing, have to (tener que), used to (imperfect), let's (vamos a),
  would like/love (me gustaría/encantaría), just + past (acabar de).
- `be` + complement: ser vs estar by adjective class / location; tener idioms
  (hambre, sed, miedo, frío, razón...); weather `hace frío`; age `tener N años`;
  professions without article and with subject gender (`ella es maestra`).
- Gustar construction with IO pronoun and agreement with the liked thing.
- Clitics inserted before the finite verb (`te voy a llamar`, `no lo sé`),
  attached to imperatives/infinitive chains with stress-preserving accents
  (`llámame`, `esperar verte`).
- Subjunctive after hope/want/need + que, negated think/believe, and in
  `when/until` clauses with a future or imperative main clause.
- Noun phrases: determiners/possessives/demonstratives/quantifiers, adjective
  position and apocope (`un buen día`), agreement, compounds (`número de
  teléfono`), personal `a`, `al`/`del`, time expressions, proper nouns.
- Spanish punctuation `¿…?` placed at the start of the question clause.
Unknown words are kept verbatim and lower `coverage`.

## 7. Spanish morphology (`data/language/spanish`)

`SpanishConjugator`: present, preterite, imperfect, future, conditional,
present subjunctive, tú imperatives (affirmative/negative), gerund,
participle; stem changes (e>ie, o>ue, u>ue, e>i) incl. -ir preterite changes;
-car/-gar/-zar, -ger/-gir, -cer/-cir (-zco), -ducir, -uir, vowel stems,
-iar/-uar accents; tables for ~20 irregular verbs; reflexives and multi-word
verbs. Vosotros is intentionally not modelled. `SpanishMorphology`: plurals
with accent shifts, adjective agreement/apocope, articles (incl. `el agua`).
`SpanishText`: accent folding (ñ kept distinct), clitic attachment stress rules.

## 8. Persistence

See DATABASE_AND_DICTIONARY_INVENTORY.md. All personal writes are atomic
`INSERT OR IGNORE` + arithmetic `UPDATE` inside a transaction (SQLite 3.19 on
API 26 lacks UPSERT). Schemas are exported to `app/schemas/`; user databases
never use destructive migration. Retention (`PersonalDataRetention`) runs once per keyboard start through DAO `DELETE`s: 30-day expiry for incidental words, 60-day expiry for single-use sequences, and caps on sequence tables.
