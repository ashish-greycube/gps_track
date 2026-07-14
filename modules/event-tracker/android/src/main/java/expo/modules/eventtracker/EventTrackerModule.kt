package expo.modules.eventtracker

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Event-driven, no-notification GPS tracker.
 *
 * Unlike the `motion-tracker` module (which runs a foreground Service + persistent
 * notification for guaranteed cadence), this module registers OS-level listeners and
 * then lets the process die. Android wakes it from a dead state when a location or
 * activity-transition event fires — the same pattern as a manifest-registered call
 * receiver. Trade-off: no notification and better battery, but cadence is opportunistic
 * (background location is throttled by the OS to a few updates per hour).
 *
 * Method names mirror `motion-tracker` so this is a drop-in swap on the JS side.
 */
class EventTrackerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("EventTracker")

    Function("startService") {
      appContext.reactContext?.let { EventTrackerRegistrar.start(it) }
      null
    }

    Function("stopService") {
      appContext.reactContext?.let { EventTrackerRegistrar.stop(it) }
      null
    }

    AsyncFunction("getLocations") {
      val context = appContext.reactContext
        ?: return@AsyncFunction emptyList<Map<String, Any>>()
      return@AsyncFunction EventLocationDbHelper(context).getAllLocations()
    }
  }
}
