package expo.modules.eventtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.LocationResult

/**
 * Woken by Play Services when batched Fused Location updates are available — even if the
 * app process was dead. Writes each point to SQLite and lets the process die again.
 */
class LocationUpdateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val result = LocationResult.extractResult(intent) ?: return
    if (result.locations.isEmpty()) return

    // goAsync() keeps the receiver alive (~10s) so the DB write can finish off the main thread.
    val pending = goAsync()
    val appContext = context.applicationContext
    Thread {
      try {
        val db = EventLocationDbHelper(appContext)
        for (loc in result.locations) {
          db.insertLocation(loc.latitude, loc.longitude, loc.time, "PASSIVE_UPDATE")
        }
        Log.d(EventTrackerRegistrar.TAG, "Saved ${result.locations.size} passive location(s)")
      } catch (e: Exception) {
        Log.e(EventTrackerRegistrar.TAG, "Failed to save passive locations", e)
      } finally {
        pending.finish()
      }
    }.start()
  }
}
