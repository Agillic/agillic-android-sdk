# Agillic SDK for Android

The Agillic SDK enables you to utilize the Agillic platform from within your Android App.
The SDK currently includes the following functionality:

 * Register devices used by a recipient in your mobile application.
 * Register a recipient push notification token which enables your Agillic Solution to send push notifications to the recipient device.
 * Track recipient events. Tracking can be paused and resumed when requested by the user. Tracked events can be used to define [Target Groups](https://support.agillic.com/hc/en-gb/articles/360007001991-All-You-Need-to-Know-About-Target-Groups) in the Agillic Dashboard which can be used to direct targeted marketing and other communication.

Other useful information:
* Read more about the Agillic Platform on the [official Agillic website](https://agillic.com).
* And in our [Developer portal](https://developers.agillic.com).
* The Agillic SDK for iOS can be found here: [Agillic iOS SDK](https://github.com/agillic/agillic-ios-sdk/)

## Requirements

- Requires minimum Android 5.0+ (API level 21+)

## Installation

See the subsections below for details about different installation methods.
* [Add using Gradle](README.md#add-using-gradle-(recommended))
* [Import Manually](README.md#import-manually)

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

In order to use AgillicSDK you have to initialize and configure it first.

You can configure your Agillic instance in code:
* ``AGILLIC API KEY``
* ``AGILLIC API SECRET``
* ``AGILLIC SOLUTION ID``

See how to setup your Agillic Solution and obtain these values
in the [Agillic Solution Setup Guide](docs/AgillicSolutionSetup.md).

### Initialize in app

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

### Logging

_NOTE: Work in progress_

Most SDK functions accept a callback parameter which can be added in order to gain information on what's happening in the SDK and when. See the following example:

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

## Usage

### Register App Installation

**Prerequisites**
* You need to make sure to do this after `Agillic.configure()`.
* You must do this upon every launch before doing any [App View Tracking](README.md#app-view-tracking). 
* Register has to be done in your Sign Up/Sign In flow but also on you splash screen if users are automatically logged in with an Access Token.
* ``RECIPIENT ID`` - Has to match `RECIPIENT.EMAIL` in the Agillic Recipient Table

###### Register App Installation
```kotlin
Agillic.register(recipientId = "RECIPIENT ID", activity = requireActivity())
```

Each time an updated push notification token becomes available from Firebase, register() should be called again while passing the updated token.

### Register Push Token

**Prerequisites**
* [Setup the Firebase Cloud Messaging SDK](https://firebase.google.com/docs/cloud-messaging/android/client)
* Read the [AgillicPushNotificationSetup](docs/AgillicPushNotificationSetup.md#ReadingPushNotificationssentfromyourAgillicSolution) document to learn how to send push notifications to your Android application directly from your Agillic Solution.
_NOTE: This requires you to have already obtained the Recipient ID and that you store this across app sessions - this is currently only supported for unknown recipients._

##### Register App Installation

###### When new token is received
```kotlin
class FirebaseMessagingServiceImpl : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Agillic.register(recipientId = "RECIPIENT ID", pushNotificationToken = token, activity = <Activity>)
    }
}
```

###### When existing token is received
```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
    if (!task.isSuccessful) {
        return@OnCompleteListener
    }
    val token: String = task.result ?: return
    Agillic.register(recipientId = "RECIPIENT ID", pushNotificationToken = token, activity = <Activity>)
})
```

## Reading Push Notifications sent from Agillic

_NOTE: Work in progress_

Agillic push notifications always contain a data payload. Thus the notification will always be delivered to your onMessageReceived callback in your FirebaseMessagingService implementation.

override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    val data = remoteMessage.data
    val agillicPushId = data["agillic_push_id"]
    val agillicDeeplink = data["agillic_deep_link"]
    val title = data["title"]
    val body = data["body"]
    val image = data["image"]
}

## Opening Push Notifications sent from Agillic

When a push notification is clicked, while the application is running, and your launcher activity is set to launchMode singleTop, the data payload will be sent to your launcher activity's onNewIntent function

```kotlin
override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val extras = intent?.extras
        val data = extras?.get("data")
    }
```

If your launcher activity has another launchMode or if the application is not running, the data payload will be sent to your launcher activity's onCreate function

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent?.extras
        val data = extras?.get("data")
    }
```

### Opening Push Notifications sent from Agillic

The SDK can be used to track if a user has opened a Push Notification sent from Agillic. To do this, pass the push notification data payload to the handlePushNotificationOpened SDK function.

```kotlin
 override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val extras = intent?.extras
        val data = extras?.get("data")
        Agillic.handlePushNotificationOpened(data)
    }
```

## Handling deeplinking from Agillic

_NOTE: Work in progress_

### App View tracking

Track recipient behavior with App View Tracking

App View Tracking is typically located in your `Activity` or `Fragment` file, but can be used elsewhere if needed.

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

_TODO: Usage in combination with 'Deeplinking' and 'Dynamic Links'_

## Questions and Issues

Please provide any feedback via a [GitHub Issue](https://github.com/agillic/agillic-android-sdk/issues/new).

## Copyright and license

Agillic SDK for Android is available under the Apache 2.0 license. See [LICENSE](LICENSE) file for more info.

```
   Copyright 2021 Agillic

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

