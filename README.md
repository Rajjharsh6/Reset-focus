# Reset Focus (work in progress)

Native Kotlin / Jetpack Compose / Material 3 Android app. Local-first (Room + DataStore), no network code.

## Build
1. Open the `ResetFocus` folder in Android Studio (Ladybug or newer). Let it sync; it will create the Gradle wrapper jar. Use JDK 17+.
2. Run on your Android 16 / HyperOS phone (USB debugging on), or Build > Build APK for a release APK (`app/build/outputs/apk/release`). The release build is signed with the debug key for personal sideloading.
3. First launch walks through each permission. On a sideloaded install, if Accessibility is greyed out: App info > menu > Allow restricted settings.
4. HyperOS: allow Autostart, set battery to "No restrictions", lock the app in Recents.

This code was written without being compiled in a build environment. Expect a few small Gradle/Kotlin fixes on first sync; send me the errors and I'll fix them.

## Implemented in this drop
Onboarding and permission panel, dashboard, screen-time analytics (UsageStatsManager), app blocking via AccessibilityService with interruption overlay, focus sessions (25/45/60/custom) with foreground-service timer, strict mode (typed phrase + wait + logged emergency exit), allow-list mode, weekly schedules (overnight supported), Urge Interceptor with 30/60 s pause, urge logging and recovery stats, settings, privacy page, data wipe.

## Not yet implemented
Website blocking (VPN DNS filter, custom domains, domain-list import, block page). The tab says so.
Also pending: unit tests, schedule-start notifications, polish pass on HyperOS.
