#!/usr/bin/env python3
"""
Reorders a bundled lexicon TSV (word, rank, flags) by the keyboard's match key,
so Lexicon's one-time sort at startup sees already-sorted input (TimSort: one
linear pass instead of ~2M string comparisons). The rank column is unchanged,
so suggestions are identical. Header lines stay first. Must mirror
Lexicon.matchKey: lowercase, apostrophes removed, and for Spanish accents and
ñ folded (SpanishText.foldIncludingEnye); ties by rank.
Usage: python3 -I tools/sort_lexicon_by_key.py <tsv> en|es
Run after build_english_lexicon.py + rerank_english_by_frequency.py (en) or
build_spanish_lexicon.py (es).
"""
import sys
import unicodedata


def fold(text):
    lower = unicodedata.normalize("NFC", text.lower())
    out = []
    for ch in lower:
        if ch == "ñ" or ord(ch) < 0x80:
            out.append(ch)
        else:
            out.append("".join(c for c in unicodedata.normalize("NFD", ch) if not unicodedata.combining(c)))
    return "".join(out).replace("ñ", "n")


def main():
    path, lang = sys.argv[1], sys.argv[2]
    header, rows = [], []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("#"):
            header.append(line)
        elif line:
            rows.append(line)
    def key(row):
        word, rank = row.split("\t")[:2]
        k = word.lower().replace("'", "").replace("’", "")
        if lang == "es":
            k = fold(k)
        return (k.encode("utf-16-be"), int(rank))  # UTF-16 order == Kotlin String.compareTo
    rows.sort(key=key)
    note = "# Rows are in match-key order (tools/sort_lexicon_by_key.py) so loading needs no full sort; the rank column is the frequency order."
    with open(path, "w", encoding="utf-8") as out:
        for h in header:
            if "match-key order" not in h:
                out.write(h + "\n")
        out.write(note + "\n")
        for r in rows:
            out.write(r + "\n")
    print(f"{len(rows)} rows sorted by match key")


if __name__ == "__main__":
    main()
