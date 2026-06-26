package expo.modules.motiontracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "MotionTrackerSilentChannel"
    }

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var locationCallback: LocationCallback
    private var motionPendingIntent: PendingIntent? = null
    private var isGpsRunning = false
    private var isHeartbeat = false
    private val dbHelper by lazy { LocationDbHelper(this) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate(){
        super.onCreate()
         createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracker Active")
            .setContentText("Monitoring location in the background...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("MotionTracker", "User swiped app away. Killing tracking and notification.")
        stopLocationTracking()
        cancelHeartbeat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
        stopMotionDetection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when(intent?.action){
            "ACTION_STATE_STILL"->{
                if (isGpsRunning) {
                    Log.d("MotionTracker","Service Command: STILL -> Stopping GPS, Starting Heartbeat")
                    stopLocationTracking()
                    scheduleHeartbeat() 
                } else {
                    // It's already paused, so ignore the duplicate 5-second spam
                    Log.d("MotionTracker", "Already STILL. Ignoring duplicate command.")
                }
            }
            "ACTION_STATE_MOVING"->{
                Log.d("MotionTracker","Service Command: MOVING -> Start GPS loop")
                cancelHeartbeat() // NEW: Stop the timer
                startLocationTracking()
            }
            "ACTION_HEARTBEAT"->{
                Log.d("MotionTracker","Service Command: HEARTBEAT -> Fetching single location")
                fetchSingleLocation() // NEW: Grab one coordinate
                scheduleHeartbeat()   // NEW: Reschedule for the next 3 mins
            }
            else -> {
                Log.d("MotionTracker","Service Booting Up")
                fetchSingleLocation()
                startMotionDetection()
                scheduleHeartbeat()
            }
        }

        return START_STICKY 
    }

    override fun onDestroy() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        cancelHeartbeat()
        stopMotionDetection()
        super.onDestroy()
        Log.d("MotionTracker", "TrackingService destroyed, stopped motion updates.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Motion Tracker Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // --- GPS CONTINUOUS LOOP (MOVING) ---
    private fun startLocationTracking() {

          if (isGpsRunning){
                Log.d("MotionTracker","GPS is already running ignore duplicate start command")
                return
            }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 180000L)
            .setMinUpdateIntervalMillis(180000L)
            .build()
            
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    val timestamp = System.currentTimeMillis()
                    dbHelper.insertLocation(location.latitude, location.longitude, timestamp , "MOVING_LOOP")
                    Log.d("MotionTracker", "Loop Location update: ${location.latitude}, ${location.longitude}")
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isGpsRunning = true
            Log.d("MotionTracker", "GPS Loop Started ")
        } catch(e: SecurityException) {
            Log.e("MotionTracker", "Failed to request location updates.", e)
        }
    }

    private fun stopLocationTracking(){
        if(::locationCallback.isInitialized){
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isGpsRunning = false
            Log.d("MotionTracker","GPS loop paused to save battery")
        }
    }

    // --- HEARTBEAT LOGIC (STILL) ---
    private fun scheduleHeartbeat() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TrackingService::class.java).apply { action = "ACTION_HEARTBEAT" }
        val pendingIntent = PendingIntent.getService(this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 3 minutes (180,000 milliseconds) from exactly right now
        val triggerTime = SystemClock.elapsedRealtime() + 180000L

        // setExactAndAllowWhileIdle pierces through deep sleep/doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun cancelHeartbeat() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TrackingService::class.java).apply { action = "ACTION_HEARTBEAT" }
        val pendingIntent = PendingIntent.getService(this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    private fun fetchSingleLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val timestamp = System.currentTimeMillis()
                        dbHelper.insertLocation(location.latitude, location.longitude, timestamp , "STILL_HEARTBEAT")
                        Log.d("MotionTracker", "Heartbeat Location: ${location.latitude}, ${location.longitude}")
                    } else {
                        Log.w("MotionTracker", "Heartbeat Location returned null.")
                    }
                }
        } catch (e: SecurityException) {
            Log.e("MotionTracker", "No permission for heartbeat location.", e)
        }
    }

    // --- MOTION DETECTOR ---
    private fun startMotionDetection() {
    val client = ActivityRecognition.getClient(this)
    val intent = Intent(this, ActivityReceiver::class.java)
    motionPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    // 1. Define the exact events you care about
    val transitions = mutableListOf<ActivityTransition>()
    
    // Event A: The user sits down
    transitions.add(
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
    )
    
    // Event B: The user starts walking
    transitions.add(
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
    )
    
    // Event C: The user starts driving
    transitions.add(
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()
    )

    val request = ActivityTransitionRequest(transitions)

    // 2. Subscribe to the events (NO MORE 5000L TIMER!)
    try {
        client.requestActivityTransitionUpdates(request, motionPendingIntent!!)
        Log.d("MotionTracker", "Event-Driven Transition API Started")
    } catch (e: SecurityException) {
        Log.e("MotionTracker", "Permission missing for transitions", e)
    }
}
private fun stopMotionDetection() {
    motionPendingIntent?.let { pendingIntent ->
        val client = ActivityRecognition.getClient(this)
        client.removeActivityTransitionUpdates(pendingIntent)
        Log.d("MotionTracker", "Activity Transitions Unregistered.")
    }
}
}