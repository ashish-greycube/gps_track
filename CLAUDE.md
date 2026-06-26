# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GPS tracking mobile app built with Expo (SDK 54) and React Native. Uses a custom Expo native module (`motion-tracker`) that runs a foreground service to track device location with motion-aware battery optimization. The Android implementation is complete; iOS is stubbed.

## Commands

```bash
npm start              # Start Expo dev server
npm run android        # Build and run on Android
npm run ios            # Build and run on iOS
npm run web            # Start web dev server
npm run lint           # ESLint
```

EAS builds: `eas build --profile development|preview|production --platform android|ios`

## Architecture

```
app/index.jsx  →  MotionTrackerModule (TS bridge)  →  Native Module (Kotlin/Swift)
                                                          ↓
                                                   TrackingService (Android foreground service)
                                                          ↓
                                              ActivityReceiver ←→ LocationDBHelper (SQLite)
```

**JS Layer:** `app/index.jsx` calls three methods on the native module: `startService()`, `stopService()`, `getLocations()`.

**Native Module Bridge:** `modules/motion-tracker/src/MotionTrackerModule.ts` loads the native module via `requireNativeModule('MotionTracker')`. Web stubs exist but are non-functional.

**Android Native (Kotlin):** The core logic lives in `modules/motion-tracker/android/src/main/java/expo/modules/motiontracker/`:
- `MotionTrackerModule.kt` — Expo module exposing JS-callable functions
- `TrackingService.kt` — Foreground service with two modes: **Moving** (continuous GPS every 3 min, HIGH_ACCURACY) and **Still** (GPS paused, heartbeat location every 3 min via alarm)
- `ActivityReceiver.kt` — Broadcast receiver using Google Activity Transitions API (STILL, WALKING, IN_VEHICLE) to switch between modes
- `LocationDBHelper.kt` — SQLite wrapper for `LocationTracker.db`

**iOS Native (Swift):** `modules/motion-tracker/ios/` contains stub implementations only.

## Database

SQLite database `LocationTracker.db`, table `locations`:
| Column | Type | Description |
|---|---|---|
| id | INTEGER PK | Auto-increment |
| latitude | REAL | GPS latitude |
| longitude | REAL | GPS longitude |
| timestamp | INTEGER | Unix timestamp (ms) |
| source | TEXT | `MOVING_LOOP` or `STILL_HEARTBEAT` |

## Key Configuration

- **Android permissions** (in `app.json`): fine/coarse/background location, foreground service (location type), notifications, activity recognition, exact alarms
- **New Architecture** and **Hermes engine** enabled
- **React Compiler** enabled
- **TypeScript strict mode** with path alias `@/*` → project root
- **Expo Router** with typed routes for file-based navigation
