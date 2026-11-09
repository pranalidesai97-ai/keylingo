# KeyLingo — Database and Dictionary Inventory

## 1. Bundled assets (read-only, `app/src/main/assets/`)

Parsed once into memory by `BundledLanguageDataSource` on a background
thread, started when the keyboard service is created (median ≈0.2 s cold on
the desktop JVM; not yet measured on a phone). All files are UTF-8,
tab-separated, `#` comments. The two lexicons are stored in match-key order
(`tools/sort_lexicon_by_key.py`) so the load-time sort is a single pass.

| File | Rows | Size | Format | Source / licence |
|---|---|---|---|---|
| `lexicon/en_words.tsv` | 111,792 | 1.9 MB | `word  rank  flags` (`n` = known, never suggested) | **Words:** SCOWL (© Kevin Atkinson, permissive licence — see `tools/data/SCOWL-Copyright.txt`; its notice must accompany redistribution) + curated contractions/capitalised/modern words, via `tools/build_english_lexicon.py`. **Rank column:** wordfreq 3.1.1 frequency order (CC BY-SA 4.0, §5 below) merged with KeyLingo's curated conversational ranks (`tools/data/en_curated_ranks.tsv`, the better rank wins), via `tools/rerank_english_by_frequency.py`; lowercase name forms (john, tom) are not promoted; crude words and slurs are flagged `n`. Then `tools/sort_lexicon_by_key.py … en`. **Do not edit by hand; regenerate** (build → rerank → sort). |
| `lexicon/es_words.tsv` | 50,000 | 0.8 MB | `word  rank  flags` | Top 50,000 Spanish words of wordfreq 3.1.1 (CC BY-SA 4.0, §5 below), each validated against the RLA-ES Hunspell dictionary at build time (lowercase, else capitalised form); profanity flagged `n`. `tools/build_spanish_lexicon.py`, then `tools/sort_lexicon_by_key.py … es`. |
| `lexicon/en_misspellings.tsv` | 111 | 2.4 KB | `typo  correction` | Hand-authored common misspellings (`becuase`, `definately`, `tomorow`, `recieve`, `alot`→`a lot`). |
| `dictionary/en_es.tsv` | 1,936 | 44 KB | `english_lemma  spanish  pos  gender` | Hand-authored for KeyLingo (migrated from the legacy JSON with accents restored). 611 entries added on 2026-11-08 for the most frequent English content words that had none (the selection used wordfreq ranks; the translations are KeyLingo's own, spellings checked with Hunspell es). |
| `dictionary/es_en_extra.tsv` | 29 | 0.7 KB | `spanish  english  pos  gender` | Hand-authored, **reverse-only**: common Spanish words (`estar`, `gente`, `simpático`…) that Spanish→English needs but English→Spanish must not pick. |
| `dictionary/idioms_en_es.tsv` | 51 | 1.5 KB | `english phrase  spanish` | Hand-authored. Only fixed expressions; grammar belongs in the translator. |
| `prediction/en_next_words.tsv` | 79 heads | 3.8 KB | `previous  next1  next2 ...` (`<s>` = sentence start) | Hand-authored cold-start model. |
| `prediction/es_next_words.tsv` | 89 heads | 3.6 KB | same | Hand-authored from common conversational Spanish (no corpus data). `BundledDataIntegrityTest` checks every word is a suggestable lexicon word with its accents. |

### Other bundled files
- `assets/licenses/wordfreq-CC-BY-SA-4.0.txt`: attribution, list of changes and the wordfreq/SUBTLEX/OpenSubtitles/Google Books terms for the two lexicon files.
- `assets/licenses/CC-BY-SA-4.0-legalcode.txt`: full text of the CC BY-SA 4.0 licence (the app is offline, so a link alone is not enough).
- `assets/licenses/PlusJakartaSans-OFL.txt`: licence for the bundled app font (`res/font/plus_jakarta_sans_*.ttf`, converted from Fontsource 5.3.0 WOFF2; Latin subset incl. Spanish characters).
- `tools/branding/keylingo-logo-master.webp`: the approved logo, source of all icon assets (not shipped).

### Dictionary rules (`en_es.tsv`)
- One row per (English lemma, part of speech). POS: `noun verb adj adv pron prep conj other`.
- Verbs: Spanish **infinitive** (reflexive `-se` and multi-word verbs such as
  `estar de acuerdo`, `hacer clic` are allowed). Never add conjugated rows.
- Nouns/adjectives: masculine singular base form. Gender column for nouns:
  `m`, `f`, `mf` (common gender person nouns: `maestro` → `maestra`), suffix `p`
  for plural-only nouns (`vacaciones` → `fp`).
- Proper nouns are written capitalised in Spanish (`México`) and never take an article.
- Accents and ñ are mandatory; `BundledDataIntegrityTest` fails on known
  accent-stripped spellings and unaccented `-cion/-sion`.
- Frequency rank and CEFR band are derived at load time from the English
  lexicon rank (CEFR: ≤800 A1, ≤2k A2, ≤5k B1, ≤12k B2, else C1 — a heuristic).

### Derived in memory (not stored)
- **Spanish lexicon** (53,563 surface forms): the 50,000 wordfreq words, plus
  every dictionary Spanish form and all generated conjugations, plurals and
  agreement forms, plus ~150 core function words; accent-folded keys. Typo
  search treats Spanish sound-alike spellings (s/c/z, b/v, g/j, y/ll, silent h)
  as cheap edits.
- **Reverse index** Spanish form → entry/tense/person, for glosses and the Spanish→English translator. It keeps every tense × person reading (`fui` = ser and ir; `como` = comer and cómo), plus the reverse-only rows.
- **Lexicon indexes**: (first letter, length) buckets and first-letter completion lists (rank ≤ 30k) for fast typo search.

### Vocabulary counts (from `VocabularyMetricsTest`)
| | First phase start | Before 2026-11-08 round (`015f63f`) | Now |
|---|---|---|---|
| English words | 61,547 | 111,795 | 111,792 (3 misspelled forms removed) |
| English words with real frequency ranks | ~1,900 | ~1,900 | 78,929 (wordfreq) + curated chat ranks |
| Suggestable / recognised-only | 61,547 / 0 | 77,137 / 34,658 | 77,062 / 34,730 (crude words flagged) |
| Inflected forms / base forms | not measured | 48,652 / 63,143 | 48,649 / 63,143 |
| Curated misspellings | 0 | 108 | 111 |
| Dictionary entries (EN→ES) | 1,325 | 1,325 (+27 reverse-only) | 1,936 (+29 reverse-only) |
| Top-3,000 English words with a translation | — | 59% | 81% (top 5,000: 42% → 57%) |
| Spanish surface forms | 8,447 | 8,603 | 53,563 |
| Spanish words with real frequency ranks | 0 | 0 | 50,000 |
| Spanish next-word heads | — | 21 | 89 |
| Idioms | — | 47 | 51 |

### What was removed
- `dictionary/en_es.json` (905 rows, every accent stripped, inflected rows,
  duplicate senses), `dictionary/chunks_en_es.json` (35 chunks, grammar now in
  rules), `autocomplete/en_words.json` (1,798 words). History in git.

## 2. Personal databases (Room, user data, never destructive)

### `keylingo_learning.db` — `LearningDatabase` v1 (schema `app/schemas/...LearningDatabase/1.json`)
| Table `learned_words` | |
|---|---|
| `id` INTEGER PK autoincrement | |
| `word` TEXT (lowercase), `language` TEXT (`en`/`es`) | UNIQUE (word, language) |
| `organicCount` INTEGER | typed then completed naturally |
| `acceptedCount` INTEGER | tapped as suggestion, or an un-done autocorrection |
| `lastUsedAtEpochMillis` INTEGER | recency |

Learned = `organicCount ≥ 3 OR acceptedCount ≥ 1`. **Retention** (once per keyboard start): rows below that bar unused for 30 days are deleted; learned rows are kept until the user deletes them (Settings → Learned words). Writes:
`INSERT OR IGNORE` + `UPDATE … SET count = count + n` in one transaction.
Schema unchanged from the shipped v1 (only DAO queries changed), so no migration was needed.

### `keylingo_context.db` — `ContextDatabase` v2 (schemas 1.json, 2.json)
| Table | Columns | Index |
|---|---|---|
| `bigrams` (v1) | id, previousWord, nextWord, language, count, lastUsedAtEpochMillis | UNIQUE (previousWord, nextWord, language) |
| `trigrams` (v2) | id, secondPreviousWord, previousWord, nextWord, language, count, lastUsedAtEpochMillis | UNIQUE (secondPreviousWord, previousWord, nextWord, language) |

**Retention:** rows with `count ≤ 1` unused for 60 days are deleted. Each table is then capped (20,000 bigrams, 30,000 trigrams), least recently used first. Forgetting a word deletes every sequence containing it. No schema change; these are DAO queries only.

`MIGRATION_1_2` creates `trigrams` and its index; existing bigrams are kept.
Verified by `UserDatabasesTest.migratingContextDatabaseFromV1KeepsExistingHistory`
with `MigrationTestHelper.runMigrationsAndValidate` against the exported 2.json.

**Rule for future changes:** bump the version, write a `Migration`, add it to
`ContextDatabase.ALL_MIGRATIONS` (or the learning equivalent), commit the new
exported schema, and extend the migration test. Never use
`fallbackToDestructiveMigration()` on these databases.

### Legacy cache databases (deleted on startup)
`keylingo_dictionary.db`, `keylingo_autocomplete.db` held only copies of
bundled assets in earlier versions. `KeyLingoApplication` deletes them.

## 3. Preferences (DataStore `keylingo_settings`)

| Key | Type | Default |
|---|---|---|
| `learning_enabled` | Boolean | true |
| `target_language` | String | `es` |
| `dark_mode` | SYSTEM/LIGHT/DARK | SYSTEM |
| `hint_size` | SMALL/MEDIUM/LARGE | MEDIUM |
| `learning_mode` | BEGINNER/INTERMEDIATE/ADVANCED | INTERMEDIATE |
| `show_number_row` | Boolean | true |
| `autocorrect_enabled` | Boolean | true (new) |
| `haptics_enabled` | Boolean | **false** (vibration off by default) |
| `sound_enabled` | Boolean | false |
| `theme_preset` | OCEAN_MIST / SUNSET_BLOOM / AQUA_BREEZE / GOLDEN_DUNE / SLATE_GREY / CUSTOM (older stored names are mapped on read, see ARCHITECTURE §5d) | OCEAN_MIST |
| `custom_accent_argb` | Int | 0xFF2E9E8F |
| `typing_language` | AUTO / ENGLISH / SPANISH | AUTO |
| `grammar_enabled` | Boolean | true |
| `sentence_translation_enabled` | Boolean | true |

## 4. Privacy summary
No network permission or code. Typed text is read only as a ≤240-character
window before the cursor to compute suggestions and is never stored, except
individual eligible words and word sequences in the two personal databases
— never from password or incognito fields. Logs contain counts, timings and
exception types only. Android cloud backup and device-to-device transfer are disabled for all app data (`allowBackup=false`, `data_extraction_rules.xml`).

## 5. Third-party data licensing (wordfreq, CC BY-SA 4.0)

**Not legal advice. The points marked ⚠ need review by someone qualified
before any public distribution (Play Store, website, sharing the APK beyond
testers).** The licence clauses below were checked against the CC BY-SA 4.0
legal code text (bundled as `assets/licenses/CC-BY-SA-4.0-legalcode.txt`,
obtained via the SPDX licence list because creativecommons.org was not
reachable from the build environment).

**Source.** wordfreq 3.1.1 by Robyn Speer (PyPI `wordfreq`), "large" English
and Spanish lists. wordfreq's *code* is Apache-2.0 (not shipped). Its *data*
is offered under CC BY-SA 4.0. It aggregates Wikipedia, SUBTLEX (with
Marc Brysbaert's permission, on condition that SUBTLEX is credited and stays
clearly free), OPUS OpenSubtitles 2018 (attribution to OpenSubtitles), Google
Books Ngrams (attribution requested), ParaCrawl, the Leeds Internet Corpus and
Twitter statistics. wordfreq is used only at build time; the APK ships the
derived TSV files, not wordfreq.

**What in KeyLingo comes from it.**
- `lexicon/es_words.tsv`: the word selection and the rank order of all 50,000 rows.
- `lexicon/en_words.tsv`: the rank column (the words come from SCOWL).
- Not from wordfreq: the dictionary translations, next-word tables, misspellings and all code. wordfreq ranks were only used to *choose* which dictionary entries to write.

**Adaptation or not.** Under §1(a) "Adapted Material" is material "derived
from or based upon the Licensed Material" and modified "in a manner requiring
permission under the Copyright and Similar Rights". KeyLingo filtered,
re-ranked, merged and re-ordered the lists, so the two files are treated as
**Adapted Material**. Whether frequency rankings attract copyright at all
varies by country, but CC BY-SA 4.0 also covers EU-style database rights:
§4(b) makes a database that includes "all or a substantial portion" of the
licensed database contents Adapted Material. KeyLingo therefore applies
CC BY-SA 4.0 to both files (the cautious reading). ⚠ Confirm this
classification, in particular for the English file, where only the rank
order is derived.

**Obligations when the APK is shared** (sharing the APK distributes these files):
1. **Attribution, §3(a)(1):** creator (Robyn Speer, wordfreq), copyright notice
   ("Copyright 2022 Robyn Speer", the only one wordfreq supplies), a notice
   referring to the licence, a notice referring to the warranty disclaimer, and
   a link to the source. All are in `assets/licenses/wordfreq-CC-BY-SA-4.0.txt`,
   plus wordfreq's own source credits (SUBTLEX citations, OpenSubtitles, Google
   Books Ngrams) verbatim.
