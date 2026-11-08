#!/usr/bin/env python3
"""
Re-ranks app/src/main/assets/lexicon/en_words.tsv by real usage frequency
(wordfreq 3.1.1 "large" English list, CC BY-SA 4.0). Run after
build_english_lexicon.py. The word list itself (SCOWL) is unchanged; only the
rank column changes:
  * words wordfreq knows are ordered by wordfreq frequency, except that a word in
    KeyLingo's curated conversational list (tools/data/en_curated_ranks.tsv) takes
    the better of its curated rank and its wordfreq position: typed chat ("hello",
    "thanks", "I") is more conversational than wordfreq's mostly written sources;
  * words it does not know follow, in their previous (SCOWL-tier) order;
  * misspelled forms (BOGUS) are dropped so autocorrect can fix them;
  * wordfreq is case-folded, so "john", "tom", "york" count every "John", "Tom",
    "York". A lowercase word whose capitalised form is in Hunspell en_US and which
    SCOWL rates uncommon (tier 40 or above: old rank >= SCOWL_UNCOMMON) is not promoted
    -- it keeps its previous position -- unless it is in LOWERCASE_OK, reviewed
    everyday lowercase words ("nah", "ex", "spam");
  * crude words and slurs (CRUDE) are flagged n: recognised, never suggested;
  * deliberately demoted words (cant, wont: usually meant as can't, won't)
    keep their late rank; flags (n = never suggest) are untouched.
Usage: python3 -I tools/rerank_english_by_frequency.py <wordfreq data dir> <en_curated_ranks.tsv> \
         /usr/share/hunspell/en_US.dic <en_words.tsv>
Run it on the output of build_english_lexicon.py (SCOWL order); it refuses an already re-ranked file.
"""
import gzip, re, sys
import msgpack

DEMOTE = {"cant", "wont"}
BOGUS = {"agreeed", "disagreeed", "understanded"}
SCOWL_UNCOMMON = 39158  # first rank of SCOWL tier 40 in the SCOWL-ordered list
LOWERCASE_OK = set("""
nah mid ex et rep spam google euro arctic congressional prof mic ops lib dis apocalypse gothic alpine mister
tertiary logos grail diaspora secretariat excellency highness nemesis nirvana utopia lordship cheddar
margarita sprite epiphany tarot campfire perm untouchable tarmac braille chardonnay cabernet lifesaver
jacuzzi mayday godspeed zeitgeist styrofoam motherland neolithic romanticism modernism protectorate
regency triad platonic stoic messiah odyssey titanic viking mogul cupid pantheon everyman antarctic
riverside constable reverend mafia rover jeep belle mandarin mesa coco natal martian monsieur
mademoiselle chimera harlequin zephyr nautilus calypso adrenalin credo padre esquire lynx hertz
ester bantam pygmy herder bur bund winkle wiles redeemer puritan creole sprite midland grange
draper dyer glover hooper brewer barker carver forester mailer popper dipper spender
""".split())
# Crude words and slurs: recognised (never "corrected"), never offered. Matched on the lowercase word.
CRUDE = re.compile(r"^(?:\w*fuck\w*|\w*shit(?:s|ty|ting|ted|head|heads|load|hole|holes|face)?|bullshit\w*|horseshit|"
                   r"cunts?|nigg(?:a|as|er|ers|ah|az)|fags?|faggots?|bitch(?:es|ing|y|ed)?|son of a bitch|whores?|sluts?|slutty|"
                   r"assholes?|arseholes?|dickheads?|dicks|cocks|cocksucker\w*|pussies|twats?|wank\w*|retards?|bastards|"
                   r"porn|porno|pornos|boobs?|boobies|milfs?|blowjobs?|dildos?|horny|goddamn(?:ed|it)?|bollocks|pissing|pisses|"
                   r"homo|negro|negroes)$")


def main():
    data_dir, curated_path, hunspell_dic, path = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    capitalised = set()
    for line in open(hunspell_dic, encoding="utf-8", errors="ignore"):
        w = line.split("/")[0].strip()
        if w[:1].isupper():
            capitalised.add(w)
    curated = {}
    for line in open(curated_path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        w, r = line.rstrip("\n").split("\t")[:2]
        curated.setdefault(w.lower().replace("’", "'"), int(r))
    with gzip.open(f"{data_dir}/large_en.msgpack.gz", "rb") as f:
        buckets = msgpack.load(f, raw=False)[1:]
    order = {}
    for bucket in buckets:
        for w in bucket:
            order.setdefault(w, len(order))
    header, rows = [], []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("#"):
            if "wordfreq" in line:
                sys.exit("already re-ranked: rebuild with build_english_lexicon.py first")
            header.append(line); continue
        if not line:
            continue
        word, rank, *rest = line.split("\t")
        if word.lower() in BOGUS:
            continue
        flags = rest[0] if rest else ""
        if CRUDE.match(word.lower()) and "n" not in flags:
            flags += "n"
        rows.append((word, int(rank), flags))
    def key(row):
        word, old, _ = row
        k = word.lower().replace("’", "'")
        name_like = word.islower() and old >= SCOWL_UNCOMMON and word.capitalize() in capitalised and k not in LOWERCASE_OK
        if k in DEMOTE or k not in order or name_like:
            return (1, old, 0)
        return (0, min(order[k], curated.get(k, order[k])), order[k])
    rows.sort(key=key)
    with open(path, "w", encoding="utf-8") as out:
        for h in header:
            out.write(h + "\n")
        out.write("# Ranks: wordfreq 3.1.1 frequency order (Robyn Speer, CC BY-SA 4.0), curated conversational ranks taking\n# precedence where better; words wordfreq lacks follow in SCOWL order.\n")
        out.write("# The rank column is therefore CC BY-SA 4.0, see assets/licenses/wordfreq-CC-BY-SA-4.0.txt.\n")
        for i, (word, _, flags) in enumerate(rows, start=1):
            out.write(f"{word}\t{i}\t{flags}\n")
    known = sum(1 for r in rows if key(r)[0] == 0)
    print(f"{len(rows)} words re-ranked; {known} ranked by wordfreq, {len(rows) - known} after")


if __name__ == "__main__":
    main()
