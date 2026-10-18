package com.nuancun.jmcomic.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneActivityTest {
    @Test fun endingOneOverlappingActivityDoesNotResumeEffectsEarly() {
        val activity = SceneActivity()
        val endScroll = activity.pause()
        val endNavigation = activity.pause()
        assertEquals(2, activity.busyCount)
        endScroll()
        assertEquals(1, activity.busyCount)
        endNavigation()
        assertEquals(0, activity.busyCount)
    }

    @Test fun repeatedCleanupIsIdempotent() {
        val activity = SceneActivity()
        val release = activity.pause()
        repeat(3) { release() }
        assertEquals(0, activity.busyCount)
        activity.pause()
        release()
        assertEquals(1, activity.busyCount)
    }
}
