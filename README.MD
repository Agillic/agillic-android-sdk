# Agillic SDK for Android

This is a minimalistics SDK to support the Agillic Platform to:

 * Register devices used by a recipient,
 * Register the token required to send a Push Notification to a device using Apple PN on iOS or Firebase Cloud Messaging for Android.
 * Track recipient behavior, which can be used in Condition Editor for selection into Target Group

## Using the Agillic SDK

Create an instance of the Agillic SDK. No configuration except authentication is required for normal use. Authentication must be done before registration (or with registerWithAuthentication):

```kotlin
var sdk: agillicSDK? = AgillicSDK()
sdk.init(key, secret)
sdk.setAuth(BasicAuth(user: key, password: secret))
            val displayMetrics = DisplayMetrics()
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            tracker = sdk!!.register(
                clientAppId,
                clientAppVersion,
                solutionInfo.solutionId,
                userId,
                apnToken,
                applicationContext,
                displayMetrics
            )
```
For each unique Application view setup a constant UUID and view/screen name and use the returned AgillicTracker to send App View tracking to the Agillic Platform on this App view usage:

```
tracker?.track(ScreenView().id(UUID).name(screenViewName))
```
The screenViewName is the value that can be matched in the Condition Editor and the suggested name convention to use some hierarchical app/sublevel-1/sublevel-2/...
