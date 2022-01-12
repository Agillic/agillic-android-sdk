package com.agillic.app.sdk

interface Callback {
    fun success(response:String)
    fun failed(response:String)
}