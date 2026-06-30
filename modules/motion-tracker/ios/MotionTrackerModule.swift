import ExpoModulesCore

public class MotionTrackerModule: Module {
    public func definition() -> ModuleDefinition {
        Name("MotionTracker")

        // Fires once per app process start — covers force-close/reopen,
        // reboot (user opens app), and iOS relaunch via significantLocationChanges.
        // No-op if isTrackingActive = false in UserDefaults.
        OnCreate {
            LocationTracker.resumeIfNeeded()
        }

        Function("startService") {
            LocationTracker.shared.startTracking()
        }

        Function("stopService") {
            LocationTracker.shared.stopTracking()
        }

        AsyncFunction("getLocations") { () -> [[String: Any]] in
            return LocationStore.shared.getAllLocations()
        }
    }
}
