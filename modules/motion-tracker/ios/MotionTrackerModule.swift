import ExpoModulesCore

public class MotionTrackerModule: Module {
    public func definition() -> ModuleDefinition {
        Name("MotionTracker")

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
