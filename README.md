# xNotificationSystem

A dynamic-island style notification overlay for Android. It draws a floating island above
the status bar, intercepts notifications and turns them into animated cards — inspired by
the "Coucou" video concept and built with an AccessibilityService host so it survives
low-memory kills.

## Features

- **Island overlay** — expands with spring animations, shows sender, app icon and a preview,
  collapses back to a pill.
- **Notification capture** — a NotificationListenerService feeds the island; grouped and
  per-app rules decide what is shown.
- **Card styles** — typewriter text, progress, media, call and custom card types.
- **Mascot** — an animated pixel character that reacts to incoming events.
- **Starfield background** and a small sound-effect layer.
- **AOD overlay** and **extra dim** quick tools.
- **Test receiver** — `adb shell am broadcast` triggers a card, so the UI can be tested
  without waiting for real notifications.
- **Accessibility host** — the overlay service is bound to an AccessibilityService, which
  keeps it alive when the app process is killed.

## Tech

Kotlin · Jetpack Compose · NotificationListenerService · AccessibilityService ·
WindowManager overlays · `minSdk 28`, `targetSdk 34`

## Architecture

```
app/src/main/java/com/maiconerick/xnotificationsystem/
├── IslandService.kt              foreground overlay service
├── IslandAccessibilityService.kt accessibility host that keeps the overlay alive
├── IslandRuntime.kt              island state machine
├── OverlayHost.kt, AodOverlay.kt window management, always-on display, extra dim
├── HubClient.kt                  optional local hub integration
├── PlayReceiver.kt               adb-triggered demo cards
└── island/                       Cards, Island UI, mascot, starfield, typewriter, settings
```

## Permissions

Notification listener access and accessibility service access are required and granted
manually by the user. Overlay permission is requested on first launch.

## Build

Requirements: JDK 17, Android SDK 34.

```bash
# local.properties
sdk.dir=/path/to/Android/Sdk

./gradlew assembleDebug
```

## License

MIT — see [LICENSE](LICENSE).
