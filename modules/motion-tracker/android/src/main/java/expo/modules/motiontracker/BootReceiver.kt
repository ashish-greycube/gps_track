package expo.modules.motiontracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences("MotionTrackerPrefs", Context.MODE_PRIVATE)
        val wasTracking = prefs.getBoolean("isTrackingActive", false)

        if (wasTracking) {
            Log.d("MotionTracker", "Boot detected — auto-resuming tracking (no user action needed)")
            val serviceIntent = Intent(context, TrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
