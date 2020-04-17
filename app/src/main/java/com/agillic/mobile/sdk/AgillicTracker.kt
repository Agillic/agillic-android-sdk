package com.agillic.app.sdk

interface AgillicTracker {
    fun track(event: Event)
    fun pauseTracking()
    fun resumeTracking()
}