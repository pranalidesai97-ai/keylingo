# KeyLingo — Project State

Generated as a handoff snapshot for continuing development in a different
Claude session. Verification legend used throughout this package:
**✓ Verified** = confirmed by direct source/data inspection.
**⚠ Assumed** = logically follows from verified facts but not directly
observed (e.g. runtime behavior inferred from code that was never
executed).
**❌ Not Verified** = could not be checked in this environment at all.

No Android SDK, emulator, device, or network access exists in this
working environment. **No line of this codebase has ever actually been
built or run during this project's history here.** Every runtime claim
below is ⚠ Assumed unless marked otherwise, and is based on tracing code
paths and, where possible, replicating the exact logic in a plain Python
simulation against the real bundled data (see DEBUG_STATE.md for that
methodology).

---

## 1. Project overview

KeyLingo is an Android custom keyboard (`InputMethodService`) that teaches
Spanish to English speakers while they type normally in any app. Two
independent suggestion pipelines run side by side:
1. **English autocomplete** — word completions for the word being typed.
2. **Spanish hint pipeline** — translations of typed English shown above
   the keyboard, read-only.

Long-term vision (per product owner, not yet implemented): a full
language-learning assistant with contextual sentence-level suggestions,
grammar correction, spaced repetition, adaptive difficulty, and eventually
multiple language packs — see TODO.md P4.

---

## 2. Features implemented

- ✓ Clean Architecture (`domain` → `data` → presentation), verified
  unchanged this session.
- ✓ Two separate Room databases: `keylingo_dictionary.db` (dictionary +
  phrase chunks, schema v3) and `keylingo_autocomplete.db` (English word
  list, schema v1).
- ✓ Dictionary: 734 entries in `assets/dictionary/en_es.json`.
- ✓ Phrase chunks: 35 entries in `assets/dictionary/chunks_en_es.json`,
  seeding logic verified correct (uses the actual `"es"` field).
- ✓ Autocomplete word list: 772 entries in `assets/autocomplete/en_words.json`.
- ✓ `SentenceAnalyzer`: tokenize → phrase-chunk match → dictionary lookup
  (with inflection fallback) → grammatical-form detection → confidence
  scoring → `LearningModePolicy` trimming.
- ✓ Dual-row `HintBar` (autocomplete row + hint row, independently hidden
  when empty).
- ✓ Autocomplete word replacement via `deleteCharactersBackward` +
  commit-with-trailing-space.
- **✓ Dictionary seeding bug fixed this session** (see §5 and
  DEBUG_STATE.md) — `DictionarySeeder.kt` was reading a JSON field
  (`"base"`) that does not exist in the bundled asset (which uses `"es"`),
  causing every one of the 734 dictionary entries to be silently skipped
  during seeding since the asset was authored. Fixed to read `"es"`.

## 3. Remaining issues

See DEBUG_STATE.md "Remaining risks" and TODO.md for the full prioritized
list. Highlights:

- **⚠ Assumed, not runtime-verified**: the dictionary fix should now make
  individual-word Spanish hints appear for the first time (previously
  zero were possible). This is a strong logical inference from the code
  and a Python simulation of the seeder against the real asset (see
  DEBUG_STATE.md), but it has never actually been observed running.
- **✓ Verified, new finding from this session's verification pass**: 11
  English words appear twice in `en_es.json` with different parts of
  speech (e.g. `"love"` as both verb→`amar` and noun→`amor`). The
  `dictionary_entries` table's unique index is on `(sourceWord,
  sourceLanguage, targetLanguage)` only — it does not include part of
  speech — and `DictionaryDao.insertAll` uses
  `OnConflictStrategy.REPLACE`. So for these 11 words, only whichever
  sense appears later in the JSON array survives seeding; the other sense
  is silently dropped. Not caused by this session's fix (the underlying
  data-model gap predates it) but only became *reachable* once the fix
  made seeding actually insert rows. Documented separately, not fixed —
  see DEBUG_STATE.md and TODO.md P1.
- **✓ Verified**: no persistent number row exists in `KeyboardLayoutProvider`'s
  letters-mode layout; numbers are only reachable via the symbols-mode
  switch. Whether this is a regression or was never built is ❌ Not
  Verified (no history/build to compare against).
- **❌ Not Verified**: typing responsiveness/smoothness relative to
  Gboard — no measurement has been possible in this environment.
- **⚠ Assumed**: English autocomplete is unaffected by this session's fix
  — confirmed by checksum that no autocomplete file was touched — but
  its actual runtime behavior (reported as "works" by the user) is itself
  ❌ Not Verified from this side.

## 4. Verified runtime behavior

None. To repeat plainly: **nothing in this project has been built or run
in this working environment at any point.** "Runtime behavior" reported
in this document reflects either (a) the user's own device testing,
relayed secondhand and not independently confirmed, or (b) logical
inference from static code/data analysis, never (c) direct observation by
whichever Claude session produced this documentation. Anywhere this
document says a pipeline "should now work," read it as ⚠ Assumed.

## 5. Known limitations

- Dictionary: 734 entries (curated, not the eventual 5000+ target);
  architecture supports a drop-in larger file with zero code changes.
- Dictionary entries have no `infinitive`/`gerund`/`rank`/`cefr` data
  populated yet (the seeder and `DictionaryEntity` already support all
  four; the bundled JSON simply doesn't set them) — this is *why* hints
  can only ever show a single base translation today, not richer forms
  (`trabajar`/`trabajando`/etc.) — a content-authoring task, not a code
  task.
- The 11-word duplicate-sense collision described in §3 (data-modeling
  gap: unique index doesn't include part of speech).
- Autocomplete word list: 772 entries, not production-scale (10k-100k
  target).
- No persistent number row.
- No key-repeat/animation/haptic polish.
- `KeyboardViewModel` currently has a temporary `android.util.Log`
  dependency added for debugging in a prior session — not yet removed.
  Tag `KLDebug`; see DEBUG_STATE.md.
- No progress tracking, spaced repetition, grammar correction, or any of
  the longer-term learning-system features — all future work, see
  TODO.md P4.

## 6. Next recommended milestone

Per the product owner's own stated priorities (most recent instructions):
1. **Confirm the dictionary fix on a real device** — build, install, type
   `"work"`, verify `trabajar` appears as a hint. This is the single most
   important next step: everything else in this document about the fix
   is inference, not observation.
2. Decide how to handle the 11-word duplicate-sense collision (see
   TODO.md P1) — likely requires adding part-of-speech to the unique
   index, or a different resolution strategy, which is a real (if small)
   architecture decision, not a one-line fix.
3. Investigate typing responsiveness (Priority 2 from the product
   owner's brief) — debounce timing, recomposition counts, coroutine
   scheduling. Not started.
4. Add the configurable number row (Priority 3).
5. Review autocomplete ranking (Priority 4).

Full detail and additional priorities: TODO.md.
