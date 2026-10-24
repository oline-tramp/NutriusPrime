package com.example.kthealthsync

import com.example.kthealthsync.domain.util.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `newer minor version returns true`() {
        assertTrue(VersionComparator.isNewer("2.38", "v2.39"))
        assertTrue(VersionComparator.isNewer("v2.38", "2.39"))
    }

    @Test
    fun `same version returns false`() {
        assertFalse(VersionComparator.isNewer("2.38", "2.38"))
        assertFalse(VersionComparator.isNewer("2.38", "v2.38"))
        assertFalse(VersionComparator.isNewer("v2.38", "2.38"))
    }

    @Test
    fun `older version returns false`() {
        assertFalse(VersionComparator.isNewer("2.39", "2.38"))
        assertFalse(VersionComparator.isNewer("2.39", "v2.38"))
    }

    @Test
    fun `patch version comparison works`() {
        assertTrue(VersionComparator.isNewer("2.38", "2.38.1"))
        assertFalse(VersionComparator.isNewer("2.38.1", "2.38"))
        assertTrue(VersionComparator.isNewer("2.38.1", "2.38.2"))
    }

    @Test
    fun `major version comparison works`() {
        assertTrue(VersionComparator.isNewer("2.99", "3.0.0"))
        assertFalse(VersionComparator.isNewer("3.0.0", "2.99"))
    }

    @Test
    fun `invalid version string returns false safely`() {
        assertFalse(VersionComparator.isNewer("2.38", "invalid"))
        assertFalse(VersionComparator.isNewer("", "v2.39"))
    }

    @Test
    fun `formatDisplayVersion formats correctly`() {
        assertEquals("v2.39", VersionComparator.formatDisplayVersion("2.39"))
        assertEquals("v2.39", VersionComparator.formatDisplayVersion("v2.39"))
        assertEquals("v2.39", VersionComparator.formatDisplayVersion("V2.39"))
    }
}
