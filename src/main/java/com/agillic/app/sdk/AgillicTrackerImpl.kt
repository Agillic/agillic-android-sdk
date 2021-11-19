package com.agillic.app.sdk
import com.snowplowanalytics.snowplow.tracker.Tracker

class AgillicTrackerImpl(private val tracker: Tracker) :
    AgillicTracker {
    private var disabled = false

    override fun track(event: com.agillic.app.sdk.events.Event) {
        if (disabled)
            return
        tracker.track(event.createSnowplowEvent());
    }

    override fun pauseTracking() {
        disabled = true
    }

    override fun resumeTracking() {
        disabled = false
    }
}