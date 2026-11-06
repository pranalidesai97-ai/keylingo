# Room, Hilt, DataStore, Navigation and Compose ship their own consumer rules,
# so no blanket -keep rules are needed (a previous "-keep class androidx.compose.**"
# disabled shrinking entirely and kept every Material icon: 18 MB of dex).
# Bundled language data is read from assets by path; nothing is accessed reflectively.
