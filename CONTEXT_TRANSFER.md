# GPS Tracking App — Context Transfer

## Project
Expo SDK 54 + React Native GPS tracking app with a custom native module (`motion-tracker`) built in Kotlin (Android) and Swift (iOS).

**Package:** `com.dev.greycube.gps_traking`  
**Bundle ID:** `com.dev.greycube.gps-traking`  
**EAS Project ID:** `5349c17c-b198-492e-abca-0400c7541994`

---

## What Was Done This Session

### 1. Native Module — Android (Kotlin) — COMPLETE

**Files modified:**
- `modules/motion-tracker/android/.../MotionTrackerModule.kt`
  - Added `ensureNotificationChannel()` called BEFORE `startForegroundService()` — fixes first-notification silent-drop bug on Samsung/Xiaomi
  - Notification channel pre-created so it's fully registered by the time `onCreate` calls `startForeground`
- `modules/motion-tracker/android/.../TrackingService.kt`
  - Changed `private val CHANNEL_ID` → `companion object { const val CHANNEL_ID }` so the module can reference it

**Architecture (Android):**
- `TrackingService.kt` — Foreground service, two modes:
  - **MOVING**: continuous GPS every 3 min (HIGH_ACCURACY), saves as `MOVING_LOOP`
  - **STILL**: GPS paused, AlarmManager heartbeat every 3 min → `fetchSingleLocation()`, saves as `STILL_HEARTBEAT`
- `ActivityReceiver.kt` — Event-driven Activity Recognition Transitions (STILL/WALKING/IN_VEHICLE)
- `LocationDBHelper.kt` — SQLite (`LocationTracker.db`, table: `locations`)
- `BootReceiver.kt` — Auto-restart after device reboot

### 2. Native Module — iOS (Swift) — COMPLETE

**Files modified:**
- `modules/motion-tracker/ios/LocationTracker.swift` — Full rewrite based on Apple docs
  - **MOVING mode**: `kCLLocationAccuracyBest`, 20m filter, saves every 3 min → `MOVING_LOOP`
  - **STILL mode**: `kCLLocationAccuracyKilometer`, 500m filter, Timer every 3 min → `requestLocation()` one-shot → `STILL_HEARTBEAT`
  - `CMMotionActivityManager` for STILL/MOVING detection
  - `startMonitoringSignificantLocationChanges` — can relaunch app after termination
  - `activityType = .otherNavigation` — Apple-recommended for power optimisation
  - `pausesLocationUpdatesAutomatically = false` — we manage pausing ourselves
  - `locationManagerDidPauseLocationUpdates` — restarts if iOS tries to pause
  - `heartbeatPending` flag — prevents passive STILL updates from being saved
  - Auto-starts via `locationManagerDidChangeAuthorization` after permission grant

**Known iOS limitation vs Android:**
- Force-quit stops tracking (Android foreground service survives)
- Significant location changes can relaunch app, but requires `UIApplicationLaunchOptionsLocationKey` handling in AppDelegate (needs Expo config plugin — NOT YET DONE)

**`app.json` iOS changes:**
- Added `NSLocationAlwaysUsageDescription`
- Added `fetch` to `UIBackgroundModes` (alongside `location`)

### 3. JS Layer — COMPLETE

