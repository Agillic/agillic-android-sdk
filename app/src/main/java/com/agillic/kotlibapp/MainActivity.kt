package com.agillic.kotlibapp

import android.app.Activity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.DisplayMetrics
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.tasks.OnCompleteListener
import com.agillic.app.sdk.AgillicSDK
import com.agillic.app.sdk.AgillicTracker
import com.google.firebase.FirebaseApp
import com.google.firebase.iid.FirebaseInstanceId

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    // From build properties
    var clientAppId : String = "httpV1" // This Applications unique id.
    var clientAppVersion : String? = "1.0" // This Applications version
    var userId : String = "dennis.schafroth@agillic.com" // Retrieved from login
    var apnToken : String? = null
    var solutionId : String = "15arnn5" // Passed down in/after login;
    var key = "VIP4hwIKU1GZ"
    var secret = "gUItpLA0U0PGsvYZ"
    var tracker : AgillicTracker? = null;
    var sdk: AgillicSDK? = null
    var TAG = "MainActivity";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        FirebaseApp.initializeApp(applicationContext)
        //initAgillicSDK()
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                apnToken = task.result?.token

                // Log and toast
                val msg = getString(R.string.msg_token_fmt, apnToken)
                Log.d(TAG, msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            })

    }

    fun initAgillicSDK() {
        synchronized(this) {
            if (sdk == null) {
                sdk = AgillicSDK.instance
                sdk!!.init(key, secret)
                //sdk!!.setCollector("https://snowplowreader-eu1.agillic.net");
            }
            val displayMetrics = DisplayMetrics()
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            tracker = sdk!!.register(
                clientAppId,
                clientAppVersion,
                solutionId,
                userId,
                apnToken,
                applicationContext,
                displayMetrics
            )
        }
    }
    override fun onStart() {
        super.onStart()
    }
    override fun onResume() {
        super.onResume()
    }
    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
    }

        override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
