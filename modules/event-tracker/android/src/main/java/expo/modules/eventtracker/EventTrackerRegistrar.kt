package expo.modules.eventtracker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Registers/unregisters the OS-level event listeners that drive this module.
 *
 * The two PendingIntents below are held by Google Play Services, NOT by our process,
 * so they survive the app being swiped away or killed — Play Services wakes our
 * manifest receivers when an event fires. They do NOT survive a reboot, which is why
 * [BootReceiver] re-registers them.
 */
object EventTrackerRegistrar {
  const val TAG = "EventTracker"

  private const val PREFS = "EventTrackerPrefs"
  private const val KEY_ACTIVE = "isActive"

  // Fused location: aim for ~3 min, allow up to 10 min batching so Play Services can
  // deliver several points at once and let the radio sleep in between (battery win).
  private const val LOCATION_INTERVAL_MS = 180_000L
  private const val LOCATION_MIN_INTERVAL_MS = 120_000L
  private const val LOCATION_MAX_DELAY_MS = 600_000L

  fun start(context: Context) {
    setActive(context, true)
    registerLocationUpdates(context)
    registerActivityTransitions(context)
    Log.d(TAG, "Event tracking registered")
  }

  fun stop(context: Context) {
    setActive(context, false)
    unregisterLocationUpdates(context)
    unregisterActivityTransitions(context)
    Log.d(TAG, "Event tracking unregistered")
  }

  fun isActive(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

  private fun setActive(context: Context, active: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putBoolean(KEY_ACTIVE, active).apply()
  }

  // --- Fused location (batched, delivered to a receiver) ---

  private fun locationPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, LocationUpdateReceiver::class.java).apply {
      action = "expo.modules.eventtracker.LOCATION_UPDATE"
    }
    return PendingIntent.getBroadcast(
      context, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
  }

  private fun registerLocationUpdates(context: Context) {
    val request = LocationRequest.Builder(
      Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS
    )
      .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
      .setMaxUpdateDelayMillis(LOCATION_MAX_DELAY_MS)
      .build()
    try {
      LocationServices.getFusedLocationProviderClient(context)
        .requestLocationUpdates(request, locationPendingIntent(context))
    } catch (e: SecurityException) {
      Log.e(TAG, "Missing location permission for updates", e)
    }
  }

  private fun unregisterLocationUpdates(context: Context) {
    LocationServices.getFusedLocationProviderClient(context)
      .removeLocationUpdates(locationPendingIntent(context))
  }

  // --- Activity Recognition transitions ---

  private fun activityPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, ActivityTransitionReceiver::class.java).apply {
      action = "expo.modules.eventtracker.ACTIVITY_TRANSITION"
    }
    return PendingIntent.getBroadcast(
      context, 1, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
  }

  private fun registerActivityTransitions(context: Context) {
    val transitions = listOf(
      DetectedActivity.STILL,
      DetectedActivity.WALKING,
      DetectedActivity.IN_VEHICLE
    ).map { type ->
      ActivityTransition.Builder()
        .setActivityType(type)
        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        .build()
    }
    val request = ActivityTransitionRequest(transitions)
    try {
      ActivityRecognition.getClient(context)
        .requestActivityTransitionUpdates(request, activityPendingIntent(context))
    } catch (e: SecurityException) {
      Log.e(TAG, "Missing activity recognition permission", e)
    }
  }

  private fun unregisterActivityTransitions(context: Context) {
    ActivityRecognition.getClient(context)
      .removeActivityTransitionUpdates(activityPendingIntent(context))
  }
}
