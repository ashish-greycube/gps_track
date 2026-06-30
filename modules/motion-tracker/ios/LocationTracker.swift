import CoreLocation
import CoreMotion

// iOS equivalent of Android's TrackingService + ActivityReceiver + BootReceiver.
//
// MOVING mode → startUpdatingLocation (kCLLocationAccuracyBest, 20m filter)
//               saves every 3 min from didUpdateLocations → "MOVING_LOOP"
//
// STILL mode  → large distanceFilter (passive GPS keeps app alive)
//               Timer every 3 min → requestLocation() one-shot fetch
//               → didUpdateLocations → "STILL_HEARTBEAT"
//               Mirrors Android's AlarmManager heartbeat.
//
// Persistence: UserDefaults "isTrackingActive" — mirrors Android SharedPreferences.
//              stopTracking() sets it false; startTracking() sets it true.
//              locationManagerDidChangeAuthorization only auto-resumes if flag is true,
//              which fixes "activity permission appearing on Stop press" bug.
//
// Relaunch after termination / reboot:
//   startMonitoringSignificantLocationChanges runs in the background even when
//   the app is terminated. iOS relaunches the app with UIApplicationLaunchOptionsKey.location.
//   AppDelegate calls LocationTracker.resumeIfNeeded() which checks UserDefaults and
//   calls startTracking() if the flag is set. This mirrors Android's BootReceiver.
//
// Background: requires UIBackgroundModes: [location] in Info.plist
//             and allowsBackgroundLocationUpdates = true (set in init below).

public class LocationTracker: NSObject, CLLocationManagerDelegate {
    public static let shared = LocationTracker()

    private let locationManager = CLLocationManager()
    private let activityManager = CMMotionActivityManager()
    private let store           = LocationStore.shared

    private var isTracking       = false
    private var isMoving         = false
    private var heartbeatTimer   : Timer?
    private var heartbeatPending = false
    private var lastMovingSave   : Date?
    private let saveInterval     : TimeInterval = 180  // 3 minutes

    private let defaults    = UserDefaults.standard
    private let trackingKey = "isTrackingActive"

    private override init() {
        super.init()
        locationManager.delegate = self

        // Required for background location updates (Apple docs: must be true
        // when UIBackgroundModes contains "location")
        locationManager.allowsBackgroundLocationUpdates = true

        // We manage our own pause logic via STILL/MOVING modes;
        // disable automatic pausing so iOS never cuts our updates
        locationManager.pausesLocationUpdatesAutomatically = false

        // Shows the blue pill in the status bar — required by Apple guidelines
        locationManager.showsBackgroundLocationIndicator = true

        // Tells iOS the nature of movement so it can optimise power.
        locationManager.activityType = .otherNavigation
    }

    // MARK: - iOS equivalent of Android's BootReceiver
    // Called from AppDelegate when iOS relaunches the app via significant location change
    // or after a device reboot. Checks UserDefaults before resuming.

    public static func resumeIfNeeded() {
        guard UserDefaults.standard.bool(forKey: "isTrackingActive") else { return }
        NSLog("MotionTracker: Relaunch detected — resuming tracking")
        shared.startTracking()
    }

    // MARK: - Public API

    public func startTracking() {
        // Persist intent so iOS can resume after relaunch or reboot.
        // Set this BEFORE the auth check so that if we call requestAlwaysAuthorization(),
        // locationManagerDidChangeAuthorization will resume tracking once permission is granted.
        defaults.set(true, forKey: trackingKey)

        let status = locationManager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            locationManager.requestAlwaysAuthorization()
            return
        }

        guard !isTracking else { return }
        isTracking     = true
        lastMovingSave = nil

        // Significant-change service: does NOT drain battery,
        // can relaunch a terminated app — iOS equivalent of BootReceiver.
        // (UIBackgroundModes "location" is NOT required for this service.)
        locationManager.startMonitoringSignificantLocationChanges()

        // Initial STILL mode setup — done inline so the guard in enterStillMode()
        // (which requires isMoving=true) does not block the startup path.
        // Mirrors Android's onStartCommand boot branch that calls scheduleHeartbeat() directly.
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        locationManager.distanceFilter  = 100
        scheduleHeartbeat()

        locationManager.startUpdatingLocation() // keeps app alive in background
        fireHeartbeat()                         // immediate first fix — mirrors Android's fetchSingleLocation() on boot
        startMotionDetection()

