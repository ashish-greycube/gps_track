package expo.modules.motiontracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MotionTrackerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("MotionTracker")

    Function("startService") {
      val context = appContext.reactContext
      if (context != null) {
        ensureNotificationChannel(context)
        context.getSharedPreferences("MotionTrackerPrefs", android.content.Context.MODE_PRIVATE)
          .edit().putBoolean("isTrackingActive", true).apply()
        val intent = Intent(context, TrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
      }
      null
    }

    Function("stopService") {
      val context = appContext.reactContext
      if (context != null) {
        context.getSharedPreferences("MotionTrackerPrefs", android.content.Context.MODE_PRIVATE)
          .edit().putBoolean("isTrackingActive", false).apply()
        val intent = Intent(context, TrackingService::class.java)
        context.stopService(intent)
      }
      null
    }

    AsyncFunction("getLocations") {
      val context = appContext.reactContext
        ?: return@AsyncFunction emptyList<Map<String, Any>>()
      val dbHelper = LocationDbHelper(context)
      return@AsyncFunction dbHelper.getAllLocations()
    }
  }
}

private fun ensureNotificationChannel(context: Context) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(TrackingService.CHANNEL_ID) == null) {
      val channel = NotificationChannel(
        TrackingService.CHANNEL_ID,
        "Motion Tracker Channel",
        NotificationManager.IMPORTANCE_LOW
      )
      manager.createNotificationChannel(channel)
    }
  }
}
