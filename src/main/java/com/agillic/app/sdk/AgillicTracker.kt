package com.agillic.app.sdk
import com.agillic.app.sdk.events.Event

interface AgillicTracker {
    fun track(event: Event)
    fun pauseTracking()
    fun resumeTracking()
}
