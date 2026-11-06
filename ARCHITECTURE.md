# KeyLingo — Architecture

Structure and data flow of the code as it exists now. Status lives in
PROJECT_STATE.md / FEATURE_IMPLEMENTATION_STATUS.md.

## 1. Layers

```
presentation   keyboard/ (IME service, KeyboardViewModel, TextContext, InputFieldPolicy, Compose UI)
               settings/, ui/ (companion app)
      │ calls use cases / interfaces only
domain         model/, repository/ (interfaces), usecase/   — pure Kotlin, no Android imports
      ▲ implemented by
data           bundled/   in-memory, read-only language data (assets)
               lexicon/   Lexicon + TypoDistance
               language/  EnglishMorphology, TypingLanguageDetector, spanish/ (conjugator, morphology, text)
               translation/ EnglishToSpanishTranslator, SpanishToEnglishGlosser
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
key press ─► KeyboardKey (Compose gesture: tap / long-press accents / backspace repeat, haptic)
          ─► KeyboardViewModel.onKeyAction
               ├─ edits via KeyboardTextConnection (commit / delete grapheme / batch edits)
               ├─ word boundary? → autocorrect (if pending + still valid) → learning (if policy allows)
               ├─ updateAutoShift()
               └─ scheduleRefresh():  suggestions after 20 ms, learning row after 220 ms (debounced, cancelled on next key)
refreshSuggestions:  TextContext(textBeforeCursor(240), textAfterCursor(4))
                     → LanguageDetector.detect(current sentence)
                     → GenerateAutocompleteSuggestionsUseCase(SuggestionRequest)
refreshHints:        current (or just-finished) sentence → GenerateHintsUseCase → LearningAssistant
```

`InputFieldPolicy` (from `EditorInfo`) gates everything per field: suggestions,
learning, autocorrect, learning hints, capitalisation mode, initial layer,
Enter action. Passwords: nothing analysed or stored. Incognito: nothing stored.

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
whole-word and prefix distance; candidates are pruned by first letter
(+ neighbours) and second letter. ~6 ms per lookup on the build VM's JVM.

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
3. Spanish sentence → `SpanishToEnglishGlosser` per word (conjugation-aware,
   accent restoration, ambiguity shown as "we were / we went").

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
never use destructive migration.
