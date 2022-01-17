package com.agillic.app.sdk

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import com.agillic.app.sdk.events.AgillicAppView
import com.snowplowanalytics.snowplow.tracker.DevicePlatforms
import com.snowplowanalytics.snowplow.tracker.Emitter
import com.snowplowanalytics.snowplow.tracker.Subject
import com.snowplowanalytics.snowplow.tracker.Tracker
import com.snowplowanalytics.snowplow.tracker.emitter.BufferOption
import com.snowplowanalytics.snowplow.tracker.emitter.HttpMethod
import com.snowplowanalytics.snowplow.tracker.emitter.RequestCallback
import com.snowplowanalytics.snowplow.tracker.emitter.RequestSecurity
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson
import com.snowplowanalytics.snowplow.tracker.utils.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.coroutines.suspendCoroutine


object Agillic {
    private var agillicTracker: AgillicTrackerImpl? = null
    private var url: String
    private val requestSecurity = RequestSecurity.HTTPS
    private var collectorEndpoint = "snowplowtrack-eu1.agillic.net"
    private val service: ExecutorService? = null
    private var auth: BasicAuth? = null
    private const val apiUrlFormat = "https://api%s-eu1.agillic.net"
    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private var solutionId: String? = null

    init {
        url = String.format(apiUrlFormat, "")
    }

    fun configure(apiKey: String, apiSecret: String, solutionId: String) {
        Agillic.solutionId = solutionId
        auth = getAuth(apiKey, apiSecret)
    }

    fun pauseTracking() {
        if (agillicTracker == null) {
            throw java.lang.RuntimeException("Agillic.register() must be called before Agillic.pauseTracking()")
        } else {
            agillicTracker?.pauseTracking()
        }
    }

    fun resumeTracking() {
        if (agillicTracker == null) {
            throw java.lang.RuntimeException("Agillic.register() must be called before Agillic.resumeTracking()")
        } else {
            agillicTracker?.resumeTracking()
        }
    }

    private fun shutdown() {
        service?.shutdownNow()
    }

    fun track(event: AgillicAppView) {
        if (agillicTracker == null) {
            throw java.lang.RuntimeException("com.agillic.app.sdk.Agillic.register() must be called before com.agillic.app.sdk.Agillic.track()")
        }
        agillicTracker?.track(event)
    }

    /** Handles push notification opened - user action for alert notifications, delivery into app. This method will parse the data and track it **/
    fun handlePushNotificationOpened(userInfo: Any) {
        val agillicPushId = getAgillicPushId(userInfo)
        if (agillicPushId == null) {
            Logger.getLogger(this.javaClass.name).warning("Skipping non-Agillic notification")
        } else {
            val pushEvent =
                AgillicAppView(screenName = "pushOpened://agillic_push_id=$agillicPushId")
            track(pushEvent)
        }
    }

    private fun getAgillicPushId(userInfo: Any): String? {
        return "" //todo
    }

    fun register(
        recipientId: String,
        activity: Activity,
        pushNotificationToken: String? = null,
        registerCallback:Callback? = null,
        trackingCallback:Callback? = null
    ) {
        // Register app with SDK and return a Tracker
        if (auth == null || solutionId == null) {
            throw java.lang.RuntimeException("com.agillic.app.sdk.Agillic.configure() must be called before com.agillic.app.sdk.Agillic.Register()")
        }
        val emitter: Emitter = createEmitter(collectorEndpoint, activity, trackingCallback)
        val subject: Subject = Subject.SubjectBuilder().build()
        subject.setUserId(recipientId)
        val tracker = createSnowPlowTracker(
            emitter,
            subject,
            "agillic",
            solutionId,
            activity
        )
        val deviceWidth: Int
        val deviceHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            deviceWidth = windowMetrics.bounds.width() - insets.left - insets.right
            deviceHeight = windowMetrics.bounds.height() - insets.top - insets.bottom
        } else {
            val outMetrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(outMetrics)
            deviceWidth = outMetrics.widthPixels
            deviceHeight = outMetrics.heightPixels
        }

        val deviceInfo = Util.getMobileContext(activity)
        val clientAppVersion: String = try {
            activity.packageManager
                .getPackageInfo(activity.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "NA"
        }
        ioScope.launch {
            register(
                tracker,
                activity.packageName,
                clientAppVersion,
                recipientId,
                auth,
                pushNotificationToken,
                deviceInfo,
                deviceWidth,
                deviceHeight,
                url,
                callback = registerCallback
            )
        }
        agillicTracker = AgillicTrackerImpl(tracker)
    }

