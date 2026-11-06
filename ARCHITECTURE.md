# KeyLingo — Architecture

Companion to PROJECT_STATE.md. Covers structure and data flow only;
status/verification lives in PROJECT_STATE.md and DEBUG_STATE.md.

---

## Layering

```
domain  (pure Kotlin: models, repository interfaces, use cases)
   ^
data    (Room, DataStore, SentenceAnalyzer, LearningModePolicy,
         PhraseMatcher, concrete provider/rules implementations)
   ^
presentation (keyboard/ IME + ViewModel + Compose UI, settings/, ui/)
```

`domain` never imports `data`. Two vertical feature slices share this
layering without sharing implementation:

- **Spanish hint pipeline**: `data/dictionary/`, `data/hint/`,
  `data/language/`.
- **English autocomplete pipeline**: `data/autocomplete/`.

They converge only at `KeyboardViewModel` (calls both use cases
independently, merges output into one state object) and `HintBar`
(renders both as separate rows).

---

## Module responsibilities

| Package | Responsibility |
|---|---|
| `domain/model` | Pure data classes/enums: `DictionaryEntry`, `HintWord`, `PhraseChunk`, `AutocompleteSuggestion`, `LearningMode`, `GrammaticalForm`, `CefrLevel`, `PartOfSpeech`. |
| `domain/repository` | Interfaces: `DictionaryProvider`, `PhraseChunkProvider`, `HintProvider`, `AutocompleteProvider`, `LanguagePack`, `SourceLanguageRules`. |
| `domain/usecase` | `GenerateHintsUseCase`, `GenerateAutocompleteSuggestionsUseCase` — the only entry points the presentation layer calls into either pipeline. |
| `data/dictionary` | Room entities/DAOs/database/seeders for the dictionary and phrase-chunk tables; `RoomDictionaryProvider`, `RoomPhraseChunkProvider`. |
| `data/autocomplete` | Room entity/DAO/database/seeder for the autocomplete word list; `RoomAutocompleteProvider`. Fully independent storage from `data/dictionary`. |
| `data/hint` | `SentenceAnalyzer`, `LearningModePolicy`, `PhraseMatcher`, `DefaultHintProvider` — all the actual hint-generation logic, Android-free and unit-testable. |
| `data/language` | `SpanishLanguagePack` (language being taught), `EnglishSourceLanguageRules` (how the typed language is analyzed). |
| `data/settings` | `SettingsDataStore`, `KeyLingoSettings`, option enums (`DarkModeOption`, `HintSizeOption`). |
| `di` | Hilt modules — see graph below. |
| `keyboard` | `KeyLingoInputMethodService` (the IME), `KeyboardViewModel`, `KeyboardTextConnection` (interface seam), `KeyboardLayoutProvider`, `KeyAction`. |
| `keyboard/ui` | `KeyboardScreen`, `HintBar`, `KeyboardKey` (Compose). |
| `settings`, `settings/ui` | Companion-app Settings screen + its own `SettingsViewModel` (real `@HiltViewModel`, unlike `KeyboardViewModel`). |
| `ui/onboarding`, `ui/theme` | Companion-app onboarding flow and Compose theme. |

---

## Dependency Injection graph

All modules `@InstallIn(SingletonComponent::class)`.

```
AppModule (abstract class, @Binds)
  DictionaryProvider    -> RoomDictionaryProvider
  PhraseChunkProvider   -> RoomPhraseChunkProvider
  HintProvider          -> DefaultHintProvider
  LanguagePack          -> SpanishLanguagePack
  SourceLanguageRules   -> EnglishSourceLanguageRules

DatabaseModule (object, @Provides)
  DictionaryDatabase, DictionaryDao, PhraseChunkDao,
  DictionarySeeder, PhraseChunkSeeder

AutocompleteModule (object, @Provides)
  AutocompleteDatabase, EnglishWordDao, EnglishWordSeeder

AutocompleteBindsModule (abstract class, @Binds)
  AutocompleteProvider  -> RoomAutocompleteProvider
```

Why four modules, not two: `@Binds` requires an abstract class/interface
method, which cannot coexist with a `@Provides`-only `object` in the same
module. Each vertical slice (dictionary-side, autocomplete-side) therefore
splits into a `@Provides` object + a `@Binds` abstract class. The
autocomplete pair is kept structurally separate from the dictionary pair
so the graph itself documents that autocomplete has no dependency on the
hint pipeline.

