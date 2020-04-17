package com.agillic.app.sdk

import com.snowplowanalytics.snowplow.tracker.Tracker
import com.snowplowanalytics.snowplow.tracker.events.ScreenView

class AgillicTrackerImpl(private val tracker: Tracker) :
    AgillicTracker {
    private var disabled = false

    override fun track(event: Event) {
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