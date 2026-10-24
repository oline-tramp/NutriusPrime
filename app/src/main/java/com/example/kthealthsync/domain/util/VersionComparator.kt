package com.example.kthealthsync.domain.util

object VersionComparator {
    /**
     * Returns true if remoteVersion is strictly newer than currentVersion.
     * Strips leading "v" or "V" and compares numeric segments dot-by-dot.
     * e.g. "2.39" > "2.38", "2.38.1" > "2.38", "3.0" > "2.39".
     */
    fun isNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentClean = currentVersion.trim().removePrefix("v").removePrefix("V")
        val remoteClean = remoteVersion.trim().removePrefix("v").removePrefix("V")

        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }

        if (currentParts.isEmpty() || remoteParts.isEmpty()) {
            return false
        }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) return true
            if (rem < curr) return false
        }
        return false
    }

    /**
     * Normalizes a tag or version string to display format (e.g. "v2.39").
     */
    fun formatDisplayVersion(versionOrTag: String): String {
        val clean = versionOrTag.trim().removePrefix("v").removePrefix("V")
        return "v$clean"
    }
}
