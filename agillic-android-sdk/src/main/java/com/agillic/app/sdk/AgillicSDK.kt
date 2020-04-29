package com.agillic.app.sdk

import android.content.Context
import android.os.AsyncTask
import android.os.Build
import com.snowplowanalytics.snowplow.tracker.DevicePlatforms
import com.snowplowanalytics.snowplow.tracker.Emitter
import com.snowplowanalytics.snowplow.tracker.Emitter.EmitterBuilder
import com.snowplowanalytics.snowplow.tracker.Subject
import com.snowplowanalytics.snowplow.tracker.Subject.SubjectBuilder
import com.snowplowanalytics.snowplow.tracker.Tracker
import com.snowplowanalytics.snowplow.tracker.Tracker.TrackerBuilder
import com.snowplowanalytics.snowplow.tracker.emitter.BufferOption
import com.snowplowanalytics.snowplow.tracker.emitter.HttpMethod
import com.snowplowanalytics.snowplow.tracker.emitter.RequestCallback
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson
import com.snowplowanalytics.snowplow.tracker.utils.Util
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class AgillicSDK private constructor() {
    private var url: String
    private val collectorEndpoint = "https://snowplowtrack-eu1.agillic.net"
    private val service: ExecutorService? = null
    private var auth: BasicAuth? = null

    fun setDevApi() {
        url = String.format(apiUrlFormat, "dev")
    }

    fun setTestApi() {
        url = String.format(apiUrlFormat, "test")
    }

    fun setApi(api: String) {
        url = api
    }

    fun init(key: String, secret: String) {
        auth = getAuth(key, secret)
    }

    fun init(auth: BasicAuth?) {
        this.auth = auth
    }

    private fun shutdown() {
        service?.shutdownNow()
    }

    fun register(
        clientAppId: String,
        clientAppVersion: String?,
        solutionId: String,
        userId: String,
        pnToken: String?,
        context: Context
    ): AgillicTracker {
        //service = Executors.newSingleThreadExecutor();
        // service.shutdown();
        // the application id to attach to events
        // the namespace to attach to events
        // Register at and return a Tracker
        val emitter: Emitter = createEmitter(collectorEndpoint, context)
        val subject: Subject = SubjectBuilder().build()
        subject.setUserId(userId)
        val tracker = createSnowPlowTracker(
            emitter,
            subject,
            "agillic",
            solutionId,
            context
        )
        val deviceInfo = Util.getMobileContext(context)
        RegisterTask(tracker, clientAppId, clientAppVersion, userId, auth, pnToken, deviceInfo).execute(url)

        return AgillicTrackerImpl(tracker)
    }

    private fun getAuth(key: String, secret: String): BasicAuth {
        return BasicAuth(key, secret)
    }

    inner class BasicAuth(key: String, secret: String) {
        private var basicAuth: String = "Basic " + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString("$key:$secret".toByteArray())
        } else {
            android.util.Base64.encodeToString("$key:$secret".toByteArray(), android.util.Base64.DEFAULT);
        }

        fun getAuth(): String {
            return basicAuth
        }
    }

    internal inner class RegisterTask(
        var tracker: Tracker,
        var clientAppId: String?,
        var clientAppVersion: String?,
        var userId: String,
        var auth: BasicAuth?,
        var appToken: String?,
        var deviceInfo: SelfDescribingJson
    ) : AsyncTask<String, Int, String>()
    {
        override fun doInBackground(vararg urls: String): String? {
            // Can remove loop: Session sesion = tracker.session.loadFromFileFuture.get()
            while (! tracker.session.waitForSessionFileLoad()) {
                Logger.getLogger(this.javaClass.name).warning("Session still not loaded")
            }
            try {
                urls.forEachIndexed { _, url ->
                    val requestUrl = "$url/register/$userId"
                    val client = createHttpClient()
                    val request = Request.Builder().url(requestUrl)
                        .addHeader("Authorization", auth!!.getAuth()).put(object : RequestBody() {
                            override fun contentType(): MediaType {
                                return MediaType.get("application/json")
                            }

                            @Throws(IOException::class)
                            override fun writeTo(bufferedSink: BufferedSink) {
                                val json = String.format(
                                    "{\n" +
                                            "  \"appInstallationId\" : \"%s\",\n" +
                                            "  \"clientAppId\": %s ,\n" +
                                            "  \"clientAppVersion\": %s,\n" +
                                            "  \"deviceModel\": %s,\n" +
                                            "  \"pushNotificationToken\": %s,\n" +
                                            "  \"modelDimX\": %s,\n" +
                                            "  \"modelDimY\": %s,\n" +
                                            " \"last\": null" +
                                            "  }\n",
                                    tracker.session.userId,
                                    emptyIfNull(clientAppId),
                                    emptyIfNull(clientAppVersion),
                                    emptyIfNull(deviceInfo.map["model"] as String?),
                                    emptyIfNull(appToken),
                                    emptyIfNull(deviceInfo.map["dimX"] as String?),
                                    emptyIfNull(deviceInfo.map["dimY"] as String?)
                                )
                                bufferedSink.write(json.toByteArray())
                            }
                        }).build()
                    var retries = 3
                    var sleep = 1000
                    while (retries-- > 0) {
                        try {
                            try {
                                val response = client.newCall(request).execute()
                                if (response.isSuccessful) return "OK"
                                if (response.code() < 500) {
                                    val msg = "Client error: " + response.code()
                                    println(msg)
                                    return msg
                                }
                            } catch (ignored: IOException) {
                            }
                            Thread.sleep(sleep.toLong())
                            sleep *= 2
                        } catch (ignored: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            } catch (ex: Exception) {
                println("Failed to run registration: " + ex.message)
                throw ex
            }
            println("Stopping registration.")
            return "Stopping"
        }
    }

    private fun emptyIfNull(string: String?): String? {
        if (string != null) {
            return "\"" + string + "\"";
        }
        return string
    }

    protected fun createHttpClient(): OkHttpClient { // use OkHttpClient to send events
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    protected fun createEmitter(url: String?, context: Context?): Emitter {
        // build an emitter, this is used by the tracker to batch and schedule transmission of events
        return EmitterBuilder(url, context)
            .method(HttpMethod.POST)
            .callback(object : RequestCallback {
                // let us know on successes (may be called multiple times)
                override fun onSuccess(successCount: Int) {
                    println("Successfully sent $successCount events")
                }

                // let us know if something has gone wrong (may be called multiple times)
                override fun onFailure(
                    successCount: Int,
                    failedCount: Int
                ) {
                    System.err.println("Successfully sent " + successCount + " events; failed to send " + failedCount + " events")
                }
            })
            .option(BufferOption.DefaultGroup)
            .build()
    }

    protected fun createSnowPlowTracker(
        emitter: Emitter?,
        subject: Subject?,
        namespace: String?,
        appId: String?,
        context: Context?
    ): Tracker { // now we have the emitter, we need a tracker to turn our events into something a Snowplow collector can understand
        return TrackerBuilder(emitter, namespace, appId, context)
            .subject(subject)
            .base64(true) //
            .sessionContext(true)
            .sessionCheckInterval(15)
            //.sessionCallbacks()
            .mobileContext(true)
            .geoLocationContext(true)
            .platform(DevicePlatforms.Mobile)
            .build()
    }

    companion object {
        private const val apiUrlFormat = "https://api%s-eu1.agillic.net"
        val instance: AgillicSDK
            get() = AgillicSDK()
    }

    init {
        url = String.format(apiUrlFormat, "")
    }
}