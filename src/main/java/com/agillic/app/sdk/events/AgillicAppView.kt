package com.agillic.app.sdk.events

import com.snowplowanalytics.snowplow.tracker.events.AbstractEvent
import java.util.*

class AgillicAppView(private val screenName: String) : CommonEvent() {
    override fun createSnowplowEvent(): AbstractEvent {
        return com.snowplowanalytics.snowplow.tracker.events.ScreenView.builder()
            .id(UUID.randomUUID().toString())
            .name(screenName)
            .build()
    }
}