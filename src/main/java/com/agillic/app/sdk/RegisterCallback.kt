package com.agillic.app.sdk

interface RegisterCallback {
    fun success(response:String)
    fun failed(response:String)
}