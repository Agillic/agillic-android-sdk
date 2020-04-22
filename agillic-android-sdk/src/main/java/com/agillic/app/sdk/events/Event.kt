package com.agillic.app.sdk.events

import com.snowplowanalytics.snowplow.tracker.events.AbstractEvent

interface Event {

    fun createSnowplowEvent(): AbstractEvent
}