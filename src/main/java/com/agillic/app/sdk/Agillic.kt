package com.agillic.app.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
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
import org.json.JSONObject
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
    private const val apiUrlFormat = "https://api%s-eu1.agillic.net/apps"
    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var registerCallback: Callback? = null
    private var unregisterCallback: Callback? = null
    private var trackingCallback: Callback? = null

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
            trackingCallback?.failed("Agillic.register() must be called before Agillic.pauseTracking()")
            return
        } else {
            agillicTracker?.pauseTracking()
        }
    }

    fun resumeTracking() {
        if (agillicTracker == null) {
            trackingCallback?.failed("Agillic.register() must be called before Agillic.resumeTracking()")
            return
        } else {
            agillicTracker?.resumeTracking()
        }
    }

    private fun shutdown() {
        service?.shutdownNow()
    }

    fun track(
        event: AgillicAppView,
    ) {
        if (agillicTracker == null) {
            trackingCallback?.failed("com.agillic.app.sdk.Agillic.register() must be called before com.agillic.app.sdk.Agillic.track()")
            return
        }
        agillicTracker?.track(event)
    }

    /** Handles push notification opened - user action for alert notifications, delivery into app. This method will parse the data and track it **/
    fun handlePushNotificationOpened(agillicPushPayload: Any, callback: Callback? = null) {
        if (agillicTracker == null) {
            callback?.failed("com.agillic.app.sdk.Agillic.register() must be called before com.agillic.app.sdk.Agillic.handlePushNotificationOpened()")
            return
        } else {
            callback?.info("Handling push notification opened")
        }
        val agillicPushId = getAgillicPushId(agillicPushPayload)
        if (agillicPushId == null) {
            Logger.getLogger(this.javaClass.name).warning("Skipping non-Agillic notification")
            callback?.failed("Agillic push_notification_id not found in payload. Aborting event.")
        } else {
            val pushEvent =
                AgillicAppView(screenName = "pushOpened://agillic_push_id=$agillicPushId")
            track(pushEvent)
            callback?.success("Agillic push_notification_id: $agillicPushId found in payload. Tracking event.")
        }
    }

    private fun getAgillicPushId(agillicPushPayload: Any): String? {
        return when (agillicPushPayload) {
            is String -> {
                //checks for json formatted String payload
                val obj = JSONObject(agillicPushPayload)
                obj.getString("agillic_push_id")
            }
            is Bundle -> {
                //checks for intent extras Bundle payload
                agillicPushPayload.getString("agillic_push_id")
            }
            else -> {
                null
            }
        }
    }

    fun unregister(
        recipientId: String,
        context: Context,
        pushNotificationToken: String? = null,
        unregisterCallback: Callback? = null
    ) {
        this.unregisterCallback = unregisterCallback
        if (sdkConfigured().not()) {
            unregisterCallback?.failed("com.agillic.app.sdk.Agillic.configure() must be called before com.agillic.app.sdk.Agillic.unregister()")
            return
        }
        handleRegistration(
            RegistrationActionEnum.UNREGISTER,
            recipientId,
            context,
            pushNotificationToken
        )
    }

    fun register(
        recipientId: String,
        context: Context,
        pushNotificationToken: String? = null,
        registerCallback: Callback? = null,
        trackingCallback: Callback? = null
    ) {
        this.registerCallback = registerCallback
        this.trackingCallback = trackingCallback
        if (sdkConfigured().not()) {
            registerCallback?.failed("com.agillic.app.sdk.Agillic.configure() must be called before com.agillic.app.sdk.Agillic.register()")
            return
        }
        handleRegistration(
            RegistrationActionEnum.REGISTER,
            recipientId,
            context,
            pushNotificationToken
        )
    }

    private fun handleRegistration(
        registrationAction: RegistrationActionEnum,
        recipientId: String,
        context: Context,
        pushNotificationToken: String?
    ) {
        val sessionUserId = when (registrationAction) {
            RegistrationActionEnum.REGISTER -> {
                val emitter: Emitter =
                    createEmitter(collectorEndpoint, context, trackingCallback)
                val subject: Subject = Subject.SubjectBuilder().build()
                subject.setUserId(recipientId)
                val tracker = createSnowPlowTracker(
                    emitter,
                    subject,
                    "agillic",
                    solutionId,
                    context
                )
                while (tracker.session?.waitForSessionFileLoad() == false) {
                    Logger.getLogger(this.javaClass.name).warning("Session still not loaded")
                }
                agillicTracker = AgillicTrackerImpl(tracker)
                tracker.session.userId
            }
            RegistrationActionEnum.UNREGISTER -> {
                agillicTracker = null
                Tracker.instance().session.userId
            }
        }

        val deviceWidth: Int
        val deviceHeight: Int
        val windowManager: WindowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            deviceWidth = windowMetrics.bounds.width() - insets.left - insets.right
            deviceHeight = windowMetrics.bounds.height() - insets.top - insets.bottom
        } else {
            val outMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(outMetrics)
            deviceWidth = outMetrics.widthPixels
            deviceHeight = outMetrics.heightPixels
        }
        val deviceInfo = Util.getMobileContext(context)
        val clientAppVersion: String = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "NA"
        }
        ioScope.launch {
            handleRegistrationAsync(
                sessionUserId,
                context.packageName,
                clientAppVersion,
                recipientId,
                auth,
                pushNotificationToken,
                deviceInfo,
                deviceWidth,
                deviceHeight,
                registrationAction,
                url
            )
        }
    }

    private fun sdkConfigured() = auth != null && solutionId != null

    private suspend fun handleRegistrationAsync(
        sessionUserId: String,
        clientAppId: String?,
        clientAppVersion: String?,
        userId: String,
        auth: BasicAuth?,
        appToken: String?,
        deviceInfo: SelfDescribingJson,
        deviceWidth: Int?,
        deviceHeight: Int?,
        registrationAction: RegistrationActionEnum,
        vararg urls: String
    ) {
        suspendCoroutine<String> { continuation ->
            val callback: Callback? = when (registrationAction) {
                RegistrationActionEnum.REGISTER -> {
                    registerCallback
                }
                RegistrationActionEnum.UNREGISTER -> {
                    unregisterCallback
                }
            }
            try {
                urls.forEachIndexed { _, url ->
                    val requestUrl = "$url/${registrationAction.value}/$userId"
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
                                    sessionUserId,
                                    emptyIfNull(clientAppId),
                                    emptyIfNull(clientAppVersion),
                                    Util.getOsType(),
                                    emptyIfNull(Util.getOsVersion()),
                                    emptyIfNull(deviceModel),
                                    emptyIfNull(appToken),
                                    deviceWidth,
                                    deviceHeight
                                )
                                Log.d(registrationAction.value, "$requestUrl : $json")
                                callback?.info("$requestUrl : $json")
                                sink.write(json.toByteArray())
                            }
                        }).build()
                    var retries = 3
                    val sleep = 5000
                    while (retries-- > 0) {
                        try {
                            try {
                                val response = client.newCall(request).execute()
                                Log.i(registrationAction.value, "${registrationAction.value}: " + response.code + " ")
                                if (response.isSuccessful) {
                                    callback?.success("${response.code}")
                                    retries = 0
                                } else {
                                    callback?.failed("${response.code}")
                                }
                            } catch (ignored: IOException) {
                                Log.e(registrationAction.value, "${registrationAction.value} exception 1: $ignored")
                            }
                            Thread.sleep(sleep.toLong())
                        } catch (ignored: InterruptedException) {
                            Log.e(registrationAction.value, "${registrationAction.value} exception 2: $ignored")
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(registrationAction.value, "${registrationAction.value} exception 3: $ex")
                Log.e(registrationAction.value, "Failed to run ${registrationAction.value}: " + ex.message)
                callback?.failed("Failed to run ${registrationAction.value}: " + ex.message)
            }
            Log.d(registrationAction.value, "Stopping ${registrationAction.value}.")
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

    private fun createEmitter(
        url: String?,
        context: Context?,
        trackingCallback: Callback?
    ): Emitter {
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