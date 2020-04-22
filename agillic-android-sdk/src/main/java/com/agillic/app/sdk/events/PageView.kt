package com.agillic.app.sdk.events

import com.snowplowanalytics.snowplow.tracker.events.AbstractEvent

class PageView : CommonEvent() {
    var url: String? = null
    var referer: String? = null
    fun url(url: String?): PageView {
        this.url = url
        return this
    }

    fun referer(referer: String?): PageView {
        this.referer = referer
        return this
    }

    override fun createSnowplowEvent() : AbstractEvent {
        return com.snowplowanalytics.snowplow.tracker.events.PageView.builder()
            .pageTitle(title)
            .pageUrl(url)
            .referrer(referer)
            .build()

    }
 }