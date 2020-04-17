package com.agillic.app.sdk

import com.snowplowanalytics.snowplow.tracker.events.AbstractEvent

abstract class CommonEvent : Event {
    var name: String? = null
        private set
    var title: String? = null
        private set
    var event: String? = null
        private set

    fun name(name: String?): CommonEvent {
        this.name = name
        return this
    }

    fun title(title: String?): CommonEvent {
        this.title = title
        return this
    }

    fun event(event: String?): CommonEvent {
        this.event = event
        return this
    }
}