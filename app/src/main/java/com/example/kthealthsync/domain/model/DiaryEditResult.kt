package com.example.kthealthsync.domain.model

enum class DiaryEditResult {
    SUCCESS,
    ADD_FAILED,
    // A replacement exists; retrying the whole edit would add it again.
    DELETE_FAILED
}
