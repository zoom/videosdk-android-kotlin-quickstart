# Video SDK Android Quickstart 

This is a Sample App of the Android Zoom Video SDK built with Kotlin. With the use of this app, developers can quickly start
their Zoom Video SDK projects, integrate the SDK into their projects, or use as reference for best coding practices for our Android Video SDK. 

Use of this Sample App is subject to our [Terms of Use](https://www.zoom.com/en/trust/terms/).

## Installation

Clone this repo into your local environment:
```
$ git clone https://github.com/zoom/videosdk-android-kotlin-quickstart.git
```

Once cloned, navigate to the 'videosdk-android-quickstart' directory

You can use the `studio .` command to open it in Android Studio.

In the `env-sample` file found in `app/src/main/assets`, you can either enter a [generated JWTToken](https://developers.zoom.us/docs/video-sdk/auth/) or enter an Endpoint URL of a server of your choice, which the app will use to query for a JWTToken. Once your data is enter rename this file to `env`. 

> :warning: **Do not store credentials in plain text on production environments**

## Configuration
If you use the apps APIClient, the request query parameters and body structure can be edited to match your servers requirements in the `ApiService.kt` file. The current request structure is as follows:
```
curl --location --request POST 'http://ENDPOINT_URL/zoomtoken?token=&name=&password=' \
--header 'Content-Type: application/json' \
--data '{                        
    "body": {                    
        "sessionName" = "",
        "role" = 0,
        "userIdentity" = "",
        "sessionkey" = "",
        "geo_regions" = "",
        "cloud_recording_option" = 0,
        "cloud_recording_election" = 0,
        "telemetry_tracking_id" = "",
        "video_webrtc_mode" = 0,
        "audio_webrtc_mode" = 0
    }
}'
```

## Usage
After entering either a JWTToken or Endpoint URL and configuring your Endpoint,  you can start the Sample App by clicking Build and Run!