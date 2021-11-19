# Configuration of the Agillic Solution

## Introduction

This section will help you to configure the Agillic Solution an
obtain the keys and id's you need to be able configure and initialize
the AgillicMobileSDK into your Android application.

---

To get started, login to your Agillic Solution and select **Settings** in the top right corner.

<img src="resources/solutionsetup1.png">

## SDK Configuration

In order to configure the SDK in your Android application you will need to reference your Agillic solutionId, apiKey and apiSecret.

Click "Push and SDK" in the left sidebar to view your staging and production solution IDs.

<img src="resources/solutionsetup10.png">

Click "API" in the left sidebar to view or create a developer key and secret.

<img src="resources/solutionsetup11.png">

## Push Notifications

Next, select **Push and SDK** in the left pane Menu, under **Integrations** and check the "enable push" checkbox

<img src="resources/solutionsetup2.png">

**Enter application name.**

<img src="resources/solutionsetup3.png">

**Enter Client Application id.** This should correspond to the package name of your Android application.

<img src="resources/solutionsetup4.png">

**Enter Service account and remember to click save.** This should correspond to the service account key of a Firebase service account authorized to manage Firebase features such as the Firebase Cloud Messaging API.
Access or create a service account by navigating to [Google Cloud Platform](https://console.cloud.google.com).
From there select your Firebase project. Then click IAM & Admin in the sidebar and then Service Accounts.

<img src="resources/solutionsetup5.png">

Continue using an existing service account or click "CREATE SERVICE ACCOUNT" to create a new one.

<img src="resources/solutionsetup6.png">

When you have a valid service account, click the actions button and then "Manage keys".

<img src="resources/solutionsetup7.png">

Click "ADD KEY" and then "Create new key".

<img src="resources/solutionsetup8.png">

In the dialog, ensure that JSON is selected and click create.

<img src="resources/solutionsetup9.png">

This will download a json file containing the service account key.

It is important that your Firebase project has the Cloud Messaging API enabled.

