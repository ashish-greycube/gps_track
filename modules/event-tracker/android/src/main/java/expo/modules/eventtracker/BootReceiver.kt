package expo.modules.eventtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Play Services location/activity PendingIntent registrations are cleared on reboot, so
 * if tracking was active before the device restarted we re-register them here.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action == Intent.ACTION_BOOT_COMPLETED ||
      action == "android.intent.action.QUICKBOOT_POWERON"
    ) {
      if (EventTrackerRegistrar.isActive(context)) {
        EventTrackerRegistrar.start(context)
        Log.d(EventTrackerRegistrar.TAG, "Re-registered event tracking after boot")
      }
    }
  }
}
