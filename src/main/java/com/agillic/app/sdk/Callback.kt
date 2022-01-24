package com.agillic.app.sdk

interface Callback {
    fun info(response:String)
    fun success(response:String)
    fun failed(response:String)
}