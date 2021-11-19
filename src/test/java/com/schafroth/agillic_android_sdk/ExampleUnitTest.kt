package com.schafroth.agillic_android_sdk

import com.agillic.app.sdk.AgillicSDK
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testSetDev() {
        var sdk = AgillicSDK.instance
        sdk.setDevApi()
        sdk.setApi("https://example.com/")
        sdk.setTestApi()
        sdk.setCollector("https://example.com/collector")


    }

}
