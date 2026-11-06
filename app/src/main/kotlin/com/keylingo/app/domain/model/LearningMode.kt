package com.keylingo.app.domain.model

/**
 * How much the hint bar shows and how it's tuned. Lives in `domain`
 * (rather than `data/settings`, where it originally started) because
 * [com.keylingo.app.domain.repository.HintProvider] needs to know about it
 * to change analysis behavior -- a domain interface depending on a type
 * defined in `data` would be a layering violation. `SettingsDataStore`
 * persists a value of this type; it doesn't own the concept.
 *
 * This enum only *names* the setting -- what each mode does to hint
 * selection/count lives in [com.keylingo.app.data.hint.LearningModePolicy].
 * Adding a future mode (e.g. Exam or Conversation) means: add a value
 * here, add its title/description strings, add one entry to the Settings
 * screen's option list, and add one branch in [com.keylingo.app.data.hint.LearningModePolicy].
 * No existing mode's code needs to change.
 */
enum class LearningMode { BEGINNER, INTERMEDIATE, ADVANCED }
