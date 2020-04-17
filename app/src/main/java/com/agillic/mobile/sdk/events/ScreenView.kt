package com.agillic.app.sdk

import com.snowplowanalytics.snowplow.tracker.events.AbstractEvent
import com.snowplowanalytics.snowplow.tracker.events.ScreenView

class ScreenView : CommonEvent() {

    override fun createSnowplowEvent(): AbstractEvent {
        return ScreenView.builder()
            .id(title)
            .eventId(event)
            .name(name)
            .build()


    }
}