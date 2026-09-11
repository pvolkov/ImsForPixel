package com.pvolkov.imsforpixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotStatusLogicTest {

    @Test
    fun imsUnknownWhenFileMissing() {
        val state = SlotStatusLogic.imsState(
            fileLastModified = null,
            content = null,
            bootTimeMillis = 1_000L,
        )
        assertEquals(SlotStatus.ImsState.Unknown, state)
    }

    @Test
    fun imsUnknownWhenFileOlderThanBoot() {
        val state = SlotStatusLogic.imsState(
            fileLastModified = 500L,
            content = "true",
            bootTimeMillis = 1_000L,
        )
        assertEquals(SlotStatus.ImsState.Unknown, state)
    }

    @Test
    fun imsRegisteredWhenFileWrittenAfterBoot() {
        val state = SlotStatusLogic.imsState(
            fileLastModified = 2_000L,
            content = "true",
            bootTimeMillis = 1_000L,
        )
        assertEquals(SlotStatus.ImsState.Registered, state)
    }

    @Test
    fun imsNotRegisteredWhenFileWrittenAfterBoot() {
        val state = SlotStatusLogic.imsState(
            fileLastModified = 2_000L,
            content = "false",
            bootTimeMillis = 1_000L,
        )
        assertEquals(SlotStatus.ImsState.NotRegistered, state)
    }

    @Test
    fun configAppliedFromSentinelEvenIfFileMissing() {
        val applied = SlotStatusLogic.isConfigApplied(
            liveAvailable = true,
            hasSentinelKey = true,
            sentinelValue = true,
            fileApplied = false,
            liveMatchesPrefs = false,
        )
        assertTrue(applied)
    }

    @Test
    fun configNotAppliedWhenSentinelFalse() {
        val applied = SlotStatusLogic.isConfigApplied(
            liveAvailable = true,
            hasSentinelKey = true,
            sentinelValue = false,
            fileApplied = true,
            liveMatchesPrefs = true,
        )
        assertFalse(applied)
    }

    @Test
    fun configFallsBackToFileWhenLiveConfigUnavailable() {
        val applied = SlotStatusLogic.isConfigApplied(
            liveAvailable = false,
            hasSentinelKey = false,
            sentinelValue = false,
            fileApplied = true,
            liveMatchesPrefs = false,
        )
        assertTrue(applied)
    }

    @Test
    fun configUsesHeuristicWhenNoSentinel() {
        val applied = SlotStatusLogic.isConfigApplied(
            liveAvailable = true,
            hasSentinelKey = false,
            sentinelValue = false,
            fileApplied = true,
            liveMatchesPrefs = true,
        )
        assertTrue(applied)
    }
}
