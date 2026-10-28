# Recorder

A simple Android voice recorder built with Kotlin and Jetpack Compose.

## Features
- Tap the mic to record, tap stop to finish (live timer while recording)
- Recordings saved as high-quality AAC `.m4a` (128 kbps, 44.1 kHz) in app storage
- List of recordings with date and length
- Play / stop, share (WhatsApp, Drive, Telegram, …) and delete with confirmation
- Light and dark mode

## Get the APK
Every push runs the **Build APK** GitHub Actions workflow. Open the repo's
**Actions** tab → latest run → download the `recorder-debug-apk` artifact,
unzip it and install `app-debug.apk` on your phone (allow "Install unknown apps").

## Build locally
Open the project in Android Studio, or run:

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 35). Minimum Android version: 8.0 (API 26).