`KeyLingoInputMethodService` (`@AndroidEntryPoint`) field-injects:
`generateHintsUseCase`, `generateAutocompleteSuggestionsUseCase`,
`settingsDataStore`, `languagePack`, `dictionarySeeder`,
`phraseChunkSeeder`, `englishWordSeeder`. It then constructs
`KeyboardViewModel` manually with these — not via Hilt's ViewModel
support, since an `InputMethodService` has no `ViewModelStoreOwner` shape
a `@HiltViewModel` expects out of the box (the service implements the
owner interfaces itself; see "Keyboard lifecycle" below).

---

## Database schema

**`keylingo_dictionary.db`** (schema v3, `fallbackToDestructiveMigration()`)

```
dictionary_entries
  id                 PK, autogenerate
  sourceWord         TEXT
  targetBase         TEXT
  targetInfinitive   TEXT?
  targetGerund       TEXT?
  sourceLanguage     TEXT
  targetLanguage     TEXT
  partOfSpeech       TEXT
  frequencyRank      INT
  cefrLevel          TEXT
  UNIQUE INDEX (sourceWord, sourceLanguage, targetLanguage)

phrase_chunks
  id                 PK, autogenerate
  sourcePhrase       TEXT
  targetPhrase       TEXT
  sourceLanguage     TEXT
  targetLanguage     TEXT
  wordCount          INT
  requiredNextForm   TEXT?
```

**`keylingo_autocomplete.db`** (schema v1, `fallbackToDestructiveMigration()`)

```
english_words
  id             PK, autogenerate
  word           TEXT, UNIQUE INDEX
  frequencyRank  INT
```

Both databases are disposable local caches of bundled JSON — schema bumps
intentionally destroy-and-reseed rather than use hand-written Migrations.

**Known data-model gap** (see PROJECT_STATE.md/DEBUG_STATE.md): the
`dictionary_entries` unique index does not include `partOfSpeech`, so a
word with two senses (e.g. "love" as noun and verb) collides on insert;
`OnConflictStrategy.REPLACE` means only the later-inserted sense survives.

---

## Hint pipeline — execution flow

```
KeyboardViewModel.refreshSuggestions()
  |
  |  (only if settings.learningEnabled)
  v
GenerateHintsUseCase(text, sourceLanguage="en", targetLanguage, learningMode)
  v
HintProvider.generateHints(...)  [DefaultHintProvider, thin wrapper]
  v
SentenceAnalyzer.analyze(...)
  |
  1. SourceLanguageRules.tokenize(text)        -> English word tokens (regex [A-Za-z']+)
  2. PhraseMatcher: longest-match-first lookup over the token stream
  |     -> PhraseChunkProvider.findChunks(...)  [Room phrase_chunks table]
  3. For each remaining (non-chunked) word:
  |     SourceLanguageRules.normalize(word)     -> lowercase
  |     DictionaryProvider.lookup(word, ...)    -> RoomDictionaryProvider
  |       (in-memory cache, keyed "word|source|target", including cached misses)
  |       -> DictionaryDao.findWord(...)         [Room dictionary_entries table]
  |     on miss: try SourceLanguageRules.inflectionCandidates(word) in order
  4. Grammatical-form detection:
  |     SourceLanguageRules.surfaceFormSignal(word)              e.g. "-ing" -> GERUND
  |     SourceLanguageRules.formImpliedByPrecedingWord(prevWord)  e.g. "to" -> INFINITIVE
  |     or a form implied by a matched phrase chunk's requiredNextForm
  5. Confidence scoring per resolution path (exact / suffix-fallback /
  |     chunk-derived / ambiguous-bare-verb)
  6. LearningModePolicy.keepScore-driven trimming to the mode's hint count/preferences
  v
List<HintWord>
  v
merged into KeyboardUiState.hints
```

---

## Autocomplete pipeline — execution flow

