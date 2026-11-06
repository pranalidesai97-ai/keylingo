# KeyLingo — Debug State

Covers the investigation and fix from this session. For the older,
still-unresolved "total suggestion silence" report from an earlier
session, see the "Older, separate, unresolved report" section at the
bottom — do not conflate the two.

---

## Original bug (this session)

Reported symptoms:
1. Many common English words produce no Spanish hint at all.
2. Some words produce only one translation (no richer forms).
3. Suggestions are not context-aware.
4. Overall Spanish learning value is low; sometimes only English
   autocomplete appears.

## Investigation process

1. Re-read `SentenceAnalyzer`'s dependency chain: it relies entirely on
   `DictionaryProvider.lookup(...)`, which is `RoomDictionaryProvider`,
   which queries `DictionaryDao.findWord(...)` against the
   `dictionary_entries` Room table.
2. That table is only ever populated by `DictionarySeeder.seedIfNeeded(...)`,
   which is called once in `KeyLingoInputMethodService.onCreate()`.
3. Read `DictionarySeeder.kt` in full, including its own KDoc, which
   documented an expected JSON shape using a `"base"` field for the
   target-language translation.
4. **Did not trust the KDoc — read the actual bundled asset directly**:
   ```
   python3 -c "import json; d=json.load(open('en_es.json')); print(d[0])"
   -> {"en": "i", "es": "yo", "pos": "pronoun"}
   ```
5. Checked every entry's keys programmatically:
   ```
   entries with a "base" key: 0
   entries with an "es" key: 734
   all distinct keys used across the file: ['en', 'es', 'pos']
   ```
6. Traced what `DictionarySeeder.kt` does when `"base"` is absent:
   `obj.optString("base")` returns `""` (org.json default for a missing
   key) → `base.isEmpty()` is true for all 734 entries → `continue` skips
   every one. **The dictionary table has been permanently empty.**
7. Cross-checked the sibling seeder for the same class of bug:
   `PhraseChunkSeeder.kt` reads `obj.optString("es")`, and
   `chunks_en_es.json` does use `"es"` — so phrase chunks seed correctly.
   This explains why phrase-chunk-pattern hints (35 fixed sentence
   patterns) could work while individual-word hints (the bulk of
   coverage) never could.

## Root cause

`DictionarySeeder.kt` (pre-fix) read the JSON key `"base"` for the
target-language translation. The bundled asset `assets/dictionary/en_es.json`
uses the key `"es"` instead, in all 734 entries. Every entry was silently
skipped during seeding (`if (source.isEmpty() || base.isEmpty()) continue`)
since the asset was authored. Not a logic bug in `SentenceAnalyzer`, not a
tokenizer issue, not a settings-gating issue — purely a field-name mismatch
between one seeder and the one asset it reads.

## Evidence

- Direct inspection of `assets/dictionary/en_es.json`: 0 of 734 entries
  contain a `"base"` key; all 734 contain an `"es"` key.
- Direct inspection of `DictionarySeeder.kt` line 52 (pre-fix):
  `val base = obj.optString("base").trim()`.
- Contrast with `PhraseChunkSeeder.kt`, which correctly reads `"es"` from
  a differently-authored asset that does use that key — isolates the bug
  to this one seeder/asset pairing, not a systemic pattern.

## Fix applied

One line, `DictionarySeeder.kt`:
```kotlin
// before
val base = obj.optString("base").trim()
// after
val base = obj.optString("es").trim()
```
Also corrected the file's KDoc, which had documented the same wrong field
name as the "expected" schema (it would have misled anyone re-authoring
the asset in the future) — updated the example to `"es"` and added a note
that `infinitive`/`gerund`/`rank`/`cefr` are supported by the code but not
yet present in the bundled data. No other line in the file changed. No
other file changed.

## Verification performed

All of the following were done by static analysis and a Python simulation
of the exact fixed logic against the real bundled JSON — **none of it is
a real Gradle build, install, or app run**, because no Android SDK,
emulator, device, or network access exists in this environment.

| Check | Result | Verified how |
|---|---|---|
| All 734 entries now pass the seeder's non-empty guard | ✓ 734 inserted, 0 skipped | Python simulation of the exact fixed logic against the real asset |
| Duplicate `sourceWord` collisions against the unique index | ✓ 11 found (see below) | Same simulation, checked for repeated keys |
| Tokenizer strips punctuation before lookup | ✓ yes, `[A-Za-z']+` regex | Direct read of `EnglishSourceLanguageRules.tokenize` |
| Case-sensitivity consistent seeder vs. lookup | ✓ both lowercase | Direct read of `DictionarySeeder`/`RoomDictionaryProvider.lookup` |
| `PartOfSpeech.fromCode` covers every `"pos"` value in the asset | ✓ yes (verb/noun/adjective/adverb/pronoun all map, no `OTHER` surprises) | Cross-referenced `PartOfSpeech.kt` against a value count over the real asset |
| Autocomplete pipeline untouched | ✓ confirmed by checksum, no file changed | md5sum before/after on every autocomplete-package file |
| Phrase-chunk pipeline untouched | ✓ confirmed by checksum | md5sum on `PhraseChunkSeeder.kt`/`PhraseChunkDao.kt` |
| `SentenceAnalyzer`/`PhraseMatcher`/`LearningModePolicy` untouched | ✓ confirmed by checksum | md5sum, unchanged |
| File compiles (brace/paren balance, manual re-read) | ✓ balanced, re-read in full | Automated balance check + full manual read |
| **Dictionary actually seeds on a real device** | ❌ Not runtime verified | No build/run capability in this environment |
| **Hints actually appear for previously-missing words** | ❌ Not runtime verified | Same reason |
| **No new crash/ANR introduced** | ❌ Not runtime verified | Same reason |

