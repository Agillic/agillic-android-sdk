# Agillic SDK for Android

> __Please note__ The app SDK will be deprecated with the release of 23.0 in January 2023.  
> Use the [Register API](https://developers.agillic.com/apis/register-api#) instead

The Agillic SDK enables you to utilize the Agillic platform from within your Android App.
The SDK currently includes the following functionality:

 * Register devices used by a recipient in your mobile application.
 * Register a recipient push notification token which enables your Agillic Solution to send push notifications to the recipient device.
 * Track recipient events. Tracking can be paused and resumed when requested by the user. Tracked events can be used to define [Target Groups](https://support.agillic.com/hc/en-gb/articles/360007001991-All-You-Need-to-Know-About-Target-Groups) in the Agillic Dashboard which can be used to direct targeted marketing and other communication.
 * Track if an Agillic push notification has been opened

Other useful information:
* Read more about the Agillic Platform on the [official Agillic website](https://agillic.com).
* And in our [developer portal](https://developers.agillic.com).
* [Agillic Help Center](https://support.agillic.com/)
* The Agillic SDK for iOS can be found here: [Agillic iOS SDK](https://github.com/agillic/agillic-ios-sdk/)

## Requirements

- Requires minimum Android 5.0+ (API level 21+)

## Installation

### Add using Gradle (recommended)

Learn how to [Add SDK as a dependency using Gradle](https://developer.android.com/studio/build/dependencies)

###### Add the maven jitpack repository to your root settings.gradle file
```bash
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        jcenter()

        //Add this line
        maven { url "https://jitpack.io" }
    }
}

```

###### Add this to your build.gradle
```bash
dependencies {
    implementation 'com.github.agillic:agillic-android-sdk:0.8'
}
```

### Import manually
_NOTE: This is not the recommended method._ 

* [Importing SDK manually using Android Studio](https://developer.android.com/studio/projects/android-library#psd-add-dependencies)

## Initializing the Agillic SDK

In order to use the SDK you have to initialize and configure it first.

You can configure your Agillic instance in code using the following credentials:
* ``AGILLIC API KEY``
* ``AGILLIC API SECRET``
* ``AGILLIC SOLUTION ID``

See how to setup your Agillic Solution and obtain these values
in the [Agillic Solution Setup Guide](docs/AgillicSolutionSetup.md).

### Initialization

Start by importing the Agillic Module into your app component

```kotlin
import Agillic
```

Initialize and configure the Agillic SDK upon launch

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Agillic.configure(apiKey = "AGILLIC API KEY", apiSecret = "AGILLIC API SECRET", solutionId = "AGILLIC SOLUTION ID")
    }
}
```

The Agillic instance is now ready to use.

## Usage

### Register app Installation

**Prerequisites**
* You need to make sure to do this after `Agillic.configure()`.
* You must do this upon every launch before doing any [App View Tracking](README.md#app-view-tracking). 
* Register has to be done in your Sign Up/Sign In flow but also on you splash screen if users are automatically logged in with an Access Token.
* ``RECIPIENT ID`` - Has to match the selected ID of the Reicipeint in Agillic. [Read how to change the RecipientID](https://support.agillic.com/hc/en-gb/articles/360014805631-How-to-Change-the-Recipient-ID)

###### Register app installation

```kotlin
Agillic.register(recipientId = "RECIPIENT ID", activity = <Activity>)
```

### Register push token

**Prerequisites**
* [Setup the Firebase Cloud Messaging SDK](https://firebase.google.com/docs/cloud-messaging/android/client)
* Read the [AgillicPushNotificationSetup](docs/AgillicPushNotificationSetup.md#ReadingPushNotificationssentfromyourAgillicSolution) document to learn how to send push notifications to your Android application directly from your Agillic Solution.
_NOTE: This requires you to have already obtained the Recipient ID and that you store this across app sessions - this is currently only supported for unknown recipients._

##### Push token should be registered along with app installation

###### When retrieving existing push token on every app launch

```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
    if (!task.isSuccessful) {
        return@OnCompleteListener
    }
    val token: String = task.result ?: return
    Agillic.register(recipientId = "RECIPIENT ID", pushNotificationToken = token, activity = <Activity>)
})
```

###### When new push token is received from Firebase

```kotlin
class FirebaseMessagingServiceImpl : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Agillic.register(recipientId = "RECIPIENT ID", pushNotificationToken = token, activity = <Activity>)
    }
}
```

### Unregister Push Token

If you wish to remove a push token from an Agillic Recipient, you can use the `unregister` function. This should be done when a user is logging out of your app.

```kotlin
Agillic.unregister(recipientId = "RECIPIENT ID", pushNotificationToken = pushToken, context = context)
```

## Reading Push Notification sent from your Agillic

### Track Push Opened when clicked

The SDK can be used to track if a user has opened a Push Notification sent from Agillic. To do this, pass the push notification data payload, received when a push notification is clicked, to the `handlePushNotificationOpened` SDK function.

When a push notification is clicked, while the application is running, and your launcher activity is set to launchMode singleTop, the data payload will be sent to your launcher activity's onNewIntent function.

```kotlin
 override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Retrieve data payload
        val extras = intent?.extras

        // Retrieve each payload value (optional)
        val title = extras.get("title")
        val body = extras.get("body")
        val image = extras.get("image")
        val agillicPushId = extras.get("agillic_push_id")
        val onClick = extras.get("on_click")

        // Track push opened
        if (extras != null) {
            Agillic.handlePushNotificationOpened(extras)
        }
    }
```

If your launcher activity has another launchMode or if the application is not running, the data payload will be sent to your launcher activity's onCreate function.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve data payload
        val extras = intent?.extras

        // Retrieve each payload value (optional)
        val title = extras.get("title")
        val body = extras.get("body")
        val image = extras.get("image")
        val agillicPushId = extras.get("agillic_push_id")
        val onClick = extras.get("on_click")

        // Track push opened
        if (extras != null) {
            Agillic.handlePushNotificationOpened(extras)
        }
    }
```

### Read Push Payload when received (Handle deeplink from Agillic)

Agillic push notifications always contain a data payload. Thus the notification will always be delivered to your onMessageReceived callback in your FirebaseMessagingService implementation.
If the application receives a push notification while running in the foreground, you will need to generate your own notification in order to display it. See the sendNotification method [in the following example](https://github.com/firebase/quickstart-android/blob/320f5fb45f155de3daf8b997c3788a4a187a024d/messaging/app/src/main/java/com/google/firebase/quickstart/fcm/java/MyFirebaseMessagingService.java#L58-L101).

```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    val data = remoteMessage.data
    val title = data["title"]
    val body = data["body"]
    val image = data["image"]
    val agillicPushId = data["agillic_push_id"]
    val onClick = data["on_click"]
}
```

### Tracking App Views

Track recipient behavior with App View Tracking

App View Tracking is typically done in your `Activity` or `Fragment` file, but can be used elsewhere if needed.

```kotlin
override fun onResume() {
    super.onResume()
    val appView = AgillicAppView(screenName = "app://sublevel-1/sublevel-2")
    Agillic.track(appView)
}
```

The ``screenName`` is the value that can be matched in the Condition Editor.
We suggest to use a hierarchical naming convention, prefixed with `app://` ex:
* ``app://sublevel-1/sublevel-2/...``

*Examples of usage:*
* ``app://landingpage``
* ``app://landingpage/sign-up/step-2``
* ``app://dashboard``
* ``app://product-offers``
* ``app://product-offers/21``
* ``app://menu/profile/edit``

### Logging

_NOTE: Work in progress_

Most SDK functions accept an optional callback parameter which can be added in order to gain information on what's happening in the SDK and when. See the following example:

```kotlin
Agillic.register(
    recipientId = "<RECIPIENT_ID>",
    pushNotificationToken = "<PUSH_NOTIFICATION_TOKEN>",
    activity = this,
    registerCallback = object : Callback {
        override fun info(response: String) {}
        override fun failed(response: String) {}
        override fun success(response: String) {}
    }
)
```

## Questions and Issues

Please provide any feedback via a [GitHub Issue](https://github.com/agillic/agillic-android-sdk/issues/new).

## Copyright and license

Agillic SDK for Android is available under the Apache 2.0 license. See [LICENSE](LICENSE) file for more info.

```
   Copyright 2022 Agillic

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