    private suspend fun register(
        tracker: Tracker,
        clientAppId: String?,
        clientAppVersion: String?,
        userId: String,
        auth: BasicAuth?,
        appToken: String?,
        deviceInfo: SelfDescribingJson,
        deviceWidth: Int?,
        deviceHeight: Int?,
        vararg urls: String,
        callback:Callback?
    ) {
        suspendCoroutine<String> { continuation ->
            while (!tracker.session.waitForSessionFileLoad()) {
                Logger.getLogger(this.javaClass.name).warning("Session still not loaded")
            }
            try {
                urls.forEachIndexed { _, url ->
                    val requestUrl = "$url/register/$userId"
                    val client = createHttpClient()
                    val request = Request.Builder().url(requestUrl)
                        .addHeader("Authorization", auth!!.getAuth()).put(object : RequestBody() {
                            override fun contentType(): MediaType {
                                return "application/json".toMediaType()
                            }

                            @Throws(IOException::class)
                            override fun writeTo(sink: BufferedSink) {
                                val deviceInfoData = deviceInfo.map["data"] as Map<String, String>
                                val deviceModel = deviceInfoData["deviceModel"]
                                val json = String.format(
                                    "{\n" +
                                            "  \"appInstallationId\" : \"%s\",\n" +
                                            "  \"clientAppId\": %s ,\n" +
                                            "  \"clientAppVersion\": %s,\n" +
                                            "  \"osName\": \"%s\" ,\n" +
                                            "  \"osVersion\": %s ,\n" +
                                            "  \"deviceModel\": %s,\n" +
                                            "  \"pushNotificationToken\": %s,\n" +
                                            "  \"modelDimX\": %d,\n" +
                                            "  \"modelDimY\": %d\n" +
                                            "}\n",
                                    tracker.session.userId,
                                    emptyIfNull(clientAppId),
                                    emptyIfNull(clientAppVersion),
                                    Util.getOsType(),
                                    emptyIfNull(Util.getOsVersion()),
                                    emptyIfNull(deviceModel),
                                    emptyIfNull(appToken),
                                    deviceWidth,
                                    deviceHeight
                                )
                                Log.d("register", "$requestUrl : $json")
                                sink.write(json.toByteArray())
                            }
                        }).build()
                    var retries = 3
                    val sleep = 5000
                    while (retries-- > 0) {
                        try {
                            try {
                                val response = client.newCall(request).execute()
                                Log.i("register", "register: " + response.code + " ")
                                if (response.isSuccessful) {
                                    continuation.resumeWith(Result.success("Registration successful"))
                                    callback?.success("${response.code}: ${response.body.string()}")
                                } else {
                                    callback?.failed("${response.code}: ${response.body.string()}")
                                }
                                if (response.code >= 300) {
                                    val msg =
                                        "Client error: " + response.code + " " + response.body
                                            .toString()
                                    Log.e("register", "doInBackground: $msg")
                                    continuation.resumeWith(Result.failure(Exception(msg)))
                                }
                            } catch (ignored: IOException) {
                            }
                            Thread.sleep(sleep.toLong())
                        } catch (ignored: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("register", "Failed to run registration: " + ex.message)
            }
            Log.d("register", "Stopping registration.")
        }
    }

    private fun getAuth(key: String, secret: String): BasicAuth {
        return BasicAuth(key, secret)
    }

    class BasicAuth(key: String, secret: String) {
        private var basicAuth: String =
            "Basic " + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString("$key:$secret".toByteArray())
            } else {
                android.util.Base64.encodeToString(
                    "$key:$secret".toByteArray(),
                    android.util.Base64.NO_WRAP
                );
            }

        fun getAuth(): String {
            return basicAuth
        }
    }

    private fun emptyIfNull(string: String?): String? {
        if (string != null) {
            return "\"" + string + "\"";
        }
        return string
    }

    private fun createHttpClient(): OkHttpClient { // use OkHttpClient to send events
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun createEmitter(url: String?, context: Context?, trackingCallback: Callback?): Emitter {
        /** Responsible for all the storage, networking and scheduling required to ensure events are sent to a collector.
        Details like the collector endpoint and sending timeout lengths are set here. **/
        return Emitter.EmitterBuilder(url, context)
            .method(HttpMethod.GET)
            .security(requestSecurity)
            .callback(object : RequestCallback {
                // let us know on successes (may be called multiple times)
                override fun onSuccess(successCount: Int) {
                    Log.d("AgillicSDK:emitter", "Successfully sent $successCount events")
                    trackingCallback?.success("Successfully sent $successCount events")
                }

                // let us know if something has gone wrong (may be called multiple times)
                override fun onFailure(
                    successCount: Int,
                    failedCount: Int
                ) {
                    Log.e(
                        "AgillicSDK:emitter",
                        "Successfully sent $successCount events; failed to send $failedCount events"
                    )
                    trackingCallback?.failed("Successfully sent $successCount events; failed to send $failedCount events")
                }
            })
            .option(BufferOption.Single)
            .build()
    }

    private fun createSnowPlowTracker(
        emitter: Emitter?,
        subject: Subject?,
        namespace: String?,
        appId: String?,
        context: Context?
    ): Tracker { // now we have the emitter, we need a tracker to turn our events into something a Snowplow collector can understand
        return Tracker.TrackerBuilder(emitter, namespace, appId, context)
            .subject(subject)
            .base64(true) //
            .sessionContext(true)
            .platform(DevicePlatforms.Mobile)
            .mobileContext(true)
            .geoLocationContext(true)
            .build()
    }
}