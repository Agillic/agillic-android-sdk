package com.agillic.app.sdk

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
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

object Agillic {
    private var agillicTracker: AgillicTrackerImpl? = null
    private var url: String
    private val requestSecurity = RequestSecurity.HTTPS
    private var collectorEndpoint = "snowplowtrack-eu1.agillic.net"
    private val service: ExecutorService? = null
    private var auth: BasicAuth? = null
    private val apiUrlFormat = "https://api%s-eu1.agillic.net"

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
            return
        }
        agillicTracker?.track(event)
    }

    fun register(
        recipientId: String,
        activity: Activity,
        pushNotificationToken: String? = null
    ) {
        if (auth == null || solutionId == null) {
            throw java.lang.RuntimeException("com.agillic.app.sdk.Agillic.configure() must be called before com.agillic.app.sdk.Agillic.Register()")
            return
        }
        //service = Executors.newSingleThreadExecutor();
        // service.shutdown();
        // the application id to attach to events
        // the namespace to attach to events
        // Register at and return a Tracker
        val emitter: Emitter = createEmitter(collectorEndpoint, activity)
        val subject: Subject = Subject.SubjectBuilder().build()
        subject.setUserId(recipientId)
        val tracker = createSnowPlowTracker(
            emitter,
            subject,
            "agillic",
            solutionId,
            activity
        )
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val deviceInfo = Util.getMobileContext(activity)
        val clientAppVersion: String = try {
            activity.packageManager
                .getPackageInfo(activity.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "NA"
        }
        RegisterTask(
            tracker,
            activity.packageName,
            clientAppVersion,
            recipientId,
            auth,
            pushNotificationToken,
            deviceInfo,
            displayMetrics
        ).execute(
            url
        )
        agillicTracker = AgillicTrackerImpl(tracker)
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

    internal class RegisterTask(
        var tracker: Tracker,
        var clientAppId: String?,
        var clientAppVersion: String?,
        var userId: String,
        var auth: BasicAuth?,
        var appToken: String?,
        var deviceInfo: SelfDescribingJson,
        var displayMetrics: DisplayMetrics?
    ) : AsyncTask<String, Int, String>() {
        override fun doInBackground(vararg urls: String): String? {
            // Can remove loop: Session sesion = tracker.session.loadFromFileFuture.get()
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
                            override fun writeTo(bufferedSink: BufferedSink) {
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
                                    displayMetrics?.widthPixels,
                                    displayMetrics?.heightPixels
                                )
                                Log.d("register", requestUrl + ": " + json)
                                bufferedSink.write(json.toByteArray())
                            }
                        }).build()
                    var retries = 3
                    val sleep = 5000
                    while (retries-- > 0) {
                        try {
                            try {
                                val response = client.newCall(request).execute()
                                Log.i("register", "register: " + response.code + " ")
                                if (response.isSuccessful) return "OK"
                                if (response.code >= 300) {
                                    val msg =
                                        "Client error: " + response.code + " " + response.body
                                            .toString()
                                    Log.e("register", "doInBackground: " + msg)
                                    return msg
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
                throw ex
            }
            Log.d("register", "Stopping registration.")
            return "Stopping"
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

    private fun createEmitter(url: String?, context: Context?): Emitter {
        // build an emitter, this is used by the tracker to batch and schedule transmission of events
        return Emitter.EmitterBuilder(url, context)
            .method(HttpMethod.GET)
            .security(requestSecurity)
            .callback(object : RequestCallback {
                // let us know on successes (may be called multiple times)
                override fun onSuccess(successCount: Int) {
                    Log.d("AgillicSDK:emitter", "Successfully sent $successCount events")
                }

                // let us know if something has gone wrong (may be called multiple times)
                override fun onFailure(
                    successCount: Int,
                    failedCount: Int
                ) {
                    Log.e(
                        "AgillicSDK:emitter",
                        "Successfully sent " + successCount + " events; failed to send " + failedCount + " events"
                    )
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
            .sessionCheckInterval(15)
            .platform(DevicePlatforms.Mobile)
            //.sessionCallbacks()
            .mobileContext(true)
            .geoLocationContext(true)
            .platform(DevicePlatforms.Mobile)
            .build()
    }
}