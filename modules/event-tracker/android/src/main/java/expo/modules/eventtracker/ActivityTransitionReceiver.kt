package expo.modules.eventtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Woken by Activity Recognition when the user starts/stops moving. Grabs one fresh
 * location and records it — this fills the gaps between throttled batched updates so a
 * "user just arrived / just left" moment always produces a point.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (!ActivityTransitionResult.hasResult(intent)) return
    val result = ActivityTransitionResult.extractResult(intent) ?: return
    if (result.transitionEvents.isEmpty()) return

    val pending = goAsync()
    val appContext = context.applicationContext
    try {
      LocationServices.getFusedLocationProviderClient(appContext)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .addOnSuccessListener { loc ->
          if (loc != null) {
            Thread {
              try {
                EventLocationDbHelper(appContext)
                  .insertLocation(loc.latitude, loc.longitude, loc.time, "ACTIVITY_TRANSITION")
                Log.d(EventTrackerRegistrar.TAG, "Saved activity-transition location")
              } finally {
                pending.finish()
              }
            }.start()
          } else {
            pending.finish()
          }
        }
        .addOnFailureListener {
          Log.e(EventTrackerRegistrar.TAG, "getCurrentLocation failed on transition", it)
          pending.finish()
        }
    } catch (e: SecurityException) {
      Log.e(EventTrackerRegistrar.TAG, "Missing location permission on transition", e)
      pending.finish()
    }
  }
}
