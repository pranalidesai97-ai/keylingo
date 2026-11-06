package com.keylingo.app.domain.model

/**
 * Common European Framework of Reference difficulty band for a dictionary
 * entry. Not used to change what gets shown yet -- it exists so
 * [LearningMode] and future features (progressive vocabulary unlocks,
 * difficulty-aware review) have a real field to read instead of requiring
 * another schema pass later.
 */
enum class CefrLevel {
    A1, A2, B1, B2, C1;

    companion object {
        /** Unknown/missing values default to B1 -- a neutral middle rather than silently skewing easy or hard. */
        fun fromCode(code: String?): CefrLevel {
            if (code.isNullOrBlank()) return B1
            return runCatching { valueOf(code.trim().uppercase()) }.getOrDefault(B1)
        }
    }
}