        NSLog("MotionTracker: Tracking started")
    }

    public func stopTracking() {
        // Clear intent FIRST — prevents locationManagerDidChangeAuthorization
        // from auto-restarting tracking (fixes "activity permission on Stop press" bug).
        defaults.set(false, forKey: trackingKey)

        guard isTracking else { return }
        isTracking     = false
        isMoving       = false
        lastMovingSave = nil

        locationManager.stopUpdatingLocation()
        locationManager.stopMonitoringSignificantLocationChanges()
        activityManager.stopActivityUpdates()
        cancelHeartbeat()

        NSLog("MotionTracker: Tracking stopped")
    }

    // MARK: - MOVING / STILL mode switching

    private func enterMovingMode() {
        guard !isMoving else { return }
        isMoving = true
        cancelHeartbeat()

        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter  = 20      // update every 20 m when moving
        NSLog("MotionTracker: -> MOVING")
    }

    private func enterStillMode() {
        guard isMoving else { return }   // mirrors Android's if (isGpsRunning) check
        isMoving         = false
        heartbeatPending = false

        // Large filter: almost no passive updates — all STILL saves come
        // only from the timer-triggered requestLocation()
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        locationManager.distanceFilter  = 100

        scheduleHeartbeat()
        NSLog("MotionTracker: -> STILL")
    }

    // MARK: - Heartbeat timer (iOS equivalent of Android's AlarmManager + setAndAllowWhileIdle)
    // The Timer fires reliably in background because startUpdatingLocation() keeps
    // the app alive via UIBackgroundModes: location.

    private func scheduleHeartbeat() {
        cancelHeartbeat()
        let timer = Timer(timeInterval: saveInterval, repeats: true) { [weak self] _ in
            self?.fireHeartbeat()
        }
        // .common mode: timer fires even while UI is scrolling
        RunLoop.main.add(timer, forMode: .common)
        heartbeatTimer = timer
    }

    private func cancelHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    private func fireHeartbeat() {
        guard isTracking, !isMoving, !heartbeatPending else { return }
        heartbeatPending = true
        // One-shot location fetch — iOS equivalent of Android's getCurrentLocation()
        locationManager.requestLocation()
        NSLog("MotionTracker: Heartbeat fired")
    }

    // MARK: - CoreMotion activity detection
    // iOS equivalent of Android's Activity Recognition Transitions API.
    // startActivityUpdates is event-driven (not polled), fires when iOS detects a
    // transition between stationary/walking/automotive.

    private func startMotionDetection() {
        guard CMMotionActivityManager.isActivityAvailable() else {
            NSLog("MotionTracker: Motion unavailable — staying in STILL mode")
            return
        }

        activityManager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let self = self, let act = activity, self.isTracking else { return }

            let moving = act.walking || act.running || act.automotive || act.cycling

            if moving && !self.isMoving {
                self.enterMovingMode()
            } else if act.stationary && self.isMoving {
                self.enterStillMode()
            }
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isTracking, let location = locations.last else { return }

        if isMoving {
            // MOVING: throttle to one save per 3-minute window
            let now = Date()
            guard lastMovingSave == nil || now.timeIntervalSince(lastMovingSave!) >= saveInterval else { return }
            lastMovingSave = now
            saveLocation(location, source: "MOVING_LOOP")

        } else if heartbeatPending {
            // STILL: only save when the heartbeat timer requested this update
            heartbeatPending = false
            saveLocation(location, source: "STILL_HEARTBEAT")
        }
        // Passive still-mode updates (distance filter rarely lets these through) are ignored
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        heartbeatPending = false     // allow next heartbeat to retry
        NSLog("MotionTracker: Error: \(error.localizedDescription)")
    }

    // Apple docs: handle pause/resume so we know if iOS ever pauses despite our setting
    public func locationManagerDidPauseLocationUpdates(_ manager: CLLocationManager) {
        NSLog("MotionTracker: WARNING — iOS paused location updates")
        // Restart immediately; we explicitly opted out of auto-pause
        manager.startUpdatingLocation()
    }

    public func locationManagerDidResumeLocationUpdates(_ manager: CLLocationManager) {
        NSLog("MotionTracker: Location updates resumed")
    }

    // Auto-start once user grants permission — ONLY if user previously requested tracking.
    // The UserDefaults guard is what prevents CMMotionActivityManager from showing its
    // permission dialog when the user presses Stop (which triggers a permission status change).
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let wantsTracking = defaults.bool(forKey: trackingKey)
        if (status == .authorizedAlways || status == .authorizedWhenInUse) && !isTracking && wantsTracking {
            startTracking()
        }
    }

    // MARK: - Helpers

    private func saveLocation(_ location: CLLocation, source: String) {
        let ts = Int64(Date().timeIntervalSince1970 * 1000)
        store.insertLocation(
            latitude:  location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            timestamp: ts,
            source:    source
        )
        NSLog("MotionTracker: Saved \(source) → \(location.coordinate.latitude), \(location.coordinate.longitude)")
    }
}
