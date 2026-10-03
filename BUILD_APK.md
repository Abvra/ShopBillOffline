# Build APK

Open this folder in Android Studio 2026.1.x or newer.

The project uses:
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk 35
- minSdk 23
- Java source compatibility suitable for JDK 17+

Then use:
Build > Build APK(s)

The generated debug APK should be under:
app/build/outputs/apk/debug/app-debug.apk

Default Admin PIN: 1234

Important:
1. The thermal printer must first be paired in Android Bluetooth settings.
2. In the app, open Admin > Bluetooth Printer and select the paired device.
3. The project intentionally has no API/server/cloud dependency for normal billing.