2. **Indicate changes, §3(a)(1)(B):** listed in the same file.
3. **Licence text or link, §3(a)(1)(C) and §3(b)(2):** link and full text bundled.
4. **ShareAlike, §3(b)(1):** the two adapted files must be licensed CC BY-SA 4.0
   (or later / BY-SA-compatible). Their headers say so.
5. **No added restrictions, §3(b)(3):** no terms or technological measures that
   restrict others' rights to these files. They sit unencrypted in the APK's
   `assets/`. ⚠ Check that any future store terms, EULA or encryption do not
   restrict these files.
6. **No endorsement, §2(a)(6):** do not imply wordfreq or its sources endorse KeyLingo.

**What ShareAlike does not obviously reach.** ShareAlike applies to Adapted
Material. KeyLingo's code reads the files but does not modify them, so
it is not obviously an adaptation of them. ⚠ Confirm that shipping the files
alongside proprietary or differently licensed code is acceptable (the usual
CC view is that separate works merely combined are not adaptations) and that
this holds for the build-time selection of dictionary entries.

**User visibility.** The About card credits wordfreq and CC BY-SA 4.0 and says
full notices are bundled. There is no in-app screen that shows the bundled
notice files. §3(a)(2) allows "any reasonable manner based on the medium";
⚠ decide whether a licences screen is needed before public release (not
added in this stage, by scope).

**Other licences in the APK:** SCOWL word list (permissive, notice required),
Plus Jakarta Sans (SIL OFL 1.1, bundled). The RLA-ES Hunspell dictionary (MPL 1.1)
was used only for build-time validation and is not shipped; the validated
Spanish words themselves come from wordfreq.

