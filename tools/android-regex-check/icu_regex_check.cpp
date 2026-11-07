// Compiles and runs regular expressions with ICU4C -- the engine behind
// java.util.regex on Android (libcore calls icu::RegexPattern::compile).
// A desktop JVM accepts some syntax that Android rejects at runtime, e.g.
// the inline (?U) flag that crashed KeyLingoInputMethodService.
//
// Input: UTF-8 TSV lines produced by AndroidRegexCompatibilityTest
//   COMPILE <flags> <pattern>
//   FIND    <flags> <pattern> <text> <expected 0|1>
// flags: "" or "i" (case-insensitive, as Kotlin RegexOption.IGNORE_CASE).
// Exit code 0 only if every pattern compiles and every FIND matches the JVM result.
#include <unicode/regex.h>
#include <unicode/unistr.h>
#include <unicode/utypes.h>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

static std::vector<std::string> split(const std::string& s) {
    std::vector<std::string> out; std::string cur; std::istringstream in(s);
    while (std::getline(in, cur, '\t')) out.push_back(cur);
    if (!s.empty() && s.back() == '\t') out.push_back("");
    return out;
}

int main(int argc, char** argv) {
    if (argc != 2) { std::cerr << "usage: icu_regex_check <cases.tsv>\n"; return 2; }
    std::ifstream f(argv[1]);
    std::string line; int compiled = 0, finds = 0, failures = 0, lineNo = 0;
    while (std::getline(f, line)) {
        ++lineNo;
        if (line.empty() || line[0] == '#') continue;
        auto c = split(line);
        if (c.size() < 3) { std::cerr << "line " << lineNo << ": malformed\n"; ++failures; continue; }
        uint32_t flags = c[1].find('i') != std::string::npos ? UREGEX_CASE_INSENSITIVE : 0;
        UErrorCode status = U_ZERO_ERROR; UParseError pe;
        icu::RegexPattern* p = icu::RegexPattern::compile(icu::UnicodeString::fromUTF8(c[2]), flags, pe, status);
        if (U_FAILURE(status)) {
            std::cout << "FAIL compile: " << u_errorName(status) << " at offset " << pe.offset << ": " << c[2] << "\n";
            ++failures; delete p; continue;
        }
        ++compiled;
        if (c[0] == "FIND" && c.size() >= 5) {
            icu::UnicodeString text = icu::UnicodeString::fromUTF8(c[3]);
            icu::RegexMatcher* m = p->matcher(text, status);
            bool found = U_SUCCESS(status) && m->find(status);
            bool expected = c[4] == "1";
            ++finds;
            if (found != expected) {
                std::cout << "FAIL find: ICU=" << found << " JVM=" << expected << " pattern=" << c[2] << " text=" << c[3] << "\n";
                ++failures;
            }
            delete m;
        }
        delete p;
    }
    std::cout << "ICU " << U_ICU_VERSION << ": " << compiled << " patterns compiled, " << finds
              << " find checks, " << failures << " failures\n";
    return failures == 0 ? 0 : 1;
}