```
KeyboardViewModel.refreshSuggestions()
  |  (always active, independent of learningEnabled)
  v
extractCurrentPrefix(fullText)   -> last whitespace-delimited token, "" if trailing whitespace
  v
GenerateAutocompleteSuggestionsUseCase(prefix, maxSuggestions=5)
  v
AutocompleteProvider.suggest(prefix, maxSuggestions)  [RoomAutocompleteProvider]
  |   normalize: trim + lowercase
  v
EnglishWordDao.findByPrefix(prefix, limit)
  |   SQL: WHERE word LIKE :prefix || '%' ORDER BY frequencyRank ASC LIMIT :limit
  v
List<AutocompleteSuggestion>
  v
merged into KeyboardUiState.autocompleteSuggestions
```

No in-memory cache on the autocomplete side (unlike the dictionary
provider) — the prefix changes almost every keystroke, so a cache would
rarely hit.

Selecting a suggestion:
```
HintBar -> onAutocompleteSelected(suggestion)
  -> KeyboardViewModel.onAutocompleteSelected:
        prefix = extractCurrentPrefix(currentFieldText())
        textConnection.deleteCharactersBackward(prefix.length)
        textConnection.commitText("word ")
        scheduleSuggestionsRefresh()
```

---

## Important classes

- **`KeyLingoInputMethodService`** — the real IME. Implements
  `KeyboardTextConnection` against `currentInputConnection`; hosts the
  Compose UI; seeds all three datasets in `onCreate()`; implements
  `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`/
  `OnBackPressedDispatcherOwner` manually (see lifecycle section).
- **`KeyboardViewModel`** — plain Kotlin state holder (not
  `androidx.lifecycle.ViewModel`). Owns `KeyboardUiState`, debounces and
  runs both suggestion pipelines from one shared text read, turns
  `KeyAction`s into `KeyboardTextConnection` calls. Currently imports
  `android.util.Log` for temporary debug instrumentation (see
  DEBUG_STATE.md) — this is a deliberate, flagged, temporary compromise
  of its original "Android-framework-free" design.
- **`KeyboardTextConnection`** — interface seam between the ViewModel and
  Android's real `InputConnection`. Methods: `commitText`,
  `deleteOneCharacterBackward`, `deleteCharactersBackward(charCount)`,
  `performEnterAction`, `currentFieldText`.
- **`SentenceAnalyzer`** — all hint-generation logic. Depends only on
  `DictionaryProvider`, `SourceLanguageRules`, `PhraseMatcher` — no
  hardcoded English morphology, no Android/Hilt/Compose dependency,
  directly unit-testable.
- **`RoomAutocompleteProvider`** / **`RoomDictionaryProvider`** — Room-backed
  implementations of their respective domain interfaces. The dictionary
  provider adds an in-memory cache (including negative/miss caching); the
  autocomplete provider deliberately does not.
- **`DictionarySeeder`** / **`PhraseChunkSeeder`** / **`EnglishWordSeeder`** —
  idempotent one-time JSON-asset-to-Room population, all triggered
  sequentially from `KeyLingoInputMethodService.onCreate()`.
- **`GenerateHintsUseCase`** / **`GenerateAutocompleteSuggestionsUseCase`** —
  the only two entry points the presentation layer calls into `domain`.
  Both thin wrappers with a blank/empty-input guard.
- **`KeyboardScreen`** — root Compose UI. Renders `HintBar` then the
  active key rows (letters/symbols/numbers-via-symbols/emoji).
- **`HintBar`** — renders both suggestion rows; each independently hidden
  when empty, whole bar hidden (with fade/slide animation) only when both
  are empty.

---

## Keyboard lifecycle

An `InputMethodService`'s soft-input window is an internal `Dialog` with
its own `DecorView`, not an Activity window, so Compose's per-window
`Recomposer` lookup has no owner tagged on that DecorView automatically.
`KeyLingoInputMethodService` implements the four owner interfaces itself
and calls `attachTreeOwnersToDialogDecorView()` before the `ComposeView`
attaches.

```
onCreate()
  -> construct KeyboardViewModel (manual, all deps passed through)
  -> serviceScope.launch { dictionarySeeder; phraseChunkSeeder; englishWordSeeder }
onCreateInputView()
  -> ComposeView { KeyboardScreen(viewModel) }, owners attached
onStartInputView(...)
  -> triggers a suggestions refresh
onDestroy()
  -> clears ViewModelStore, cancels serviceScope
```

The seeding coroutine and `KeyboardViewModel`'s settings-flow collector
(which can itself trigger a refresh) start concurrently — a known,
unaddressed race on a cold start (see PROJECT_STATE.md "Known
limitations").