## Additional issue found during verification (documented, NOT fixed)

**11 English words collide on part-of-speech.** `en_es.json` contains
both a noun and verb sense for the following, listed here in full since
this is a small, enumerable, real list, not an estimate:

```
answer   (responder / respuesta)
plan     (planear / plan)
love     (amar / amor)
hope     (esperar / esperanza)
light    (luz / ligero)
clean    (limpiar / limpio)
open     (abrir / abierto)
closed   (cerro / cerrado)
today    (hoy / hoy)
tomorrow (manana / manana)
yesterday(ayer / ayer)
```

(Three of these — `today`/`tomorrow`/`yesterday` — have the same target
word for both senses, so they're not actually affected in practice; the
real collisions are the first eight.)

`dictionary_entries`' unique index is `(sourceWord, sourceLanguage,
targetLanguage)` with no part-of-speech column, and
`DictionaryDao.insertAll` uses `OnConflictStrategy.REPLACE`. Insertion
order follows the JSON array's order, so for each colliding word, only
whichever entry appears **later** in the array survives; the earlier
one is silently overwritten. This means one grammatical sense of each of
these 8 words will never be seedable as currently modeled, regardless of
JSON content, until either the unique index or the lookup/storage
strategy changes to account for part of speech. This is a pre-existing
gap in the data model — not introduced by this session's fix, only newly
*reachable* now that seeding actually inserts rows at all. Per
instruction, this is documented, not fixed, and is filed as TODO.md P1.

## Remaining risks

- Fix is unverified at runtime (see table above) — the single highest
  priority next step for whoever picks this up is building and running it
  on a real device.
- The 11-word (8 practically-relevant) collision issue above.
- Seeding race on cold start (pre-existing, documented in earlier
  sessions, unrelated to this fix): `KeyboardViewModel`'s settings
  collector can call `refreshSuggestions()` before seeding finishes, since
  both start concurrently in `onCreate()`.
- Silent seeding failure path (pre-existing): if `readAsset()` throws
  `IOException`, it's logged and the seeder returns — no retry, no
  user-visible signal. Not confirmed to have ever occurred.
- Dictionary entries still have no `infinitive`/`gerund`/`rank`/`cefr`
  populated, so even after this fix, hints will only ever show one base
  translation per word, not the richer forms in the product owner's
  stated goal — a content-authoring task, separate from this fix.

## Lessons learned

- **"Verified" claims about seeded data in earlier sessions of this
  project only checked JSON array length, never whether the seeder's
  field-mapping actually succeeds.** That gap directly hid this bug
  across multiple prior sessions. Going forward, verifying a seeder means
  simulating its actual field-extraction logic against the real asset,
  not just counting array entries.
- A working sibling seeder (`PhraseChunkSeeder`) using the correct field
  name from a differently-authored asset was strong corroborating
  evidence and a useful cross-check technique: when two similar
  components diverge in behavior, diff their assumptions against their
  actual inputs before assuming the more complex one has the bug.
- KDoc/comments can be as wrong as code and should be checked against
  real data, not trusted as the schema's source of truth — the seeder's
  own documentation described the very field name that caused the bug.

---

## Older, separate, unresolved report (from an earlier session — do not conflate with the above)

A previous session investigated a report that **neither** autocomplete
**nor** hints produced any output at all. A full static trace found no
defect. `Log.d`/`Log.e` instrumentation (tag `KLDebug`) was added at 14
pipeline checkpoints plus a detailed seeder trace specifically to capture
real evidence:

1. `commitText()` — `KeyLingoInputMethodService`
2. `scheduleSuggestionsRefresh()` — `KeyboardViewModel`
3. `refreshSuggestions()` started — `KeyboardViewModel`
4. current field text — `KeyboardViewModel`
5. `extractCurrentPrefix()` result — `KeyboardViewModel`
6a/6b. `GenerateAutocompleteSuggestionsUseCase` call site + inside the use case
7. `RoomAutocompleteProvider.suggest()` invoked
8. `EnglishWordDao.findByPrefix()` executing
9. DAO rows returned
10. `autocompleteSuggestions` size after the use case call
11. StateFlow updated
12. `KeyboardScreen` receives state (`LaunchedEffect`)
13. `HintBar` render
14. Visibility conditions (`hasContent` / per-row)

Plus a full seeding trace in `EnglishWordSeeder` (existing-row-count,
asset-read success/failure, parsed-entry count, exact final row count
after insert).

**This logging is still present in the code** (tag `KLDebug`), not yet
removed. **No logcat output from this instrumentation was ever received**
before the conversation moved to a different, later runtime report
(autocomplete reported working, hints "weak" — which turned out to have
the real, separate root cause documented above). It is unknown whether
these two reports describe the same underlying issue, a since-resolved
issue, or unrelated issues — this was never reconciled with evidence.
Whoever continues this project should either (a) capture real
`adb logcat -s KLDebug` output to close this out properly, or (b)
explicitly decide it's obsolete/superseded and remove the instrumentation
(restoring `KeyboardViewModel`'s Android-framework-free design) — but
should not assume either without checking, since it was never confirmed
which.