**`app/index.jsx`:**
- Full UI redesign — clean card layout, blue (#1A73E8) / red buttons
- Permission flow on startup: `requestAllPermissions()` in `useEffect`
  - Requests: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS` (batch)
  - Then separately: `ACCESS_BACKGROUND_LOCATION` (Android 11+ two-step requirement)
- `handleStartShield()` re-checks/requests permissions before starting service
- Alert with "Open Settings" if permanently denied
- "Powered by Greycube" footer → `https://greycube.in`
- Title: "GPS Tracking", subtitle: "This app will track your location every 5 minutes"

**`app/MapScreen.jsx`:**
- `useEffect` loads locations from `MotionTracker.getLocations()` on mount
- Camera centers on `cordData[0]`, polyline through all points (`#1A73E8`, width 5)
- Points badge in header is tappable — shows all saved points with timestamp/coords/source
- Empty state when no data
- Custom back button (no Stack header — `headerShown: false` in `_layout.jsx`)
- iOS branch uses `AppleMaps.View` (camera + polylines not yet implemented — user stopped this)

**`app/_layout.jsx`:**
- `StatusBar style="dark"`
- `Stack.Screen name="index"` → title: "GPS Tracking"
- `Stack.Screen name="MapScreen"` → `headerShown: false`

### 4. Google Maps Setup
- API key in `app.json`: `AIzaSyD1xrMjhfyVICCXqP3L-2PwpilxBIp4R1g`
- Key is restricted to EAS dev build SHA-1 — `npx expo run:android` won't show map tiles (debug keystore SHA-1 not registered)
- Fix: add debug keystore SHA-1 to Google Cloud Console, OR use EAS build

---

## Branding (all apps)
- Footer: "Powered by Greycube" → `https://greycube.in`
- Background: `#F0F4FF`, Primary: `#1A73E8`, Start: green, Stop: `#EA4335`
- Cards: white, `borderRadius: 20`, soft shadow
- SafeAreaView on every screen

---

## Build Commands
```bash
# Android (JS changes only — hot reload)
npm start

# Android (native changes — requires rebuild)
npm run android
# or
eas build --profile development --platform android --local

# iOS simulator
npx expo run:ios   # ios/ directory already exists

# iOS device (EAS)
eas build --profile development --platform ios --local
```

---

## Second module: `event-tracker` (Android-only, event-driven, NO notification)

New parallel module at `modules/event-tracker/` — an alternative to `motion-tracker` that eliminates the foreground service + persistent notification. Built on the "CallReceiver" pattern: manifest-registered receivers that the OS wakes from a dead process.

- **No** `TrackingService`, **no** notification, **no** `FOREGROUND_SERVICE_*` / `POST_NOTIFICATIONS` perms.
- Package `expo.modules.eventtracker`, native module name `EventTracker`, own SQLite DB `EventTracker.db`.
- JS API identical to motion-tracker (`startService`/`stopService`/`getLocations`) → drop-in swap.
- Kotlin: `EventTrackerModule` (bridge), `EventTrackerRegistrar` (registers Fused Location batched-updates PendingIntent + Activity Transition PendingIntent — both held by Play Services so they survive app kill), `LocationUpdateReceiver` (`PASSIVE_UPDATE`), `ActivityTransitionReceiver` (`ACTIVITY_TRANSITION`, one-shot `getCurrentLocation`), `EventLocationDbHelper`, `BootReceiver` (re-registers after reboot).
- **Trade-off (by design):** no notification + better battery, BUT cadence is opportunistic — background location is OS-throttled to ~a few updates/hour; Doze defers further. Not for guaranteed 3-min points.
- **Status:** scaffolded + wired into app UI, NOT yet built/tested on device. iOS not implemented (module declares only `android`+`web` platforms; importing on iOS will throw — guard with `Platform.OS === "android"`).
- **App wiring:** `app/index.jsx` has an engine selector (Foreground = motion-tracker, Event-Driven = event-tracker). `EventTracker` is `require()`d only on Android. Start/Stop act on the selected engine; the choice is passed to `MapScreen.jsx` via `?engine=` param so the map reads the matching DB. Lint passes.
- **IMPORTANT:** event-tracker adds NEW native Kotlin — needs a native rebuild (`npm run android`, not just `npm start`) to compile it and merge its AndroidManifest.

## What's Next / Pending
- [ ] Build & test `event-tracker` on a real Android device (`npm run android`) — verify receivers fire from killed state, points land in `EventTracker.db`
- [ ] Wire `event-tracker` into a test screen / decide which apps use it vs `motion-tracker`
- [ ] iOS simulator test — `npx expo run:ios` (was about to do this when session ended)
- [ ] `AppleMaps.View` camera + polylines in `MapScreen.jsx` for iOS
- [ ] Expo config plugin for `UIApplicationLaunchOptionsLocationKey` in AppDelegate (app relaunch after termination on iOS)
- [ ] Register debug keystore SHA-1 in Google Cloud Console (or just always use EAS builds)
