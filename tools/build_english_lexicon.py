#!/usr/bin/env python3
"""Builds app/src/main/assets/lexicon/en_words.tsv -- the bundled English lexicon.

Sources (all permissively licensed, see DATABASE_AND_DICTIONARY_INVENTORY.md):
  * SCOWL word lists (Kevin Atkinson), via the `wordlist-english` npm package
    (english-words-NN.json + american-words-NN.json). SCOWL size levels are a
    commonness tier: 10 = most common ... 50 = standard dictionary words.
  * KeyLingo's own hand-curated frequency list (tools/data/en_curated_ranks.tsv),
    which provides the precise ranks for the ~1,800 most important words.
  * The supplement below: contractions, words that are always capitalised,
    and modern/texting vocabulary missing from SCOWL.

Output columns: word <TAB> rank <TAB> flags
  rank  -- lower = more common. Curated words keep their curated rank; other
           words are ranked by SCOWL tier, then by length (shorter words are
           more frequent on average -- Zipf's law of abbreviation), then
           alphabetically, so the order is deterministic.
  flags -- "n" = known word, never proactively suggested (profanity, very
           rare dictionary words). Known words are still never "autocorrected".

Usage:
  python3 tools/build_english_lexicon.py <path-to-wordlist-english/package> \
      tools/data/en_curated_ranks.tsv app/src/main/assets/lexicon/en_words.tsv
"""
import json
import re
import sys

SUGGEST_LEVELS = (10, 20, 35)
KNOWN_ONLY_LEVELS = (40, 50)

CONTRACTIONS = [
    "I'm", "I've", "I'll", "I'd", "you're", "you've", "you'll", "you'd", "he's", "he'll", "he'd",
    "she's", "she'll", "she'd", "it's", "it'll", "we're", "we've", "we'll", "we'd", "they're",
    "they've", "they'll", "they'd", "that's", "that'll", "there's", "here's", "what's", "who's",
    "where's", "when's", "how's", "let's", "don't", "doesn't", "didn't", "can't", "couldn't",
    "won't", "wouldn't", "shouldn't", "isn't", "aren't", "wasn't", "weren't", "haven't", "hasn't",
    "hadn't", "mustn't", "needn't", "y'all", "ma'am", "o'clock",
]
CAPITALISED = [
    # Month/name words that are also common lowercase words (may, march, august, god, ok)
    # are deliberately NOT listed: forcing a capital on them would corrupt normal typing.
    "I", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    "January", "February", "April", "June", "July", "September",
    "October", "November", "December", "English", "Spanish", "French", "German", "Italian",
    "Portuguese", "Chinese", "Japanese", "American", "Mexican", "British", "Canadian", "European",
    "Latin", "America", "Mexico", "Spain", "Canada", "England", "Europe", "Christmas", "Easter",
    "Halloween", "Thanksgiving", "Mr", "Mrs", "Ms", "Dr", "TV", "USA", "UK",
]
MODERN = [
    "ok", "okay", "app", "apps", "wifi", "online", "offline", "email", "emails", "emailed",
    "website", "websites", "internet", "selfie", "selfies", "smartphone", "laptop", "laptops",
    "login", "logout", "username", "password", "passwords", "download", "downloads", "upload",
    "uploads", "update", "updates", "text", "texted", "texting", "texts", "lol", "omg", "btw",
    "thx", "pls", "gonna", "wanna", "gotta", "yeah", "yep", "nope", "hi", "hey", "hello", "bye",
    "awesome", "cool", "weekend", "weekends", "podcast", "podcasts", "streaming", "emoji",
    "emojis", "hashtag", "playlist", "covid", "vaccine", "zoom", "google", "googled",
    "screenshot", "screenshots", "chatbot", "blog", "blogs", "vlog", "spam", "inbox",
]
# Recognised (so they are never "corrected"), but never offered proactively.
NO_SUGGEST = {
    "fuck", "fucking", "fucked", "fucker", "shit", "shitty", "bitch", "bitches", "bastard",
    "asshole", "dick", "cock", "cunt", "pussy", "whore", "slut", "nigger", "nigga", "faggot",
    "fag", "retard", "retarded", "damn", "crap", "piss", "pissed", "tits", "twat", "wanker",
}

# Real but archaic words that are almost always a missing-apostrophe typo in practice
# ("cant" -> "can't"). Kept as known words, but ranked last and never suggested.
DEMOTE = {"cant", "wont"}

WORD_RE = re.compile(r"^[A-Za-z][A-Za-z']*$")


def load_levels(package_dir, levels):
    words = []
    for level in levels:
        for variant in ("english", "american"):
            path = f"{package_dir}/{variant}-words-{level}.json"
            words.extend((w, level) for w in json.load(open(path, encoding="utf-8")))
    return words


def main(package_dir, curated_path, out_path):
    entries = {}  # lowercase key -> (display form, rank, flags)

    curated = []
    for line in open(curated_path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        word, rank = line.rstrip("\n").split("\t")[:2]
        curated.append((word, int(rank)))
    for word, rank in curated:
        entries[word.lower()] = (word, rank, "")
    next_rank = max(r for _, r in curated) + 1

    # Contractions and capitalised words are very frequent in real typing;
    # give them explicit early ranks rather than burying them in a tier.
    for i, word in enumerate(CONTRACTIONS):
        key = word.lower()
        rank = 12 + i * 3 if key in ("i'm", "don't", "it's", "i'll", "can't", "that's") else 60 + i * 9
        entries[key] = (word, min(rank, entries.get(key, (None, 10**9))[1]), "")
    for i, word in enumerate(CAPITALISED):
        key = word.lower()
        prev = entries.get(key)
        rank = prev[1] if prev else 300 + i * 7
        entries[key] = (word, rank, "")
    for word in MODERN:
        entries.setdefault(word.lower(), (word, None, ""))

    tiered = sorted(
        load_levels(package_dir, SUGGEST_LEVELS) + load_levels(package_dir, KNOWN_ONLY_LEVELS),
        key=lambda wl: (wl[1], len(wl[0]), wl[0]),
    )
    for word, level in tiered:
        if not WORD_RE.match(word) or "'" in word:
            continue
        if len(word) == 1 and word not in ("a",):
            continue
        key = word.lower()
        if key in entries and entries[key][1] is not None:
            continue
        flags = "n" if level in KNOWN_ONLY_LEVELS else ""
        display = entries[key][0] if key in entries else word
        entries[key] = (display, next_rank, flags)
        next_rank += 1

    for key, (display, rank, flags) in list(entries.items()):
        if rank is None:  # modern words not present anywhere else
            entries[key] = (display, next_rank, "")
            next_rank += 1
        if key in NO_SUGGEST:
            entries[key] = (entries[key][0], entries[key][1], "n")

    for key in DEMOTE:
        if key in entries:
            entries[key] = (entries[key][0], next_rank, "n")
            next_rank += 1

    rows = sorted(entries.values(), key=lambda e: (e[1], e[0]))
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("# KeyLingo English lexicon. Generated by tools/build_english_lexicon.py -- do not edit by hand.\n")
        out.write("# Contains SCOWL data: Copyright 2000-2016 by Kevin Atkinson (permissive licence, see\n")
        out.write("# DATABASE_AND_DICTIONARY_INVENTORY.md). columns: word, rank (lower = more common), flags (n = never suggest)\n")
        for display, rank, flags in rows:
            out.write(f"{display}\t{rank}\t{flags}\n")
    print(f"wrote {len(rows)} words to {out_path}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
