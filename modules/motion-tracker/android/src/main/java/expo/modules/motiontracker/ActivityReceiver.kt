package expo.modules.motiontracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)!!
            
            for (event in result.transitionEvents) {
                val serviceIntent = Intent(context, TrackingService::class.java)
                
                when (event.activityType) {
                    DetectedActivity.STILL -> {
                        Log.d("MotionTracker", "EVENT DETECTED: Entered STILL")
                        serviceIntent.action = "ACTION_STATE_STILL"
                    }
                    DetectedActivity.WALKING, DetectedActivity.IN_VEHICLE -> {
                        Log.d("MotionTracker", "EVENT DETECTED: Entered MOVING")
                        serviceIntent.action = "ACTION_STATE_MOVING"
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}