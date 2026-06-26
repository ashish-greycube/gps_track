import CoreLocation
import CoreMotion

// iOS equivalent of Android's TrackingService + ActivityReceiver.
//
// MOVING mode → startUpdatingLocation (kCLLocationAccuracyBest, 20m filter)
//               saves every 3 min from didUpdateLocations → "MOVING_LOOP"
//
// STILL mode  → large distanceFilter (passive GPS)
//               Timer every 3 min → requestLocation() one-shot fetch
//               → didUpdateLocations → "STILL_HEARTBEAT"
//               Mirrors Android's AlarmManager heartbeat.
//
// Background: requires UIBackgroundModes: [location] in Info.plist
//             and allowsBackgroundLocationUpdates = true (set below).
//
// App relaunch after termination: startMonitoringSignificantLocationChanges
//   can relaunch the app. Handle UIApplicationLaunchOptionsLocationKey
//   in AppDelegate to resume tracking (requires Expo config plugin).

class LocationTracker: NSObject, CLLocationManagerDelegate {
    static let shared = LocationTracker()

    private let locationManager = CLLocationManager()
    private let activityManager = CMMotionActivityManager()
    private let store           = LocationStore.shared

    private var isTracking       = false
    private var isMoving         = false
    private var heartbeatTimer   : Timer?
    private var heartbeatPending = false
    private var lastMovingSave   : Date?
    private let saveInterval     : TimeInterval = 180   // 3 minutes

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
        // OtherNavigation covers general asset/person tracking.
        locationManager.activityType = .otherNavigation
    }

    // MARK: - Public API

    func startTracking() {
        guard !isTracking else { return }

        let status = locationManager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            locationManager.requestAlwaysAuthorization()
            return
        }

        isTracking     = true
        lastMovingSave = nil

        // Significant-change service: does NOT require background mode,
        // can relaunch a terminated app (Apple docs table 1-1)
        locationManager.startMonitoringSignificantLocationChanges()

        enterStillMode()                     // default; motion upgrades to MOVING
        locationManager.startUpdatingLocation()
        startMotionDetection()

        NSLog("MotionTracker: Tracking started")
    }

    func stopTracking() {
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
        isMoving         = false
        heartbeatPending = false

        // Large filter: almost no passive updates — all STILL saves come
        // only from the timer-triggered requestLocation()
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        locationManager.distanceFilter  = 500

        scheduleHeartbeat()
        NSLog("MotionTracker: -> STILL")
    }

    // MARK: - Heartbeat timer (iOS equivalent of Android's AlarmManager)

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
        // One-shot location fetch — equivalent to Android's getCurrentLocation()
        locationManager.requestLocation()
        NSLog("MotionTracker: Heartbeat fired")
    }

    // MARK: - CoreMotion activity detection
    // Equivalent to Android's Activity Recognition Transitions API

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

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
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

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        heartbeatPending = false     // allow next heartbeat to retry
        NSLog("MotionTracker: Error: \(error.localizedDescription)")
    }

    // Apple docs: handle pause/resume so we know if iOS ever pauses despite our setting
    func locationManagerDidPauseLocationUpdates(_ manager: CLLocationManager) {
        NSLog("MotionTracker: WARNING — iOS paused location updates")
        // Restart immediately; we explicitly opted out of auto-pause
        manager.startUpdatingLocation()
    }

    func locationManagerDidResumeLocationUpdates(_ manager: CLLocationManager) {
        NSLog("MotionTracker: Location updates resumed")
    }

    // Auto-start once user grants permission
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        if (status == .authorizedAlways || status == .authorizedWhenInUse) && !isTracking {
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
